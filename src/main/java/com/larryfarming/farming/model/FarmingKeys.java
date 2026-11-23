package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.FarmingWorldMap;
import java.util.*;

/**
 * The config keys of every farming setting, spelled once.
 *
 * <p>The side panel writes these keys, the plugin listens for them, and the planner reads
 * them, all through this class. None is a {@code @ConfigItem}, so none shows on RuneLite's
 * settings page.
 */
public final class FarmingKeys {

    // Switches that plant nothing.
    public static final String PICK_FRUIT_IN_PASSING = "pickFruitInPassing";
    // Travel methods' keys come from the travel methods table: TravelMethod.getKey().
    // What the player says their house holds is keyed by each HouseItem: HouseItem.getKey().

    /**
     * Every site's key, each true while the run includes the site, in the stops table's
     * order: its {@code switch} lines. Callers read it as a set. Each key is named for its
     * place rather than derived from the site's key. The herb run's extras have their crop
     * switches or their picks instead.
     */
    public static final List<String> SITES;

    /**
     * The allotments and flower patches whose crop the player picks, in the stops table's
     * order: its {@code pick} lines. Keyed by site.
     */
    public static final Map<String, PatchPick> PICKS;

    static {
        List<Tables.Row> stops = Tables.read("/com/larryfarming/stops.tsv");
        SITES = siteKeys(stops);
        PICKS = picks(stops);
    }

    /** A pick's value when the patch plants nothing. */
    public static final String OFF = "OFF";

    // How the planner ranks and paces. The favourite herb holds a herb's Crop name, or
    // NO_FAVOURITE; the others are booleans.
    public static final String FAVOURITE_HERB = "favouriteHerb";
    public static final String BEST_SEEDS_EVERYWHERE = "bestSeedsInEveryPatch";
    public static final String HERB_RUN_FIRST = "herbRunFirst";
    public static final String WAIT_FOR_EVERY_PATCH = "waitForEveryPatch";
    public static final String FRUIT_TREES_WAIT_FOR_SAPLINGS = "fruitTreesWaitForSaplings";
    public static final String REPLANT_GROWN_TREES = "replantGrownTrees";
    public static final String HERB_RUNS_WAIT_FOR_LEVEL = "herbRunsWaitForLevel";
    public static final String COMPOST_WITH_FERTILE_SOIL = "compostWithFertileSoil";

    // The compost thresholds hold whole numbers.
    public static final String MAKE_COMPOST_BELOW = "makeCompostBelow";
    public static final String STOP_COMPOST_AT = "stopCompostAt";
    /** The longest run after a teleport a route may ask for, in whole seconds. */
    public static final String LONGEST_RUN_SECONDS = "longestRunSeconds";
    /**
     * The shared switch for every teleport tablet. A tablet whose own switch is unsaved follows
     * it, so a player who turned tablets off keeps them off.
     */
    public static final String TELEPORT_TABLETS = "useTeleportTablets";

    /** The favourite herb's value when there is none. */
    public static final String NO_FAVOURITE = "none";

    /** Each crop's tick, for every crop {@link FarmingPreferences#hasAswitch} accepts. */
    public static final Map<Crop, String> CROPS = cropKeys();

    private static final Set<String> ALL = allKeys();

    private FarmingKeys() {
    }

    /** The crop data table's config key column, for the crops that have one. */
    private static Map<Crop, String> cropKeys() {
        Map<Crop, String> keys = new EnumMap<>(Crop.class);
        Set<String> seen = new HashSet<>();
        for (Tables.Row row : Tables.read("/com/larryfarming/crop-data.tsv", 15)) {
            String key = row.text(13);
            if (key.isEmpty()) {
                continue;
            }
            if (!seen.add(key)) {
                throw row.error("a second crop keyed " + key);
            }
            keys.put(row.constant(Crop.class, 0), key);
        }
        return Collections.unmodifiableMap(keys);
    }

    /** The stops table's switch keys, in its order. */
    private static List<String> siteKeys(List<Tables.Row> stops) {
        List<String> keys = new ArrayList<>();
        for (Tables.Row row : stops) {
            if (row.text(0).equals("switch")) {
                String key = row.requireWidth(3).text(1);
                if (keys.contains(key)) {
                    throw row.error("a second switch " + key);
                }
                keys.add(key);
            }
        }
        return List.copyOf(keys);
    }

    /**
     * The stops table's pick lines, each under the site it picks for: the pick's key, the
     * For compost key or - for a patch with no bin, the farm, the picker's title, and the crop
     * switches read while the pick is unsaved.
     */
    private static Map<String, PatchPick> picks(List<Tables.Row> stops) {
        Map<String, PatchPick> picks = new LinkedHashMap<>();
        String site = null;
        for (Tables.Row row : stops) {
            if (row.text(0).equals("site")) {
                site = row.text(1);
            } else if (row.text(0).equals("pick")) {
                row.requireWidth(6);
                if (FarmingWorldMap.byCatalogueKey(site) == null) {
                    throw row.error("a pick at " + site + ", which is no patch's key");
                }
                if (picks.put(site, new PatchPick(site, row.text(1), row.optionalText(2), row.text(3), row.text(4),
                        row.constants(Crop.class, 5))) != null) {
                    throw row.error("a second pick at " + site);
                }
            }
        }
        return Collections.unmodifiableMap(picks);
    }

    private static Set<String> allKeys() {
        Set<String> all = new HashSet<>(CROPS.values());
        all.addAll(List.of(PICK_FRUIT_IN_PASSING, FAVOURITE_HERB, BEST_SEEDS_EVERYWHERE, HERB_RUN_FIRST, WAIT_FOR_EVERY_PATCH,
                FRUIT_TREES_WAIT_FOR_SAPLINGS, REPLANT_GROWN_TREES, HERB_RUNS_WAIT_FOR_LEVEL, COMPOST_WITH_FERTILE_SOIL,
                MAKE_COMPOST_BELOW, STOP_COMPOST_AT, LONGEST_RUN_SECONDS, TELEPORT_TABLETS));
        for (TravelMethod method : TravelMethod.values()) {
            all.add(method.getKey());
        }
        all.addAll(SITES);
        for (PatchPick pick : PICKS.values()) {
            all.add(pick.getKey());
            if (pick.getCompostKey() != null) {
                all.add(pick.getCompostKey());
            }
        }
        for (HouseItem item : HouseItem.all()) {
            all.add(item.getKey());
        }
        return Collections.unmodifiableSet(all);
    }

    /** Every key a farming setting is saved under. */
    public static Set<String> all() {
        return ALL;
    }
}
