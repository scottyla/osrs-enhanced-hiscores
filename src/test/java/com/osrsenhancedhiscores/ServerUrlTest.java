package com.osrsenhancedhiscores;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ServerUrlTest
{
	@Test
	public void defaultsToTheWebsite()
	{
		assertEquals("https://osrsenhancedhiscores.com/", OsrsEnhancedHiscoresConfig.DEFAULT_SERVER_URL + "/");
		assertEquals("https://osrsenhancedhiscores.com/", HiscoresClient.serverUrl(OsrsEnhancedHiscoresConfig.DEFAULT_SERVER_URL).toString());
	}

	@Test
	public void blankSettingUsesTheWebsite()
	{
		assertEquals("https://osrsenhancedhiscores.com/", HiscoresClient.serverUrl("").toString());
		assertEquals("https://osrsenhancedhiscores.com/", HiscoresClient.serverUrl("   ").toString());
		assertEquals("https://osrsenhancedhiscores.com/", HiscoresClient.serverUrl(null).toString());
	}

	@Test
	public void customServerStillWorks()
	{
		assertEquals("https://hiscores.example.org/", HiscoresClient.serverUrl(" https://hiscores.example.org ").toString());
		assertNull(HiscoresClient.serverUrl("not a url"));
	}
}
