package com.larryfarming.panel;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One clickable square in a farming setting grid: a crop or item to switch on or off.
 *
 * <p>Drawn like an {@link ItemSquare}, with three states on top. On draws a green outline, the
 * spellbook picker's live-choice green, because faint alone reads too close to full colour on
 * a grid of similar sprites. Off draws the sprite faint with no outline. Locked draws it grey,
 * faint, with a padlock and the level it needs. Missing, for an item the player does not own,
 * draws it grey with no padlock and stays clickable; off as well draws it grey and faint.
 *
 * <p>Every setting grid in the panel uses this one square, so on and off read the same in each.
 *
 * <p>The states change in place, so a click shows at once without waiting for the re-plan
 * that follows it.
 */
class SettingSquare extends JPanel
{
	/** How strongly an unticked sprite shows, and any other faint icon. */
	static final AlphaComposite DIM = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f);
	/** The tag fades less than the sprite, so it stays readable. */
	private static final AlphaComposite DIM_TAG = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f);

	private static final Color COLOR_TAG = Color.WHITE;
	private static final Color COLOR_SHADOW = Color.BLACK;
	private static final Color COLOR_LEVEL = PanelColors.LEVEL;
	private static final Color COLOR_LOCK = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color COLOR_LOCK_BACK = new Color(0, 0, 0, 160);

	private static final Border PLAIN_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);
	private static final Border ON_BORDER = BorderFactory.createLineBorder(ColorScheme.PROGRESS_COMPLETE_COLOR);

	private final ImageIcon sprite;
	private final ImageIcon greySprite;
	private final String tag;
	private final String level;
	private final boolean locked;
	private final boolean missing;
	private boolean on;

	/**
	 * @param greySprite the sprite drained of colour, drawn while locked or missing
	 * @param missing    true for an item the player does not own
	 */
	SettingSquare(ImageIcon sprite, ImageIcon greySprite, String tag, int level, boolean locked, boolean on, boolean missing)
	{
		this.sprite = sprite;
		this.greySprite = greySprite;
		this.tag = tag;
		this.level = String.valueOf(level);
		this.locked = locked;
		this.on = on;
		this.missing = missing;
		// Transparent so the InventoryBackdrop slot colour shows through.
		setOpaque(false);
		setFont(FontManager.getRunescapeSmallFont());
		Dimension size = new Dimension(ItemSquare.SIZE, ItemSquare.SIZE);
		setPreferredSize(size);
		setMinimumSize(size);
		setBorder(isOutlined() ? ON_BORDER : PLAIN_BORDER);
		if (!locked)
		{
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}
	}

	boolean isOn()
	{
		return on;
	}

	boolean isLocked()
	{
		return locked;
	}

	boolean isMissing()
	{
		return missing;
	}

	/** Whether the sprite is drawn drained of colour: locked, or not owned. */
	boolean isGrey()
	{
		return (locked || missing) && greySprite != null;
	}

	/** Whether the sprite is drawn faint: locked, or switched off. */
	boolean isFaint()
	{
		return locked || !on;
	}

	/** Whether the square shows the green outline that marks it on. A locked square never does. */
	boolean isOutlined()
	{
		return on && !locked;
	}

	void setOn(boolean on)
	{
		if (this.on != on)
		{
			this.on = on;
			setBorder(isOutlined() ? ON_BORDER : PLAIN_BORDER);
			repaint();
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			Composite full = g2.getComposite();
			ImageIcon shown = isGrey() ? greySprite : sprite;
			boolean faint = isFaint();
			if (shown != null)
			{
				if (faint)
				{
					g2.setComposite(DIM);
				}
				shown.paintIcon(this, g2, (getWidth() - shown.getIconWidth()) / 2, (getHeight() - shown.getIconHeight()) / 2);
				g2.setComposite(full);
			}
			g2.setFont(getFont());
			if (locked)
			{
				int ascent = g2.getFontMetrics().getAscent();
				g2.setColor(COLOR_SHADOW);
				g2.drawString(level, 4, ascent + 2);
				g2.setColor(COLOR_LEVEL);
				g2.drawString(level, 3, ascent + 1);
				drawLock(g2, getWidth() - 12, 3);
			}
			else if (tag != null && !tag.isEmpty())
			{
				if (!on)
				{
					g2.setComposite(DIM_TAG);
				}
				int y = getHeight() - 3;
				g2.setColor(COLOR_SHADOW);
				g2.drawString(tag, 4, y + 1);
				g2.setColor(COLOR_TAG);
				g2.drawString(tag, 3, y);
			}
		}
		finally
		{
			g2.dispose();
		}
	}

	/** The padlock on a dark backing, so it reads over any sprite. */
	private static void drawLock(Graphics2D g, int x, int y)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(COLOR_LOCK_BACK);
		g.fillRoundRect(x - 1, y + 3, 10, 9, 2, 2);
		Padlock.draw(g, x, y, COLOR_LOCK);
	}
}
