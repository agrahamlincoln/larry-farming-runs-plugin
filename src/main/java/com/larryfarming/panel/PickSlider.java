package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingSettingPick;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * A setting over a range of numbers, drawn as a slider across the options in their fixed
 * order with the value's label beside it. Built once per rebuild, like every
 * {@link SettingRow}.
 *
 * <p>The label follows the thumb while it is dragged, but the value is sent only once it is
 * let go: each send re-plans the run, so a drag that sent every step would re-plan dozens
 * of times. The listener is added after the thumb is placed, so building never sends.
 */
class PickSlider extends JPanel
{
	/** @param send receives the setting's key and the picked value */
	PickSlider(FarmingSettingPick pick, BiConsumer<String, String> send)
	{
		super(new BorderLayout(4, 0));
		setOpaque(false);
		List<FarmingSettingPick.Option> options = pick.getOptions();
		int at = options.stream().map(FarmingSettingPick.Option::getValue).collect(Collectors.toList()).indexOf(pick.getSelected());
		JSlider slider = new JSlider(0, options.size() - 1, Math.max(0, at));
		slider.setOpaque(false);
		slider.setFocusable(false);
		slider.setToolTipText(pick.getHint());
		// Sized for the last label, the widest, so the slider keeps its length as the value changes.
		JLabel value = PanelText.label(options.get(options.size() - 1).getLabel(), PanelColors.HEADER);
		value.setPreferredSize(value.getPreferredSize());
		value.setText(options.get(slider.getValue()).getLabel());
		slider.addChangeListener(e ->
		{
			value.setText(options.get(slider.getValue()).getLabel());
			if (!slider.getValueIsAdjusting())
			{
				send.accept(pick.getKey(), options.get(slider.getValue()).getValue());
			}
		});
		add(slider, BorderLayout.CENTER);
		add(value, BorderLayout.EAST);
	}
}
