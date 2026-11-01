package com.larryfarming;

import net.runelite.client.config.*;

/**
 * The plugin's one settings-page item. The farming settings live in the same group, under the
 * keys in {@link com.larryfarming.farming.model.FarmingKeys}, but the side panel edits them
 * and none is declared here: an unsaved key reads as the preset's value.
 */
@ConfigGroup(LarryConfig.GROUP)
public interface LarryConfig extends Config
{
	/** Named so a config listener can tell our settings from every other plugin's. */
	String GROUP = "larryfarming";

	@ConfigItem(
		keyName = "showFarmingAdvisor",
		name = "Show Farming Run Infobox",
		description = "Show an infobox while a farming run is due",
		position = 1
	)
	default boolean showFarmingAdvisor()
	{
		return true;
	}
}
