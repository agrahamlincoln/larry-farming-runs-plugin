package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.detection.DiaryTiers;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import java.util.*;

/**
 * The seed policy's diary overrides in force: one patch that takes a crop every other patch
 * of its family gives up once a diary is done.
 *
 * <p>The elite Falador diary makes the Falador Park tree patch disease-free, so magic
 * saplings go there and nowhere else. Before the diary a ticked magic is an ordinary rung
 * of the tree run's ladder.
 *
 * <p>An override never plants an unticked crop, and an unknown diary reads as not done, as
 * in {@link PatchPriority}.
 */
final class DiaryPlantings {

    private DiaryPlantings() {
    }

    /**
     * The crop this patch takes ahead of its run's ladder, or null.
     *
     * <p>Only while the player holds one, so a player out of magic saplings plants the
     * ladder's next crop at Falador rather than leaving it bare.
     */
    static Crop claimedBy(PatchRef patch, PlayerState state, FarmingPreferences prefs) {
        for (SeedPolicy.DiaryOverride override : inForce(state, prefs)) {
            if (override.getPatch().equals(patch) && SeedSelector.holdsAny(override.getCrop(), state)) {
                return override.getCrop();
            }
        }
        return null;
    }

    /** The crops kept for an override's patch, which every other patch goes without. */
    static Set<Crop> withheld(PlayerState state, FarmingPreferences prefs) {
        Set<Crop> withheld = EnumSet.noneOf(Crop.class);
        for (SeedPolicy.DiaryOverride override : inForce(state, prefs)) {
            withheld.add(override.getCrop());
        }
        return withheld;
    }

    /**
     * What this stop could be filled from: the run's ladder less the withheld crops, with
     * the stop's own claimed crop put back.
     */
    static List<Crop> ladderAt(List<Crop> ladder, RunStop stop, PlayerState state, FarmingPreferences prefs) {
        List<SeedPolicy.DiaryOverride> overrides = inForce(state, prefs);
        if (overrides.isEmpty()) {
            return ladder;
        }
        List<Crop> crops = new ArrayList<>(ladder.size());
        for (Crop crop : ladder) {
            if (!withheldFrom(stop, crop, overrides)) {
                crops.add(crop);
            }
        }
        return crops;
    }

    private static boolean withheldFrom(RunStop stop, Crop crop, List<SeedPolicy.DiaryOverride> overrides) {
        boolean withheld = false;
        for (SeedPolicy.DiaryOverride override : overrides) {
            if (override.getCrop() != crop) {
                continue;
            }
            if (stop.getPatches().contains(override.getPatch())) {
                return false;
            }
            withheld = true;
        }
        return withheld;
    }

    private static List<SeedPolicy.DiaryOverride> inForce(PlayerState state, FarmingPreferences prefs) {
        List<SeedPolicy.DiaryOverride> inForce = new ArrayList<>();
        for (SeedPolicy.DiaryOverride override : FarmingCatalogue.seedPolicy().getDiaryOverrides()) {
            if (prefs.allows(override.getCrop()) && DiaryTiers.completed(override.getDiaryArea(), override.getDiaryTier(),
                            state) == CapabilityState.HAVE) {
                inForce.add(override);
            }
        }
        return inForce;
    }
}
