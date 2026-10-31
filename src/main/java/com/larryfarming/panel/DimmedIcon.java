package com.larryfarming.panel;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.Icon;

/**
 * An icon drawn faint, as an unticked setting square draws its sprite.
 *
 * <p>Wraps rather than copies, so an item sprite that loads after the row is built still
 * paints through it.
 */
final class DimmedIcon implements Icon
{
	/** The same strength as {@link SettingSquare}'s unticked sprite. */
	private static final AlphaComposite DIM =
		AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f);

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
			g2.setComposite(DIM);
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
