package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.model.*;
import java.util.*;
import lombok.Value;

/**
 * Every farm run, encoded: its stops, the routes to each, and the crops it plants.
 *
 * <p>Each run is assembled from its family in {@link Families} and its sites in
 * {@link Site}, read from the stops table, which fails to load on any patch, crop or
 * capability it does not know.
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

    private static final Map<RunId, List<FarmingRun>> RUNS_BY_ID = collectRunsById();

    private static Map<RunId, List<FarmingRun>> collectRunsById() {
        Map<RunId, List<FarmingRun>> byId = new EnumMap<>(RunId.class);
        for (FarmingRun run : RUNS) {
            byId.computeIfAbsent(run.getId(), id -> new ArrayList<>()).add(run);
        }
        return frozen(byId);
    }

    /** The same map, its lists copied, read-only. */
    private static <K, V> Map<K, List<V>> frozen(Map<K, List<V>> grouped) {
        grouped.replaceAll((key, values) -> List.copyOf(values));
        return Collections.unmodifiableMap(grouped);
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
        for (Site site : Site.values()) {
            if (site.switchKey() != null && site.family() == run) {
                sites.add(new SiteSwitch(site.switchKey(), site.switchLabel(), site.anyStop()));
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

    /** The key of the switchable site this stop works, or null when it has no switch. */
    public static String siteKey(RunStop stop) {
        return SITE_KEYS_BY_PATCH.get(stop.primaryPatch());
    }

    private static final Map<PatchRef, String> SITE_KEYS_BY_PATCH = collectSiteKeysByPatch();

    private static Map<PatchRef, String> collectSiteKeysByPatch() {
        Map<PatchRef, String> keys = new HashMap<>();
        for (Site site : Site.values()) {
            if (site.switchKey() != null) {
                for (PatchRef patch : site.patches()) {
                    keys.put(patch, site.switchKey());
                }
            }
        }
        return Collections.unmodifiableMap(keys);
    }

    /**
     * The crops this stop may grow, best first: the default crop and any the player may
     * tick in its place. Empty for a stop the run's seeds fill.
     */
    public static List<Crop> cropChoices(RunStop stop) {
        if (!stop.isFixedCrop()) {
            return List.of();
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
            if (spellbook == Spellbook.ANY || run.getSpellbook() == spellbook || run.getSpellbook() == Spellbook.ANY) {
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
        return CatalogueLoader.loaded().toAfairyRing;
    }

    /**
     * Every approach a rung's walk leaves out: the ways onto the fairy rings and the spirit
     * trees, each with its walk from landing to network. The planner adds the walk of the way
     * in this account takes, which is why a ladder with such a rung is ranked per account.
     */
    public static List<Approach> approaches() {
        return CatalogueLoader.loaded().approachList;
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

    /**
     * Every Farming Guild patch a contract can name, which is everywhere a contracted crop
     * could be growing.
     *
     * <p>The spirit tree and anima patch are left out because no contract
     * asks for them.
     *
     * <p>The scheduler reads these patches to tell whether a contract has grown: the
     * contract varbits name the assigned crop but not whether it is ready.
     */
    public static List<PatchRef> contractPatches() {
        return CatalogueLoader.loaded().contractPatches;
    }

    /**
     * The patch that settles this capability, or null if it is not a patch question.
     *
     * <p>A few capabilities are patch states: a grown spirit tree is a travel option, and a
     * usable Weiss herb patch proves the fire of nourishment is lit. {@code PatchSight}
     * reads them. Only the spirit trees a chain offers a rung for count.
     */
    public static PatchRef patchFor(Capability capability) {
        return CatalogueLoader.loaded().patchFor.get(capability);
    }
}
