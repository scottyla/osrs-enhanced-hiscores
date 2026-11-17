package com.osrsenhancedhiscores;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OsrsEnhancedHiscoresPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(OsrsEnhancedHiscoresPlugin.class);
		RuneLite.main(args);
	}
}
