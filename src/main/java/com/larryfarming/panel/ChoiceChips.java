package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingSettingPick;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;

/**
 * A setting that takes one of a few values, drawn as a row of chips. The picked chip wears
 * the green outline a ticked {@link SettingSquare} does, and a click on another picks it at
 * once and sends its value.
 *
 * <p>Each chip is a {@link SmallButton} as wide as its text, and the row's spare width is
 * shared out in the same proportion. Refreshes restyle the chips in place, rebuilding them
 * only when the options change, so a refresh cannot flicker.
 */
class ChoiceChips extends JPanel
{
	private static final int CHIP_HEIGHT = 20;
	private static final int GAP = 2;
	private static final Border ON_BORDER =
		BorderFactory.createLineBorder(ColorScheme.PROGRESS_COMPLETE_COLOR);

	private final BiConsumer<String, String> send;
	private final List<SmallButton> chips = new ArrayList<>();
	private final List<String> values = new ArrayList<>();
	/** The button look before any outline, put back when a chip is no longer picked. */
	private Border plainBorder;
	private Color plainText;
	private List<FarmingSettingPick.Option> shownOptions = List.of();
	private String key;
	private String selected;

	/** @param send receives the setting's key and the picked value */
	ChoiceChips(BiConsumer<String, String> send)
	{
		super(new GridBagLayout());
		this.send = send;
		setOpaque(false);
	}

	/** Shows this setting, rebuilding the chips only when its options have changed. */
	void show(FarmingSettingPick pick)
	{
		key = pick.getKey();
		if (!pick.getOptions().equals(shownOptions))
		{
			build(pick.getOptions());
		}
		select(pick.getSelected());
	}

	private void build(List<FarmingSettingPick.Option> options)
	{
		removeAll();
		chips.clear();
		values.clear();
		shownOptions = List.copyOf(options);
		for (int i = 0; i < options.size(); i++)
		{
			FarmingSettingPick.Option option = options.get(i);
			SmallButton chip = new SmallButton(option.getLabel());
			if (plainBorder == null)
			{
				plainBorder = chip.getBorder();
				plainText = chip.getForeground();
			}
			chip.setToolTipText(option.getHint());
			// Measured before any outline, so picking a chip never changes its size.
			int width = chip.getPreferredSize().width;
			chip.setPreferredSize(new Dimension(width, CHIP_HEIGHT));
			chip.addActionListener(e -> pick(option.getValue()));
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = i;
			c.weightx = width;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.insets = new Insets(0, i == 0 ? 0 : GAP, 0, 0);
			add(chip, c);
			chips.add(chip);
			values.add(option.getValue());
		}
		revalidate();
	}

	private void pick(String value)
	{
		if (value.equals(selected))
		{
			return;
		}
		select(value);
		if (send != null && key != null)
		{
			send.accept(key, value);
		}
	}

	private void select(String value)
	{
		selected = value;
		for (int i = 0; i < chips.size(); i++)
		{
			boolean on = values.get(i).equals(value);
			SmallButton chip = chips.get(i);
			chip.setBorder(on ? ON_BORDER : plainBorder);
			chip.setForeground(on ? Color.WHITE : plainText);
		}
	}
}
