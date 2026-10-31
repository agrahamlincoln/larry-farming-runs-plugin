package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Value;

/**
 * One setting in the farming tab that takes one of several values: a row of chips, or a
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
        @SerializedName("value") String value;

        /** Named for a player: "Bonus patches", "Ranarr". */
        @SerializedName("label") String label;

        /** A tooltip for a chip whose effect its label can't make obvious, or null. */
        @SerializedName("hint") String hint;
    }

    /** The {@code LarryConfig} key a pick writes, such as {@code herbRunFirst}. */
    @SerializedName("key") String key;

    /** The label drawn beside the control: "Run first". */
    @SerializedName("label") String label;

    @SerializedName("shape") Shape shape;

    /** Every option, in the fixed order drawn. */
    @SerializedName("options") List<Option> options;

    /** The value in force, one of the options' values. */
    @SerializedName("selected") String selected;

    /** A tooltip for the dropdown, or null. Chips carry their own. */
    @SerializedName("hint") String hint;
}
