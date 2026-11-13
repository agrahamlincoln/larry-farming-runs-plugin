package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingPatchPick;
import com.larryfarming.snapshot.FarmingSettingChoice;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.*;
import net.runelite.api.gameval.ItemID;

/**
 * The herb farms' allotments and flower patches: a row per farm with a square per patch,
 * drawn as its pick, and under the row the picker of the patch opened. One picker is open at
 * a time, and its square takes the gold outline that marks what is current.
 *
 * <p>Opening and closing a picker rebuilds this block alone. It holds no wrapping text, so
 * the rebuild cannot flicker.
 */
final class PatchPicks extends JPanel
{
	private static final int LABEL_WIDTH = 58;
	private static final int BADGE_SIZE = 14;

	private final List<FarmingPatchPick> picks;
	private final IconProvider icons;
	private final BiConsumer<String, String> send;
	/** Hears which patch's picker is open, or null, so a rebuild of the list reopens it. */
	private final Consumer<String> remember;
	private String open;

	PatchPicks(List<FarmingPatchPick> picks, String open, IconProvider icons, BiConsumer<String, String> send, Consumer<String> remember)
	{
		this.picks = picks;
		this.open = open;
		this.icons = icons;
		this.send = send;
		this.remember = remember;
		PanelText.stack(this);
		build();
	}

	/** The picks come farm by farm, so a farm's row ends where the next farm's begins. */
	private void build()
	{
		removeAll();
		int start = 0;
		for (int i = 1; i <= picks.size(); i++)
		{
			if (i == picks.size() || !picks.get(i).getFarm().equals(picks.get(start).getFarm()))
			{
				List<FarmingPatchPick> farm = picks.subList(start, i);
				List<JComponent> squares = new ArrayList<>();
				for (FarmingPatchPick patch : farm)
				{
					squares.add(patchSquare(patch));
				}
				JLabel name = PanelText.label(farm.get(0).getFarm(), PanelColors.HEADER);
				name.setPreferredSize(new Dimension(LABEL_WIDTH, ItemSquare.SIZE));
				add(row(name, SettingGrid.grid(squares, icons, squares.size())));
				for (FarmingPatchPick patch : farm)
				{
					if (patch.getKey().equals(open))
					{
						add(picker(patch));
					}
				}
				start = i;
			}
		}
		revalidate();
		repaint();
	}

	private void show(String key)
	{
		open = key;
		remember.accept(key);
		build();
	}

	/** The patch as picked: its crop at full colour, or faint and tagged OFF, with a bucket for compost. */
	private SettingSquare patchSquare(FarmingPatchPick patch)
	{
		FarmingSettingChoice picked = patch.getOptions().stream().filter(FarmingSettingChoice::isOn).findFirst().orElse(patch.getOptions().get(0));
		boolean on = picked.getItemId() > 0;
		ImageIcon sprite = icons == null || !on ? null : icons.getInventoryIcon(picked.getItemId(), true);
		SettingSquare square = new SettingSquare(sprite, null, picked.getTag(), picked.getLevel(), false, on, false);
		// A pick above the player's level plants once they reach it, so it keeps its colour.
		if (on && picked.isLocked())
		{
			square.showLevel();
		}
		if (icons != null && patch.isComposting())
		{
			square.setBadge(icons.getGearIcon(ItemID.BUCKET_COMPOST, BADGE_SIZE, true, true));
		}
		boolean opened = patch.getKey().equals(open);
		// The patch square summarises the pick, so it takes no green on-outline: gold while
		// its picker is open, the mark of what is current.
		square.setBorder(opened ? BorderFactory.createLineBorder(FarmingPanel.COLOR_DUE) : BorderFactory.createEmptyBorder(1, 1, 1, 1));
		square.setToolTipText(patch.getTitle() + ": " + picked.getLabel() + (patch.isComposting() ? ", for compost" : ""));
		PanelText.onLeftClick(square, () -> show(opened ? null : patch.getKey()));
		return square;
	}

	/**
	 * The open patch's picker: Off and its crops, the pick outlined green. A patch beside a
	 * compost bin has For compost beside the last crop, so it costs no row of its own.
	 */
	private JPanel picker(FarmingPatchPick patch)
	{
		JPanel picker = PanelText.stack();
		picker.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, FarmingPanel.COLOR_DUE),
			BorderFactory.createEmptyBorder(2, 6, 6, 0)));
		picker.add(SiteList.caption(patch.getTitle(), 0));
		List<JComponent> options = new ArrayList<>();
		for (FarmingSettingChoice option : patch.getOptions())
		{
			SettingSquare square = SettingGrid.square(option, icons);
			square.outlineWhileLocked();
			if (!option.isLocked())
			{
				PanelText.onLeftClick(square, () ->
				{
					// The pick saved again would turn a quiet default into the player's choice.
					if (!option.isOn())
					{
						send.accept(patch.getKey(), option.getKey());
					}
					show(null);
				});
			}
			options.add(square);
		}
		if (patch.getCompostKey() == null)
		{
			picker.add(SettingGrid.grid(options, icons));
			return picker;
		}
		JComponent last = options.remove(options.size() - 1);
		picker.add(SettingGrid.grid(options, icons));
		JCheckBox compost = FarmingPanel.checkbox("For compost", 0);
		compost.setSelected(patch.isForCompost());
		compost.setToolTipText("Plant only while your compost is low");
		compost.addActionListener(e -> send.accept(patch.getCompostKey(), String.valueOf(compost.isSelected())));
		JPanel lastRow = row(SettingGrid.grid(List.of(last), icons, 1), compost);
		lastRow.setBorder(BorderFactory.createEmptyBorder(ItemSquare.GAP, 0, 0, 0));
		picker.add(lastRow);
		return picker;
	}

	/** A line of the block: the lead, then the rest, each at its own width so no grid stretches. */
	private static JPanel row(JComponent lead, JComponent rest)
	{
		JPanel row = PanelText.panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, ItemSquare.GAP, 0));
		row.add(lead);
		row.add(Box.createHorizontalStrut(ItemSquare.GAP * 2));
		row.add(rest);
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, ItemSquare.SIZE + ItemSquare.GAP));
		return row;
	}
}
