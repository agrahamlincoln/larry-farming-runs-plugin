package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.*;
import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.*;
import com.larryfarming.snapshot.*;
import java.util.*;

/**
 * The farming settings the panel draws under each run, read off the run's structure and the
 * player's preferences, plus the travel switches.
 *
 * <p>A run's controls follow from what defines it, the same test {@link PlantingGate} uses
 * to switch it off: the herb rotation and each tree family take a tick per crop, and a run
 * of one switchable crop takes one checkbox. Calquat has site switches as well, so it opens
 * like the tree runs, onto one square for its crop.
 *
 * <p>Each control carries its {@link FarmingKeys} key, because the snapshot takes it to the
 * panel, which passes a click back to the plugin unread.
 */
public final class RunSettings {

    /** A switched-off run's status in {@code FarmingAdvice.switchedOff}. */
    public static final String STATUS_OFF = "OFF";

    private RunSettings() {
    }

    /**
     * The runs the player has switched off, as rows for the panel to draw dimmed. The panel puts
     * them after the scheduled runs, in priority order among themselves.
     */
    public static List<FarmingRunStatus> switchedOff(FarmingPreferences prefs) {
        List<FarmingRunStatus> off = new ArrayList<>();
        for (Map.Entry<RunId, List<FarmingRun>> entry : FarmingCatalogue.runsById().entrySet()) {
            if (!PlantingGate.silencesWholeRun(entry.getValue().get(0), prefs)) {
                continue;
            }
            RunId id = entry.getKey();
            // Named, so a run whose crop switches are still on does not read as plainly off.
            String detail = prefs.dropsEverySiteOf(FarmingCatalogue.siteKeys(id)) ? "no patches on" : "off";
            off.add(new FarmingRunStatus(id.name(), id.getDisplayName(), STATUS_OFF, detail, 0L, id.getIconItemId(), false, false));
        }
        off.sort(Comparator.comparingInt(s -> RunId.valueOf(s.getRunId()).getPriority()));
        return off;
    }

    /**
     * The settings behind every run that has any.
     *
     * @param sight what the patches were last seen holding, which settles whether some
     *     sites are open, such as Weiss once its patch has been seen
     * @param quiet {@link #quietPicks} for these preferences, this player and this sight
     */
    public static List<FarmingRunSettings> forRuns(FarmingPreferences prefs, PlayerState state, PatchSight sight,
            CompostStock compost, Set<String> quiet) {
        int level = farmingLevel(state);
        List<FarmingRunSettings> all = new ArrayList<>();
        for (Map.Entry<RunId, List<FarmingRun>> entry : FarmingCatalogue.runsById().entrySet()) {
            FarmingRunSettings settings = forRun(entry.getKey(), entry.getValue(), prefs, level, sites(entry.getKey(), prefs, state, sight),
                    FertileSoil.couldCast(state), quiet, compost);
            if (settings != null) {
                all.add(settings);
            }
        }
        return all;
    }

    /** Null for a run the player has no say over, such as the contract. */
    private static FarmingRunSettings forRun(RunId id, List<FarmingRun> variants,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites, boolean castsFertileSoil,
            Set<String> quiet, CompostStock compost) {
        // Jane decides what the contract grows.
        if (id == RunId.CONTRACT) {
            return null;
        }
        FarmingRun run = variants.get(0);
        if (run.getSeedPatchType() != null) {
            return forNamedSeeds(id, run.getSeedPatchType(), prefs, level, sites);
        }
        PatchType rotation = PlantingGate.rotationFamilyOf(run);
        if (rotation == PatchType.HERB) {
            return herbs(id, variants, prefs, level, sites, castsFertileSoil, quiet, compost);
        }
        Set<Crop> fixed = switchableFixedCrops(variants);
        if (fixed.size() == 1) {
            return oneCrop(id, fixed.iterator().next(), prefs, level, sites);
        }
        if (fixed.size() > 1) {
            return severalCrops(id, fixed, prefs, level, sites);
        }
        return null;
    }

