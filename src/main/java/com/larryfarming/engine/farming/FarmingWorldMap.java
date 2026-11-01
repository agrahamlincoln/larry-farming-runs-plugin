/*
 * Copyright (c) 2018, NotFoxtrot <https://github.com/NotFoxtrot>
 * Copyright (c) 2018 Abex
 * Copyright (c) 2026, agrahamlincoln
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.larryfarming.engine.farming;

import com.larryfarming.data.Tables;
import java.util.*;

/**
 * Static registry of every tracked farming patch, keyed by map region ID, read from
 * {@code patches.tsv}.
 *
 * <p>OSRS transmits nearby farming patch state through a small pool of generic
 * "transmit" varbits — the same varbit means a different patch depending on which
 * map region the player is standing in. A patch is therefore identified by the pair
 * (region, varbit), not the varbit alone. This mirrors RuneLite's Time Tracking
 * {@code FarmingWorld} registry, including its compost bins.
 *
 * <p>Pure logic, no RuneLite dependency. One map region can host patches belonging
 * to more than one {@link RegionDef} (region 11317 carries the Catherby herb cluster
 * and the Catherby fruit tree; region 12083 carries the Falador herb cluster and the
 * Port Sarim spirit tree), so {@link #regionsAt} returns every candidate and leaves
 * disambiguation to {@link RegionDef#isInBounds}.
 *
 * <p>{@link #allPatches()} is grouped by {@link PatchType}, in the enum's order. Within a
 * type, patches keep the table's order.
 */
public final class FarmingWorldMap {

    private static final String RESOURCE = "/com/larryfarming/patches.tsv";

    /** One tracked patch: its location, identifying varbit, and type. */
    public static final class PatchDef {
        private final String location;
        private final String patchName;
        private final int regionId;
        private final int varbitId;
        private final PatchType type;

        private PatchDef(String location, String patchName, int regionId, int varbitId, PatchType type) {
            this.location = location;
            this.patchName = patchName;
            this.regionId = regionId;
            this.varbitId = varbitId;
            this.type = type;
        }

        public String getLocation() {
            return location;
        }

        /** "" for single patches, or a disambiguator like "North"/"South"/"East" for multi-patch regions. */
        public String getPatchName() {
            return patchName;
        }

        /** The owning region's primary region ID — used as the persistence key, not necessarily where the player stands. */
        public int getRegionId() {
            return regionId;
        }

        public int getVarbitId() {
            return varbitId;
        }

        public PatchType getType() {
            return type;
        }

        /** Persistence key, matching RuneLite's "regionId.varbitId" convention. */
        public String configKey() {
            return regionId + "." + varbitId;
        }

        /** Human-readable label: location alone, or location plus the disambiguating patch name. */
        public String displayName() {
            return patchName.isEmpty() ? location : location + " (" + patchName + ")";
        }
    }

    /**
     * Where in a shared map region a cluster's varbits are sent, for the clusters that
     * share one with another cluster. Each check mirrors RuneLite's {@code FarmingWorld}
     * override for the same cluster.
     */
    private enum Bounds {
        ANYWHERE,
        CATHERBY_HERBS,
        CATHERBY_FRUIT_TREE,
        FALADOR_HERBS,
        PORT_SARIM_SPIRIT_TREE,
        FOSSIL_ISLAND_HARDWOOD
    }

    /** A cluster of patches sharing a primary map region, plus the bounds check that disambiguates it from siblings. */
    public static final class RegionDef {
        private final String name;
        private final List<PatchDef> patches;
        private final Bounds bounds;

        private RegionDef(String name, List<PatchDef> patches, Bounds bounds) {
            this.name = name;
            this.patches = List.copyOf(patches);
            this.bounds = bounds;
        }

        public String getName() {
            return name;
        }

        public List<PatchDef> getPatches() {
            return patches;
        }

