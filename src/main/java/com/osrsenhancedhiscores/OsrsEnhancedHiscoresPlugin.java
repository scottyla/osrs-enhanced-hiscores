package com.osrsenhancedhiscores;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.ScriptEvent;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "OSRS Enhanced Hiscores",
	description = "Hiscores for your loot, collection log, quest times, diaries, combat achievements, kill counts and more",
	tags = {"loot", "tracker", "hiscores", "highscores", "collection", "log", "quest", "diary", "combat", "achievements", "kc", "pb", "xp", "gains", "hardcore", "ironman", "playtime"}
)
public class OsrsEnhancedHiscoresPlugin extends Plugin
{
	/**
	 * Client script that draws one item on a collection log page. Args: [widget, itemId, quantity, ...].
	 * There is no gameval constant for it.
	 */
	private static final int COLLECTION_LOG_ITEM_DRAW_SCRIPT = 4100;

	/** How often collected data is sent. Drops etc. are batched in between, so this bounds requests per player. */
	private static final int FLUSH_INTERVAL_SECONDS = 120;
	/** Ticks to wait after login before reading varps, so the server has sent them. */
	private static final int LOGIN_SETTLE_TICKS = 5;
	/** Max loot sources per request, to keep request bodies well under the server's size limit. */
	private static final int SOURCES_PER_REQUEST = 100;
	private static final int MAX_PENDING_DROPS = 500;
	/** Stats (24 skills etc.) change constantly while training; every 5 minutes (or on a level-up) is plenty for hiscores. */
	private static final long STATS_INTERVAL_MS = 300_000;
	/** How often to re-read quest states (about 30 seconds). Also re-read whenever quest points change. */
	private static final int QUEST_CHECK_TICKS = 50;
	/** "You have completed 12 elite Treasure Trails." */
	static final Pattern CLUE_COUNT = Pattern.compile(
		"You have completed ([0-9,]+) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OsrsEnhancedHiscoresConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private HiscoresClient hiscoresClient;

	@Inject
	private LootTrackerReader lootTrackerReader;

	@Inject
	private QuestTracker questTracker;

	@Inject
	private KillCountReader killCountReader;

	private ScheduledFuture<?> flushTask;

	// Everything below is only touched on the client thread, except where noted.

	/** The account currently logged in, or null when logged out or on a world we don't track. */
	private Identity identity;
	/** Profile key of {@link #identity}, readable from any thread for {@link #onConfigChanged}. */
	private volatile String activeProfileKey;
	private int settleTicks;
	private int ticksSinceQuestCheck;
	private boolean questCheckRequested;

	/** RuneScape profiles that have had a full loot tracker sync this session. */
	private final Set<String> fullySyncedProfiles = new HashSet<>();
	/** Loot tracker keys changed since the last flush. Written from whichever thread saves loot tracker config. */
	private final Set<String> dirtyLootKeys = ConcurrentHashMap.newKeySet();
	/** Kill count / PB names changed since the last flush (Chat Commands config). Any thread. */
	private final Set<String> dirtyKillCounts = ConcurrentHashMap.newKeySet();
	/** RuneScape profiles whose kill counts have been fully sent this session. */
	private final Set<String> killCountSyncedProfiles = new HashSet<>();
	/** Clue completion counts seen in chat, by name. */
	private final Map<String, Integer> pendingClueCounts = new HashMap<>();
	/** Real levels at the last StatChanged, to spot level-ups. Filled once the login has settled. */
	private final Map<Skill, Integer> knownLevels = new HashMap<>();
	private final List<Submission.LevelUp> pendingLevelUps = new ArrayList<>();

	private final List<Submission.Drop> pendingDrops = new ArrayList<>();
	private final Map<Integer, Integer> pendingCollectionLog = new HashMap<>();
	private final Map<Integer, Integer> sentCollectionLog = new HashMap<>();
	private String lastSentStatsJson;
	private long lastStatsSentAt;
	private final Map<Integer, Submission.ItemInfo> itemInfoCache = new HashMap<>();

	@Value
	private static class Identity
	{
		long accountHash;
		String username;
		String profileKey;
		String profileType;
		String gameMode;
	}

	@Provides
	OsrsEnhancedHiscoresConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsEnhancedHiscoresConfig.class);
	}

	@Override
	protected void startUp()
	{
		flushTask = executor.scheduleWithFixedDelay(
			() -> clientThread.invoke(this::flush),
			FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown()
	{
		if (flushTask != null)
		{
			flushTask.cancel(false);
			flushTask = null;
		}
		hiscoresClient.reset();
		clientThread.invoke(this::clearState);
	}

	private void clearState()
	{
		questTracker.setProfile(null);
		identity = null;
		activeProfileKey = null;
		settleTicks = 0;
		fullySyncedProfiles.clear();
		killCountSyncedProfiles.clear();
		dirtyLootKeys.clear();
		dirtyKillCounts.clear();
		pendingClueCounts.clear();
		knownLevels.clear();
		pendingLevelUps.clear();
		pendingDrops.clear();
		pendingCollectionLog.clear();
		sentCollectionLog.clear();
		lastSentStatsJson = null;
		itemInfoCache.clear();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			// Keep the current identity through LOADING/HOPPING; logout is handled in onGameStateChanged.
			return;
		}

		Identity current = readIdentity();
		if (!Objects.equals(current, identity))
		{
			// Send anything collected for the previous account before switching.
			flush();
			identity = current;
			activeProfileKey = current == null ? null : current.getProfileKey();
			questTracker.setProfile(activeProfileKey);
			ticksSinceQuestCheck = 0;
			settleTicks = LOGIN_SETTLE_TICKS;
			lastSentStatsJson = null;
			sentCollectionLog.clear();
			dirtyLootKeys.clear();
			dirtyKillCounts.clear();
			knownLevels.clear();
		}
		else if (settleTicks > 0 && --settleTicks == 0)
		{
			// First flush after login: initial stats, quests and the full loot history.
			for (Skill skill : Skill.values())
			{
				if (!"OVERALL".equals(skill.name()))
				{
					knownLevels.put(skill, client.getRealSkillLevel(skill));
				}
			}
			checkQuests();
			flush();
		}

		if (identity != null && settleTicks == 0)
		{
			questTracker.onTick(System.currentTimeMillis());
			if (questCheckRequested || ++ticksSinceQuestCheck >= QUEST_CHECK_TICKS)
			{
				checkQuests();
			}
		}
	}

	private void checkQuests()
	{
		questCheckRequested = false;
		ticksSinceQuestCheck = 0;
		if (config.enableSubmission() && config.submitQuests())
		{
			questTracker.check(System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Integer previous = knownLevels.get(event.getSkill());
		if (previous == null)
		{
			// Not settled after login yet: these are the initial values, not level-ups.
			return;
		}
		int level = event.getLevel();
		knownLevels.put(event.getSkill(), level);
		if (level > previous && identity != null && config.enableSubmission() && config.submitStats())
		{
			long now = System.currentTimeMillis();
			for (int l = previous + 1; l <= level; l++)
			{
				pendingLevelUps.add(new Submission.LevelUp(event.getSkill().name(), l, now));
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (identity == null || event.getType() != ChatMessageType.GAMEMESSAGE
			|| !config.enableSubmission() || !config.submitKillCounts())
		{
			return;
		}
		Matcher m = CLUE_COUNT.matcher(Text.removeTags(event.getMessage()));
		if (m.find())
		{
			pendingClueCounts.put("clue scrolls (" + m.group(2) + ")", Integer.parseInt(m.group(1).replace(",", "")));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Completing a quest awards quest points, so check right away to get an accurate completion time.
		if (event.getVarpId() == VarPlayerID.QP)
		{
			questCheckRequested = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN && identity != null)
		{
			flush();
			identity = null;
			activeProfileKey = null;
			questTracker.setProfile(null);
		}
	}

	private Identity readIdentity()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
		String gameMode = GameMode.resolve(client.getWorldType(), profileType);
		if (gameMode == null)
		{
			return null;
		}

		Player local = client.getLocalPlayer();
		long accountHash = client.getAccountHash();
		String profileKey = configManager.getRSProfileKey();
		if (local == null || local.getName() == null || accountHash == -1 || profileKey == null)
		{
			return null;
		}
		return new Identity(accountHash, local.getName(), profileKey, profileType.name(), gameMode);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		String group = event.getGroup();
		if (LootTrackerReader.LOOT_TRACKER_GROUP.equals(group))
		{
			// May fire off the client thread: Loot Tracker saves from its own executor.
			String profile = activeProfileKey;
			if (event.getNewValue() != null
				&& event.getKey().startsWith(LootTrackerReader.DROPS_PREFIX)
				&& profile != null && profile.equals(event.getProfile()))
			{
				dirtyLootKeys.add(event.getKey());
			}
		}
		else if (KillCountReader.KC_GROUP.equals(group) || KillCountReader.PB_GROUP.equals(group))
		{
			// Chat Commands saves these when the game prints a kill count or personal best.
			String profile = activeProfileKey;
			if (profile != null && profile.equals(event.getProfile()))
			{
				dirtyKillCounts.add(event.getKey());
			}
		}
		else if (OsrsEnhancedHiscoresConfig.GROUP.equals(group) && event.getProfile() == null)
		{
			// Any setting change: start fresh so newly enabled data gets sent in full.
			hiscoresClient.reset();
			clientThread.invoke(() ->
			{
				fullySyncedProfiles.clear();
				killCountSyncedProfiles.clear();
				questTracker.resendAll();
				questCheckRequested = true;
				dirtyLootKeys.clear();
				pendingDrops.clear();
				pendingCollectionLog.clear();
				sentCollectionLog.clear();
				lastSentStatsJson = null;
			});
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (identity == null || !config.enableSubmission() || !config.submitDrops()
			|| event.getType() == null || event.getName() == null)
		{
			return;
		}

		Map<Integer, Integer> quantities = new LinkedHashMap<>();
		Collection<ItemStack> items = event.getItems();
		for (ItemStack stack : items)
		{
			quantities.merge(itemManager.canonicalize(stack.getId()), stack.getQuantity(), Integer::sum);
		}
		if (quantities.isEmpty())
		{
			return;
		}

		Submission.Drop drop = new Submission.Drop();
		drop.setId(UUID.randomUUID().toString());
		drop.setType(event.getType().name());
		drop.setName(event.getName());
		drop.setCombatLevel(event.getCombatLevel());
		drop.setAmount(event.getAmount());
		drop.setTimestamp(System.currentTimeMillis());
		drop.setWorld(client.getWorld());
		quantities.forEach((id, qty) -> drop.getItems().add(new int[]{id, qty}));

		if (pendingDrops.size() >= MAX_PENDING_DROPS)
		{
			pendingDrops.remove(0);
		}
		pendingDrops.add(drop);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != COLLECTION_LOG_ITEM_DRAW_SCRIPT
			|| identity == null || !config.enableSubmission() || !config.submitCollectionLog())
		{
			return;
		}

		ScriptEvent scriptEvent = event.getScriptEvent();
		Object[] args = scriptEvent == null ? null : scriptEvent.getArguments();
		if (args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}

		int itemId = (Integer) args[1];
		int quantity = (Integer) args[2];
		if (quantity > 0 && !Objects.equals(sentCollectionLog.get(itemId), quantity))
		{
			pendingCollectionLog.put(itemId, quantity);
		}
	}

	/** Build submissions from everything collected since the last flush and queue them. Client thread only. */
	private void flush()
	{
		if (identity == null || !config.enableSubmission())
		{
			return;
		}

		// Still waiting for varps after login; the settle tick will flush.
		boolean settled = settleTicks == 0;

		Submission main = newSubmission();

		long now = System.currentTimeMillis();
		// Always on the first flush after login (lastSentStatsJson is reset) or a level-up; otherwise throttled.
		// Only while logged in: after logout the client's skill values can't be relied on.
		boolean statsDue = lastSentStatsJson == null || !pendingLevelUps.isEmpty() || now - lastStatsSentAt >= STATS_INTERVAL_MS;
		if (settled && config.submitStats() && statsDue && client.getGameState() == GameState.LOGGED_IN)
		{
			Submission.Stats stats = collectStats();
			String json = gson.toJson(stats);
			if (!json.equals(lastSentStatsJson))
			{
				main.setStats(stats);
				lastSentStatsJson = json;
				lastStatsSentAt = now;
			}
		}

		main.getDrops().addAll(pendingDrops);
		pendingDrops.clear();

		if (config.submitQuests())
		{
			main.getQuests().addAll(questTracker.drain());
		}
		if (config.submitStats())
		{
			main.getLevelUps().addAll(pendingLevelUps);
		}
		pendingLevelUps.clear();
		if (settled && config.submitKillCounts())
		{
			main.getKillCounts().addAll(collectKillCounts());
		}
		pendingClueCounts.clear();
		questTracker.save(false);

		pendingCollectionLog.forEach((id, qty) -> main.getCollectionLogItems().add(new int[]{id, qty}));
		sentCollectionLog.putAll(pendingCollectionLog);
		pendingCollectionLog.clear();

		List<Submission.LootSource> sources = settled && config.syncLootHistory() ? collectLootSources() : new ArrayList<>();

		List<Submission> submissions = new ArrayList<>();
		submissions.add(main);
		for (int i = 0; i < sources.size(); i += SOURCES_PER_REQUEST)
		{
			List<Submission.LootSource> chunk = sources.subList(i, Math.min(i + SOURCES_PER_REQUEST, sources.size()));
			Submission target = i == 0 ? main : newSubmission();
			target.getLootSources().addAll(chunk);
			if (target != main)
			{
				submissions.add(target);
			}
		}

		for (Submission submission : submissions)
		{
			if (!submission.isEmpty())
			{
				addItemInfo(submission);
				hiscoresClient.enqueue(submission);
			}
		}
		// Serializing a large submission (e.g. the full loot history) shouldn't happen on the client thread.
		executor.execute(hiscoresClient::pump);
	}

	private Submission newSubmission()
	{
		Submission s = new Submission();
		s.setAccountHash(Long.toString(identity.getAccountHash()));
		s.setUsername(identity.getUsername());
		s.setAccountType(client.getVarbitValue(VarbitID.IRONMAN));
		s.setProfileType(identity.getProfileType());
		s.setGameMode(identity.getGameMode());
		s.setProfileKey(identity.getProfileKey());
		return s;
	}

	private List<Submission.KillCount> collectKillCounts()
	{
		String profileKey = identity.getProfileKey();
		List<Submission.KillCount> out = new ArrayList<>();
		// The full list goes with the first flush for each profile this session, then only changes.
		if (killCountSyncedProfiles.add(profileKey))
		{
			dirtyKillCounts.clear();
			out.addAll(killCountReader.readAll(profileKey));
		}
		else
		{
			for (String name : new ArrayList<>(dirtyKillCounts))
			{
				dirtyKillCounts.remove(name);
				Submission.KillCount kc = killCountReader.read(profileKey, name);
				if (kc != null)
				{
					out.add(kc);
				}
			}
		}
		pendingClueCounts.forEach((name, count) -> out.add(new Submission.KillCount(name, count, null)));
		return out;
	}

	private List<Submission.LootSource> collectLootSources()
	{
		String profileKey = identity.getProfileKey();
		if (fullySyncedProfiles.add(profileKey))
		{
			dirtyLootKeys.clear();
			List<Submission.LootSource> all = lootTrackerReader.readAll(profileKey);
			log.debug("Full loot tracker sync: {} sources", all.size());
			return all;
		}

		List<Submission.LootSource> changed = new ArrayList<>();
		for (String key : new ArrayList<>(dirtyLootKeys))
		{
			dirtyLootKeys.remove(key);
			Submission.LootSource source = lootTrackerReader.read(profileKey, key);
			if (source != null)
			{
				changed.add(source);
			}
		}
		return changed;
	}

	private Submission.Stats collectStats()
	{
		Submission.Stats stats = new Submission.Stats();
		for (Skill skill : Skill.values())
		{
			// OVERALL is deprecated and not a real skill; the server totals it.
			if (!"OVERALL".equals(skill.name()))
			{
				stats.getSkills().put(skill.name(), new int[]{client.getRealSkillLevel(skill), client.getSkillExperience(skill)});
			}
		}
		Player local = client.getLocalPlayer();
		if (local != null)
		{
			stats.setCombatLevel(local.getCombatLevel());
		}
		stats.setQuestPoints(client.getVarpValue(VarPlayerID.QP));

		int clogTotal = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		if (clogTotal > 0)
		{
			stats.setCollectionLogObtained(client.getVarpValue(VarPlayerID.COLLECTION_COUNT));
			stats.setCollectionLogTotal(clogTotal);
			Map<String, int[]> categories = stats.getCollectionLogCategories();
			categories.put("Bosses", new int[]{client.getVarpValue(VarPlayerID.COLLECTION_COUNT_BOSSES), client.getVarpValue(VarPlayerID.COLLECTION_COUNT_BOSSES_MAX)});
			categories.put("Raids", new int[]{client.getVarpValue(VarPlayerID.COLLECTION_COUNT_RAIDS), client.getVarpValue(VarPlayerID.COLLECTION_COUNT_RAIDS_MAX)});
			categories.put("Clues", new int[]{client.getVarpValue(VarPlayerID.COLLECTION_COUNT_CLUES), client.getVarpValue(VarPlayerID.COLLECTION_COUNT_CLUES_MAX)});
			categories.put("Minigames", new int[]{client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MINIGAMES), client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MINIGAMES_MAX)});
			categories.put("Other", new int[]{client.getVarpValue(VarPlayerID.COLLECTION_COUNT_OTHER), client.getVarpValue(VarPlayerID.COLLECTION_COUNT_OTHER_MAX)});
		}

		stats.setDiaries(AccountVars.diaries(client));
		stats.setCombatAchievementPoints(AccountVars.combatAchievementPoints(client));
		stats.setCombatAchievementTasks(AccountVars.combatAchievementTasks(client));
		stats.setSlayerPoints(AccountVars.slayerPoints(client));
		stats.setSlayerStreak(AccountVars.slayerStreak(client));

		// Only populated after the player reveals their playtime in the Character Summary tab.
		int playtime = client.getVarcIntValue(VarClientID.ACCOUNT_SUMMARY_PLAYTIME);
		if (playtime > 0)
		{
			stats.setPlaytimeMinutes(playtime);
		}
		return stats;
	}

	private void addItemInfo(Submission submission)
	{
		Set<Integer> ids = new HashSet<>();
		for (Submission.LootSource source : submission.getLootSources())
		{
			int[] drops = source.getDrops();
			for (int i = 0; i < drops.length; i += 2)
			{
				ids.add(drops[i]);
			}
		}
		for (Submission.Drop drop : submission.getDrops())
		{
			for (int[] item : drop.getItems())
			{
				ids.add(item[0]);
			}
		}
		for (int[] item : submission.getCollectionLogItems())
		{
			ids.add(item[0]);
		}

		for (int id : ids)
		{
			submission.getItems().put(id, itemInfoCache.computeIfAbsent(id, this::lookupItem));
		}
	}

	private Submission.ItemInfo lookupItem(int itemId)
	{
		ItemComposition composition = itemManager.getItemComposition(itemId);
		return new Submission.ItemInfo(composition.getMembersName(), itemManager.getItemPrice(itemId), composition.getHaPrice());
	}
}
