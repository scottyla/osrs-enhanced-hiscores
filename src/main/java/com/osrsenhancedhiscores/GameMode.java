package com.osrsenhancedhiscores;

import java.util.Set;
import net.runelite.api.WorldType;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.hiscore.HiscoreEndpoint;

/**
 * Decides which hiscores a character's data belongs to, based on the world it's logged into.
 * <p>
 * The result is one of the {@link HiscoreEndpoint} names RuneLite itself uses to pick an official
 * hiscores board: NORMAL, HARDCORE_MAIN (Hardcore Worlds), FRESH_START_WORLD, SEASONAL (Leagues) or DEADMAN.
 * Ironman variants are worked out on the server from the account type, the same way the official
 * hiscores list ironmen on both the Normal and Ironman boards.
 */
final class GameMode
{
	private GameMode()
	{
	}

	/**
	 * @return the game mode name, or null if data from this world shouldn't be submitted
	 * (beta, quest speedrunning, PvP Arena, tournament worlds).
	 */
	static String resolve(Set<WorldType> worldTypes, RuneScapeProfileType profileType)
	{
		if (profileType == null)
		{
			return null;
		}

		switch (profileType)
		{
			case BETA:
			case QUEST_SPEEDRUNNING:
			case PVP_ARENA:
				return null;
			default:
				break;
		}

		if (worldTypes.contains(WorldType.BETA_WORLD) || worldTypes.contains(WorldType.NOSAVE_MODE))
		{
			return null;
		}

		HiscoreEndpoint endpoint = HiscoreEndpoint.fromWorldTypes(worldTypes);
		switch (endpoint)
		{
			case NORMAL:
			case HARDCORE_MAIN:
			case FRESH_START_WORLD:
			case SEASONAL:
			case DEADMAN:
				return endpoint.name();
			default:
				return null;
		}
	}
}
