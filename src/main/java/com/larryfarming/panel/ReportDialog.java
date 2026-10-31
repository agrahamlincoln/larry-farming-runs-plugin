package com.larryfarming.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * "Report a problem": the player says what went wrong, saves Larry's report to a file of
 * their choosing, and opens an issue to attach it to. Nothing leaves their machine unless
 * they attach it themselves.
 *
 * <p>The dialog stays open after a save, with the result spelled out under the buttons, so
 * the issue button is still there for the next step.
 */
class ReportDialog extends JDialog
{
	/** Every line of text in the dialog wraps at this width rather than being cut short. */
	private static final int TEXT_WIDTH = 220;

	/** What the report holds, in player words, so they know before they share it. */
	static final String CONTENTS = "The report holds your farm runs as Larry planned them, "
		+ "the items and patches he tracks, your Larry settings and his notes on what he read, "
		+ "from this session and the one before. It never holds your player name. Read it "
		+ "before you share it: it is a plain text file.";

	private final JTextArea message = new JTextArea(5, 24);
	private final JLabel result = text("", PanelColors.HEADER);
	private final JLabel resultDetail = text("", PanelColors.META);
	private final SmallButton showFolder = new SmallButton("Show in folder");
	/** Off while a save is under way, so a second click cannot start another. */
	private final SmallButton save = new SmallButton("Save report...");
	private Runnable openFolder;

	ReportDialog(Window owner, ReportActions actions)
	{
		super(owner, "Report a problem with Larry", ModalityType.MODELESS);
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);

		body.add(left(text("What went wrong?", PanelColors.HEADER)));
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setFont(FontManager.getRunescapeFont());
		JScrollPane scroll = new JScrollPane(message);
		scroll.setAlignmentX(LEFT_ALIGNMENT);
		body.add(scroll);

		JLabel contents = text(CONTENTS, PanelColors.META);
		contents.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		body.add(left(contents));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		buttons.setOpaque(false);
		save.addActionListener(e -> actions.save(message.getText(), this, new Shown()));
		SmallButton issue = new SmallButton("Open a GitHub issue");
		issue.addActionListener(e -> actions.openIssue(message.getText()));
		buttons.add(save);
		buttons.add(Box.createHorizontalStrut(6));
		buttons.add(issue);
		body.add(left(buttons));

		result.setFont(FontManager.getRunescapeBoldFont());
		result.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		body.add(left(result));
		body.add(left(resultDetail));
		showFolder.setVisible(false);
		showFolder.addActionListener(e ->
		{
			if (openFolder != null)
			{
				openFolder.run();
			}
		});
		JPanel folderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		folderRow.setOpaque(false);
		folderRow.add(showFolder);
		body.add(left(folderRow));

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(body, BorderLayout.CENTER);
		pack();
		setLocationRelativeTo(owner);
	}

	/** Opens the dialog over the window that holds {@code from}. */
	static void open(Component from, ReportActions actions)
	{
		new ReportDialog(SwingUtilities.getWindowAncestor(from), actions).setVisible(true);
	}

	/** Puts a save's outcome under the buttons, and grows the dialog to fit it. */
	private class Shown implements ReportActions.Outcome
	{
		@Override
		public void working(String what)
		{
			show(what, "", null);
			save.setEnabled(false);
		}

		@Override
		public void saved(String fileName, Runnable folder)
		{
			show("Report saved.", "Saved as " + fileName + ". Now open a GitHub issue and "
				+ "drag the file into it.", folder);
			save.setEnabled(true);
		}

		@Override
		public void notSaved(String why)
		{
			show("Not saved.", why, null);
			save.setEnabled(true);
		}

		private void show(String headline, String detail, Runnable folder)
		{
			result.setText(wrapped(headline));
			resultDetail.setText(detail.isEmpty() ? "" : wrapped(detail));
			openFolder = folder;
			showFolder.setVisible(folder != null);
			pack();
		}
	}

	private static JLabel text(String words, Color colour)
	{
		JLabel label = new JLabel(words.isEmpty() ? "" : wrapped(words));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	/** HTML at a fixed width, so the label wraps and finds its own height. */
	private static String wrapped(String words)
	{
		return "<html><div style='width:" + TEXT_WIDTH + "px'>" + escape(words) + "</div></html>";
	}

	private static String escape(String words)
	{
		return words.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static <T extends JComponent> T left(T component)
	{
		component.setAlignmentX(LEFT_ALIGNMENT);
		return component;
	}
}
