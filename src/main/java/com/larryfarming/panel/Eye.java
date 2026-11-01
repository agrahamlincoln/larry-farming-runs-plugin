package com.larryfarming.panel;

import java.awt.*;
import java.awt.geom.Path2D;

/**
 * The open eye that marks a setting Larry has seen for himself, drawn in a row's checkbox
 * place. It sits beside the {@link Padlock}: seen built shows the eye, impossible the lock.
 */
final class Eye extends Mark
{
	/** The eye's own width and height, corner to corner. */
	static final int WIDTH = 11;
	static final int HEIGHT = 8;

	private static final Stroke STROKE = new BasicStroke(1.2f);

	/** The lids, two arcs meeting at the corners, drawn once and translated to each place. */
	private static final Path2D LIDS = lids();

	/**
	 * An eye icon that takes up {@code width} by {@code height}, with the eye centred in it,
	 * so it can stand where another icon would.
	 */
	Eye(Color colour, int width, int height)
	{
		super(colour, WIDTH, HEIGHT, width, height);
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
	@Override
	void drawAt(Graphics2D g, int x, int y, Color colour)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(colour);
		g.setStroke(STROKE);
		g.translate(x, y);
		g.draw(LIDS);
		g.fillOval(3, 1, 5, 5);
		g.translate(-x, -y);
	}
}
