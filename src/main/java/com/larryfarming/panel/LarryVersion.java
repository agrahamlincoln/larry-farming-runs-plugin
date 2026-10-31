package com.larryfarming.panel;

import com.larryfarming.LarryPlugin;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.externalplugins.PluginHubManifest;

/**
 * The plugin's version as the Plugin Hub lists it, "1.0.0", or "dev" for a build the Hub did
 * not ship.
 */
public final class LarryVersion
{
	/** The version as drawn: "v1.0.0" or "dev". A bug report carries it too. */
	public static final String SHOWN = shown(hubVersion());

	private LarryVersion()
	{
	}

	static String shown(String version)
	{
		if (version == null || version.isBlank())
		{
			return "dev";
		}
		return Character.isDigit(version.charAt(0)) ? "v" + version : version;
	}

	/** Null outside a Hub build: the Hub's class loader is what carries the listing. */
	private static String hubVersion()
	{
		PluginHubManifest.DisplayData listing =
			ExternalPluginManager.getDisplayData(LarryPlugin.class);
		return listing == null ? null : listing.getVersion();
	}
}
