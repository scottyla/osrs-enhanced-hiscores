package com.osrsenhancedhiscores;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * Reads the official kill counts and personal bests that RuneLite's Chat Commands plugin saves
 * whenever the game prints them ("Your Zulrah kill count is: 120", "Fight duration: 1:05").
 * They're stored per RuneScape profile, keyed by the lowercase boss/activity name:
 * {@code killcount.<name>} as an integer and {@code personalbest.<name>} as seconds.
 */
@Singleton
class KillCountReader
{
	static final String KC_GROUP = "killcount";
	static final String PB_GROUP = "personalbest";

	private final ConfigManager configManager;

	@Inject
	KillCountReader(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	List<Submission.KillCount> readAll(String profileKey)
	{
		Set<String> names = new TreeSet<>(configManager.getRSProfileConfigurationKeys(KC_GROUP, profileKey, ""));
		names.addAll(configManager.getRSProfileConfigurationKeys(PB_GROUP, profileKey, ""));
		List<Submission.KillCount> out = new ArrayList<>();
		for (String name : names)
		{
			Submission.KillCount kc = read(profileKey, name);
			if (kc != null)
			{
				out.add(kc);
			}
		}
		return out;
	}

	Submission.KillCount read(String profileKey, String name)
	{
		Integer kc = parseInt(configManager.getConfiguration(KC_GROUP, profileKey, name));
		Double pb = parseDouble(configManager.getConfiguration(PB_GROUP, profileKey, name));
		if (kc == null && pb == null)
		{
			return null;
		}
		return new Submission.KillCount(name, kc, pb);
	}

	static Integer parseInt(String s)
	{
		if (s == null)
		{
			return null;
		}
		try
		{
			int v = Integer.parseInt(s.trim());
			return v >= 0 ? v : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	static Double parseDouble(String s)
	{
		if (s == null)
		{
			return null;
		}
		try
		{
			double v = Double.parseDouble(s.trim());
			return v > 0 && !Double.isInfinite(v) && !Double.isNaN(v) ? v : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
