package com.larryfarming.panel;

import java.awt.Color;
import java.util.function.Consumer;
import javax.swing.*;
import net.runelite.client.ui.FontManager;

/**
 * A clickable header line over content that shows only while open. Every fold on the
 * side panel is one of these, so their headers cannot drift apart.
 *
 * <p>The header can carry a suffix and colour of its owner's choosing, so a consequence
 * stays readable with the fold shut (see {@link TravelFold}).
 */
class Fold extends JPanel
{
	private static final String SHUT = "▸";
	private static final String OPEN = "▾";

	private final String title;
	private final JLabel toggle;
	private final JPanel content;

	private boolean open;
	private String suffix = "";
	private Color colour = PanelColors.HEADER;
	private Consumer<Boolean> openCallback;

	Fold(String title)
	{
		this.title = title;
		PanelText.stack(this);
		setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

		toggle = new JLabel();
		toggle.setFont(FontManager.getRunescapeSmallFont());
		toggle.setAlignmentX(LEFT_ALIGNMENT);
		toggle.setBorder(BorderFactory.createEmptyBorder(0, 2, 3, 0));
		PanelText.onLeftClick(toggle, () ->
		{
			open = !open;
			showOpen();
			if (openCallback != null)
			{
				openCallback.accept(open);
			}
		});

		content = PanelText.stack();
		content.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));

		add(toggle);
		add(content);
		showOpen();
	}

	/** What the fold shows while open. */
	JPanel content()
	{
		return content;
	}

	/** Puts the remembered open state back on screen, without reporting a change. */
	void setOpen(boolean open)
	{
		this.open = open;
		showOpen();
	}

	/** Receives the fold's new state when the player opens or shuts it. */
	void setOpenCallback(Consumer<Boolean> callback)
	{
		this.openCallback = callback;
	}

	/** Words after the title and the header's colour, kept while the fold is shut. */
	void setHeader(String suffix, Color colour)
	{
		this.suffix = suffix;
		this.colour = colour;
		showOpen();
	}

	/** The header as drawn. */
	String headerText()
	{
		return toggle.getText();
	}

	/** The header's colour as drawn. */
	Color headerColour()
	{
		return toggle.getForeground();
	}

	private void showOpen()
	{
		content.setVisible(open);
		toggle.setText((open ? OPEN : SHUT) + " " + title + suffix);
		toggle.setForeground(colour);
		revalidate();
		repaint();
	}
}
