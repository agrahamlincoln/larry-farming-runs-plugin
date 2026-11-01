package com.larryfarming.panel;

import java.awt.*;
import javax.swing.Icon;

/**
 * A small drawing centred in an icon of a size the caller picks, so it can stand where
 * another icon would, such as a checkbox's.
 */
abstract class Mark implements Icon
{
	private final Color colour;
	private final int markWidth;
	private final int markHeight;
	private final int width;
	private final int height;

	/** A mark {@code markWidth} by {@code markHeight}, in an icon at least that size. */
	Mark(Color colour, int markWidth, int markHeight, int width, int height)
	{
		this.colour = colour;
		this.markWidth = markWidth;
		this.markHeight = markHeight;
		this.width = Math.max(markWidth, width);
		this.height = Math.max(markHeight, height);
	}

	/** Draws the mark with its bounding box's top-left corner at x, y. */
	abstract void drawAt(Graphics2D g, int x, int y, Color colour);

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			drawAt(g2, x + (width - markWidth) / 2, y + (height - markHeight) / 2, colour);
		}
		finally
		{
			g2.dispose();
		}
	}

	@Override
	public int getIconWidth()
	{
		return width;
	}

	@Override
	public int getIconHeight()
	{
		return height;
	}
}
