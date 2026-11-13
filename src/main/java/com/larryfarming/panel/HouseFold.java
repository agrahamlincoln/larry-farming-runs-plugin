package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingHouse;
import java.util.function.BiConsumer;

/**
 * The side panel's house fold: what the player's house holds, as two columns of rows under
 * "Portals", "Portal Nexus" and "Garden", in a fixed order.
 *
 * <p>An item Larry has not seen is the player's to declare, ticked for built. An item he
 * has seen shows what he saw and takes no click: an eye for built, the padlock for not
 * built. A player with something they would rather Larry not use switches it off in Travel.
 *
 * <p>The header says how much of the house is still unseen, readable with the fold shut.
 *
 * <p>The rows are rebuilt only when what they show moves, so a refresh arriving before a
 * click's re-plan leaves the click on screen. Nothing here wraps, so a rebuild cannot
 * flicker.
 */
class HouseFold extends Fold
{
	private final RebuildGuard guard = new RebuildGuard();
	private final BiConsumer<String, String> send;

	/** @param send receives each ticked row as its config key and new value */
	HouseFold(BiConsumer<String, String> send)
	{
		super("Your house");
		this.send = send;
		setVisible(false);
	}

	/** Draws the fold's rows from the advice. Hidden for advice without them. */
	void show(FarmingAdvice advice)
	{
		FarmingHouse house = advice.getHouse();
		setVisible(house != null);
		if (house == null)
		{
			return;
		}
		// The header is set in place: a one-line label cannot flicker.
		setHeader(suffix(house.getSight()), PanelColors.HEADER);
		if (!guard.shouldRebuild(house.getGroups()))
		{
			return;
		}
		content().removeAll();
		content().add(SiteList.inGroups(house.getGroups(), send));
		content().revalidate();
		content().repaint();
	}

	private static String suffix(FarmingHouse.Sight sight)
	{
		switch (sight)
		{
			case NOT_SEEN:
				return ": not seen yet";
			case NEXUS_NOT_SEEN:
				return ": Nexus not opened yet";
			default:
				return "";
		}
	}
}
