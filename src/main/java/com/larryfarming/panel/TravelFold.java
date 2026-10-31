package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingSettingChoice;
import com.larryfarming.snapshot.FarmingSettingGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The farming tab's travel fold: one on/off square per travel method, in a fixed order,
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

	private boolean blocked;
	private BiConsumer<String, String> settingCallback;

	/** The groups the squares were last built from. */
	private List<FarmingSettingGroup> shown = List.of();

	/** The icons the squares were last drawn with, so a provider arriving later redraws them. */
	private IconProvider shownIcons;

	TravelFold()
	{
		super("Travel");
		setVisible(false);
	}

	void setSettingCallback(BiConsumer<String, String> callback)
	{
		this.settingCallback = callback;
	}

	/** Draws the fold's squares from the advice. Hidden for advice without them. */
	void show(FarmingAdvice advice, IconProvider icons)
	{
		List<FarmingSettingGroup> groups = listOf(advice.getTravelGroups());
		setVisible(!groups.isEmpty());
		if (groups.isEmpty())
		{
			return;
		}
		// The header's text and colour are set in place: a one-line label cannot flicker.
		if (blocked != advice.isFairyRingBlocked())
		{
			blocked = advice.isFairyRingBlocked();
			setHeader(blocked ? BLOCKED : "",
				blocked ? FarmingPanel.COLOR_DUE : PanelColors.HEADER);
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
			List<SettingSquare> squares = new ArrayList<>(group.getChoices().size());
			for (FarmingSettingChoice item : group.getChoices())
			{
				squares.add(SettingGrid.toggle(item, icons, this::send));
			}
			int gapAbove = content().getComponentCount() == 0 ? 0 : 6;
			content().add(SiteList.caption(group.getLabel(), gapAbove));
			content().add(SettingGrid.grid(squares, icons));
		}
		content().revalidate();
		content().repaint();
	}

	private void send(String key, String value)
	{
		if (settingCallback != null && key != null)
		{
			settingCallback.accept(key, value);
		}
	}

	private static <T> List<T> listOf(List<T> list)
	{
		return list != null ? list : List.of();
	}
}
