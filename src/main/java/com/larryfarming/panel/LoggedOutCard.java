package com.larryfarming.panel;

import java.awt.*;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** What the panel shows while no account is logged in: Larry, his line, and a nudge to log in. */
class LoggedOutCard extends JPanel
{
	static final String LOG_IN = "Log in to plan a farm run.";

	LoggedOutCard()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);
		setBorder(BorderFactory.createEmptyBorder(14, 8, 14, 8));

		add(centred(new JLabel(BrandHeader.face(4))));
		add(Box.createVerticalStrut(10));
		add(centred(PanelText.label("Larry plans the route.", PanelColors.HEADER)));
		add(centred(PanelText.label("You plant the patches.", PanelColors.HEADER)));
		add(Box.createVerticalStrut(12));
		add(centred(PanelText.label(LOG_IN, FontManager.getRunescapeFont(), Color.WHITE)));

		setMaximumSize(new Dimension(Short.MAX_VALUE, getPreferredSize().height));
	}

	private static JComponent centred(JComponent inner)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(inner);
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, inner.getPreferredSize().height));
		return row;
	}
}
