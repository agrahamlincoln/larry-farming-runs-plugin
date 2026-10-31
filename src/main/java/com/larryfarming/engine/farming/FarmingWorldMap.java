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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static registry of every tracked farming patch, keyed by map region ID.
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
 * <p>{@link #allPatches()} is grouped by {@link PatchType} in {@link #TYPE_ORDER}.
 * Within a type, patches keep the region construction order below.
 */
public final class FarmingWorldMap {

    // Transmit varbit IDs, matching RuneLite's VarbitID.FARMING_TRANSMIT_* constants.
    private static final int TRANSMIT_A = 4771;
    private static final int TRANSMIT_B = 4772;
    private static final int TRANSMIT_C = 4773;
    private static final int TRANSMIT_D = 4774;
    private static final int TRANSMIT_E = 4775;
    private static final int TRANSMIT_F = 7904;
    private static final int TRANSMIT_G = 7905;
    private static final int TRANSMIT_H = 7906;
    private static final int TRANSMIT_I = 7907;
    private static final int TRANSMIT_J = 7908;
    private static final int TRANSMIT_K = 7909;
    private static final int TRANSMIT_L = 7910;
    private static final int TRANSMIT_M = 7911;
    private static final int TRANSMIT_N = 7912;

    // Kourend vinery slots: the twelve grape patches use their own varbit block
    // (VarbitID.FARMING_TRANSMIT_A1..F2), not the shared A..N pool.
    private static final int TRANSMIT_A1 = 4953;
    private static final int TRANSMIT_A2 = 4954;
    private static final int TRANSMIT_B1 = 4955;
    private static final int TRANSMIT_B2 = 4956;
    private static final int TRANSMIT_C1 = 4957;
    private static final int TRANSMIT_C2 = 4958;
    private static final int TRANSMIT_D1 = 4959;
    private static final int TRANSMIT_D2 = 4960;
    private static final int TRANSMIT_E1 = 4961;
    private static final int TRANSMIT_E2 = 4962;
    private static final int TRANSMIT_F1 = 4963;
    private static final int TRANSMIT_F2 = 4964;

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

    /** A cluster of patches sharing a primary map region, plus the bounds check that disambiguates it from siblings. */
    public static class RegionDef {
        private final String name;
        private final List<PatchDef> patches;

        private RegionDef(String name, List<PatchDef> patches) {
            this.name = name;
            this.patches = List.copyOf(patches);
        }

        public String getName() {
            return name;
        }

        public List<PatchDef> getPatches() {
            return patches;
        }

        /**
         * True if a player standing at these world coordinates is inside this region's
         * patch cluster. Default true; overridden only where a map region ID is shared
         * with another region and the transmit varbits must be disambiguated by position.
         */
        public boolean isInBounds(int worldX, int worldY, int plane) {
            return true;
        }
    }

    /** Every map region ID (primary or extra) that can carry patches for a RegionDef, mapping to the owning RegionDef(s). */
    private static final Map<Integer, List<RegionDef>> REGIONS_BY_MAP_REGION = new HashMap<>();

    /** All tracked patches, appended in region-construction order, then sorted by {@link #TYPE_ORDER}. */
    private static final List<PatchDef> ALL_PATCHES = new ArrayList<>();

    /** Run order for {@link #allPatches()}: patches are grouped by type in this sequence. */
    private static final List<PatchType> TYPE_ORDER = List.of(
            PatchType.HERB, PatchType.TREE, PatchType.HARDWOOD_TREE,
            PatchType.ALLOTMENT, PatchType.FLOWER, PatchType.HOPS, PatchType.BUSH,
            PatchType.FRUIT_TREE, PatchType.SEAWEED, PatchType.CACTUS, PatchType.MUSHROOM,
            PatchType.BELLADONNA, PatchType.CALQUAT, PatchType.CELASTRUS, PatchType.REDWOOD,
            PatchType.CRYSTAL_TREE, PatchType.SPIRIT_TREE, PatchType.HESPORI, PatchType.ANIMA,
            PatchType.GRAPES, PatchType.CORAL, PatchType.COMPOST, PatchType.BIG_COMPOST);

    private static PatchDef patch(String location, String patchName, int regionId, int varbitId, PatchType type) {
        PatchDef def = new PatchDef(location, patchName, regionId, varbitId, type);
        ALL_PATCHES.add(def);
        return def;
    }

    private static void register(RegionDef region, int primaryRegionId, int... extraRegionIds) {
        REGIONS_BY_MAP_REGION.computeIfAbsent(primaryRegionId, k -> new ArrayList<>()).add(region);
        for (int extra : extraRegionIds) {
            REGIONS_BY_MAP_REGION.computeIfAbsent(extra, k -> new ArrayList<>()).add(region);
        }
    }

    // --- Herb clusters ---
    //
    // Registration order sets the order of herb patches within allPatches().

    static {
        register(new RegionDef("Ardougne", List.of(
                patch("Ardougne", "North", 10548, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Ardougne", "South", 10548, TRANSMIT_B, PatchType.ALLOTMENT),
                patch("Ardougne", "", 10548, TRANSMIT_C, PatchType.FLOWER),
                patch("Ardougne", "", 10548, TRANSMIT_D, PatchType.HERB),
                patch("Ardougne", "", 10548, TRANSMIT_E, PatchType.COMPOST)
        )), 10548);

        register(new RegionDef("Catherby", List.of(
                patch("Catherby", "North", 11062, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Catherby", "South", 11062, TRANSMIT_B, PatchType.ALLOTMENT),
                patch("Catherby", "", 11062, TRANSMIT_C, PatchType.FLOWER),
                patch("Catherby", "", 11062, TRANSMIT_D, PatchType.HERB),
                patch("Catherby", "", 11062, TRANSMIT_E, PatchType.COMPOST)
        )) {
            @Override
            public boolean isInBounds(int worldX, int worldY, int plane) {
                // Upstairs sends different varbits — mirrors RuneLite's FarmingWorld Catherby override.
                if (worldX >= 2816 && worldY < 3456) {
                    return worldX < 2840 && worldY >= 3440 && plane == 0;
                }
                return true;
            }
        }, 11062, 11061, 11318, 11317);

        register(new RegionDef("Civitas illa Fortis", List.of(
                patch("Civitas illa Fortis", "North", 6192, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Civitas illa Fortis", "South", 6192, TRANSMIT_B, PatchType.ALLOTMENT),
                patch("Civitas illa Fortis", "", 6192, TRANSMIT_C, PatchType.FLOWER),
                patch("Civitas illa Fortis", "", 6192, TRANSMIT_D, PatchType.HERB),
                patch("Civitas illa Fortis", "", 6192, TRANSMIT_E, PatchType.COMPOST)
        )), 6192, 6447, 6448, 6449, 6191, 6193);

        register(new RegionDef("Falador", List.of(
                patch("Falador", "North West", 12083, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Falador", "South East", 12083, TRANSMIT_B, PatchType.ALLOTMENT),
                patch("Falador", "", 12083, TRANSMIT_C, PatchType.FLOWER),
                patch("Falador", "", 12083, TRANSMIT_D, PatchType.HERB),
                patch("Falador", "", 12083, TRANSMIT_E, PatchType.COMPOST)
        )) {
            @Override
            public boolean isInBounds(int worldX, int worldY, int plane) {
                // Not on the region boundary due to the Port Sarim spirit tree patch.
                return worldY >= 3272;
            }
        }, 12083);

        register(new RegionDef("Harmony", List.of(
                patch("Harmony", "", 15148, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Harmony", "", 15148, TRANSMIT_B, PatchType.HERB)
        )), 15148);

        register(new RegionDef("Hosidius", List.of(
                patch("Hosidius", "North East", 6967, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Hosidius", "South West", 6967, TRANSMIT_B, PatchType.ALLOTMENT),
                patch("Hosidius", "", 6967, TRANSMIT_C, PatchType.FLOWER),
                patch("Hosidius", "", 6967, TRANSMIT_D, PatchType.HERB),
                patch("Hosidius", "", 6967, TRANSMIT_E, PatchType.COMPOST),
                patch("Hosidius", "", 6967, TRANSMIT_F, PatchType.SPIRIT_TREE)
        )), 6967, 6711);

        register(new RegionDef("Canifis", List.of(
                patch("Canifis", "North West", 14391, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Canifis", "South East", 14391, TRANSMIT_B, PatchType.ALLOTMENT),
                patch("Canifis", "", 14391, TRANSMIT_C, PatchType.FLOWER),
                patch("Canifis", "", 14391, TRANSMIT_D, PatchType.HERB),
                patch("Canifis", "", 14391, TRANSMIT_E, PatchType.COMPOST)
        )), 14391, 14390);

        register(new RegionDef("Troll Stronghold", List.of(
                patch("Troll Stronghold", "", 11321, TRANSMIT_A, PatchType.HERB)
        )), 11321);

        register(new RegionDef("Weiss", List.of(
                patch("Weiss", "", 11325, TRANSMIT_A, PatchType.HERB)
        )), 11325);
    }

    // --- Farming Guild ---
    //
    // Registered between the herb clusters and the tree patches so its HERB is the
    // last herb and its TREE the first tree in allPatches().

    static {
        register(new RegionDef("Farming Guild", List.of(
                patch("Farming Guild", "", 4922, TRANSMIT_G, PatchType.TREE),
                patch("Farming Guild", "", 4922, TRANSMIT_E, PatchType.HERB),
                patch("Farming Guild", "", 4922, TRANSMIT_B, PatchType.BUSH),
                patch("Farming Guild", "", 4922, TRANSMIT_H, PatchType.FLOWER),
                patch("Farming Guild", "North", 4922, TRANSMIT_C, PatchType.ALLOTMENT),
                patch("Farming Guild", "South", 4922, TRANSMIT_D, PatchType.ALLOTMENT),
                patch("Farming Guild", "", 4922, TRANSMIT_F, PatchType.CACTUS),
                patch("Farming Guild", "", 4922, TRANSMIT_A, PatchType.SPIRIT_TREE),
                patch("Farming Guild", "", 4922, TRANSMIT_K, PatchType.FRUIT_TREE),
                patch("Farming Guild", "Anima", 4922, TRANSMIT_M, PatchType.ANIMA),
                patch("Farming Guild", "", 4922, TRANSMIT_L, PatchType.CELASTRUS),
                patch("Farming Guild", "", 4922, TRANSMIT_I, PatchType.REDWOOD),
                patch("Farming Guild", "", 4922, TRANSMIT_N, PatchType.BIG_COMPOST)
        )), 4922, 5177, 5178, 5179, 4921, 4923, 4665, 4666, 4667);

        register(new RegionDef("Farming Guild", List.of(
                patch("Farming Guild", "Hespori", 5021, TRANSMIT_J, PatchType.HESPORI)
        )), 5021);
    }

    // --- Tree patches ---

    static {
        register(new RegionDef("Auburnvale", List.of(
                patch("Auburnvale", "", 5427, TRANSMIT_A, PatchType.TREE),
                patch("Auburnvale", "", 5427, TRANSMIT_B, PatchType.BELLADONNA)
        )), 5427, 5428, 5684);

        register(new RegionDef("Falador", List.of(
                patch("Falador", "", 11828, TRANSMIT_A, PatchType.TREE)
        )), 11828, 12084);

        register(new RegionDef("Gnome Stronghold", List.of(
                patch("Gnome Stronghold", "", 9781, TRANSMIT_A, PatchType.TREE),
                patch("Gnome Stronghold", "", 9781, TRANSMIT_B, PatchType.FRUIT_TREE)
        )), 9781, 9782, 9526, 9525);

        register(new RegionDef("Lumbridge", List.of(
                patch("Lumbridge", "", 12594, TRANSMIT_A, PatchType.TREE)
        )), 12594, 12850);

        register(new RegionDef("Taverley", List.of(
                patch("Taverley", "", 11573, TRANSMIT_A, PatchType.TREE)
        )), 11573, 11829);

        register(new RegionDef("Varrock", List.of(
                patch("Varrock", "", 12854, TRANSMIT_A, PatchType.TREE)
        )), 12854, 12853);
    }

    // --- Hardwood tree patches ---

    static {
        register(new RegionDef("Anglers' Retreat", List.of(
                patch("Anglers' Retreat", "", 9770, TRANSMIT_A, PatchType.HARDWOOD_TREE)
        )), 9770);

        register(new RegionDef("Avium Savannah", List.of(
                patch("Avium Savannah", "", 6702, TRANSMIT_A, PatchType.HARDWOOD_TREE)
        )), 6702, 6446);

        register(new RegionDef("Fossil Island", List.of(
                patch("Fossil Island", "East", 14651, TRANSMIT_A, PatchType.HARDWOOD_TREE),
                patch("Fossil Island", "Middle", 14651, TRANSMIT_B, PatchType.HARDWOOD_TREE),
                patch("Fossil Island", "West", 14651, TRANSMIT_C, PatchType.HARDWOOD_TREE)
        )) {
            @Override
            public boolean isInBounds(int worldX, int worldY, int plane) {
                // Hardwood tree varbits are sent anywhere on plane 0 of Fossil Island, but
                // fire a tick early on certain ladders/stairs — mirrors RuneLite's override.
                if (worldX == 3753 && worldY >= 3868 && worldY <= 3870) {
                    return false;
                }
                if ((worldX == 3728 || worldX == 3729 || worldX == 3746 || worldX == 3747)
                        && worldY >= 3830 && worldY <= 3832) {
                    return false;
                }
                return plane == 0;
            }
        }, 14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163);
    }

    // --- Every remaining single- and multi-type region ---

    static {
        register(new RegionDef("Al Kharid", List.of(
                patch("Al Kharid", "", 13106, TRANSMIT_A, PatchType.CACTUS)
        )), 13106, 13362, 13105);

        register(new RegionDef("Aldarin", List.of(
                patch("Aldarin", "", 5421, TRANSMIT_A, PatchType.HOPS)
        )), 5421, 5165, 5166, 5422, 5677, 5678);

        // Named so its label differs from the main Ardougne patch cluster.
        register(new RegionDef("Ardougne", List.of(
                patch("Ardougne", "Monastery", 10290, TRANSMIT_A, PatchType.BUSH)
        )), 10290, 10546);

        register(new RegionDef("Brimhaven", List.of(
                patch("Brimhaven", "", 11058, TRANSMIT_A, PatchType.FRUIT_TREE),
                patch("Brimhaven", "", 11058, TRANSMIT_B, PatchType.SPIRIT_TREE)
        )), 11058, 11057);

        register(new RegionDef("Catherby", List.of(
                patch("Catherby", "", 11317, TRANSMIT_A, PatchType.FRUIT_TREE)
        )) {
            @Override
            public boolean isInBounds(int worldX, int worldY, int plane) {
                // The fruit-tree patch is always sent when upstairs in 11317 — shares the
                // map region with the Catherby herb cluster and is disambiguated by bounds.
                return worldX >= 2840 || worldY < 3440 || plane == 1;
            }
        }, 11317);

        register(new RegionDef("Champions' Guild", List.of(
                patch("Champions' Guild", "", 12596, TRANSMIT_A, PatchType.BUSH)
        )), 12596);

        register(new RegionDef("Draynor Manor", List.of(
                patch("Draynor Manor", "", 12340, TRANSMIT_A, PatchType.BELLADONNA)
        )), 12340);

        register(new RegionDef("Entrana", List.of(
                patch("Entrana", "", 11060, TRANSMIT_A, PatchType.HOPS)
        )), 11060, 11316);

        register(new RegionDef("Etceteria", List.of(
                patch("Etceteria", "", 10300, TRANSMIT_A, PatchType.BUSH),
                patch("Etceteria", "", 10300, TRANSMIT_B, PatchType.SPIRIT_TREE)
        )), 10300);

        register(new RegionDef("Fossil Island", List.of(
                patch("Fossil Island", "North", 15008, TRANSMIT_A, PatchType.SEAWEED),
                patch("Fossil Island", "South", 15008, TRANSMIT_B, PatchType.SEAWEED)
        )), 15008);

        register(new RegionDef("Great Conch", List.of(
                patch("Great Conch", "East", 12581, TRANSMIT_A, PatchType.CORAL),
                patch("Great Conch", "West", 12581, TRANSMIT_B, PatchType.CORAL),
                patch("Great Conch", "", 12581, TRANSMIT_C, PatchType.CALQUAT)
        )), 12581, 12325, 12326, 12327, 12580, 12582, 12583,
                12836, 12837, 12838, 12839, 13092, 13093, 13194);

        register(new RegionDef("Kastori", List.of(
                patch("Kastori", "", 5423, TRANSMIT_A, PatchType.CALQUAT),
                patch("Kastori", "", 5423, TRANSMIT_B, PatchType.FRUIT_TREE),
                patch("Kastori", "", 5423, TRANSMIT_C, PatchType.FLOWER)
        )), 5423, 5167, 5424);

        register(new RegionDef("Kourend Vinery", List.of(
                patch("Kourend Vinery", "East 1", 7223, TRANSMIT_A1, PatchType.GRAPES),
                patch("Kourend Vinery", "East 2", 7223, TRANSMIT_A2, PatchType.GRAPES),
                patch("Kourend Vinery", "East 3", 7223, TRANSMIT_B1, PatchType.GRAPES),
                patch("Kourend Vinery", "East 4", 7223, TRANSMIT_B2, PatchType.GRAPES),
                patch("Kourend Vinery", "East 5", 7223, TRANSMIT_C1, PatchType.GRAPES),
                patch("Kourend Vinery", "East 6", 7223, TRANSMIT_C2, PatchType.GRAPES),
                patch("Kourend Vinery", "West 1", 7223, TRANSMIT_D1, PatchType.GRAPES),
                patch("Kourend Vinery", "West 2", 7223, TRANSMIT_D2, PatchType.GRAPES),
                patch("Kourend Vinery", "West 3", 7223, TRANSMIT_E1, PatchType.GRAPES),
                patch("Kourend Vinery", "West 4", 7223, TRANSMIT_E2, PatchType.GRAPES),
                patch("Kourend Vinery", "West 5", 7223, TRANSMIT_F1, PatchType.GRAPES),
                patch("Kourend Vinery", "West 6", 7223, TRANSMIT_F2, PatchType.GRAPES)
        )), 7223);

        register(new RegionDef("Lletya", List.of(
                patch("Lletya", "", 9265, TRANSMIT_A, PatchType.FRUIT_TREE)
        )), 9265, 11103);

        register(new RegionDef("Lumbridge", List.of(
                patch("Lumbridge", "", 12851, TRANSMIT_A, PatchType.HOPS)
        )), 12851);

        register(new RegionDef("Morytania", List.of(
                patch("Morytania", "Mushroom", 13622, TRANSMIT_A, PatchType.MUSHROOM)
        )), 13622, 13878);

        register(new RegionDef("Port Sarim", List.of(
                patch("Port Sarim", "", 12082, TRANSMIT_A, PatchType.SPIRIT_TREE)
        )) {
            @Override
            public boolean isInBounds(int worldX, int worldY, int plane) {
                // Shares map region 12083 with the Falador herb cluster; the spirit tree
                // only transmits south of the boundary.
                return worldY < 3272;
            }
        }, 12082, 12083);

        register(new RegionDef("Rimmington", List.of(
                patch("Rimmington", "", 11570, TRANSMIT_A, PatchType.BUSH)
        )), 11570, 11826);

        register(new RegionDef("Seers' Village", List.of(
                patch("Seers' Village", "", 10551, TRANSMIT_A, PatchType.HOPS)
        )), 10551, 10550);

        register(new RegionDef("Tai Bwo Wannai", List.of(
                patch("Tai Bwo Wannai", "", 11056, TRANSMIT_A, PatchType.CALQUAT)
        )), 11056);

        register(new RegionDef("Tree Gnome Village", List.of(
                patch("Tree Gnome Village", "", 9777, TRANSMIT_A, PatchType.FRUIT_TREE)
        )), 9777, 10033);

        register(new RegionDef("Yanille", List.of(
                patch("Yanille", "", 10288, TRANSMIT_A, PatchType.HOPS)
        )), 10288);

        // All of Prifddinas, and all of Prifddinas Underground.
        register(new RegionDef("Prifddinas", List.of(
                patch("Prifddinas", "North", 13151, TRANSMIT_A, PatchType.ALLOTMENT),
                patch("Prifddinas", "South", 13151, TRANSMIT_B, PatchType.ALLOTMENT),
                patch("Prifddinas", "", 13151, TRANSMIT_C, PatchType.FLOWER),
                patch("Prifddinas", "", 13151, TRANSMIT_D, PatchType.COMPOST),
                patch("Prifddinas", "", 13151, TRANSMIT_E, PatchType.CRYSTAL_TREE)
        )), 13151, 12895, 12894, 13150,
                12994, 12993, 12737, 12738, 12126, 12127, 13250);
    }

    private static final Map<String, PatchDef> BY_CONFIG_KEY = new HashMap<>();

    static {
        // List.sort is stable, so patches keep their construction order within each type.
        ALL_PATCHES.sort(Comparator.comparingInt(def -> TYPE_ORDER.indexOf(def.getType())));

        for (PatchDef def : ALL_PATCHES) {
            BY_CONFIG_KEY.put(def.configKey(), def);
        }
    }

    private FarmingWorldMap() {
    }

    /** RegionDefs that may own patches in this map region ID (empty list if none). Caller applies {@link RegionDef#isInBounds}. */
    public static List<RegionDef> regionsAt(int regionId) {
        List<RegionDef> found = REGIONS_BY_MAP_REGION.get(regionId);
        return found == null ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    /** Every tracked patch, grouped by {@link PatchType} in {@link #TYPE_ORDER}. */
    public static List<PatchDef> allPatches() {
        return Collections.unmodifiableList(ALL_PATCHES);
    }

    /** Reverse lookup for persistence keys; null if unknown. */
    public static PatchDef byConfigKey(String key) {
        return BY_CONFIG_KEY.get(key);
    }
}
