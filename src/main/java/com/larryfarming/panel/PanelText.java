package com.larryfarming.panel;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;

/** The panel's one wrapping-text component. */
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
