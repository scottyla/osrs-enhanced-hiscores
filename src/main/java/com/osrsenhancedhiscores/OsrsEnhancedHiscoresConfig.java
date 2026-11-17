package com.osrsenhancedhiscores;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(OsrsEnhancedHiscoresConfig.GROUP)
public interface OsrsEnhancedHiscoresConfig extends Config
{
	String GROUP = "osrsenhancedhiscores";
	String DEFAULT_SERVER_URL = "https://osrsenhancedhiscores.com";

	@ConfigItem(
		keyName = "enableSubmission",
		name = "Submit data",
		description = "Send your loot tracker data and account stats to OSRS Enhanced Hiscores (osrsenhancedhiscores.com)."
			+ " Normal, Hardcore Worlds, Fresh Start, Leagues and Deadman characters each get their own hiscores.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default boolean enableSubmission()
	{
		return false;
	}

	@ConfigSection(
		name = "Data to submit",
		description = "Choose which data is sent to the hiscores server",
		position = 1
	)
	String dataSection = "data";

	@ConfigItem(
		keyName = "syncLootHistory",
		name = "Loot tracker history",
		description = "Sync the totals stored by the Loot Tracker plugin (requires Loot Tracker to be enabled)",
		section = dataSection,
		position = 2
	)
	default boolean syncLootHistory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "submitDrops",
		name = "Individual drops",
		description = "Send each drop as it happens, for the recent drops feed",
		section = dataSection,
		position = 3
	)
	default boolean submitDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "submitStats",
		name = "Account stats",
		description = "Send levels and XP, level-up dates, quest points, collection log totals, achievement diaries,"
			+ " combat achievements, Slayer points and streak, and time played."
			+ " Time played is only known after you reveal it in the Character Summary tab.",
		section = dataSection,
		position = 4
	)
	default boolean submitStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "submitCollectionLog",
		name = "Collection log items",
		description = "Send collection log items as you browse the collection log pages",
		section = dataSection,
		position = 5
	)
	default boolean submitCollectionLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "submitQuests",
		name = "Quest progress",
		description = "Send which quests you've completed, and how long each one took."
			+ " Times are only measured for quests you start while this plugin is running.",
		section = dataSection,
		position = 6
	)
	default boolean submitQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "submitKillCounts",
		name = "Kill counts & PBs",
		description = "Send boss kill counts and personal bests recorded by RuneLite's Chat Commands plugin,"
			+ " and your clue scroll completion counts",
		section = dataSection,
		position = 7
	)
	default boolean submitKillCounts()
	{
		return true;
	}

	@ConfigSection(
		name = "Advanced",
		description = "Advanced settings",
		position = 10,
		closedByDefault = true
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server URL",
		description = "Where to send data. Leave as https://osrsenhancedhiscores.com (or blank) unless you run your own server.",
		section = advancedSection,
		position = 11
	)
	default String serverUrl()
	{
		return DEFAULT_SERVER_URL;
	}
}
