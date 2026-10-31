package com.larryfarming.panel;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Shared visual constants used across panel classes. Colors that appear in
 * two or more panels live here; single-use colors stay in their panel file.
 */
final class PanelColors
{
	/** Muted text for section headings and labels. */
	static final Color HEADER = ColorScheme.LIGHT_GRAY_COLOR;
	/** Muted list content, such as the unresolved-items line. */
	static final Color META = new Color(130, 130, 130);
	/** Divider line between items in a container. */
	static final Color DIVIDER = new Color(55, 55, 55);
	static final Color CONTAINER_BG = ColorScheme.DARKER_GRAY_COLOR.darker();
	static final Color CONTAINER_BORDER = ColorScheme.MEDIUM_GRAY_COLOR.darker();
	/** The level a locked setting needs, in the game's dark stack-count yellow. */
	static final Color LEVEL = new Color(178, 140, 35);

	private PanelColors()
	{
	}

	/** A 1px horizontal divider line for use inside list containers. */
	static JPanel divider()
	{
		JPanel line = new JPanel();
		line.setBackground(DIVIDER);
		line.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setPreferredSize(new Dimension(0, 1));
		return line;
	}
}
