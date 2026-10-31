package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.catalogue.RunIcons;
import com.larryfarming.farming.catalogue.Teleports;
import com.larryfarming.farming.catalogue.TravelIcons;
import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.detection.CapabilitySources;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.detection.HouseSight;
import com.larryfarming.farming.detection.HouseSighting;
import com.larryfarming.farming.detection.PatchSight;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.FarmingKeys;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.FarmingRun;
import com.larryfarming.farming.model.HouseItem;
import com.larryfarming.farming.model.Preset;
import com.larryfarming.farming.model.RunId;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.TeleportMeans;
import com.larryfarming.farming.model.TravelMethod;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import com.larryfarming.model.Skill;
import com.larryfarming.snapshot.FarmingHouse;
import com.larryfarming.snapshot.FarmingRunSettings;
import com.larryfarming.snapshot.FarmingRunStatus;
import com.larryfarming.snapshot.FarmingSettingChoice;
import com.larryfarming.snapshot.FarmingSettingGroup;
import com.larryfarming.snapshot.FarmingSettingPick;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The farming settings the tab draws under each run, read off the run's structure and the
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

    /** Each herb's name on its square in the herb grid, short enough for an item square. */
    private static final Map<Crop, String> HERB_TAGS = Map.ofEntries(
            Map.entry(Crop.GUAM, "GUAM"), Map.entry(Crop.MARRENTILL, "MAR"),
            Map.entry(Crop.TARROMIN, "TAR"), Map.entry(Crop.HARRALANDER, "HAR"),
            Map.entry(Crop.RANARR, "RAN"), Map.entry(Crop.TOADFLAX, "TOAD"),
            Map.entry(Crop.IRIT, "IRIT"), Map.entry(Crop.AVANTOE, "AVA"),
            Map.entry(Crop.KWUARM, "KWU"), Map.entry(Crop.SNAPDRAGON, "SNAP"),
            Map.entry(Crop.CADANTINE, "CAD"), Map.entry(Crop.LANTADYME, "LANT"),
            Map.entry(Crop.DWARF_WEED, "DWF"), Map.entry(Crop.TORSTOL, "TORS"));

    /** The label on the compost bins' switch, whose square shows a bucket of ultracompost. */
    private static final String COMPOST_BINS_LABEL = "Fill compost bins";

    private RunSettings() {
    }

    /**
     * The runs the player has switched off, as rows for the tab to draw dimmed. The tab puts
     * them after the scheduled runs, in priority order among themselves.
     */
    public static List<FarmingRunStatus> switchedOff(FarmingPreferences prefs) {
        List<FarmingRunStatus> off = new ArrayList<>();
        for (Map.Entry<RunId, List<FarmingRun>> entry
                : FarmingCatalogue.runsById().entrySet()) {
            if (!PlantingGate.silencesWholeRun(entry.getValue().get(0), prefs)) {
                continue;
            }
            RunId id = entry.getKey();
            // Named, so a run whose crop switches are still on does not read as plainly off.
            String detail = prefs.dropsEverySiteOf(FarmingCatalogue.siteKeys(id))
                    ? "no patches on" : "off";
            off.add(new FarmingRunStatus(id.name(), id.getDisplayName(), STATUS_OFF, detail,
                    0L, 0, RunIcons.of(id), false, false, false));
        }
        off.sort(Comparator.comparingInt(s -> RunId.valueOf(s.getRunId()).getPriority()));
        return off;
    }

    /** The settings behind every run that has any, blind to what grows in the patches. */
    public static List<FarmingRunSettings> forRuns(FarmingPreferences prefs, PlayerState state) {
        return forRuns(prefs, state, PatchSight.blind());
    }

    /**
     * The settings behind every run that has any.
     *
     * @param sight what the patches were last seen holding, which settles whether some
     *     sites are open, such as Weiss once its patch has been seen
     */
    public static List<FarmingRunSettings> forRuns(FarmingPreferences prefs, PlayerState state,
            PatchSight sight) {
        int level = farmingLevel(state);
        List<FarmingRunSettings> all = new ArrayList<>();
        for (Map.Entry<RunId, List<FarmingRun>> entry
                : FarmingCatalogue.runsById().entrySet()) {
            FarmingRunSettings settings = forRun(entry.getKey(), entry.getValue(), prefs,
                    level, sites(entry.getKey(), prefs, state, sight),
                    FertileSoil.couldCast(state));
            if (settings != null) {
                all.add(settings);
            }
        }
        return all;
    }

    /** Null for a run the player has no say over, such as the contract. */
    private static FarmingRunSettings forRun(RunId id, List<FarmingRun> variants,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites,
            boolean castsFertileSoil) {
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
            return herbs(id, variants, prefs, level, sites);
        }
        Set<Crop> fixed = switchableFixedCrops(variants);
        if (fixed.size() == 1) {
            Crop only = fixed.iterator().next();
            return oneCrop(id, only, prefs, level, sites);
        }
        if (fixed.size() > 1) {
            return severalCrops(id, fixed, prefs, level, sites);
        }
        if (fixed.isEmpty() && PlantingGate.everyStopWorksAbin(run)) {
            return compostBins(id, prefs, sites, castsFertileSoil);
        }
        return null;
    }

    /**
     * The compost bins open like calquat: one square, drawn as the ultracompost the bins
     * make, switches the run, and the bins sit below it, each one a site to drop.
     */
    private static FarmingRunSettings compostBins(RunId id, FarmingPreferences prefs,
            List<FarmingSettingChoice> sites, boolean castsFertileSoil) {
        if (sites.isEmpty()) {
            return runSwitch(id, checkbox(FarmingKeys.WORK_COMPOST_BINS, COMPOST_BINS_LABEL,
                    prefs.worksCompostBins()));
        }
        FarmingSettingChoice bins = new FarmingSettingChoice(FarmingKeys.WORK_COMPOST_BINS,
                COMPOST_BINS_LABEL, Crop.ULTRACOMPOST.getItemId(), null, 0,
                prefs.worksCompostBins(), false, false);
        return FarmingRunSettings.builder()
                .runId(id.name())
                .shape(FarmingRunSettings.Shape.SEEDS)
                .choices(List.of(bins))
                .sites(sites)
                .extras(List.of())
                // Drawn only for a player who could cast the spell; for anyone else it does
                // nothing.
                .switches(castsFertileSoil
                        ? List.of(checkbox(FarmingKeys.COMPOST_WITH_FERTILE_SOIL,
                                "Cast Fertile Soil with volcanic ash",
                                prefs.compostsWithFertileSoil()))
                        : List.of())
                .picks(List.of(makeCompostBelow(prefs), stopCompostAt(prefs)))
                .build();
    }

    /**
     * The stock below which the bins are worked again: every step that leaves one above it
     * for {@link #stopCompostAt}.
     */
    static FarmingSettingPick makeCompostBelow(FarmingPreferences prefs) {
        return new FarmingSettingPick(FarmingKeys.MAKE_COMPOST_BELOW, "Make more below",
                FarmingSettingPick.Shape.DROPDOWN,
                compostSteps(FarmingPreferences.COMPOST_STEP,
                        FarmingPreferences.COMPOST_MOST - FarmingPreferences.COMPOST_STEP),
                String.valueOf(prefs.makeCompostBelow()),
                "Fill the bins again when your ultracompost drops below this");
    }

    /** The stock at which the bins are left: only steps above the low threshold. */
    static FarmingSettingPick stopCompostAt(FarmingPreferences prefs) {
        return new FarmingSettingPick(FarmingKeys.STOP_COMPOST_AT, "Stop at",
                FarmingSettingPick.Shape.DROPDOWN,
                compostSteps(prefs.makeCompostBelow() + FarmingPreferences.COMPOST_STEP,
                        FarmingPreferences.COMPOST_MOST),
                String.valueOf(prefs.stopCompostAt()),
                "Leave the bins once you have this much ultracompost");
    }

    /** Every step from {@code least} to {@code most}, labelled as the game writes numbers. */
    private static List<FarmingSettingPick.Option> compostSteps(int least, int most) {
        List<FarmingSettingPick.Option> steps = new ArrayList<>();
        for (int step = least; step <= most; step += FarmingPreferences.COMPOST_STEP) {
            steps.add(new FarmingSettingPick.Option(String.valueOf(step),
                    String.format(Locale.ENGLISH, "%,d", step), null));
        }
        return steps;
    }

    /**
     * A run naming its own seeds: a tick per crop of a tree family, or its one crop.
     */
    private static FarmingRunSettings forNamedSeeds(RunId id, PatchType family,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites) {
        List<Crop> trees = FarmingPreferences.familyChoices(family);
        if (!trees.isEmpty()) {
            List<FarmingSettingChoice> switches = family == PatchType.FRUIT_TREE
                    ? List.of(checkbox(FarmingKeys.PICK_FRUIT_IN_PASSING,
                                    "Pick fruit from trees you pass",
                                    prefs.picksFruitInPassing()),
                            checkbox(FarmingKeys.FRUIT_TREES_WAIT_FOR_SAPLINGS,
                                    "Wait for a sapling for every patch",
                                    prefs.fruitTreesWaitForSaplings()),
                            checkbox(FarmingKeys.REPLANT_GROWN_TREES,
                                    "Replant grown trees",
                                    prefs.replantsGrownTrees())
                                    .withTip("Chop grown trees for a sapling as good or better,"
                                            + " for the health check experience"))
                    : List.of();
            List<FarmingSettingChoice> choices = new ArrayList<>(trees.size());
            // The tree drawn, not its sapling: every sapling shares one pot sprite.
            for (Crop crop : byLevel(trees)) {
                choices.add(toggle(crop, crop.getItemId(), prefs, level, null));
            }
            return FarmingRunSettings.builder()
                    .runId(id.name())
                    .shape(FarmingRunSettings.Shape.SEEDS)
                    .choices(choices)
                    .choicesCaption("Trees to plant")
                    .sites(sites)
                    .extras(List.of())
                    .switches(switches)
                    .build();
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
     * The herb grid in Farming level order and the crops the run plants in passing. The
     * compost bins have their own run row, which is the one switch for them: no herb stop
     * works a bin.
     */
    private static FarmingRunSettings herbs(RunId id, List<FarmingRun> variants,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites) {
        // The herb drawn, not its seed: every herb seed is the same pile of seeds. The tags
        // are longer than the bag's one-letter ones, since here they are the only name.
        List<FarmingSettingChoice> seeds = new ArrayList<>();
        for (Crop herb : byLevel(FarmingPreferences.HERB_CHOICES)) {
            seeds.add(toggle(herb, herb.getItemId(), prefs, level, HERB_TAGS.get(herb)));
        }
        // The inventory's own tags. Only herbs and saplings draw one in the bag, but here each
        // extra stands beside the herbs and wants a name.
        List<FarmingSettingChoice> extras = new ArrayList<>();
        for (Crop crop : switchableFixedCrops(variants)) {
            extras.add(toggle(crop, prefs, level, CropData.forCrop(crop).getStoredTag()));
        }
        return FarmingRunSettings.builder()
                .runId(id.name())
                .shape(FarmingRunSettings.Shape.SEEDS)
                .choices(seeds)
                .choicesCaption("Herbs to plant")
                .sites(sites)
                .extras(extras)
                // Drawn only below the level it waits for: at or above it, the switch does
                // nothing.
                .switches(level >= Preset.HERB_RUN_LEVEL ? List.of()
                        : List.of(checkbox(FarmingKeys.HERB_RUNS_WAIT_FOR_LEVEL,
                                "Wait for " + Preset.HERB_RUN_LEVEL + " Farming",
                                prefs.herbRunsWaitForLevel())))
                .picks(List.of(favouriteHerb(prefs), bestSeeds(prefs)))
                .build();
    }

    /**
     * A checkbox per site the run may drop, in the registry's fixed order. A site the
     * account cannot reach yet is locked: its own gate is shut, as Harmony is before the
     * elite Morytania diary, or the place itself is, as Weiss is before its fire.
     */
    private static List<FarmingSettingChoice> sites(RunId id, FarmingPreferences prefs,
            PlayerState state, PatchSight sight) {
        List<FarmingSettingChoice> sites = new ArrayList<>();
        for (FarmingCatalogue.SiteSwitch site : FarmingCatalogue.siteSwitches(id)) {
            sites.add(new FarmingSettingChoice(site.getKey(), site.getLabel(), 0, null, 0,
                    prefs.visits(site.getKey()), false, shutTo(site.getStop(), state, sight)));
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
                if (shutTo(site.getStop(), state, sight)) {
                    shut.add(site.getKey());
                }
            }
        }
        return shut;
    }

    /**
     * Whether this account certainly cannot work the stop yet. An unread answer leaves it
     * open, so a login still being read locks nothing.
     */
    private static boolean shutTo(RunStop stop, PlayerState state, PatchSight sight) {
        if (state == null) {
            return false;
        }
        if (RunAvailability.of(stop.getGate(), state) == CapabilityState.LACK) {
            return true;
        }
        Capability access = stop.getAccessGate();
        return access != null && CapabilitySources.resolve(access, state, sight).getState()
                == CapabilityState.LACK;
    }

    /** No favourite, then every herb the grid ticks, in the grid's order. */
    private static FarmingSettingPick favouriteHerb(FarmingPreferences prefs) {
        List<FarmingSettingPick.Option> options = new ArrayList<>();
        options.add(new FarmingSettingPick.Option(FarmingKeys.NO_FAVOURITE, "None", null));
        for (Crop herb : byLevel(FarmingPreferences.HERB_CHOICES)) {
            options.add(new FarmingSettingPick.Option(herb.name(), herb.getDisplayName(), null));
        }
        Crop favourite = prefs.favouriteHerb();
        return new FarmingSettingPick(FarmingKeys.FAVOURITE_HERB, "Favourite herb",
                FarmingSettingPick.Shape.DROPDOWN, options,
                favourite == null ? FarmingKeys.NO_FAVOURITE : favourite.name(),
                "Planted before your other herbs");
    }

    private static FarmingSettingPick bestSeeds(FarmingPreferences prefs) {
        return new FarmingSettingPick(FarmingKeys.BEST_SEEDS_EVERYWHERE, "Best seeds in",
                FarmingSettingPick.Shape.CHIPS, List.of(
                        new FarmingSettingPick.Option("false", "Bonus patches",
                                "The few patches with the biggest yield bonus"),
                        new FarmingSettingPick.Option("true", "All", null)),
                String.valueOf(prefs.plantsBestSeedsEverywhere()), null);
    }

    /** Which run leads when several are due: the preset's first run, hardwood, or herbs. */
    public static FarmingSettingPick runFirst(FarmingPreferences prefs) {
        return new FarmingSettingPick(FarmingKeys.HERB_RUN_FIRST,
                "Herbs and hardwood both ready", FarmingSettingPick.Shape.CHIPS, List.of(
                        new FarmingSettingPick.Option("true", "Herbs first", null),
                        new FarmingSettingPick.Option("false", "Hardwood first", null)),
                String.valueOf(prefs.herbRunFirst()), null);
    }

    /** The checkbox that holds a run back until every patch on it needs a visit. */
    public static FarmingSettingChoice waitForEveryPatch(FarmingPreferences prefs) {
        return checkbox(FarmingKeys.WAIT_FOR_EVERY_PATCH, "Wait for every patch on a run",
                prefs.waitsForEveryPatch());
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
                // A feeding allotment's crop answers to its bin, not to a crop switch.
                if (!stop.isPlanting() || stop.feedsAbin()) {
                    continue;
                }
                for (Crop crop : byLevel(FarmingCatalogue.cropChoices(stop))) {
                    if (FarmingPreferences.hasAswitch(crop)
                            && !FarmingPreferences.HERB_CHOICES.contains(crop)) {
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
    private static FarmingRunSettings oneCrop(RunId id, Crop crop, FarmingPreferences prefs,
            int level, List<FarmingSettingChoice> sites) {
        if (sites.isEmpty()) {
            return runSwitch(id, toggle(crop, prefs, level, null));
        }
        return FarmingRunSettings.builder()
                .runId(id.name())
                .shape(FarmingRunSettings.Shape.SEEDS)
                .choices(List.of(toggle(crop, crop.getItemId(), prefs, level, null)))
                .sites(sites)
                .extras(List.of())
                .switches(List.of())
                .build();
    }

    /**
     * A run whose patches take any of several crops, the anima plants or the corals: a
     * square per crop, drawn as its seed with the inventory's tag, as the tree runs open.
     * Larry plants the one the route's rule ranks first among those ticked.
     *
     * <p>The coral run also carries its wait for {@link Preset#CORAL_LEVEL} Farming and an
     * Attas plant. Unlike the herb run's wait, it shows at every level: at 99 it still
     * waits for the plant.
     */
    private static FarmingRunSettings severalCrops(RunId id, Set<Crop> crops,
            FarmingPreferences prefs, int level, List<FarmingSettingChoice> sites) {
        List<FarmingSettingChoice> choices = new ArrayList<>(crops.size());
        for (Crop crop : crops) {
            choices.add(toggle(crop, prefs, level, CropData.forCrop(crop).getStoredTag()));
        }
        return FarmingRunSettings.builder()
                .runId(id.name())
                .shape(FarmingRunSettings.Shape.SEEDS)
                .choices(choices)
                .choicesCaption("Plant one of")
                .sites(sites)
                .extras(List.of())
                .switches(id != RunId.CORAL ? List.of()
                        : List.of(checkbox(FarmingKeys.CORAL_WAITS_FOR_ATTAS,
                                "Wait for " + Preset.CORAL_LEVEL + " Farming and Attas",
                                prefs.coralWaitsForAttas())))
                .build();
    }

    private static FarmingRunSettings runSwitch(RunId id, FarmingSettingChoice choice) {
        return FarmingRunSettings.builder()
                .runId(id.name())
                .shape(FarmingRunSettings.Shape.SWITCH)
                .choices(List.of())
                .extras(List.of())
                .switches(List.of())
                .runSwitch(choice)
                .build();
    }

    /** One crop's tick, drawn as its seed: a click writes the opposite of what is set. */
    private static FarmingSettingChoice toggle(Crop crop, FarmingPreferences prefs, int level,
            String tag) {
        return toggle(crop, CropData.forCrop(crop).getSeedItemId(), prefs, level, tag);
    }

    private static FarmingSettingChoice toggle(Crop crop, int itemId, FarmingPreferences prefs,
            int level, String tag) {
        CropData data = CropData.forCrop(crop);
        boolean on = prefs.allows(crop);
        return new FarmingSettingChoice(FarmingKeys.CROPS.get(crop), crop.getDisplayName(), itemId, tag != null ? tag : data.getTag(),
                data.getFarmingLevel(), on, false, data.getFarmingLevel() > level);
    }

    private static FarmingSettingChoice checkbox(String key, String label, boolean on) {
        return new FarmingSettingChoice(key, label, 0, null, 0, on, false, false);
    }

    /**
     * The player's Farming level, or no ceiling at all when the game has not reported it,
     * so an unread level locks nothing.
     */
    private static int farmingLevel(PlayerState state) {
        if (state == null || state.getSkills() == null
                || !state.getSkills().containsKey(Skill.FARMING)) {
            return Integer.MAX_VALUE;
        }
        return state.getSkillLevel(Skill.FARMING);
    }

    // --- Travel --------------------------------------------------------------

    /**
     * Every travel method as an on/off square, in {@link TravelMethod}'s order, which never
     * moves. A click writes the opposite of what is set. A square for a method the player has
     * nothing for is marked missing.
     */
    public static List<FarmingSettingChoice> travelSwitches(FarmingPreferences prefs,
            PlayerState state) {
        return travelSwitches(prefs, state, HouseSight.unseen());
    }

    /**
     * The same, with the house as last seen, so a house method greys only when the house
     * holds none of it, seen or declared.
     */
    public static List<FarmingSettingChoice> travelSwitches(FarmingPreferences prefs,
            PlayerState state, HouseSight house) {
        List<FarmingSettingChoice> squares = new ArrayList<>(TravelMethod.values().length);
        for (TravelMethod method : TravelMethod.values()) {
            squares.add(travelSquare(method, prefs, state, house));
        }
        return squares;
    }

    /** The travel squares under their captions, each group in {@link TravelMethod}'s order. */
    public static List<FarmingSettingGroup> travelGroups(FarmingPreferences prefs,
            PlayerState state, HouseSight house) {
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

    private static FarmingSettingChoice travelSquare(TravelMethod method,
            FarmingPreferences prefs, PlayerState state, HouseSight house) {
        int sprite = method == TravelMethod.SPELLBOOK_SWAP
                ? Teleports.SPELLBOOK_SWAP.getSpriteId() : 0;
        boolean missing = !owns(method, state, house, prefs.houseDeclared());
        return new FarmingSettingChoice(method.getKey(), method.getLabel(),
                sprite > 0 ? 0 : itemFor(method), tagFor(method), 0, prefs.uses(method),
                missing, false, sprite,
                missing && isSeenAbsent(method, house) ? notBuilt(method) : null);
    }

    /**
     * Whether a method is furniture the house was seen without, every piece of it, so its
     * square can say so rather than "None seen yet".
     */
    private static boolean isSeenAbsent(TravelMethod method, HouseSight house) {
        if (method.getCapabilities().isEmpty()) {
            return false;
        }
        for (Capability capability : method.getCapabilities()) {
            if (CapabilitySources.houseSighting(capability, house) != HouseSighting.ABSENT) {
                return false;
            }
        }
        return true;
    }

    private static String notBuilt(TravelMethod method) {
        return method.getCapabilities().size() == 1 ? "Not built" : "None built";
    }

    /**
     * The item a method's square draws: its first capability's, or for a method with no item
     * of its own a tablet that stands for it, tagged by {@link #tagFor}.
     */
    private static int itemFor(TravelMethod method) {
        switch (method) {
            case SALVE_GRAVEYARD_TABLETS:
                return SALVE_GRAVEYARD_TABLET;
            case TELEPORT_TABLETS:
                return CAMELOT_TABLET;
            case HOUSE_PORTALS:
            case HOUSE_FAIRY_RING:
                return HOUSE_TABLET;
            default:
                return TravelIcons.forCapability(method.getCapabilities().get(0));
        }
    }

    /** The word on a square whose sprite stands for something else, or null. */
    private static String tagFor(TravelMethod method) {
        switch (method) {
            case TELEPORT_TABLETS:
                return "TABS";
            case HOUSE_PORTALS:
                return "PORT";
            case HOUSE_FAIRY_RING:
                return "RING";
            default:
                return null;
        }
    }

    /**
     * Whether the player has anything this method uses: one of its items, any teleport
     * tablet, the Salve graveyard tablet, or Spellbook Swap's level and quest. An unread
     * answer counts as owned, so an unopened bank never greys a square. A house item counts
     * as owned while declared and not seen otherwise.
     */
    private static boolean owns(TravelMethod method, PlayerState state, HouseSight house,
            Set<Capability> houseDeclared) {
        if (state == null) {
            return true;
        }
        switch (method) {
            case SALVE_GRAVEYARD_TABLETS:
                return state.getBankLastSeen() == null
                        || ItemCounts.ownsAny(state, SALVE_GRAVEYARD_TABLET);
            case TELEPORT_TABLETS:
                return state.getBankLastSeen() == null || ownsAnyTablet(state)
                        || ownsAnyOf(method, state, house, houseDeclared);
            case SPELLBOOK_SWAP:
                return canSwap(state);
            default:
                return ownsAnyOf(method, state, house, houseDeclared);
        }
    }

    private static boolean ownsAnyOf(TravelMethod method, PlayerState state,
            HouseSight house, Set<Capability> houseDeclared) {
        for (Capability capability : method.getCapabilities()) {
            if (CapabilitySources.resolve(capability, state, PatchSight.blind(), house,
                    houseDeclared).getState() != CapabilityState.LACK) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownsAnyTablet(PlayerState state) {
        for (int tablet : TABLETS) {
            if (ItemCounts.ownsAny(state, tablet)) {
                return true;
            }
        }
        return false;
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
    public static FarmingHouse house(FarmingPreferences prefs, PlayerState state,
            HouseSight house) {
        Map<HouseItem.Group, List<FarmingSettingChoice>> rows =
                new EnumMap<>(HouseItem.Group.class);
        boolean nexusUnseen = false;
        for (HouseItem item : HouseItem.values()) {
            Capability capability = item.getCapability();
            HouseSighting sighting = CapabilitySources.houseSighting(capability, house);
            boolean built = sighting == HouseSighting.BUILT;
            boolean outOfReach = !built
                    && CapabilitySources.isHouseItemOutOfReach(capability, state);
            int level = outOfReach
                    ? CapabilitySources.houseItemLevelShort(capability, state) : 0;
            boolean locked = outOfReach || sighting == HouseSighting.ABSENT;
            if (item.getGroup() == HouseItem.Group.NEXUS && sighting == HouseSighting.UNSEEN) {
                nexusUnseen = true;
            }
            rows.computeIfAbsent(item.getGroup(), g -> new ArrayList<>())
                    .add(new FarmingSettingChoice(item.getKey(), item.getLabel(), 0, null, level,
                            built || prefs.declares(item), false, locked, 0, null,
                            sighting != HouseSighting.UNSEEN, null));
        }
        List<FarmingSettingGroup> groups = new ArrayList<>(rows.size());
        for (Map.Entry<HouseItem.Group, List<FarmingSettingChoice>> group : rows.entrySet()) {
            groups.add(new FarmingSettingGroup(group.getKey().getLabel(),
                    List.copyOf(group.getValue())));
        }
        FarmingHouse.Sight sight = house == null || !house.isScanned()
                ? FarmingHouse.Sight.NOT_SEEN
                : nexusUnseen ? FarmingHouse.Sight.NEXUS_NOT_SEEN : FarmingHouse.Sight.SEEN;
        return new FarmingHouse(sight, List.copyOf(groups));
    }

    /** Teleport to house, the tablet drawn for the house's portals and fairy ring. */
    private static final int HOUSE_TABLET = 8013;

    /** Camelot teleport, the tablet drawn for every teleport tablet. */
    private static final int CAMELOT_TABLET = 8010;

    /** The tablet the Salve graveyard rung spends, read off the catalogue's teleports. */
    private static final int SALVE_GRAVEYARD_TABLET = salveGraveyardTablet();

    /** Every teleport tablet the catalogue names, for whether the player owns any. */
    private static final int[] TABLETS = tablets();

    private static int salveGraveyardTablet() {
        for (TeleportMeans means : Teleports.to(Destination.SALVE_GRAVEYARD)) {
            if (means.isTablet()) {
                return means.getItemId();
            }
        }
        throw new IllegalStateException("the catalogue has no Salve graveyard tablet");
    }

    private static int[] tablets() {
        Set<Integer> ids = new LinkedHashSet<>();
        for (Destination destination : Destination.values()) {
            for (TeleportMeans means : Teleports.to(destination)) {
                if (means.isTablet()) {
                    ids.add(means.getItemId());
                }
            }
        }
        int[] all = new int[ids.size()];
        int i = 0;
        for (int id : ids) {
            all[i++] = id;
        }
        return all;
    }
}
