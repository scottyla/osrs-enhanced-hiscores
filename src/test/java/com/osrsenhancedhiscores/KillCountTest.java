package com.osrsenhancedhiscores;

import java.util.regex.Matcher;
import net.runelite.client.util.Text;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class KillCountTest
{
	private static Matcher clue(String message)
	{
		return OsrsEnhancedHiscoresPlugin.CLUE_COUNT.matcher(Text.removeTags(message));
	}

	@Test
	public void parsesClueCompletionMessages()
	{
		Matcher m = clue("You have completed <col=ff0000>1,234</col> elite Treasure Trails.");
		assertTrue(m.find());
		assertEquals("1,234", m.group(1));
		assertEquals("elite", m.group(2));

		Matcher one = clue("You have completed <col=ff0000>1</col> beginner Treasure Trail.");
		assertTrue(one.find());
		assertEquals("beginner", one.group(2));

		assertFalse(clue("You have completed 5 laps of the course.").find());
	}

	@Test
	public void parsesChatCommandsValues()
	{
		assertEquals(Integer.valueOf(120), KillCountReader.parseInt("120"));
		assertNull(KillCountReader.parseInt("abc"));
		assertNull(KillCountReader.parseInt("-1"));
		assertEquals(Double.valueOf(311.0), KillCountReader.parseDouble("311.0"));
		assertEquals(Double.valueOf(10.2), KillCountReader.parseDouble("10.2"));
		assertNull(KillCountReader.parseDouble("0"));
		assertNull(KillCountReader.parseDouble(null));
	}
}
