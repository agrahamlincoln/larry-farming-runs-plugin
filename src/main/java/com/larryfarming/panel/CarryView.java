package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingCarryItem;
import java.awt.*;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * The bag for a run, laid out the way the game lays out an inventory.
 *
 * <p>Sprites in a four-wide grid, with names on hover, so the player can check the bag at a
 * glance.
 */
class CarryView extends JPanel
{
	/** The game inventory's width. */
	static final int COLUMNS = 4;
	private static final int SQUARE = ItemSquare.SIZE;
	private static final int GAP = ItemSquare.GAP;

	/** Already in the bag. */
	private static final Color COLOR_HELD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	/** Owned but not carried: one withdrawal away. */
	static final Color COLOR_TO_WITHDRAW = ColorScheme.BRAND_ORANGE;

	/**
	 * Shown when the seeds are only accounted for inside a seed box whose contents have not
	 * been checked. Worded as the in-game errand that settles it.
	 * {@code CarryPlanner.theBoxWouldFinishIt} explains the state.
	 *
	 * <p>Shared by this grid's hover and the bank row's line so the two cannot drift.
	 */
	static final String CHECK_SEED_BOX = "Check your seed box";


	private final InventoryBackdrop grid;

	CarryView()
	{
		PanelText.stack(this);
		grid = new InventoryBackdrop(null);
		grid.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 0));

		add(grid);
	}

	/**
	 * Redraws the bag. Called only when the carry signature has changed, since the caller
	 * guards structural rebuilds.
	 *
	 * @param width the width the missing-items line should wrap against, or 0 if not laid
	 *              out yet
	 */
	void rebuild(List<FarmingCarryItem> carry, IconProvider icons, int width)
	{
		grid.removeAll();
		grid.setIcons(icons);
		if (carry == null || carry.isEmpty())
		{
			setVisible(false);
			return;
		}
		setVisible(true);

		int rows = (carry.size() + COLUMNS - 1) / COLUMNS;
		grid.setLayout(new GridLayout(rows, COLUMNS, GAP, GAP));
		// GridLayout divides all the width it gets. Cap it so squares stay sprite-sized.
		Dimension size = new Dimension(COLUMNS * SQUARE + (COLUMNS - 1) * GAP + 4, rows * SQUARE + (rows - 1) * GAP + 6);
		grid.setMaximumSize(size);
		grid.setPreferredSize(size);

		for (FarmingCarryItem item : carry)
		{
			grid.addSlot(square(item, icons));
		}
		// Trailing cells are blanks, not empty slots, which would imply "room for more".
		for (int i = carry.size(); i < rows * COLUMNS; i++)
		{
			grid.add(gap());
		}
	}

	/**
	 * A hover of two lines: the item, then what to know about it. Separate lines, so the
	 * note reads as its own sentence instead of hanging off the name by a dash.
	 */
	static String twoLines(String first, String second)
	{
		return "<html>" + PanelText.escape(first) + "<br>" + PanelText.escape(second) + "</html>";
	}

	/** "3 × Toadflax seed", or a bare name where the count is not the point. */
	static String label(FarmingCarryItem item)
	{
		return item.getQuantity() > 0 ? item.getQuantity() + " × " + item.getName() : item.getName();
	}

	/** A blank, sprite-sized cell for a grid's trailing slots. */
	static JPanel gap()
	{
		JPanel cell = new JPanel();
		cell.setOpaque(false);
		cell.setPreferredSize(new Dimension(SQUARE, SQUARE));
		return cell;
	}

	/**
	 * One inventory square, bordered green when carried or with the leprechaun, orange when
	 * banked or in the seed box, and unbordered when the player owns none.
	 */
	private static JPanel square(FarmingCarryItem item, IconProvider icons)
	{
		ItemSquare cell = ItemSquare.of(item, icons, item.isOwned());

		// The tooltip names the item and the border gives its state. The tooltip adds words
		// only for what a border cannot show: an item note, or the two states below.
		cell.setToolTipText(item.getNote() == null || item.getNote().isEmpty() ? label(item) : twoLines(label(item), item.getNote()));
		switch (item.getAvailability())
		{
			case CARRIED:
				cell.setBorder(BorderFactory.createLineBorder(COLOR_HELD));
				break;
			case AT_LEPRECHAUN:
				// Green like CARRIED: nothing to do before leaving. Taken out on arrival.
				cell.setBorder(BorderFactory.createLineBorder(COLOR_HELD));
				cell.setToolTipText(twoLines(label(item), "With the leprechaun."));
				break;
			case IN_SEED_BOX:
				// Orange like BANKED: not settled until the player checks the box.
				cell.setBorder(BorderFactory.createLineBorder(COLOR_TO_WITHDRAW));
				cell.setToolTipText(twoLines(label(item), CHECK_SEED_BOX + "."));
				break;
			case BANKED:
				cell.setBorder(BorderFactory.createLineBorder(COLOR_TO_WITHDRAW));
				break;
			default:
				cell.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
				break;
		}
		return cell;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
