package com.osrsenhancedhiscores;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import net.runelite.client.config.RuneScapeProfileType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class GameModeTest
{
	@Test
	public void mainGameWorlds()
	{
		assertEquals("NORMAL", GameMode.resolve(EnumSet.noneOf(WorldType.class), RuneScapeProfileType.STANDARD));
		assertEquals("NORMAL", GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL), RuneScapeProfileType.STANDARD));
		assertEquals("NORMAL", GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.PVP), RuneScapeProfileType.STANDARD));
	}

	@Test
	public void separateGameModes()
	{
		assertEquals("HARDCORE_MAIN", GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.HARDCORE_MAIN), RuneScapeProfileType.STANDARD));
		assertEquals("FRESH_START_WORLD", GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.FRESH_START_WORLD), RuneScapeProfileType.STANDARD));
		assertEquals("SEASONAL", GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL), RuneScapeProfileType.DEMONIC_PACTS_LEAGUE));
		assertEquals("DEADMAN", GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.DEADMAN), RuneScapeProfileType.DEADMAN));
	}

	@Test
	public void untrackedWorlds()
	{
		assertNull(GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.BETA_WORLD), RuneScapeProfileType.BETA));
		assertNull(GameMode.resolve(EnumSet.of(WorldType.NOSAVE_MODE), RuneScapeProfileType.STANDARD));
		assertNull(GameMode.resolve(EnumSet.of(WorldType.QUEST_SPEEDRUNNING), RuneScapeProfileType.QUEST_SPEEDRUNNING));
		assertNull(GameMode.resolve(EnumSet.of(WorldType.PVP_ARENA), RuneScapeProfileType.PVP_ARENA));
		assertNull(GameMode.resolve(EnumSet.of(WorldType.MEMBERS, WorldType.TOURNAMENT_WORLD), RuneScapeProfileType.STANDARD));
		assertNull(GameMode.resolve(EnumSet.noneOf(WorldType.class), null));
	}
}
