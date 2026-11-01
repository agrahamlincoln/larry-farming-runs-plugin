package com.larryfarming.snapshot;

import java.util.List;
import lombok.Value;

/**
 * One setting in the side panel that takes one of several values: a row of chips, or a
 * dropdown when the values are too many for one row.
 *
 * <p>Carries the config key and each option's value, so the panel never decides what a
 * pick means: picking an option writes its value to the key.
 */
@Value
public class FarmingSettingPick {

    /** How the options are drawn. */
    public enum Shape {
        /** Every option on one row, the picked one outlined. */
        CHIPS,
        /** A dropdown, for a list too long for one row. */
        DROPDOWN
    }

    /** One value the setting can take. */
    @Value
    public static class Option {
        /** What a pick writes to the key. */
        String value;

        /** Named for a player: "Bonus patches", "Ranarr". */
        String label;

        /** A tooltip for a chip whose effect its label can't make obvious, or null. */
        String hint;
    }

    /** The {@code FarmingKeys} key a pick writes, such as {@code herbRunFirst}. */
    String key;

    /** The label drawn beside the control: "Favourite herb". */
    String label;

    Shape shape;

    /** Every option, in the fixed order drawn. */
    List<Option> options;

    /** The value in force, one of the options' values. */
    String selected;

    /** A tooltip for the dropdown, or null. Chips carry their own. */
    String hint;
}
