package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.FarmingRun;
import com.larryfarming.farming.model.RunId;
import com.larryfarming.farming.model.RunStop;
import java.util.List;

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
     * Whether the player has switched this patch off, so its timer is hidden: its whole
     * family is unticked, every crop the stop may grow is, or its site is dropped from the
     * runs.
     *
     * <p>A Farming Guild patch is never hidden: a contract can land in it, and a contract
     * answers to Guildmaster Jane rather than to these settings.
     */
    public static boolean hidesPatch(FarmingWorldMap.PatchDef patch, FarmingPreferences prefs) {
        if (FarmingCatalogue.isContractPatch(patch)) {
            return false;
        }
        if (prefs.isFamilyOff(patch.getType())) {
            return true;
        }
        String site = FarmingCatalogue.siteKey(patch);
        if (site != null && !prefs.visits(site)) {
            return true;
        }
        // An allotment feeding a bin keeps its timer while that bin is worked, whatever the
        // crop switches say: Catherby north feeds its bin once its snape grass is off.
        String binSite = FarmingCatalogue.binSiteFedBy(patch);
        if (binSite != null && prefs.worksCompostBins() && prefs.visits(binSite)) {
            return false;
        }
        List<Crop> named = FarmingCatalogue.namedCropPatches().get(patch);
        if (named == null) {
            return false;
        }
        for (Crop crop : named) {
            if (prefs.allows(crop)) {
                return false;
            }
        }
        return true;
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
        // A bin has its own switch, since it grows nothing. Every bin belongs to the
        // ultracompost run, which the whole-run check above already covers; this line guards
        // a bin added to a run that grows something.
        if (worksNoBins(stop, prefs)) {
            return true;
        }
        if (stop.feedsAbin() && grownForItsOwnCrop(stop, prefs)) {
            return true;
        }
        return stop.isFixedCrop() && FixedCrops.ticked(stop, prefs).isEmpty();
    }

    /**
     * Whether another stop grows a switched-on crop in this feeding allotment's patch, so
     * the allotment is not the bin's. Catherby north is the snape grass stop's while snape
     * grass, or the watermelon ticked in its place, is on.
     */
    static boolean grownForItsOwnCrop(RunStop stop, FarmingPreferences prefs) {
        FarmingWorldMap.PatchDef def = stop.primaryPatch().resolve();
        List<Crop> named = def == null ? null : FarmingCatalogue.namedCropPatches().get(def);
        if (named == null) {
            return false;
        }
        for (Crop crop : named) {
            if (prefs.allows(crop)) {
                return true;
            }
        }
        return false;
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

    private static boolean worksNoBins(RunStop stop, FarmingPreferences prefs) {
        return !prefs.worksCompostBins() && worksAbin(stop);
    }

    /**
     * Whether this stop is a compost bin, judged by its declared actions so a new bin needs
     * no listing here, or an allotment that fills one.
     */
    static boolean worksAbin(RunStop stop) {
        if (stop.feedsAbin()) {
            return true;
        }
        return stop.getActions() != null
                && (stop.getActions().contains(RunStop.StopAction.FILL_COMPOST_BIN)
                        || stop.getActions().contains(RunStop.StopAction.EMPTY_COMPOST_BIN));
    }

    /**
     * Whether the player has switched this whole run off.
     *
     * <p>A run is defined by the crop it exists to grow, so switching that crop off ends
     * the run. The herb run's woven-in extras (snape grass, potato cactus and the like) are
     * secondaries of that run and go with it.
     *
     * <p>The compost bins run has no defining crop and must not vanish for failing a test
     * about seeds. Its own switch governs it instead.
     */
    static boolean silencesWholeRun(FarmingRun run, FarmingPreferences prefs) {
        if (run.getId() == RunId.CONTRACT) {
            return false;
        }
        // A run of nothing but bins ends when bins are switched off. Derived from the stops
        // rather than the run id.
        boolean binsOnly = everyStopWorksAbin(run);
        if (!prefs.worksCompostBins() && binsOnly) {
            return true;
        }
        // Every site dropped: the woven-in extras are no reason to go on their own.
        if (dropsEverySite(run, prefs)) {
            return true;
        }
        // Its allotments grow the bins' fill, not a crop the run exists for.
        if (binsOnly) {
            return false;
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

    /**
     * Whether a run has stops and every one is a compost bin or an allotment that fills one.
     */
    static boolean everyStopWorksAbin(FarmingRun run) {
        if (run.getStops().isEmpty()) {
            return false;
        }
        for (RunStop stop : run.getStops()) {
            if (!worksAbin(stop)) {
                return false;
            }
        }
        return true;
    }
}
