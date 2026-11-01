package com.larryfarming.snapshot;

import lombok.Value;

/**
 * A patch at an arrival that is not being worked, and why.
 *
 * <p>An arrival is a place, not a patch: Catherby's herb, flower and allotment patches are
 * one visit. When the flower patch is left alone, this note keeps it visible in the row.
 *
 * <p>Complements {@link FarmingPlantAdvice}: between them the two lists account for every
 * patch at the stop.
 */
@Value
public class FarmingPatchNote {

    /** Which patch, without the place the row already names: "Flower", "North allotment". */
    String patchName;

    /** What kind of skip this is, for the short label and the colour. */
    FarmingSkipReason reason;

    /** The full clause, for the hover. */
    String because;

    /** When whatever is growing here finishes, or 0 when nothing is. */
    long readyAtEpochSec;
}