    /**
     * A run naming its own seeds: a tick per crop of a tree family, or its one crop.
     */
    private static FarmingRunSettings forNamedSeeds(RunId id, PatchType family,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites) {
        List<Crop> trees = FarmingPreferences.familyChoices(family);
        if (!trees.isEmpty()) {
            List<FarmingSettingChoice> switches = family == PatchType.FRUIT_TREE ? List.of(checkbox(FarmingKeys.PICK_FRUIT_IN_PASSING,
                                    "Pick fruit from trees you pass", prefs.picksFruitInPassing()),
                            checkbox(FarmingKeys.FRUIT_TREES_WAIT_FOR_SAPLINGS, "Wait for a sapling for every patch",
                                    prefs.fruitTreesWaitForSaplings()), checkbox(FarmingKeys.REPLANT_GROWN_TREES, "Replant grown trees",
                                    prefs.replantsGrownTrees()).withTip("Chop down a grown tree and replant it when you have a sapling as"
                                            + " good or better, to earn its health check again.")) : List.of();
            List<FarmingSettingChoice> choices = new ArrayList<>(trees.size());
            // The tree drawn, not its sapling: every sapling shares one pot sprite.
            for (Crop crop : byLevel(trees)) {
                choices.add(toggle(crop, crop.getItemId(), prefs, level, null));
            }
            return seeds(id, choices, sites).choicesCaption("Trees to plant").switches(switches).build();
        }
        List<Crop> crops = CropData.plantableIn(family);
        if (crops.size() == 1 && FarmingPreferences.hasAswitch(crops.get(0))) {
            return oneCrop(id, crops.get(0), prefs, level, sites);
        }
        return null;
    }

    /** The crops in Farming level order, lowest first, as every grid draws them. */
    private static List<Crop> byLevel(List<Crop> crops) {
        List<Crop> sorted = new ArrayList<>(crops);
        sorted.sort(Comparator.comparingInt(c -> CropData.forCrop(c).getFarmingLevel()));
        return sorted;
    }

    /**
     * The herb grid in Farming level order and the crops the run plants in passing.
     */
    private static FarmingRunSettings herbs(RunId id, List<FarmingRun> variants,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites, boolean castsFertileSoil,
            Set<String> quiet, CompostStock compost) {
        // The herb drawn, not its seed: every herb seed is the same pile of seeds. The tags
        // are longer than the bag's one-letter ones, since here they are the only name.
        List<FarmingSettingChoice> seeds = new ArrayList<>();
        for (Crop herb : byLevel(FarmingPreferences.HERB_CHOICES)) {
            seeds.add(toggle(herb, herb.getItemId(), prefs, level, CropData.forCrop(herb).getGridTag()));
        }
        // The inventory's own tags. Only herbs and saplings draw one in the bag, but here each
        // extra stands beside the herbs and wants a name.
        List<FarmingSettingChoice> extras = storedTagToggles(switchableFixedCrops(variants), prefs, level);
        // Each drawn only where it does something: the level wait below the level it waits
        // for, and the spell for a player who could cast it.
        List<FarmingSettingChoice> switches = new ArrayList<>(2);
        if (level < Preset.HERB_RUN_LEVEL) {
            switches.add(checkbox(FarmingKeys.HERB_RUNS_WAIT_FOR_LEVEL, "Wait for " + Preset.HERB_RUN_LEVEL + " Farming",
                    prefs.herbRunsWaitForLevel()));
        }
        if (castsFertileSoil) {
            switches.add(checkbox(FarmingKeys.COMPOST_WITH_FERTILE_SOIL,
                    "Cast Fertile Soil with volcanic ash", prefs.compostsWithFertileSoil()));
        }
        List<FarmingPatchPick> patchPicks = patchPicks(prefs, level, sites, quiet);
        // The thresholds, the stock and the paused line show only while some allotment would
        // plant for compost. The thresholds come last, so the stock sits under them and their
        // coming and going moves no other row.
        boolean composting = patchPicks.stream().anyMatch(FarmingPatchPick::isComposting);
        List<FarmingSettingPick> picks = new ArrayList<>();
        picks.add(favouriteHerb(prefs));
        picks.add(bestSeeds(prefs));
        String stocked = null;
        if (composting) {
            stocked = compost.stockedLine(prefs.makeCompostBelow());
            // Each offers only steps that keep "Stop at" above "Restock below", so the saved
            // pair is the pair in force.
            picks.add(compostSteps("Restock below", FarmingKeys.MAKE_COMPOST_BELOW, FarmingPreferences.COMPOST_STEP,
                    prefs.stopCompostAt() - FarmingPreferences.COMPOST_STEP, prefs.makeCompostBelow(),
                    "Make compost again when your stock drops below this many uses"));
            picks.add(compostSteps("Stop at", FarmingKeys.STOP_COMPOST_AT, prefs.makeCompostBelow() + FarmingPreferences.COMPOST_STEP,
                    FarmingPreferences.COMPOST_MOST, prefs.stopCompostAt(), "Stop making compost once you have this many uses"));
        }
        return seeds(id, seeds, sites).choicesCaption("Herbs to plant").extras(extras).patchPicks(patchPicks)
                .switches(switches).picks(picks).paused(composting ? compost.pausedLine(prefs.makeCompostBelow()) : null)
                .stocked(stocked).stockedTip(stocked == null ? null : String.format(Locale.ENGLISH,
                        "Stop making compost until your stock drops below %,d", prefs.makeCompostBelow())).build();
    }

