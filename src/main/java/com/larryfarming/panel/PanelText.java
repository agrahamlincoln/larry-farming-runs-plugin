package com.larryfarming.panel;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import net.runelite.client.ui.FontManager;

/** The panel's one wrapping-text component, and the small builders every view shares. */
final class PanelText
{
	private PanelText()
	{
	}

	/**
	 * A read-only, word-wrapping text area that draws like a label.
	 *
	 * <p>Wraps to its parent's width. A {@code JLabel} cannot wrap without HTML, which
	 * measures its height wrongly inside a {@code BoxLayout}.
	 */
	static JTextArea textArea(String text, Color color, Font font)
	{
		JTextArea area = new JTextArea(text);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setForeground(color);
		area.setFont(font);
		area.setAlignmentX(Component.LEFT_ALIGNMENT);
		area.setBorder(BorderFactory.createEmptyBorder());
		area.setCaret(new NonScrollingCaret());
		return area;
	}

	/** {@link #textArea} in the small font, which nearly every line of the panel uses. */
	static JTextArea small(String text, Color color)
	{
		return textArea(text, color, FontManager.getRunescapeSmallFont());
	}

	/** Lays {@code panel} out as a transparent, left-aligned vertical stack. */
	static <T extends JPanel> T stack(T panel)
	{
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	/** A new {@link #stack}. */
	static JPanel stack()
	{
		return stack(new JPanel());
	}

	/** Adds a row's text column at {@code column}, taking the spare width. */
	static void addFill(JPanel row, GridBagConstraints gbc, int column, Component text)
	{
		gbc.gridx = column;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		row.add(text, gbc);
	}

	/** Gives {@code target} a hand cursor and runs {@code action} on each left click. */
	static void onLeftClick(Component target, Runnable action)
	{
		target.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		target.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					action.run();
				}
			}
		});
	}

	/** Escapes text for an HTML label. */
	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Whether there is anything to show: not null and not blank. */
	static boolean hasText(String text)
	{
		return text != null && !text.isBlank();
	}

	/** The list, or an empty one for null. */
	static <T> List<T> listOf(List<T> list)
	{
		return list != null ? list : List.of();
	}

	/**
	 * Caret that never scrolls its enclosing scroll pane.
	 *
	 * <p>{@code DefaultCaret.adjustVisibility()} calls {@code scrollRectToVisible()}
	 * whenever a rebuilt text component is added. The request bubbles up to the
	 * sidebar's {@code JScrollPane}, which jumps to the new component and loses the
	 * player's scroll position. Panel text is never editable or focusable, so there
	 * is no caret worth following.
	 */
	static final class NonScrollingCaret extends DefaultCaret
	{
		@Override
		protected void adjustVisibility(Rectangle nloc)
		{
			// Empty on purpose: never move the viewport.
		}
	}
}
