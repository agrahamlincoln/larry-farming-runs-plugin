package com.larryfarming.panel;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Which spellbook the run is planned for: three chips, with the planned book outlined green
 * when the player is on it and orange when it is a preview.
 *
 * <p>Sits beside the packing list's heading, not inside {@link CarryView}, because
 * {@code CarryView} hides itself when the bag is empty and would take the picker with it.
 *
 * <p>The chips are rebuilt from scratch on a book change and sit outside the panel's
 * {@code RebuildGuard}, so {@link #rebuild} keeps its own change check.
 */
class SpellbookPicker extends JPanel
{
	/** The books a player can plan for, in the order the picker shows them. */
	private static final String[] BOOKS = {"STANDARD", "LUNAR", "ARCEUUS"};
	private static final String[] BOOK_LABELS = {"Standard", "Lunar", "Arceuus"};

	/** The book the player is actually on, which needs no action. */
	private static final Color COLOR_LIVE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	/** A book being previewed: the bag on screen is for one the player has to swap to. */
	private static final Color COLOR_PREVIEW = ColorScheme.BRAND_ORANGE;

	private final JPanel books;

	private Consumer<String> spellbookCallback;

	/**
	 * The inputs the on-screen chips were drawn from. {@link #rebuild} skips an unchanged set
	 * to avoid reallocating labels, borders and listeners on every game event. Nothing here
	 * wraps, so this guards allocation only, not re-measurement.
	 */
	private String drawnPackedFor;
	private String drawnOpen;
	private IconProvider drawnIcons;

	SpellbookPicker()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);

		books = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		books.setOpaque(false);
		books.setAlignmentX(LEFT_ALIGNMENT);

		add(books);
	}

	/**
	 * Receives the player's choice of book. Clicking the open book sends null, meaning
	 * "follow the book I am on".
	 */
	void setSpellbookCallback(Consumer<String> callback)
	{
		this.spellbookCallback = callback;
	}

	/**
	 * The book chips: which one the run is planned for, and which the player is on.
	 *
	 * <p>Both are shown because they can differ: a player who picked another book sees a run
	 * planned for a book they are not on.
	 */
	void rebuild(String packedFor, String open, IconProvider icons)
	{
		// Identity on icons: the change that matters is null to loaded once sprites arrive.
		if (Objects.equals(packedFor, drawnPackedFor) && Objects.equals(open, drawnOpen)
			&& icons == drawnIcons)
		{
			return;
		}
		drawnPackedFor = packedFor;
		drawnOpen = open;
		drawnIcons = icons;

		books.removeAll();
		for (int i = 0; i < BOOKS.length; i++)
		{
			String book = BOOKS[i];
			boolean packed = book.equals(packedFor);
			boolean live = book.equals(open);
			// Icon only: icons with names overflow the sidebar, and this FlowLayout row cuts
			// off rather than wraps. The tooltips name the books.
			ImageIcon sprite = icons != null ? icons.getSpellbookIcon(book) : null;
			JLabel chip = sprite != null ? new JLabel(sprite) : new JLabel(BOOK_LABELS[i]);
			chip.setFont(FontManager.getRunescapeSmallFont());
			chip.setForeground(packed ? Color.WHITE : PanelColors.HEADER);
			chip.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(packed
					? (live ? COLOR_LIVE : COLOR_PREVIEW)
					: PanelColors.CONTAINER_BORDER),
				BorderFactory.createEmptyBorder(2, 3, 2, 3)));
			chip.setToolTipText(live
				? "Your spellbook: " + BOOK_LABELS[i]
				// Mid-sentence, "standard" is lower case; Lunar and Arceuus are names.
				: "Plan for the " + ("STANDARD".equals(book) ? "standard" : BOOK_LABELS[i])
					+ " spellbook");
			chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			chip.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (spellbookCallback != null)
					{
						spellbookCallback.accept(live ? null : book);
					}
				}
			});
			books.add(chip);
		}

		// Revalidate here, not rely on the panel's: the caller returns early when its rows are
		// unchanged, which would leave the highlight on the previous book.
		revalidate();
		repaint();
	}
}
