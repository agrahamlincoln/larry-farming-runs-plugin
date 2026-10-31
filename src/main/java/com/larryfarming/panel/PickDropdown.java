package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingSettingPick;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.DefaultComboBoxModel;

/**
 * A setting that takes one of many values, as a dropdown of the options' labels in their
 * fixed order. Picking one sends its value; a selection made in code never does, so a
 * refresh cannot write a setting back.
 */
class PickDropdown extends SmallDropdown<String>
{
	private final DefaultComboBoxModel<String> model;
	private final BiConsumer<String, String> send;
	private final List<String> values = new ArrayList<>();
	private List<FarmingSettingPick.Option> shownOptions = List.of();
	private String key;
	/** True while the dropdown is changed in code, so its selection events are not a pick. */
	private boolean updating;

	/** @param send receives the setting's key and the picked value */
	PickDropdown(BiConsumer<String, String> send)
	{
		this(new DefaultComboBoxModel<>(), send);
	}

	private PickDropdown(DefaultComboBoxModel<String> model, BiConsumer<String, String> send)
	{
		super(model);
		this.model = model;
		this.send = send;
		addActionListener(e -> picked());
	}

	/** Shows this setting, refilling the list only when its options have changed. */
	void show(FarmingSettingPick pick)
	{
		updating = true;
		try
		{
			key = pick.getKey();
			setToolTipText(pick.getHint());
			if (!pick.getOptions().equals(shownOptions))
			{
				shownOptions = List.copyOf(pick.getOptions());
				model.removeAllElements();
				values.clear();
				for (FarmingSettingPick.Option option : shownOptions)
				{
					model.addElement(option.getLabel());
					values.add(option.getValue());
				}
			}
			int at = values.indexOf(pick.getSelected());
			if (at >= 0 && at != getSelectedIndex())
			{
				setSelectedIndex(at);
			}
		}
		finally
		{
			updating = false;
		}
	}

	/** The value picked, or null when nothing is. */
	String selectedValue()
	{
		int at = getSelectedIndex();
		return at >= 0 && at < values.size() ? values.get(at) : null;
	}

	private void picked()
	{
		String value = selectedValue();
		if (updating || value == null || send == null || key == null)
		{
			return;
		}
		send.accept(key, value);
	}
}
