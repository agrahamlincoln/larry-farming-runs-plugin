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
	/** The header's arrows, which the runs list's rows share. */
	static final String SHUT = "▸";
	static final String OPEN = "▾";

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

	/**
	 * Puts the remembered open state back on screen, without reporting a change.
	 *
	 * @param callback receives the fold's new state when the player opens or shuts it, or
	 *     null for nobody
	 */
	void restore(boolean open, Consumer<Boolean> callback)
	{
		this.open = open;
		this.openCallback = callback;
		showOpen();
	}

	/**
	 * Words after the title and the header's colour, kept while the fold is shut. Set in
	 * place, and only on a change, since the header relays out when set.
	 */
	void setHeader(String suffix, Color colour)
	{
		if (suffix.equals(this.suffix) && colour.equals(this.colour))
		{
			return;
		}
		this.suffix = suffix;
		this.colour = colour;
		showOpen();
	}

	/** Appends {@code words} to the header in amber while {@code on}, and nothing otherwise. */
	void warn(boolean on, String words)
	{
		setHeader(on ? words : "", on ? FarmingPanel.COLOR_DUE : PanelColors.HEADER);
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
