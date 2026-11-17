package com.osrsenhancedhiscores;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * JSON body sent to {@code POST /api/v1/submit}. Field names are the wire format; keep them
 * in sync with server/src/validate.ts.
 */
@Data
class Submission
{
	/** Account hash as a string, since JS numbers can't hold a full 64-bit long. */
	private String accountHash;
	private String username;
	/** IRONMAN varbit: 0 regular, 1 ironman, 2 ultimate, 3 hardcore, 4 group, 5 hardcore group, 6 unranked group. */
	private int accountType;
	/** RuneLite RuneScapeProfileType name, e.g. STANDARD or a league. Each profile has separate data. */
	private String profileType;
	/** See {@link GameMode}: NORMAL, HARDCORE_MAIN, FRESH_START_WORLD, SEASONAL or DEADMAN. */
	private String gameMode;

	/** Null when stats weren't collected for this submission. */
	private Stats stats;

	/** Item metadata for every item id referenced in this submission, keyed by item id. */
	private Map<Integer, ItemInfo> items = new HashMap<>();

	/** Full loot tracker totals per source. These replace the server's totals for the same source. */
	private List<LootSource> lootSources = new ArrayList<>();

	/** Individual drops for the recent drops feed. Server de-duplicates by id. */
	private List<Drop> drops = new ArrayList<>();

	/** Collection log items as [itemId, quantity] pairs. */
	private List<int[]> collectionLogItems = new ArrayList<>();

	/** Quest states that changed (all quests on the first submission after login). */
	private List<QuestProgress> quests = new ArrayList<>();

	/** Official kill counts / personal bests (from RuneLite's Chat Commands plugin) and clue completions. */
	private List<KillCount> killCounts = new ArrayList<>();

	/** Levels gained while the plugin was running. */
	private List<LevelUp> levelUps = new ArrayList<>();

	/** RuneScape profile this submission belongs to. Used to store the auth token; not serialized. */
	private transient String profileKey;

	boolean isEmpty()
	{
		return stats == null && lootSources.isEmpty() && drops.isEmpty() && collectionLogItems.isEmpty() && quests.isEmpty()
			&& killCounts.isEmpty() && levelUps.isEmpty();
	}

	@Data
	static class Stats
	{
		/** Skill name to [level, xp]. */
		private Map<String, int[]> skills = new LinkedHashMap<>();
		private Integer combatLevel;
		private Integer questPoints;
		private Integer collectionLogObtained;
		private Integer collectionLogTotal;
		/** Category name to [obtained, total]. */
		private Map<String, int[]> collectionLogCategories = new LinkedHashMap<>();
		/** Null until the player has revealed their playtime in the Character Summary tab. */
		private Integer playtimeMinutes;
		/** Region to [easy, medium, hard, elite], 1 when complete. */
		private Map<String, int[]> diaries = new LinkedHashMap<>();
		private Integer combatAchievementPoints;
		private Integer combatAchievementTasks;
		private Integer slayerPoints;
		private Integer slayerStreak;
	}

	@Data
	static class KillCount
	{
		/** Lowercase name as the game/Chat Commands plugin uses it, e.g. "zulrah", "clue scrolls (elite)". */
		private final String name;
		private final Integer kc;
		/** Personal best in seconds. */
		private final Double pb;
	}

	@Data
	static class LevelUp
	{
		private final String skill;
		private final int level;
		private final long at;
	}

	@Data
	static class ItemInfo
	{
		private final String name;
		private final long gePrice;
		private final long haPrice;
	}

	@Data
	static class LootSource
	{
		private String type;
		private String name;
		private int kills;
		private Long first;
		private Long last;
		/** Flattened [itemId, quantity, itemId, quantity, ...], the same layout the Loot Tracker uses. */
		private int[] drops;
	}

	@Data
	static class QuestProgress
	{
		/** {@link net.runelite.api.Quest#getId()} */
		private int id;
		private String name;
		/** NOT_STARTED, IN_PROGRESS or FINISHED */
		private String state;
		/** Only known for quests started while the plugin was running. */
		private Long startedAt;
		/** Only set on the submission where we saw the quest complete. */
		private Long completedAt;
		/** Real time from start to completion. */
		private Long elapsedMs;
		/** Logged-in time from start to completion. */
		private Long playedMs;
	}

	@Data
	static class Drop
	{
		/** Client-generated UUID so a retried request doesn't double-count. */
		private String id;
		private String type;
		private String name;
		private int combatLevel;
		private int amount;
		private long timestamp;
		private int world;
		private List<int[]> items = new ArrayList<>();
	}
}
