package com.larryfarming.panel;

import java.awt.Color;
import java.awt.Graphics;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * One inventory square: a sprite, its stack count, and a seed tag along the bottom.
 *
 * <p>Painted by hand because a Swing label puts its text beside the icon, and the count and
 * tag both belong on top of the sprite.
 *
 * <p>Shared by the packing grid ({@link CarryView}) and the bank rows so both read as the
 * same inventory.
 */
class ItemSquare extends JPanel
{
	/**
	 * An item sprite (36×32) plus a margin. Sprites draw at full size because shrunken seed
	 * sprites are hard to tell apart.
	 */
	static final int SIZE = 42;
	static final int GAP = 3;

	/** The game's own stack-count colour. */
	private static final Color COLOR_COUNT = Color.YELLOW;
	/** The seed tag, and the dark line under it that keeps it readable on a pale sprite. */
	private static final Color COLOR_TAG = Color.WHITE;
	private static final Color COLOR_TAG_SHADOW = Color.BLACK;

	private final ImageIcon sprite;
	private final String count;
	private final String tag;

	ItemSquare(ImageIcon sprite, String count, String tag)
	{
		this.sprite = sprite;
		this.count = count;
		this.tag = tag;
		// Transparent so the InventoryBackdrop slot colour shows through.
		setOpaque(false);
		setFont(FontManager.getRunescapeSmallFont());
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (sprite != null)
		{
			sprite.paintIcon(this, g,
				(getWidth() - sprite.getIconWidth()) / 2,
				(getHeight() - sprite.getIconHeight()) / 2);
		}
		if (count != null)
		{
			g.setFont(getFont());
			g.setColor(COLOR_COUNT);
			g.drawString(count, 3, g.getFontMetrics().getAscent() + 1);
		}
		if (tag != null && !tag.isEmpty())
		{
			// Bottom edge, so it never collides with the count at the top.
			g.setFont(getFont());
			int x = 2;
			int y = getHeight() - 2;
			g.setColor(COLOR_TAG_SHADOW);
			g.drawString(tag, x + 1, y + 1);
			g.setColor(COLOR_TAG);
			g.drawString(tag, x, y);
		}
	}
}
