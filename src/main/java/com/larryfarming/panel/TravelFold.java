package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingSettingGroup;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The side panel's travel fold: one on/off square per travel method, in a fixed order,
 * under captions such as "Jewellery". Off means never use it, on any route. A method the
 * player has nothing for draws grey and still switches.
 *
 * <p>When nothing switched on and owned reaches a fairy ring, while the network itself is
 * open, the header turns amber and says so, readable with the fold shut.
 *
 * <p>The squares are rebuilt only when the advice's answer moves, so a refresh arriving
 * before a click's re-plan leaves the click on screen. Nothing here wraps, so a rebuild
 * cannot flicker.
 */
class TravelFold extends Fold
{
	/**
	 * Appended while nothing the player uses reaches a ring, so a shut fold still says so.
	 * It names the rings: the fold holds every travel method, and most still work.
	 */
	private static final String BLOCKED = ": no fairy ring access";

	private final BiConsumer<String, String> send;
	private boolean blocked;

	/** The groups the squares were last built from. */
	private List<FarmingSettingGroup> shown = List.of();

	/** The icons the squares were last drawn with, so a provider arriving later redraws them. */
	private IconProvider shownIcons;

	/** @param send receives each switched square as its config key and new value */
	TravelFold(BiConsumer<String, String> send)
	{
		super("Travel");
		this.send = send;
		setVisible(false);
	}

	/** Draws the fold's squares from the advice. Hidden for advice without them. */
	void show(FarmingAdvice advice, IconProvider icons)
	{
		List<FarmingSettingGroup> groups = PanelText.listOf(advice.getTravelGroups());
		setVisible(!groups.isEmpty());
		if (groups.isEmpty())
		{
			return;
		}
		// The header's text and colour are set in place: a one-line label cannot flicker.
		if (blocked != advice.isFairyRingBlocked())
		{
			blocked = advice.isFairyRingBlocked();
			setHeader(blocked ? BLOCKED : "", blocked ? FarmingPanel.COLOR_DUE : PanelColors.HEADER);
		}
		if (groups.equals(shown) && icons == shownIcons)
		{
			return;
		}
		shown = groups;
		shownIcons = icons;

		content().removeAll();
		for (FarmingSettingGroup group : groups)
		{
			int gapAbove = content().getComponentCount() == 0 ? 0 : 6;
			content().add(SiteList.caption(group.getLabel(), gapAbove));
			content().add(SettingGrid.toggles(group.getChoices(), icons, send));
		}
		content().revalidate();
		content().repaint();
	}
}
