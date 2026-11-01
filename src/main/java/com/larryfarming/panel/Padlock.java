package com.larryfarming.panel;

import java.awt.*;

/**
 * The padlock that marks a setting the player cannot change: drawn on a locked setting
 * square, and in a locked row's checkbox place.
 *
 * <p>One drawing for both, so a locked square and a locked row read as the same thing.
 */
final class Padlock extends Mark
{
	/** The lock's own width and height, shackle to base. */
	static final int WIDTH = 8;
	static final int HEIGHT = 11;

	private static final Stroke STROKE = new BasicStroke(1.4f);
	private static final Color KEYHOLE = new Color(40, 40, 40);

	/**
	 * A padlock icon that takes up {@code width} by {@code height}, with the lock centred in
	 * it, so it can stand where another icon would.
	 */
	Padlock(Color colour, int width, int height)
	{
		super(colour, WIDTH, HEIGHT, width, height);
	}

	/** Draws the lock with its shackle's top-left corner at x, y. */
	static void draw(Graphics2D g, int x, int y, Color colour)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(colour);
		g.setStroke(STROKE);
		g.drawArc(x + 1, y, 6, 8, 0, 180);
		g.drawLine(x + 1, y + 4, x + 1, y + 5);
		g.drawLine(x + 7, y + 4, x + 7, y + 5);
		g.fillRoundRect(x, y + 5, 8, 6, 2, 2);
		g.setColor(KEYHOLE);
		g.fillRect(x + 3, y + 7, 2, 2);
	}

	@Override
	void drawAt(Graphics2D g, int x, int y, Color colour)
	{
		draw(g, x, y, colour);
	}
}
