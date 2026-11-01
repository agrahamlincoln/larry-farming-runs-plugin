package com.larryfarming.panel;

import java.awt.*;
import javax.swing.Icon;

/**
 * An icon drawn faint, as an unticked setting square draws its sprite.
 *
 * <p>Wraps rather than copies, so an item sprite that loads after the row is built still
 * paints through it.
 */
final class DimmedIcon implements Icon
{
	private final Icon icon;

	DimmedIcon(Icon icon)
	{
		this.icon = icon;
	}

	/** The icon drawn faint. */
	Icon getIcon()
	{
		return icon;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			// The same strength as an unticked setting square's sprite.
			g2.setComposite(SettingSquare.DIM);
			icon.paintIcon(c, g2, x, y);
		}
		finally
		{
			g2.dispose();
		}
	}

	@Override
	public int getIconWidth()
	{
		return icon.getIconWidth();
	}

	@Override
	public int getIconHeight()
	{
		return icon.getIconHeight();
	}
}
