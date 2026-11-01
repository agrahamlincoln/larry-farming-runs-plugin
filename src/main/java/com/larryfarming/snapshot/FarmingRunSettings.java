package com.larryfarming.snapshot;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The settings behind one run, as the side panel draws them under the run's row.
 *
 * <p>Derived from the run's own structure: the family its rotation fills, the seeds it
 * names, the crops its stops grow. A new run gets controls without the panel learning its
 * name.
 */
@Value
@Builder
public class FarmingRunSettings {

    /** How the run's settings are drawn. */
    public enum Shape {
        /** Herbs, tree families and calquat: one tick per crop, in a grid. */
        SEEDS,
        /** A run that one setting switches on or off: a checkbox on the row itself. */
        SWITCH
    }

    /** The run these belong to, as {@code RunId.name()}. */
    String runId;

    Shape shape;

    /** The crops to tick, in the order drawn. Empty for {@link Shape#SWITCH}. */
    List<FarmingSettingChoice> choices;

    /** The caption over a grid of several crops, "Herbs to plant"; null over one crop. */
    String choicesCaption;

    /**
     * The sites the run visits, each a checkbox, in the registry's fixed order. A site the
     * account cannot reach yet is locked. Null or empty for a run with no site switches.
     */
    List<FarmingSettingChoice> sites;

    /** Crops the run plants in passing, each with its own tick. */
    List<FarmingSettingChoice> extras;

    /** Checkboxes under the grid, such as the level wait or picking fruit. */
    List<FarmingSettingChoice> switches;

    /**
     * Settings of several values at the bottom of the block, each beside its label: the
     * herb run's favourite herb and where its best seeds go. Null or empty for most runs.
     */
    List<FarmingSettingPick> picks;

    /** The row's checkbox, for {@link Shape#SWITCH}; null otherwise. */
    FarmingSettingChoice runSwitch;
}
