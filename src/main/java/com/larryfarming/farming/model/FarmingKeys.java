package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.farming.Crop;
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

    // What the player says their house holds, see HouseItem, each true while declared built.
    // A declaration counts only until Larry has seen the item.
    public static final String DECLARE_WEISS_PORTAL = "declareWeissPortal";
    public static final String DECLARE_TROLL_STRONGHOLD_PORTAL = "declareTrollStrongholdPortal";
    public static final String DECLARE_KOUREND_PORTAL = "declareKourendPortal";
    public static final String DECLARE_FENKENSTRAIN_PORTAL = "declareFenkenstrainPortal";
    public static final String DECLARE_HARMONY_PORTAL = "declareHarmonyPortal";
    public static final String DECLARE_WEISS_IN_NEXUS = "declareWeissInNexus";
    public static final String DECLARE_TROLL_STRONGHOLD_IN_NEXUS = "declareTrollStrongholdInNexus";
    public static final String DECLARE_HARMONY_IN_NEXUS = "declareHarmonyInNexus";
    public static final String DECLARE_HOUSE_SPIRIT_TREE = "declareHouseSpiritTree";
    public static final String DECLARE_HOUSE_FAIRY_RING = "declareHouseFairyRing";

    /**
     * Every site's key, each true while the run includes the site, in the stops table's
     * order: its {@code switch} lines. Callers read it as a set. Each key is named for its
     * place rather than derived from the site's key. The herb run's extras have their crop
     * switches instead.
     */
    public static final List<String> SITES = siteKeys();

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
        for (Tables.Row row : Tables.read("/com/larryfarming/crop-data.tsv", 14)) {
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
    private static List<String> siteKeys() {
        List<String> keys = new ArrayList<>();
        for (Tables.Row row : Tables.read("/com/larryfarming/stops.tsv")) {
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

    private static Set<String> allKeys() {
        Set<String> all = new HashSet<>(CROPS.values());
        all.add(PICK_FRUIT_IN_PASSING);
        for (TravelMethod method : TravelMethod.values()) {
            all.add(method.getKey());
        }
        all.addAll(SITES);
        for (HouseItem item : HouseItem.values()) {
            all.add(item.getKey());
        }
        all.add(FAVOURITE_HERB);
        all.add(BEST_SEEDS_EVERYWHERE);
        all.add(HERB_RUN_FIRST);
        all.add(WAIT_FOR_EVERY_PATCH);
        all.add(FRUIT_TREES_WAIT_FOR_SAPLINGS);
        all.add(REPLANT_GROWN_TREES);
        all.add(HERB_RUNS_WAIT_FOR_LEVEL);
        all.add(COMPOST_WITH_FERTILE_SOIL);
        return Collections.unmodifiableSet(all);
    }

    /** Every key a farming setting is saved under. */
    public static Set<String> all() {
        return ALL;
    }
}
