package com.larryfarming.panel;

import java.awt.*;
import javax.swing.Icon;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An icon drawn faint, as an unticked setting square draws its sprite.
 *
 * <p>Wraps rather than copies, so an item sprite that loads after the row is built still
 * paints through it.
 */
@RequiredArgsConstructor
final class DimmedIcon implements Icon
{
	/** The icon drawn faint. */
	@Getter
	private final Icon icon;

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