    /** A threshold dropdown offering each step from {@code least} to {@code most}. */
    private static FarmingSettingPick compostSteps(String label, String key, int least, int most, int selected,
            String hint) {
        List<FarmingSettingPick.Option> steps = new ArrayList<>();
        for (int step = least; step <= most; step += FarmingPreferences.COMPOST_STEP) {
            steps.add(new FarmingSettingPick.Option(String.valueOf(step), String.format(Locale.ENGLISH, "%,d", step), null));
        }
        return new FarmingSettingPick(key, label, FarmingSettingPick.Shape.DROPDOWN, steps, String.valueOf(selected), hint);
    }

    /**
     * Each allotment and flower patch, in the stops table's order, which groups them by farm.
     * Each offers Off, then every crop of its patch in level order. A quiet preset pick shows
     * as picked: it plants once the account can. A farm whose herb patch is switched off or
     * shut to the account, as Harmony is before the elite Morytania diary, shows none: its
     * patches are worked only beside the herb patch.
     */
    private static List<FarmingPatchPick> patchPicks(FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites,
            Set<String> quiet) {
        List<FarmingPatchPick> picks = new ArrayList<>();
        for (Map.Entry<PatchRef, PatchPick> patch : FarmingCatalogue.picksByPatch().entrySet()) {
            PatchPick pick = patch.getValue();
            String herb = FarmingCatalogue.siteKey(patch.getKey());
            if (sites.stream().anyMatch(site -> site.getKey().equals(herb) && (!site.isOn() || site.isLocked()))) {
                continue;
            }
            Crop picked = prefs.picks().get(pick.getSite());
            List<FarmingSettingChoice> options = new ArrayList<>();
            options.add(new FarmingSettingChoice(FarmingKeys.OFF, "Off", 0, "OFF", 0, picked == null, false, false));
            for (Crop crop : byLevel(CropData.plantableIn(patch.getKey().type()))) {
                CropData data = CropData.forCrop(crop);
                options.add(new FarmingSettingChoice(crop.name(), crop.getDisplayName(), crop.getItemId(), data.getStoredTag(),
                        data.getFarmingLevel(), crop == picked, false, data.getFarmingLevel() > level));
            }
            picks.add(new FarmingPatchPick(pick.getFarm(), pick.getTitle(), pick.getKey(), pick.getCompostKey(),
                    prefs.plantsForCompost(pick.getSite()), CompostBins.composts(pick.getSite(), prefs, quiet), options));
        }
        return picks;
    }

    /**
     * A checkbox per site the run may drop, in the registry's fixed order. A site the
     * account cannot reach yet is locked: its own gate is shut, as Harmony is before the
     * elite Morytania diary, or the place itself is, as Weiss is before its fire. A locked
     * site's tip says what opens it, since a padlock alone leaves the player guessing.
     */
    private static List<FarmingSettingChoice> sites(RunId id, FarmingPreferences prefs, PlayerState state, PatchSight sight) {
        List<FarmingSettingChoice> sites = new ArrayList<>();
        for (FarmingCatalogue.SiteSwitch site : FarmingCatalogue.siteSwitches(id)) {
            String why = whyShut(site.getStop(), state, sight);
            sites.add(new FarmingSettingChoice(site.getKey(), site.getLabel(), 0, null, 0,
                    prefs.visits(site.getKey()), false, why != null).withTip(why));
        }
        return sites;
    }

