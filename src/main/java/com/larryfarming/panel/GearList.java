package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingUpgrade;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;

/**
 * One kind of farm-run gear advice as a boxed list: the gear to put on before setting off
 * ({@link FarmingUpgrade.Kind#EQUIP}), or the gear worth earning between runs
 * ({@link FarmingUpgrade.Kind#GOAL}). Hides itself when it has no rows.
 */
class GearList extends JPanel
{
	private static final int ROW_ICON = 20;
	/** Owned and worth putting on now. */
	private static final Color COLOR_EQUIP = ColorScheme.BRAND_ORANGE;

	private final FarmingUpgrade.Kind kind;

	GearList(FarmingUpgrade.Kind kind)
	{
		this.kind = kind;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(PanelColors.CONTAINER_BG);
		setAlignmentX(LEFT_ALIGNMENT);
		setBorder(BorderFactory.createLineBorder(PanelColors.CONTAINER_BORDER, 1));
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
		List<FarmingUpgrade> rows = new ArrayList<>();
		if (upgrades != null)
		{
			for (FarmingUpgrade upgrade : upgrades)
			{
				if (upgrade.getKind() == kind)
				{
					rows.add(upgrade);
				}
			}
		}
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

		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
		row.setAlignmentX(LEFT_ALIGNMENT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.CENTER;

		int col = 0;
		if (icons != null && upgrade.getItemId() > 0)
		{
			// Colour if owned, grey if not.
			ImageIcon sprite = icons.getGearIcon(upgrade.getItemId(), ROW_ICON, equip);
			if (sprite != null)
			{
				JLabel icon = new JLabel(sprite);
				icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
				gbc.gridx = col++;
				gbc.weightx = 0;
				gbc.fill = GridBagConstraints.NONE;
				row.add(icon, gbc);
			}
		}

		// The engine words the whole sentence, so the row adds nothing to it.
		PanelText.addFill(row, gbc, col, PanelText.small(upgrade.getDetail(), equip ? COLOR_EQUIP : PanelColors.HEADER));
		return FarmingPanel.sized(row, width);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
