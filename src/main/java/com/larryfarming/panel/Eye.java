package com.larryfarming.panel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * The open eye that marks a setting Larry has seen for himself, drawn in a row's checkbox
 * place. It sits beside the {@link Padlock}: seen built shows the eye, impossible the lock.
 */
final class Eye implements Icon
{
	/** The eye's own width and height, corner to corner. */
	static final int WIDTH = 11;
	static final int HEIGHT = 8;

	private static final Stroke STROKE = new BasicStroke(1.2f);

	/** The lids, two arcs meeting at the corners, drawn once and translated to each place. */
	private static final Path2D LIDS = lids();

	private final Color colour;
	private final int width;
	private final int height;

	/**
	 * An eye icon that takes up {@code width} by {@code height}, with the eye centred in it,
	 * so it can stand where another icon would.
	 */
	Eye(Color colour, int width, int height)
	{
		this.colour = colour;
		this.width = Math.max(WIDTH, width);
		this.height = Math.max(HEIGHT, height);
	}

	private static Path2D lids()
	{
		Path2D lids = new Path2D.Float();
		lids.moveTo(0, 4);
		lids.quadTo(5.5, -2, 11, 4);
		lids.quadTo(5.5, 10, 0, 4);
		return lids;
	}

	/** Draws the eye with its bounding box's top-left corner at x, y. */
	static void draw(Graphics2D g, int x, int y, Color colour)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(colour);
		g.setStroke(STROKE);
		g.translate(x, y);
		g.draw(LIDS);
		g.fillOval(3, 1, 5, 5);
		g.translate(-x, -y);
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			draw(g2, x + (width - WIDTH) / 2, y + (height - HEIGHT) / 2, colour);
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
