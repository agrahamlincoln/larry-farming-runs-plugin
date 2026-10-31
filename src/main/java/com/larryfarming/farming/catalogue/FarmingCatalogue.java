package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.Approach;
import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.ContractStrategy;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.FarmingRun;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.Preset;
import com.larryfarming.farming.model.Requirement;
import com.larryfarming.farming.model.RunId;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.SeedPolicy;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.farming.model.TravelOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Every farm run, encoded: its stops, the routes to each, and the crops it plants.
 *
 * <p>Written as Java so the compiler checks every patch, crop and capability a stop
 * names. Each run is assembled from its family in {@link Families} and its sites in
 * {@link Site}.
 *
 * <p>Everything here is data. Deciding which runs are due, which rung a player can take
 * and what to show them belongs elsewhere.
 */
public final class FarmingCatalogue {

    private FarmingCatalogue() {
    }

    /** Assembled once, so every caller sees the same run objects. */
    private static final List<FarmingRun> RUNS = RunAssembler.assemble();

    private static final SeedPolicy SEED_POLICY = Seeds.policy();

    private static final ContractStrategy CONTRACT_STRATEGY =
            Preset.DEFAULT.getContractPreplants();

    public static List<FarmingRun> allRuns() {
        return RUNS;
    }

    private static final Map<RunId, List<FarmingRun>> RUNS_BY_ID = collectRunsById();