    /**
     * The keys of every site this account certainly cannot work yet, the ones the sites list
     * locks. Empty for an unread state.
     */
    static Set<String> shutSites(PlayerState state, PatchSight sight) {
        Set<String> shut = new HashSet<>();
        for (RunId id : RunId.values()) {
            for (FarmingCatalogue.SiteSwitch site : FarmingCatalogue.siteSwitches(id)) {
                if (whyShut(site.getStop(), state, sight) != null) {
                    shut.add(site.getKey());
                }
            }
        }
        return shut;
    }

    /**
     * What keeps this account from working the stop, as one sentence for a player ("Needs the
     * elite Morytania diary"), or null when it certainly can or it is not yet known. An unread
     * answer leaves it open, so a login still being read locks nothing.
     */
    private static String whyShut(RunStop stop, PlayerState state, PatchSight sight) {
        if (state == null) {
            return null;
        }
        if (RunAvailability.of(stop.getGate(), state) == CapabilityState.LACK) {
            return StopRowWriter.capitalise(RunAvailability.shortfall(stop.getGate(), state));
        }
        Capability access = stop.getAccessGate();
        if (access == null) {
            return null;
        }
        CapabilityVerdict verdict = CapabilitySources.resolve(access, state, sight);
        return verdict.getState() == CapabilityState.LACK ? StopRowWriter.capitalise(verdict.getBecause()) : null;
    }

    /** No favourite, then every herb the grid ticks, in the grid's order. */
    private static FarmingSettingPick favouriteHerb(FarmingPreferences prefs) {
        List<FarmingSettingPick.Option> options = new ArrayList<>();
        options.add(new FarmingSettingPick.Option(FarmingKeys.NO_FAVOURITE, "None", null));
        for (Crop herb : byLevel(FarmingPreferences.HERB_CHOICES)) {
            options.add(new FarmingSettingPick.Option(herb.name(), herb.getDisplayName(), null));
        }
        Crop favourite = prefs.favouriteHerb();
        return new FarmingSettingPick(FarmingKeys.FAVOURITE_HERB, "Favourite herb", FarmingSettingPick.Shape.DROPDOWN, options,
                favourite == null ? FarmingKeys.NO_FAVOURITE : favourite.name(), "Planted before your other herbs");
    }

    private static FarmingSettingPick bestSeeds(FarmingPreferences prefs) {
        return new FarmingSettingPick(FarmingKeys.BEST_SEEDS_EVERYWHERE, "Best seeds in", FarmingSettingPick.Shape.CHIPS, List.of(
                        new FarmingSettingPick.Option("false", "Bonus patches", "The few patches with the biggest yield bonus"),
                        new FarmingSettingPick.Option("true", "All", null)), String.valueOf(prefs.plantsBestSeedsEverywhere()), null);
    }

    /** The longest run after a teleport a route may ask for, a slider stop a second. */
    public static FarmingSettingPick longestRun(FarmingPreferences prefs) {
        List<FarmingSettingPick.Option> steps = new ArrayList<>();
        for (int seconds = FarmingPreferences.RUN_LEAST_SECONDS; seconds <= FarmingPreferences.RUN_MOST_SECONDS; seconds++) {
            steps.add(new FarmingSettingPick.Option(String.valueOf(seconds), seconds + " s", null));
        }
        return new FarmingSettingPick(FarmingKeys.LONGEST_RUN_SECONDS, "Longest run", FarmingSettingPick.Shape.SLIDER, steps,
                String.valueOf(prefs.longestRunSeconds()), "Longest run from a teleport to a patch, running");
    }

    /** Which run leads when several are due: the preset's first run, hardwood, or herbs. */
    public static FarmingSettingPick runFirst(FarmingPreferences prefs) {
        return new FarmingSettingPick(FarmingKeys.HERB_RUN_FIRST, "Herbs and hardwood both ready", FarmingSettingPick.Shape.CHIPS, List.of(
                        new FarmingSettingPick.Option("true", "Herbs first", null),
                        new FarmingSettingPick.Option("false", "Hardwood first", null)), String.valueOf(prefs.herbRunFirst()), null);
    }

    /** The checkbox that holds a run back until every patch on it needs a visit. */
    public static FarmingSettingChoice waitForEveryPatch(FarmingPreferences prefs) {
        return checkbox(FarmingKeys.WAIT_FOR_EVERY_PATCH, "Wait for every patch on a run", prefs.waitsForEveryPatch());
    }

