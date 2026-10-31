package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingSettingChoice;
import com.larryfarming.snapshot.FarmingSettingGroup;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Settings as two columns of whole-row checkboxes, in the order given, which never changes:
 * the sites a run visits, filled top to bottom, and what the player's house holds, filled
 * row by row under captions.
 *
 * <p>On is ticked. Off is an empty box with faded text, as a switched-off run row fades. A
 * setting that cannot apply shows a padlock in the box's place and grey text, and takes no
 * click: grey means impossible. A locked setting short of a level shows that level in the
 * box's place instead, in a locked seed's level colour. A house item Larry has seen built
 * shows an eye in the box's place and white text, and takes no click either: what he saw
 * is not the player's to change.
 *
 * <p>A click flips the box and its text at once, before the re-plan that follows it. Nothing
 * here wraps, so the block holding it can be rebuilt without the re-measure flicker.
 */
final class SiteList extends JPanel
{
	private static final int COLUMNS = 2;
	private static final int ROW_HEIGHT = 19;
	private static final int COLUMN_GAP = 10;
	/** Above every caption but the first, so each group reads apart from the one before. */
	private static final int CAPTION_GAP = 5;

	private SiteList()
	{
		super(new GridBagLayout());
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
	}

	/** The sites, two columns filled top to bottom. */
	SiteList(List<FarmingSettingChoice> sites, BiConsumer<String, String> send)
	{
		this();
		int rows = (sites.size() + COLUMNS - 1) / COLUMNS;
		for (int i = 0; i < sites.size(); i++)
		{
			addRow(sites.get(i), i / rows, i % rows, send);
		}
		setMaximumSize(new Dimension(Short.MAX_VALUE, rows * ROW_HEIGHT));
	}

	/**
	 * Each group under its caption, two columns filled left to right, all in one grid so the
	 * columns line up down the whole list.
	 */
	static SiteList inGroups(List<FarmingSettingGroup> groups, BiConsumer<String, String> send)
	{
		SiteList list = new SiteList();
		int y = 0;
		for (FarmingSettingGroup group : groups)
		{
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = y;
			c.gridwidth = COLUMNS;
			c.anchor = GridBagConstraints.WEST;
			list.add(caption(group.getLabel(), y == 0 ? 0 : CAPTION_GAP), c);
			y++;
			List<FarmingSettingChoice> choices = group.getChoices();
			for (int i = 0; i < choices.size(); i++)
			{
				list.addRow(choices.get(i), i % COLUMNS, y + i / COLUMNS, send);
			}
			y += (choices.size() + COLUMNS - 1) / COLUMNS;
		}
		list.setMaximumSize(new Dimension(Short.MAX_VALUE, list.getPreferredSize().height));
		return list;
	}

	/** A caption over settings, as every settings block on the tab heads its groups. */
	static JLabel caption(String text, int gapAbove)
	{
		JLabel caption = new JLabel(text);
		caption.setFont(FontManager.getRunescapeSmallFont());
		caption.setForeground(PanelColors.HEADER);
		caption.setAlignmentX(LEFT_ALIGNMENT);
		caption.setBorder(BorderFactory.createEmptyBorder(gapAbove, 2, 3, 0));
		return caption;
	}

	private void addRow(FarmingSettingChoice choice, int column, int line,
		BiConsumer<String, String> send)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = column;
		c.gridy = line;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		// The last column takes the spare width, so every name keeps its own.
		c.weightx = column == COLUMNS - 1 ? 1 : 0;
		c.insets = new Insets(0, 0, 0, column < COLUMNS - 1 ? COLUMN_GAP : 0);
		JComponent row = row(choice, send);
		row.setPreferredSize(new Dimension(row.getPreferredSize().width, ROW_HEIGHT));
		add(row, c);
	}

	/**
	 * One setting: the tab's checkbox, or in the box's place an eye for a seen house item,
	 * and for a locked one the level it needs or else a padlock.
	 */
	private static JComponent row(FarmingSettingChoice choice, BiConsumer<String, String> send)
	{
		JCheckBox box = FarmingPanel.checkbox(choice.getLabel());
		box.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		if (choice.isSeen() && !choice.isLocked())
		{
			return marked(choice.getLabel(), box,
				new Eye(PanelColors.HEADER, boxWidth(), boxHeight()), Color.WHITE);
		}
		if (choice.isLocked())
		{
			Icon mark = choice.getLevel() > 0
				? new LevelMark(choice.getLevel(), boxWidth(), boxHeight())
				: new Padlock(ColorScheme.MEDIUM_GRAY_COLOR, boxWidth(), boxHeight());
			return marked(choice.getLabel(), box, mark, ColorScheme.MEDIUM_GRAY_COLOR);
		}
		box.setSelected(choice.isOn());
		box.setForeground(colour(choice.isOn()));
		box.addActionListener(e ->
		{
			box.setForeground(colour(box.isSelected()));
			send.accept(choice.getKey(), String.valueOf(box.isSelected()));
		});
		return box;
	}

	/**
	 * A label laid out as the checkbox it stands for, with a mark in the box's place, so its
	 * text lines up with the rest.
	 */
	private static JLabel marked(String name, JCheckBox like, Icon mark, Color text)
	{
		JLabel label = new JLabel(name, mark, SwingConstants.LEFT);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(text);
		label.setIconTextGap(like.getIconTextGap());
		label.setBorder(like.getBorder());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * The level a locked setting needs, in the box's place, drawn as a locked seed's level
	 * is: the stack-count yellow over a dark shadow. It takes the box's place rather than
	 * following the name, because two columns of names with a level after each are wider
	 * than the sidebar.
	 */
	static final class LevelMark implements Icon
	{
		private static final Color SHADOW = Color.BLACK;

		private final String level;
		private final int width;
		private final int height;

		LevelMark(int level, int width, int height)
		{
			this.level = String.valueOf(level);
			this.width = width;
			this.height = height;
		}

		/** The level drawn, for a reader that cannot see the pixels. */
		String level()
		{
			return level;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setFont(FontManager.getRunescapeSmallFont());
				FontMetrics metrics = g2.getFontMetrics();
				int textX = x + (width - metrics.stringWidth(level)) / 2;
				int baseline = y + (height + metrics.getAscent()) / 2 - 1;
				g2.setColor(SHADOW);
				g2.drawString(level, textX + 1, baseline + 1);
				g2.setColor(PanelColors.LEVEL);
				g2.drawString(level, textX, baseline);
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth()
		{
			return width;
		}

		@Override
		public int getIconHeight()
		{
			return height;
		}
	}

	private static int boxWidth()
	{
		Icon boxIcon = UIManager.getIcon("CheckBox.icon");
		return boxIcon != null ? boxIcon.getIconWidth() : Padlock.WIDTH + 4;
	}

	private static int boxHeight()
	{
		Icon boxIcon = UIManager.getIcon("CheckBox.icon");
		return boxIcon != null ? boxIcon.getIconHeight() : Padlock.HEIGHT + 2;
	}

	private static Color colour(boolean on)
	{
		return on ? PanelColors.HEADER : RunsListView.COLOR_OFF;
	}
}
