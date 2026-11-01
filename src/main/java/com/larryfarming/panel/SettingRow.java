package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingSettingPick;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.BiConsumer;
import javax.swing.*;
import net.runelite.client.ui.FontManager;

/**
 * A setting's label beside its control. Every such row in the panel is built here, so the
 * labels share one width and the controls line up down the panel.
 */
final class SettingRow
{
	/**
	 * Wide enough for the longest label, "Favourite herb" (74 pixels in the small font), and
	 * no wider, so the chips beside "Best seeds in" keep their room at sidebar width.
	 */
	private static final int LABEL_WIDTH = 75;
	private static final int ROW_HEIGHT = 23;

	private SettingRow()
	{
	}

	static JPanel of(String label, JComponent control)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(3, 2, 0, 0));
		JLabel name = new JLabel(label);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(PanelColors.HEADER);
		name.setPreferredSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT - 3));
		row.add(name, BorderLayout.WEST);
		row.add(control, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, ROW_HEIGHT));
		return row;
	}

	/**
	 * A row for a setting of several values, drawn as its shape asks: chips or a dropdown.
	 * Built from scratch, for a block that is rebuilt whole.
	 *
	 * @param send receives the setting's key and the picked value
	 */
	static JPanel of(FarmingSettingPick pick, BiConsumer<String, String> send)
	{
		if (pick.getShape() == FarmingSettingPick.Shape.DROPDOWN)
		{
			PickDropdown dropdown = new PickDropdown(send);
			dropdown.show(pick);
			return of(pick.getLabel(), dropdown);
		}
		ChoiceChips chips = new ChoiceChips(send);
		chips.show(pick);
		return of(pick.getLabel(), chips);
	}
}