    private static Map<RunId, List<FarmingRun>> collectRunsById() {
        Map<RunId, List<FarmingRun>> byId = new EnumMap<>(RunId.class);
        for (FarmingRun run : RUNS) {
            byId.computeIfAbsent(run.getId(), id -> new ArrayList<>()).add(run);
        }
        Map<RunId, List<FarmingRun>> frozen = new EnumMap<>(RunId.class);
        for (Map.Entry<RunId, List<FarmingRun>> entry : byId.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * Every variant of each run, in run-id order. The herb run is in the catalogue once per
     * spellbook.
     */
    public static Map<RunId, List<FarmingRun>> runsById() {
        return RUNS_BY_ID;
    }

    /**
     * This run's stops in the order {@link Site} declares them. Reordering a run's walk
     * does not move this order, so a list the player reads, such as a crop grid, draws
     * from it to keep one fixed order.
     */
    public static List<RunStop> stopsInRegistryOrder(FarmingRun run) {
        List<RunStop> stops = new ArrayList<>(run.getStops().size());
        for (Site site : Site.values()) {
            if (site.family() == run.getId() && site.books().contains(run.getSpellbook())) {
                RunStop stop = site.stop(run.getSpellbook());
                if (run.getStops().contains(stop)) {
                    stops.add(stop);
                }
            }
        }
        return List.copyOf(stops);
    }

    /** A site the player may drop from a run: its key, its name, and one of its stops. */
    @Value
    public static class SiteSwitch {
        /** The {@code FarmingKeys} key that holds whether the runs visit it. */
        String key;
        /** Named for a player, as the run's settings list it: "Weiss". */
        String label;
        /** The site's stop on any book, for the gates every book's stop shares. */
        RunStop stop;
    }

    /**
     * The sites of this run the player may drop, in the order {@link Site} declares them,
     * which never moves. Empty for a run with no such sites.
     */
    public static List<SiteSwitch> siteSwitches(RunId run) {
        List<SiteSwitch> sites = new ArrayList<>();
        for (Map.Entry<Site, SiteSwitches.Entry> site : SiteSwitches.BY_SITE.entrySet()) {
            if (site.getKey().family() == run) {
                sites.add(new SiteSwitch(site.getValue().key, site.getValue().label,
                        site.getKey().anyStop()));
            }
        }
        return List.copyOf(sites);
    }

    /** The keys of {@link #siteSwitches}, in the same order, read without allocating. */
    public static List<String> siteKeys(RunId run) {
        return SITE_KEYS.getOrDefault(run, List.of());
    }

    private static final Map<RunId, List<String>> SITE_KEYS = collectSiteKeys();

    private static Map<RunId, List<String>> collectSiteKeys() {
        Map<RunId, List<String>> keys = new EnumMap<>(RunId.class);
        for (RunId run : RunId.values()) {
            List<String> ofRun = new ArrayList<>();
            for (SiteSwitch site : siteSwitches(run)) {
                ofRun.add(site.getKey());
            }
            keys.put(run, List.copyOf(ofRun));
        }
        return Collections.unmodifiableMap(keys);
    }

    /**
     * The key of the switchable site this stop works, or null when it has no switch. An
     * allotment feeding a bin answers with its bin's key, so dropping the bin drops it too.
     */
    public static String siteKey(RunStop stop) {
        return SiteSwitches.BY_PATCH.get(
                stop.feedsAbin() ? stop.getFeeds() : stop.primaryPatch());
    }

    /** The allotments that fill this bin, in registry order; empty for the guild's bin. */
    public static List<RunStop> feedersOf(PatchRef bin) {
        return FEEDERS.getOrDefault(bin, List.of());
    }

    private static final Map<PatchRef, List<RunStop>> FEEDERS = collectFeeders();

    private static Map<PatchRef, List<RunStop>> collectFeeders() {
        Map<PatchRef, List<RunStop>> feeders = new LinkedHashMap<>();
        for (Site site : Site.values()) {
            RunStop stop = site.anyStop();
            if (stop.feedsAbin()) {
                feeders.computeIfAbsent(stop.getFeeds(), bin -> new ArrayList<>()).add(stop);
            }
        }
        Map<PatchRef, List<RunStop>> frozen = new LinkedHashMap<>();
        for (Map.Entry<PatchRef, List<RunStop>> entry : feeders.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    /** The stop that works this compost bin, or null for a patch no bin stop works. */
    public static RunStop binStop(PatchRef bin) {
        return BIN_STOPS.get(bin);
    }

    private static final Map<PatchRef, RunStop> BIN_STOPS = collectBinStops();

    private static Map<PatchRef, RunStop> collectBinStops() {
        Map<PatchRef, RunStop> bins = new LinkedHashMap<>();
        for (Site site : Site.values()) {
            RunStop stop = site.anyStop();
            if (stop.getActions().contains(RunStop.StopAction.FILL_COMPOST_BIN)) {
                bins.put(stop.primaryPatch(), stop);
            }
        }
        return Collections.unmodifiableMap(bins);
    }

    /**
     * The site key of the bin this patch is worked to fill, or null when no stop works it
     * for a bin.
     */
    public static String binSiteFedBy(FarmingWorldMap.PatchDef patch) {
        return FEEDING_PATCHES.get(patch);
    }

    /** Identity-keyed, as {@code PatchDef} has no {@code equals}. */
    private static final Map<FarmingWorldMap.PatchDef, String> FEEDING_PATCHES =
            collectFeedingPatches();

    private static Map<FarmingWorldMap.PatchDef, String> collectFeedingPatches() {
        Map<FarmingWorldMap.PatchDef, String> feeding = new IdentityHashMap<>();
        for (FarmingRun run : RUNS) {
            for (RunStop stop : run.getStops()) {
                String binSite = stop.feedsAbin() ? siteKey(stop) : null;
                if (binSite == null) {
                    continue;
                }
                for (PatchRef ref : stop.getPatches()) {
                    FarmingWorldMap.PatchDef def = ref.resolve();
                    if (def != null) {
                        feeding.put(def, binSite);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(feeding);
    }

    /** The key of the switchable site that works this patch, or null when none does. */
    public static String siteKey(FarmingWorldMap.PatchDef patch) {
        return SiteSwitches.BY_PATCH_DEF.get(patch);
    }

    /**
     * The crops this stop may grow, best first: the default crop and any the player may
     * tick in its place. Empty for a stop the run's seeds fill. An allotment feeding a bin
     * grows its one crop: the bin's row names what fills it.
     */
    public static List<Crop> cropChoices(RunStop stop) {
        if (!stop.isFixedCrop()) {
            return List.of();
        }
        if (stop.feedsAbin()) {
            return List.of(stop.getFixedCrop());
        }
        List<Crop> family = CropChoices.forPatch(stop.patchType());
        return family.contains(stop.getFixedCrop()) ? family : List.of(stop.getFixedCrop());
    }

    /**
     * How many herb runs pass between visits to this stop while it grows this crop.
     *
     * <p>The stop's own figure is the one source for its default crop, and answers for a
     * stop with no choice. {@link CropChoices} carries the figure only for the crops a
     * player may switch on in the default crop's place.
     */
    public static int everyNthRun(RunStop stop, Crop crop) {
        if (crop == null || crop == stop.getFixedCrop()) {
            return stop.getEveryNthRun();
        }
        int ofCrop = CropChoices.everyNthRun(crop);
        return ofCrop > 0 && cropChoices(stop).contains(crop) ? ofCrop : stop.getEveryNthRun();
    }

    /**
     * The run for this activity on this spellbook, or null if there is none.
     *
     * <p>{@link Spellbook#ANY} means "it does not matter" in both directions: a run
     * declared for ANY matches whatever is asked for, and asking for ANY takes the
     * first run with that id.
     */
    public static FarmingRun run(RunId id, Spellbook spellbook) {
        for (FarmingRun run : RUNS) {
            if (run.getId() != id) {
                continue;
            }
            if (spellbook == Spellbook.ANY
                    || run.getSpellbook() == spellbook
                    || run.getSpellbook() == Spellbook.ANY) {
                return run;
            }
        }
        return null;
    }

    public static SeedPolicy seedPolicy() {
        return SEED_POLICY;
    }

    /**
     * Some way to stand at a fairy ring, the half of every fairy ring rung that the player's
     * items and switches decide. The same instance every chain uses, so the planner can find
     * it inside a rung.
     */
    public static Requirement toAfairyRing() {
        return Networks.TO_A_FAIRY_RING;
    }

    /**
     * Every approach a rung's walk leaves out: the ways onto the fairy rings and the spirit
     * trees, each with its walk from landing to network. The planner adds the walk of the way
     * in this account takes, which is why a ladder with such a rung is ranked per account.
     */
    public static List<Approach> approaches() {
        return Networks.APPROACHES;
    }

    /**
     * How to reach an arrival when several of its patches are being worked at once, or
     * empty when the lead stop's own chain serves (see {@link Arrivals}).
     *
     * <p>Empty is the common answer, not an error. Never hand the empty list to a route
     * resolver: it would report the place unreachable. Fall back to the lead stop's chain.
     */
    public static List<TravelOption> combinedRoute(Arrival arrival) {
        return Arrivals.combinedChain(arrival);
    }

    public static ContractStrategy contractStrategy() {
        return CONTRACT_STRATEGY;
    }

    /**
     * Every Farming Guild patch a contract can name, which is everywhere a contracted crop
     * could be growing.
     *
     * <p>The same ten the hard-tier preplant list fills. The big compost bin, spirit tree and
     * anima patch are left out because no contract asks for them.
     *
     * <p>The scheduler reads these patches to tell whether a contract has grown: the
     * contract varbits name the assigned crop but not whether it is ready.
     */
    public static List<PatchRef> contractPatches() {
        return Patches.CONTRACT_PATCHES;
    }

    /**
     * The patch that settles this capability, or null if it is not a patch question.
     *
     * <p>A few capabilities are patch states: a grown spirit tree is a travel option, and a
     * usable Weiss herb patch proves the fire of nourishment is lit. {@code PatchSight}
     * reads them.
     */
    public static PatchRef patchFor(Capability capability) {
        return PATCH_BACKED.get(capability);
    }

    /**
     * Only the spirit trees a chain offers a rung for. Adding Etceteria or Hosidius needs a
     * rung as well as an entry here.
     */
    private static final Map<Capability, PatchRef> PATCH_BACKED = Map.of(
            Capability.SPIRIT_TREE_GROWN_BRIMHAVEN, Patches.BRIMHAVEN_SPIRIT_TREE,
            Capability.SPIRIT_TREE_GROWN_FARMING_GUILD, Patches.FARMING_GUILD_SPIRIT_TREE,
            Capability.SPIRIT_TREE_GROWN_PORT_SARIM, Patches.PORT_SARIM_SPIRIT_TREE,
            Capability.WEISS_HERB_PATCH, Patches.WEISS_HERB);

    /**
     * Whether a contract could be growing in this patch.
     *
     * <p>Player settings may not silence a contract patch, even when its patch type
     * belongs to a family the settings switch off.
     */
    public static boolean isContractPatch(FarmingWorldMap.PatchDef patch) {
        return CONTRACT_PATCH_DEFS.contains(patch);
    }

    private static final Set<FarmingWorldMap.PatchDef> CONTRACT_PATCH_DEFS =
            collectContractPatchDefs();

    private static Set<FarmingWorldMap.PatchDef> collectContractPatchDefs() {
        Set<FarmingWorldMap.PatchDef> defs = new LinkedHashSet<>();
        for (PatchRef ref : Patches.CONTRACT_PATCHES) {
            FarmingWorldMap.PatchDef def = ref.resolve();
            if (def != null) {
                defs.add(def);
            }
        }
        return Collections.unmodifiableSet(defs);
    }

    /**
     * Every patch with a named crop, and the crops it may grow.
     *
     * <p>Answers "what would be lost by silencing this patch". Herb and tree patches are
     * absent: their crop comes from the rotation or the family's ticks.
     *
     * <p>A crop counts as named two ways: a stop that {@linkplain RunStop.Builder#plants
     * names} one, or a whole run whose seed patch type grows one crop
     * (calquat).
     *
     * <p>{@code PatchDef} has no {@code equals}, so this map is identity-keyed. That holds
     * only because every instance comes from {@link FarmingWorldMap}'s registry; a
     * deserialized {@code PatchDef} would silently miss.
     */
    public static Map<FarmingWorldMap.PatchDef, List<Crop>> namedCropPatches() {
        return NAMED_CROP_PATCHES;
    }

    private static final Map<FarmingWorldMap.PatchDef, List<Crop>> NAMED_CROP_PATCHES =
            collectNamedCropPatches();

    private static Map<FarmingWorldMap.PatchDef, List<Crop>> collectNamedCropPatches() {
        Map<FarmingWorldMap.PatchDef, List<Crop>> named = new LinkedHashMap<>();
        for (FarmingRun run : RUNS) {
            Crop wholeRun = onlyCropOf(run);
            for (RunStop stop : run.getStops()) {
                // A feeding allotment answers to its bin's switches, not to a crop's. Catherby
                // north keeps the snape grass stop's crops, which decide whether it feeds.
                if (stop.feedsAbin()) {
                    continue;
                }
                List<Crop> crops = stop.isFixedCrop() ? cropChoices(stop)
                        : wholeRun != null ? List.of(wholeRun) : List.of();
                if (crops.isEmpty()) {
                    continue;
                }
                for (PatchRef ref : stop.getPatches()) {
                    FarmingWorldMap.PatchDef def = ref.resolve();
                    if (def != null) {
                        named.put(def, crops);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(named);
    }

    /**
     * The one crop a whole run grows, or null when it is not that kind of run.
     *
     * <p>A seed patch type with one crop and no ticks of its own: calquat. A tree family is
     * ticked crop by crop ({@link FarmingPreferences#familyChoices}), so no one crop
     * names it.
     */
    private static Crop onlyCropOf(FarmingRun run) {
        PatchType type = run.getSeedPatchType();
        if (type == null || !FarmingPreferences.familyChoices(type).isEmpty()) {
            return null;
        }
        List<Crop> crops = CropData.plantableIn(type);
        return crops.size() == 1 ? crops.get(0) : null;
    }

    /**
     * Every capability any travel chain or access gate asks for, in the order first
     * encountered.
     */
    private static final Set<Capability> REFERENCED_CAPABILITIES = collectCapabilities();

    public static Set<Capability> allCapabilitiesReferenced() {
        return REFERENCED_CAPABILITIES;
    }

    private static Set<Capability> collectCapabilities() {
        Set<Capability> referenced = new LinkedHashSet<>();
        for (FarmingRun run : RUNS) {
            for (RunStop stop : run.getStops()) {
                for (TravelOption option : stop.getTravel()) {
                    referenced.addAll(option.getRequires().leafCapabilities());
                }
                // The access gate is on no rung but resolves against the account like one.
                if (stop.getAccessGate() != null) {
                    referenced.add(stop.getAccessGate());
                }
            }
        }
        return Collections.unmodifiableSet(referenced);
    }
}
