package com.osrsenhancedhiscores;

import com.google.gson.Gson;
import java.time.Instant;
import net.runelite.api.gameval.ItemID;
import net.runelite.http.api.RuneLiteAPI;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class LootTrackerReaderTest
{
	// The Gson RuneLite injects into plugins, and that the Loot Tracker uses to save its data.
	private final Gson gson = RuneLiteAPI.GSON;

	@Test
	public void parsesLootTrackerConfigFormat()
	{
		Instant first = Instant.parse("2025-01-02T03:04:05Z");
		Instant last = Instant.parse("2026-11-01T00:00:00Z");
		// Shape of a value the Loot Tracker stores under loottracker.<profile>.drops_NPC_Vorkath (RuneLite 1.13.6).
		String json = "{\"type\":\"NPC\",\"name\":\"Vorkath\",\"kills\":250,"
			+ "\"first\":" + first.toEpochMilli() + ",\"last\":" + last.toEpochMilli() + ","
			+ "\"drops\":[" + ItemID.COINS + ",50000," + ItemID.DRAGON_BONES + ",2]}";

		Submission.LootSource source = LootTrackerReader.parse(gson, json, "drops_NPC_Vorkath");

		assertNotNull(source);
		assertEquals("NPC", source.getType());
		assertEquals("Vorkath", source.getName());
		assertEquals(250, source.getKills());
		assertEquals(Long.valueOf(first.toEpochMilli()), source.getFirst());
		assertEquals(Long.valueOf(last.toEpochMilli()), source.getLast());
		assertArrayEquals(new int[]{ItemID.COINS, 50_000, ItemID.DRAGON_BONES, 2}, source.getDrops());
	}

	@Test
	public void skipsMalformedEntries()
	{
		assertNull(LootTrackerReader.parse(gson, "{not json", "drops_NPC_x"));
		assertNull(LootTrackerReader.parse(gson, "{\"type\":\"NPC\",\"name\":\"x\",\"kills\":1,\"drops\":[1]}", "drops_NPC_x"));
		assertNull(LootTrackerReader.parse(gson, "{\"type\":\"NPC\",\"kills\":1,\"drops\":[]}", "drops_NPC_x"));
	}
}
