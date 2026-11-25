package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingUpgrade;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;

/**
 * One kind of farm-run advice as a boxed list: the gear to put on before setting off
 * ({@link FarmingUpgrade.Kind#EQUIP}), or what to work toward between runs
 * ({@link FarmingUpgrade.Kind#GOAL}). Draws one stretch of that kind's rows, so the goals
 * can split across the list and a fold. Hides itself when it has no rows.
 */
class GearList extends StretchPanel
{
	private static final int ROW_ICON = 20;
	/** Owned and worth putting on now. */
	private static final Color COLOR_EQUIP = ColorScheme.BRAND_ORANGE;

	private final FarmingUpgrade.Kind kind;
	/** The first row of the kind this list draws, and the row after its last. */
	private final int from;
	private final int to;

	GearList(FarmingUpgrade.Kind kind, int from, int to)
	{
		this.kind = kind;
		this.from = from;
		this.to = to;
		PanelColors.container(this);
		setVisible(false);
	}

	/**
	 * Redraws the rows of this list's kind. Called only when the upgrades have changed, since
	 * the caller guards structural rebuilds.
	 *
	 * @param width the width rows should wrap against, or 0 if not laid out yet
	 * @return whether any row was drawn, so the caller can hide the list's heading too
	 */
	boolean rebuild(List<FarmingUpgrade> upgrades, IconProvider icons, int width)
	{
		removeAll();
		List<FarmingUpgrade> rows = PanelText.listOf(upgrades).stream().filter(upgrade -> upgrade.getKind() == kind)
			.skip(from).limit(to - from).collect(Collectors.toList());
		setVisible(!rows.isEmpty());
		boolean divider = false;
		for (FarmingUpgrade upgrade : rows)
		{
			if (divider)
			{
				add(PanelColors.divider());
			}
			add(row(upgrade, icons, width));
			divider = true;
		}
		return !rows.isEmpty();
	}

	/** One line of the list. A GridBag lets the text wrap to the sidebar width. */
	private JPanel row(FarmingUpgrade upgrade, IconProvider icons, int width)
	{
		boolean equip = upgrade.getKind() == FarmingUpgrade.Kind.EQUIP;

		JPanel row = PanelText.panel(new GridBagLayout());
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));

		// In colour either way: grey means impossible, and a goal is within reach. An item is
		// cropped to its art, which sits small in its inventory-sized box.
		int id = upgrade.getItemId();
		ImageIcon sprite = icons == null ? null
			: id < 0 ? icons.getSpriteIcon(-id, ROW_ICON) : id > 0 ? icons.getGearIcon(id, ROW_ICON, true) : null;
		// The engine words the whole sentence, so the row adds nothing to it.
		PanelText.addIconAndFill(row, GridBagConstraints.CENTER, sprite, 5,
			PanelText.small(upgrade.getDetail(), equip ? COLOR_EQUIP : PanelColors.HEADER));
		return FarmingPanel.sized(row, width);
	}
}
