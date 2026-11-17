package com.osrsenhancedhiscores;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.config.ConfigManager;

/**
 * Tracks quest states and times how long each quest takes.
 * <p>
 * The game doesn't record when a quest was started or finished, so timing only exists for quests
 * the player starts while this plugin is running. For those, two durations are recorded:
 * <ul>
 * <li>elapsed: real time from start to completion, including time logged out</li>
 * <li>played: time spent logged in between start and completion</li>
 * </ul>
 * Start times are saved in the RuneScape profile config so they survive client restarts.
 * <p>
 * Client thread only.
 */
@Slf4j
@Singleton
class QuestTracker
{
	private static final String STARTS_KEY = "questStarts";
	private static final String PLAYED_KEY = "playedMs";
	/** Gaps longer than this between ticks (e.g. the PC slept) aren't counted as played time. */
	private static final long MAX_TICK_GAP_MS = 5_000;
	/** Played time is saved at most this often (start times are saved straight away). */
	private static final long PLAYED_SAVE_INTERVAL_MS = 5 * 60_000;

	private final Client client;
	private final ConfigManager configManager;
	private final Gson gson;

	private String profileKey;
	/** States from the previous check, or null before the first check for this profile. */
	private Map<Integer, QuestState> lastStates;
	private Map<Integer, QuestStart> starts = new HashMap<>();
	private long playedMs;
	private long lastTickAt;
	private long lastCheckAt;
	private long playedAtLastCheck;
	/** Start times changed and need saving. */
	private boolean startsDirty;
	private long playedSavedAt;
	private final List<Submission.QuestProgress> pending = new ArrayList<>();

	/** What is saved in config: a plain wrapper class, so Gson can read and write it directly. */
	private static class SavedStarts
	{
		Map<Integer, QuestStart> starts = new HashMap<>();
	}

	static class QuestStart
	{
		long startedAt;
		long playedAtStart;

		QuestStart()
		{
		}

		QuestStart(long startedAt, long playedAtStart)
		{
			this.startedAt = startedAt;
			this.playedAtStart = playedAtStart;
		}
	}

