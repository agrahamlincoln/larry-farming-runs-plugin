package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.model.*;

/**
 * Which stops the player has switched off, and which runs that empties out entirely.
 *
 * <p>Switching a crop off silences it rather than skipping it with a reason: the stop
 * leaves the walkthrough, the run leaves the list, and the patch leaves the timers. Dropping
 * a site from a run does the same.
 *
 * <p>The scheduler, the advice builder and the patch timers all read the rule here, so a
 * run, its walkthrough and the timers always agree about which patches exist.
 */
public final class PlantingGate {

    private PlantingGate() {
    }

    /**
     * Whether this stop should vanish.
     *
     * <p>Contracts are always exempt: crop preferences cover the player's own runs, and
     * silencing Guildmaster Jane's request would leave a contract that cannot be completed.
     */
    static boolean silences(FarmingRun run, RunStop stop, FarmingPreferences prefs) {
        if (run.getId() == RunId.CONTRACT) {
            return false;
        }
        // A run switched off takes every stop with it, woven-in extras included.
        if (silencesWholeRun(run, prefs)) {
            return true;
        }
        if (dropsSite(stop, prefs)) {
            return true;
        }
        return stop.isFixedCrop() && FixedCrops.ticked(stop, prefs).isEmpty();
    }

    /** Whether the player has dropped this stop's site from their runs. */
    private static boolean dropsSite(RunStop stop, FarmingPreferences prefs) {
        String site = FarmingCatalogue.siteKey(stop);
        return site != null && !prefs.visits(site);
    }

    /**
     * Whether the player has dropped every site of this run's family they can reach, which
     * leaves the run nothing to go for. A site the account is seen unable to reach counts as
     * dropped, so Harmony before the elite Morytania diary cannot keep a run open alone. Asked
     * of the family, not the variant, so every spellbook's run agrees. False for a family
     * with no site switches.
     */
    private static boolean dropsEverySite(FarmingRun run, FarmingPreferences prefs) {
        return prefs.dropsEverySiteOf(FarmingCatalogue.siteKeys(run.getId()));
    }

    /**
     * Whether the player has switched this whole run off.
     *
     * <p>A run is defined by the crop it exists to grow, so switching that crop off ends
     * the run. The herb run's woven-in extras (snape grass, potato cactus and the like) are
     * secondaries of that run and go with it.
     */
    static boolean silencesWholeRun(FarmingRun run, FarmingPreferences prefs) {
        if (run.getId() == RunId.CONTRACT) {
            return false;
        }
        // Every site dropped: the woven-in extras are no reason to go on their own.
        if (dropsEverySite(run, prefs)) {
            return true;
        }
        // A run naming its own seeds is defined by them — every tree of the family unticked,
        // or the single crop of a one-crop run switched off.
        if (run.getSeedPatchType() != null) {
            return SeedSelector.plantingLadder(prefs, run.getSeedPatchType()).isEmpty();
        }
        PatchType rotation = rotationFamilyOf(run);
        if (rotation != null) {
            return prefs.isFamilyOff(rotation);
        }
        // Left: runs of fixed-crop stops (hespori, seaweed), off once all of them are.
        boolean plants = false;
        for (RunStop stop : run.getStops()) {
            if (!stop.isPlanting() || !stop.isFixedCrop()) {
                continue;
            }
            plants = true;
            if (!FixedCrops.ticked(stop, prefs).isEmpty()) {
                return false;
            }
        }
        return plants;
    }

    /**
     * The patch family this run's seed rotation fills, or null when it has no rotation.
     *
     * <p>Read off the first planting stop with no fixed crop, rather than the run id. Only
     * called for runs that name no seeds of their own, so such a stop draws on the
     * rotation. Woven-in extras name their crop and are skipped.
     */
    static PatchType rotationFamilyOf(FarmingRun run) {
        for (RunStop stop : run.getStops()) {
            if (stop.isPlanting() && !stop.isFixedCrop()) {
                return stop.patchType();
            }
        }
        return null;
    }
}
