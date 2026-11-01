package com.larryfarming.panel;

import java.awt.Cursor;
import java.awt.Insets;
import javax.swing.JButton;
import net.runelite.client.ui.FontManager;

/**
 * The side panel's one button style: a compact RuneLite button in the small font. Every
 * button in the panel is one of these, so their look cannot drift apart.
 */
class SmallButton extends JButton
{
	private static final Insets MARGIN = new Insets(1, 4, 1, 4);

	SmallButton(String text)
	{
		super(text);
		setFont(FontManager.getRunescapeSmallFont());
		setFocusPainted(false);
		setMargin(MARGIN);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}
}