    /**
     * Every switchable crop the runs' fixed-crop stops may grow, in registry order rather
     * than route order so the grid never moves when a route does, each stop's crops lowest
     * level first as the grids draw them.
     */
    private static Set<Crop> switchableFixedCrops(List<FarmingRun> variants) {
        Set<Crop> crops = new LinkedHashSet<>();
        for (FarmingRun run : variants) {
            for (RunStop stop : FarmingCatalogue.stopsInRegistryOrder(run)) {
                if (!stop.isPlanting() || stop.isPicked()) {
                    continue;
                }
                for (Crop crop : byLevel(FarmingCatalogue.cropChoices(stop))) {
                    if (FarmingPreferences.hasAswitch(crop) && !FarmingPreferences.HERB_CHOICES.contains(crop)) {
                        crops.add(crop);
                    }
                }
            }
        }
        return crops;
    }

    /**
     * A run of one switchable crop. With no site switches it takes a checkbox on its row,
     * drawn as its seed. With site switches, as calquat has, it opens like the tree runs: its
     * crop is one square, drawn as the crop itself, above its sites.
     */
    private static FarmingRunSettings oneCrop(RunId id, Crop crop, FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites) {
        if (sites.isEmpty()) {
            return settings(id, FarmingRunSettings.Shape.SWITCH, List.of(), null).runSwitch(toggle(crop, prefs, level, null)).build();
        }
        return seeds(id, List.of(toggle(crop, crop.getItemId(), prefs, level, null)), sites).build();
    }

    /**
     * A run whose patches take any of several crops, the anima plants: a square per crop,
     * drawn as its seed with the inventory's tag, as the tree runs open. Larry plants the first
     * one ticked that the player holds seeds for.
     */
    private static FarmingRunSettings severalCrops(RunId id, Set<Crop> crops,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites) {
        return seeds(id, storedTagToggles(crops, prefs, level), sites).choicesCaption("Plant one of").build();
    }

    /** A tick per crop, drawn as its seed with the inventory's tag. */
    private static List<FarmingSettingChoice> storedTagToggles(Collection<Crop> crops, FarmingPreferences prefs, int level) {
        List<FarmingSettingChoice> toggles = new ArrayList<>(crops.size());
        for (Crop crop : crops) {
            toggles.add(toggle(crop, prefs, level, CropData.forCrop(crop).getStoredTag()));
        }
        return toggles;
    }

    private static FarmingRunSettings.FarmingRunSettingsBuilder seeds(RunId id,
            List<FarmingSettingChoice> choices, List<FarmingSettingChoice> sites) {
        return settings(id, FarmingRunSettings.Shape.SEEDS, choices, sites);
    }

    /** A run's settings with no extras and no switches until the caller sets them. */
    private static FarmingRunSettings.FarmingRunSettingsBuilder settings(RunId id,
            FarmingRunSettings.Shape shape, List<FarmingSettingChoice> choices, List<FarmingSettingChoice> sites) {
        return FarmingRunSettings.builder().runId(id.name()).shape(shape).choices(choices)
                .sites(sites).extras(List.of()).switches(List.of());
    }

    /** One crop's tick, drawn as its seed: a click writes the opposite of what is set. */
    private static FarmingSettingChoice toggle(Crop crop, FarmingPreferences prefs, int level, String tag) {
        return toggle(crop, CropData.forCrop(crop).getSeedItemId(), prefs, level, tag);
    }

    private static FarmingSettingChoice toggle(Crop crop, int itemId, FarmingPreferences prefs, int level, String tag) {
        CropData data = CropData.forCrop(crop);
        boolean on = prefs.allows(crop);
        return new FarmingSettingChoice(FarmingKeys.CROPS.get(crop), crop.getDisplayName(), itemId, tag != null ? tag : data.getTag(),
                data.getFarmingLevel(), on, false, data.getFarmingLevel() > level);
    }

    private static FarmingSettingChoice checkbox(String key, String label, boolean on) {
        return new FarmingSettingChoice(key, label, 0, null, 0, on, false, false);
    }

