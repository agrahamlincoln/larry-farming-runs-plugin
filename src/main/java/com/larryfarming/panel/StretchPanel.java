package com.larryfarming.panel;

import java.awt.Dimension;
import javax.swing.JPanel;

/**
 * A panel that stretches to its parent's width in a vertical stack but never grows taller
 * than its content, so a {@code BoxLayout} parent cannot hand it spare height.
 */
class StretchPanel extends JPanel
{
	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
