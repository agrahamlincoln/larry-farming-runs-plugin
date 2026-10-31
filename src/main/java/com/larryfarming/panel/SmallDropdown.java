package com.larryfarming.panel;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import net.runelite.client.ui.FontManager;

/**
 * The farming tab's one dropdown style: a RuneLite combo box in the small font that never
 * takes the focus. Every dropdown on the tab is one of these, so their look cannot drift
 * apart.
 */
class SmallDropdown<T> extends JComboBox<T>
{
	SmallDropdown(ComboBoxModel<T> model)
	{
		super(model);
		setFont(FontManager.getRunescapeSmallFont());
		setFocusable(false);
	}
}
