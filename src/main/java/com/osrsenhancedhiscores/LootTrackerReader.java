package com.osrsenhancedhiscores;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the per-source totals that the built-in Loot Tracker plugin persists to the
 * RuneScape profile config. Each source is stored as JSON under the key
 * {@code drops_<TYPE>_<name>} in the {@code loottracker} group.
 */
@Slf4j
@Singleton
class LootTrackerReader
{
	static final String LOOT_TRACKER_GROUP = "loottracker";
	static final String DROPS_PREFIX = "drops_";

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	LootTrackerReader(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	List<Submission.LootSource> readAll(String profileKey)
	{
		List<Submission.LootSource> sources = new ArrayList<>();
		for (String key : configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, profileKey, DROPS_PREFIX))
		{
			Submission.LootSource source = read(profileKey, key);
			if (source != null)
			{
				sources.add(source);
			}
		}
		return sources;
	}

	Submission.LootSource read(String profileKey, String key)
	{
		String json = configManager.getConfiguration(LOOT_TRACKER_GROUP, profileKey, key);
		return json == null ? null : parse(gson, json, key);
	}

	static Submission.LootSource parse(Gson gson, String json, String key)
	{
		StoredLoot stored;
		try
		{
			// The injected Gson has RuneLite's Instant adapter, which matches how Loot Tracker writes these.
			stored = gson.fromJson(json, StoredLoot.class);
		}
		catch (JsonParseException e)
		{
			log.debug("Skipping malformed loot tracker entry {}", key, e);
			return null;
		}

		if (stored == null || stored.type == null || stored.name == null || stored.drops == null || stored.drops.length % 2 != 0)
		{
			return null;
		}

		Submission.LootSource source = new Submission.LootSource();
		source.setType(stored.type);
		source.setName(stored.name);
		source.setKills(stored.kills);
		source.setFirst(stored.first == null ? null : stored.first.toEpochMilli());
		source.setLast(stored.last == null ? null : stored.last.toEpochMilli());
		source.setDrops(stored.drops);
		return source;
	}

	/** Mirror of the Loot Tracker's package-private ConfigLoot. */
	private static class StoredLoot
	{
		String type;
		String name;
		int kills;
		Instant first;
		Instant last;
		int[] drops;
	}
}