    /**
     * The picked patches whose crop is the preset's and the account can't plant: above its
     * Farming level, or certainly out of the seed with none of the crop in the patch to
     * pick. They say nothing until the account can plant them. A crop the player picked
     * keeps its reasons.
     */
    static Set<String> quietPicks(FarmingPreferences prefs, PlayerState state, PatchSight sight) {
        int level = farmingLevel(state);
        Set<String> quiet = new HashSet<>();
        for (Map.Entry<PatchRef, PatchPick> picked : FarmingCatalogue.picksByPatch().entrySet()) {
            String site = picked.getValue().getSite();
            Crop crop = prefs.picks().get(site);
            CropData data = crop == null ? null : CropData.forCrop(crop);
            if (data == null || prefs.picksChosen().contains(site)) {
                continue;
            }
            PatchPrediction seen = sight.at(picked.getKey());
            boolean standing = seen != null && seen.getCrop() == crop;
            if (data.getFarmingLevel() > level || !standing && SeedSelector.definitelyOutOf(crop, state, data.getSeedsPerPatch())) {
                quiet.add(site);
            }
        }
        return quiet;
    }

    /**
     * The player's Farming level, or no ceiling at all when the game has not reported it,
     * so an unread level locks nothing.
     */
    static int farmingLevel(PlayerState state) {
        if (state == null || state.getSkills() == null || !state.getSkills().containsKey(Skill.FARMING)) {
            return Integer.MAX_VALUE;
        }
        return state.getSkillLevel(Skill.FARMING);
    }

    // --- Travel --------------------------------------------------------------

    /**
     * The travel squares under their captions, each group in {@link TravelMethod}'s order,
     * which never moves. A click writes the opposite of what is set. A square for a method
     * the player has nothing for is marked missing; a house method greys only when the house
     * holds none of it, seen or declared.
     */
    public static List<FarmingSettingGroup> travelGroups(FarmingPreferences prefs, PlayerState state, HouseSight house) {
        List<FarmingSettingGroup> groups = new ArrayList<>();
        for (TravelMethod.Group group : TravelMethod.Group.values()) {
            List<FarmingSettingChoice> inGroup = new ArrayList<>();
            for (TravelMethod method : TravelMethod.values()) {
                if (method.getGroup() == group) {
                    inGroup.add(travelSquare(method, prefs, state, house));
                }
            }
            if (!inGroup.isEmpty()) {
                groups.add(new FarmingSettingGroup(group.getLabel(), inGroup));
            }
        }
        return groups;
    }

    private static FarmingSettingChoice travelSquare(TravelMethod method, FarmingPreferences prefs, PlayerState state, HouseSight house) {
        int sprite = method == TravelMethod.SPELLBOOK_SWAP ? Teleports.SPELLBOOK_SWAP.getSpriteId()
                : method.getCapabilities().isEmpty() ? 0 : Teleports.iconFor(method.getCapabilities().get(0));
        boolean missing = !owns(method, state, house, prefs.houseDeclared());
        return new FarmingSettingChoice(method.getKey(), method.getLabel(),
                sprite > 0 ? 0 : itemFor(method), method.getTag(), 0, prefs.uses(method), missing, false, sprite,
                missing ? missingNote(method, state, house, prefs.houseDeclared()) : null, false, null);
    }

    /**
     * Why a missing square is missing, where "None seen yet" would mislead: furniture the house
     * was seen without, or a diary reward whose tier is not earned. A player holding Explorer's
     * ring 1 owns the ring, but no route can use it.
     */
    private static String missingNote(TravelMethod method, PlayerState state, HouseSight house, Set<Capability> houseDeclared) {
        if (isSeenAbsent(method, house)) {
            return method.getCapabilities().size() == 1 ? "Not built" : "None built";
        }
        Capability first = method.getCapabilities().isEmpty() ? null : method.getCapabilities().get(0);
        if (first == null || DiaryRewards.earnedBy(first) == null) {
            return null;
        }
        return StopRowWriter.capitalise(CapabilitySources.resolve(first, state, PatchSight.blind(), house, houseDeclared).getBecause());
    }

    /**
     * Whether a method is furniture the house was seen without, every piece of it, so its
     * square can say so rather than "None seen yet".
     */
    private static boolean isSeenAbsent(TravelMethod method, HouseSight house) {
        return !method.getCapabilities().isEmpty() && method.getCapabilities().stream()
                .allMatch(capability -> CapabilitySources.houseSighting(capability, house) == HouseSighting.ABSENT);
    }