        /**
         * True if a player standing at these world coordinates is inside this region's
         * patch cluster. True everywhere except where a map region ID is shared with
         * another cluster and the transmit varbits must be disambiguated by position.
         */
        public boolean isInBounds(int worldX, int worldY, int plane) {
            switch (bounds) {
                case CATHERBY_HERBS:
                    // Upstairs sends different varbits.
                    if (worldX >= 2816 && worldY < 3456) {
                        return worldX < 2840 && worldY >= 3440 && plane == 0;
                    }
                    return true;
                case CATHERBY_FRUIT_TREE:
                    // Always sent upstairs in 11317, which it shares with the herb cluster.
                    return worldX >= 2840 || worldY < 3440 || plane == 1;
                case FALADOR_HERBS:
                    // Not on the region boundary, because of the Port Sarim spirit tree.
                    return worldY >= 3272;
                case PORT_SARIM_SPIRIT_TREE:
                    return worldY < 3272;
                case FOSSIL_ISLAND_HARDWOOD:
                    // Sent anywhere on plane 0 of the island, but a tick early on certain
                    // ladders and stairs.
                    if (worldX == 3753 && worldY >= 3868 && worldY <= 3870) {
                        return false;
                    }
                    if ((worldX == 3728 || worldX == 3729 || worldX == 3746 || worldX == 3747) && worldY >= 3830 && worldY <= 3832) {
                        return false;
                    }
                    return plane == 0;
                default:
                    return true;
            }
        }
    }

    /** Every map region ID (primary or extra) that can carry patches for a RegionDef, mapping to the owning RegionDef(s). */
    private static final Map<Integer, List<RegionDef>> REGIONS_BY_MAP_REGION = new HashMap<>();

    /** All tracked patches, grouped by type. */
    private static final List<PatchDef> ALL_PATCHES = new ArrayList<>();

    private static final Map<String, PatchDef> BY_CONFIG_KEY = new HashMap<>();

    /** The patches the farming catalogue names, by the key in the table's last column. */
    private static final Map<String, PatchDef> BY_CATALOGUE_KEY = new HashMap<>();

    static {
        Tables.Row region = null;
        List<PatchDef> patches = new ArrayList<>();
        for (Tables.Row row : Tables.read(RESOURCE)) {
            switch (row.text(0)) {
                case "region":
                    if (region != null) {
                        register(region, patches);
                    }
                    region = row.requireWidth(5);
                    patches = new ArrayList<>();
                    break;
                case "patch":
                    if (region == null) {
                        throw row.error("a patch before any region");
                    }
                    row.requireWidth(6);
                    PatchDef def = new PatchDef(region.text(1), row.text(1), region.integer(2),
                            row.integer(2), row.constant(PatchType.class, 4));
                    patches.add(def);
                    if (BY_CONFIG_KEY.put(def.configKey(), def) != null) {
                        throw row.error("a second patch at " + def.configKey());
                    }
                    String key = row.text(5);
                    if (!key.isEmpty() && BY_CATALOGUE_KEY.put(key, def) != null) {
                        throw row.error("a second patch keyed " + key);
                    }
                    break;
                default:
                    throw row.error("expected a region or patch row");
            }
        }
        if (region != null) {
            register(region, patches);
        }
        // List.sort is stable, so patches keep the table's order within each type.
        ALL_PATCHES.sort(Comparator.comparing(PatchDef::getType));
    }

    private static void register(Tables.Row row, List<PatchDef> patches) {
        if (patches.isEmpty()) {
            throw row.error("a region with no patches");
        }
        RegionDef region = new RegionDef(row.text(1), patches, row.constant(Bounds.class, 4));
        REGIONS_BY_MAP_REGION.computeIfAbsent(row.integer(2), k -> new ArrayList<>()).add(region);
        for (int extra : row.integers(3)) {
            REGIONS_BY_MAP_REGION.computeIfAbsent(extra, k -> new ArrayList<>()).add(region);
        }
        ALL_PATCHES.addAll(patches);
    }

    private FarmingWorldMap() {
    }

    /** RegionDefs that may own patches in this map region ID (empty list if none). Caller applies {@link RegionDef#isInBounds}. */
    public static List<RegionDef> regionsAt(int regionId) {
        List<RegionDef> found = REGIONS_BY_MAP_REGION.get(regionId);
        return found == null ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    /** Every tracked patch, grouped by {@link PatchType} in the enum's order. */
    public static List<PatchDef> allPatches() {
        return Collections.unmodifiableList(ALL_PATCHES);
    }

    /** Reverse lookup for persistence keys; null if unknown. */
    public static PatchDef byConfigKey(String key) {
        return BY_CONFIG_KEY.get(key);
    }

    /** The patch the farming catalogue names {@code key}, such as {@code WEISS_HERB}; null if none. */
    public static PatchDef byCatalogueKey(String key) {
        return BY_CATALOGUE_KEY.get(key);
    }
}
