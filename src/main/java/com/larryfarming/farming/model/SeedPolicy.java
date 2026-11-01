package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import java.util.List;
import lombok.*;

/**
 * How the herb rotation decides which herb seeds go in the ground.
 *
 * <p>The rotation plants two good seeds where diary yield bonuses apply and five or
 * six filler seeds elsewhere, walking inward from both ends of the tier list as each
 * stack runs out and restarting from the outside when the two pointers meet. The
 * tier list is in XP order, not Farming level order: snapdragon sits below irit despite
 * needing eighteen more levels.
 *
 * <p>The Farming Guild herb patch stays out of herb runs so its yield goes to contracts.
 * Torstol tops the list but is a tick like any other herb, unticked by default.
 */
@Value
@Builder
public class SeedPolicy {

    /**
     * A crop that replaces the usual choice at one patch once a diary tier is done. For
     * example, Falador Elite makes the Falador tree patch disease-free, so it takes magic
     * saplings while other patches keep yew.
     */
    @Value
    public static class DiaryOverride {
        String diaryArea;
        String diaryTier;
        PatchRef patch;
        Crop crop;
    }

    /** Best to worst by experience. */
    @Singular("tier")
    List<Crop> herbTierList;

    @Singular("override")
    List<DiaryOverride> diaryOverrides;

    /**
     * How many good seeds a run plants. The rest of the patches take filler.
     *
     * <p>Planning chooses which patches get them per player, because each patch's yield
     * bonus (Kandarin at Catherby, Kourend at Hosidius) depends on a diary the player may
     * not have done.
     */
    int goodSeedsPerRun;
}
