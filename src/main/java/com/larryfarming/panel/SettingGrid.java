package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingSettingChoice;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The farming tab's grids of setting squares: one {@link SettingSquare} per choice, laid out
 * four across over the inventory colour like the packing grid.
 */
final class SettingGrid
{
	/** A spell's icon on a square, about an inventory sprite's height. */
	private static final int SPRITE_SIZE = 28;

	private SettingGrid()
	{
	}

	/**
	 * Ticks: a click flips the one square at once and sends its new state. A locked square
	 * takes no click.
	 */
	static InventoryBackdrop toggles(List<FarmingSettingChoice> choices, IconProvider icons,
		BiConsumer<String, String> send)
	{
		List<SettingSquare> squares = new ArrayList<>(choices.size());
		for (FarmingSettingChoice choice : choices)
		{
			squares.add(toggle(choice, icons, send));
		}
		return grid(squares, icons);
	}

	/** One tick square, flipping itself on a click and sending its new state. */
	static SettingSquare toggle(FarmingSettingChoice choice, IconProvider icons,
		BiConsumer<String, String> send)
	{
		SettingSquare square = square(choice, icons);
		if (!choice.isLocked())
		{
			square.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (!SwingUtilities.isLeftMouseButton(e))
					{
						return;
					}
					boolean on = !square.isOn();
					square.setOn(on);
					send.accept(choice.getKey(), String.valueOf(on));
				}
			});
		}
		return square;
	}

	/**
	 * A square showing the choice's item, tag, lock, ownership and state. Takes no click by
	 * itself.
	 */
	static SettingSquare square(FarmingSettingChoice choice, IconProvider icons)
	{
		boolean grey = choice.isLocked() || choice.isMissing();
		ImageIcon sprite = icon(choice, icons, true);
		ImageIcon greySprite = grey ? icon(choice, icons, false) : null;
		SettingSquare square = new SettingSquare(sprite, greySprite, choice.getTag(),
			choice.getLevel(), choice.isLocked(), choice.isOn(), choice.isMissing());
		square.setToolTipText(tooltip(choice));
		return square;
	}

	/** The choice's game sprite where it names one, else its item, or null for neither. */
	private static ImageIcon icon(FarmingSettingChoice choice, IconProvider icons, boolean held)
	{
		if (icons == null)
		{
			return null;
		}
		if (choice.getSpriteId() > 0)
		{
			return icons.getSpriteIcon(choice.getSpriteId(), SPRITE_SIZE, held);
		}
		return choice.getItemId() > 0 ? icons.getInventoryIcon(choice.getItemId(), held) : null;
	}

	private static String tooltip(FarmingSettingChoice choice)
	{
		if (choice.isLocked())
		{
			return choice.getLabel() + " needs " + choice.getLevel() + " Farming";
		}
		if (!choice.isMissing())
		{
			return choice.getLabel();
		}
		String note = choice.getMissingNote();
		return choice.getLabel() + ". " + (note != null ? note : "None seen yet");
	}

	/**
	 * Squares in the packing grid's layout: four across over the inventory colour, trailing
	 * cells left blank.
	 */
	static InventoryBackdrop grid(List<? extends JComponent> cells, IconProvider icons)
	{
		int rows = Math.max(1, (cells.size() + CarryView.COLUMNS - 1) / CarryView.COLUMNS);
		InventoryBackdrop grid = new InventoryBackdrop(new GridLayout(rows,
			CarryView.COLUMNS, ItemSquare.GAP, ItemSquare.GAP));
		grid.setIcons(icons);
		// GridLayout divides all the width it gets. Cap it so squares stay sprite-sized.
		Dimension size = new Dimension(
			CarryView.COLUMNS * ItemSquare.SIZE + (CarryView.COLUMNS - 1) * ItemSquare.GAP,
			rows * ItemSquare.SIZE + (rows - 1) * ItemSquare.GAP);
		grid.setMaximumSize(size);
		grid.setPreferredSize(size);
		for (JComponent cell : cells)
		{
			grid.addSlot(cell);
		}
		// Trailing cells are blanks, not empty slots, which would imply "room for more".
		for (int i = cells.size(); i < rows * CarryView.COLUMNS; i++)
		{
			JPanel blank = new JPanel();
			blank.setOpaque(false);
			grid.add(blank);
		}
		return grid;
	}
}