    /** The item a method's square draws: its icon item, otherwise its first capability's. */
    private static int itemFor(TravelMethod method) {
        int icon = method.getIconItemId();
        return icon > 0 ? icon : CarryItems.iconFor(method.getCapabilities().get(0));
    }

    /**
     * Whether the player has anything this method uses: one of its items, the one tablet it
     * switches, or Spellbook Swap's level and quest. An unread
     * answer counts as owned, so an unopened bank never greys a square. A house item counts
     * as owned while declared and not seen otherwise.
     */
    private static boolean owns(TravelMethod method, PlayerState state, HouseSight house, Set<Capability> houseDeclared) {
        if (state == null) {
            return true;
        }
        if (method == TravelMethod.SPELLBOOK_SWAP) {
            return canSwap(state);
        }
        // A place's tablet square draws the one tablet it switches.
        if (method.getGroup() == TravelMethod.Group.TABLETS && method.getIconItemId() > 0) {
            return state.getBankLastSeen() == null || ItemCounts.ownsAny(state, method.getIconItemId());
        }
        return method.getCapabilities().stream().anyMatch(capability ->
                CapabilitySources.resolve(capability, state, PatchSight.blind(), house, houseDeclared).getState() != CapabilityState.LACK);
    }

    /**
     * Whether Spellbook Swap's Magic level and quest are met. A quest not yet read leaves it
     * open, as an unread bank does an item.
     */
    private static boolean canSwap(PlayerState state) {
        TeleportMeans swap = Teleports.SPELLBOOK_SWAP;
        if (state.getSkills() != null && state.getSkills().containsKey(Skill.MAGIC)
                && state.getSkillLevel(Skill.MAGIC) < swap.getMagicLevel()) {
            return false;
        }
        QuestStatus quest = state.getQuests().get(swap.getQuestName());
        return quest == null || quest == QuestStatus.COMPLETED;
    }

    // --- The house -----------------------------------------------------------

    /**
     * Every house item as a row under its group, in {@link HouseItem}'s order, and how much
     * of the house Larry has seen. An item Larry has seen is settled by the sighting and
     * takes no click; an item a level or quest rules out is locked, seen or not, unless seen
     * built. Every other item is the player's declaration.
     */
    public static FarmingHouse house(FarmingPreferences prefs, PlayerState state, HouseSight house) {
        Map<HouseItem.Group, List<FarmingSettingChoice>> rows = new EnumMap<>(HouseItem.Group.class);
        boolean nexusUnseen = false;
        for (HouseItem item : HouseItem.all()) {
            Capability capability = item.getCapability();
            HouseSighting sighting = CapabilitySources.houseSighting(capability, house);
            boolean built = sighting == HouseSighting.BUILT;
            boolean outOfReach = !built && CapabilitySources.isHouseItemOutOfReach(capability, state);
            int level = outOfReach ? CapabilitySources.houseItemLevelShort(capability, state) : 0;
            boolean locked = outOfReach || sighting == HouseSighting.ABSENT;
            if (item.getGroup() == HouseItem.Group.NEXUS && sighting == HouseSighting.UNSEEN) {
                nexusUnseen = true;
            }
            rows.computeIfAbsent(item.getGroup(), g -> new ArrayList<>())
                    .add(new FarmingSettingChoice(item.getKey(), item.getLabel(), 0, null, level,
                            built || prefs.declares(item), false, locked, 0, null, sighting != HouseSighting.UNSEEN, null));
        }
        List<FarmingSettingGroup> groups = new ArrayList<>(rows.size());
        for (Map.Entry<HouseItem.Group, List<FarmingSettingChoice>> group : rows.entrySet()) {
            groups.add(new FarmingSettingGroup(group.getKey().getLabel(), List.copyOf(group.getValue())));
        }
        // A scan by a lower scanner asks for a visit too: it never looked for every feature.
        FarmingHouse.Sight sight = house == null || house.getScanner() < HouseFeature.SCANNER ? FarmingHouse.Sight.NOT_SEEN
                : nexusUnseen ? FarmingHouse.Sight.NEXUS_NOT_SEEN : FarmingHouse.Sight.SEEN;
        return new FarmingHouse(sight, List.copyOf(groups));
    }

}