	@Inject
	QuestTracker(Client client, ConfigManager configManager, Gson gson)
	{
		this.client = client;
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Switch to another RuneScape profile (or none, on logout). Saves the current one first. */
	void setProfile(String newProfileKey)
	{
		save(true);
		reset(newProfileKey, new HashMap<>(), 0);
		if (profileKey == null)
		{
			return;
		}

		String json = configManager.getConfiguration(OsrsEnhancedHiscoresConfig.GROUP, profileKey, STARTS_KEY);
		if (json != null)
		{
			try
			{
				SavedStarts loaded = gson.fromJson(json, SavedStarts.class);
				if (loaded != null && loaded.starts != null)
				{
					starts = new HashMap<>(loaded.starts);
				}
			}
			catch (JsonParseException e)
			{
				log.debug("Ignoring malformed quest start times", e);
			}
		}
		String played = configManager.getConfiguration(OsrsEnhancedHiscoresConfig.GROUP, profileKey, PLAYED_KEY);
		if (played != null)
		{
			try
			{
				playedMs = Long.parseLong(played);
			}
			catch (NumberFormatException e)
			{
				playedMs = 0;
			}
		}
	}

	/** Start tracking a profile with the given saved state. */
	void reset(String newProfileKey, Map<Integer, QuestStart> savedStarts, long savedPlayedMs)
	{
		profileKey = newProfileKey;
		lastStates = null;
		lastTickAt = 0;
		lastCheckAt = 0;
		playedAtLastCheck = 0;
		pending.clear();
		starts = savedStarts;
		startsDirty = false;
		playedMs = savedPlayedMs;
		playedSavedAt = System.currentTimeMillis();
	}

	/**
	 * Send every quest's state again on the next check, e.g. after the server URL changes.
	 * Saved start times are kept, so quests in progress stay timed.
	 */
	void resendAll()
	{
		lastStates = null;
		pending.clear();
	}

	/** Called every game tick while logged in, to accumulate logged-in time. */
	void onTick(long now)
	{
		if (lastTickAt != 0)
		{
			long delta = now - lastTickAt;
			if (delta > 0 && delta <= MAX_TICK_GAP_MS)
			{
				playedMs += delta;
			}
		}
		lastTickAt = now;
	}

	/** Re-read every quest's state and record starts and completions. */
	void check(long now)
	{
		if (profileKey == null)
		{
			return;
		}

		Map<Integer, QuestState> current = new HashMap<>();
		Map<Integer, String> names = new HashMap<>();
		for (Quest quest : Quest.values())
		{
			current.put(quest.getId(), quest.getState(client));
			names.put(quest.getId(), quest.getName());
		}
		update(current, names, now);
	}

	/** Compare against the previous states and record what changed. Separate from {@link #check} for testing. */
	void update(Map<Integer, QuestState> current, Map<Integer, String> names, long now)
	{
		boolean firstCheck = lastStates == null;
		for (Map.Entry<Integer, QuestState> entry : current.entrySet())
		{
			int id = entry.getKey();
			QuestState state = entry.getValue();
			QuestState previous = firstCheck ? null : lastStates.get(id);
			if (!firstCheck && state == previous)
			{
				continue;
			}

			if (previous == QuestState.NOT_STARTED && state != QuestState.NOT_STARTED)
			{
				// It started some time since the last check; use the last check as a conservative start.
				starts.putIfAbsent(id, new QuestStart(lastCheckAt, playedAtLastCheck));
				startsDirty = true;
			}

			Submission.QuestProgress progress = new Submission.QuestProgress();
			progress.setId(id);
			progress.setName(names.get(id));
			progress.setState(state.name());

			QuestStart start = starts.get(id);
			if (start != null)
			{
				progress.setStartedAt(start.startedAt);
			}

			// Only a transition we actually saw gives a completion time; on the first check we don't know when.
			if (state == QuestState.FINISHED && previous != null && previous != QuestState.FINISHED)
			{
				progress.setCompletedAt(now);
				if (start != null)
				{
					progress.setElapsedMs(now - start.startedAt);
					progress.setPlayedMs(playedMs - start.playedAtStart);
				}
				log.debug("Quest completed: {} (timed: {})", names.get(id), start != null);
			}

			if (state == QuestState.FINISHED || state == QuestState.NOT_STARTED)
			{
				if (starts.remove(id) != null)
				{
					startsDirty = true;
				}
			}
			pending.add(progress);
		}

		lastStates = current;
		lastCheckAt = now;
		playedAtLastCheck = playedMs;
	}

	/** Quest changes since the last drain (every quest after the first check). */
	List<Submission.QuestProgress> drain()
	{
		List<Submission.QuestProgress> out = new ArrayList<>(pending);
		pending.clear();
		return out;
	}

	/**
	 * Persist start times and played time to the RuneScape profile config.
	 * Start times are written whenever they change; played time only every few minutes unless forced.
	 */
	void save(boolean force)
	{
		if (profileKey == null)
		{
			return;
		}
		if (startsDirty)
		{
			SavedStarts saved = new SavedStarts();
			saved.starts = starts;
			configManager.setConfiguration(OsrsEnhancedHiscoresConfig.GROUP, profileKey, STARTS_KEY, gson.toJson(saved));
			startsDirty = false;
		}
		long now = System.currentTimeMillis();
		if (force || startsDirty || now - playedSavedAt >= PLAYED_SAVE_INTERVAL_MS)
		{
			configManager.setConfiguration(OsrsEnhancedHiscoresConfig.GROUP, profileKey, PLAYED_KEY, Long.toString(playedMs));
			playedSavedAt = now;
		}
	}
}
