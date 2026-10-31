/*
 * Copyright (c) 2019 Abex
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

/**
 * Every kind of farming patch the plugin tracks, and the logic that turns a patch's
 * transmit-varbit byte into a {@link DecodedPatch}.
 *
 * <p>OSRS encodes the full state of a nearby patch — crop species, health, and
 * growth stage — as a single value in the range 0-255. The mapping is dense and
 * irregular (weed filler ranges, one-off "check health" states, and gaps that
 * decode to nothing), so the authoritative source is RuneLite's Time Tracking
 * plugin ({@code PatchImplementation}); every boundary here is transcribed from
 * its per-type {@code forVarbitValue} case.
 *
 * <p>The tree and herb types decode from per-species base tables. The remaining
 * types have irregular layouts (weed filler of varying width, disease/dead runs
 * that skip values), so their decoders open-code Time Tracking's value ranges.
 */
public enum PatchType {
    HERB("Herb", false, 199) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeHerb(varbitValue);
        }
    },
    TREE("Tree", true, 5370) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeTree(varbitValue);
        }
    },
    HARDWOOD_TREE("Hardwood", true, 6333) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeHardwood(varbitValue);
        }
    },
    ALLOTMENT("Allotment", false, 1942) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeAllotment(varbitValue);
        }
    },
    FLOWER("Flower", false, 6010) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeFlower(varbitValue);
        }
    },
    HOPS("Hops", false, 6006) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeHops(varbitValue);
        }
    },
    BUSH("Bush", true, 1951) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeBush(varbitValue);
        }
    },
    FRUIT_TREE("Fruit tree", true, 1955) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeFruitTree(varbitValue);
        }
    },
    SEAWEED("Seaweed", false, 21504) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeSeaweed(varbitValue);
        }
    },
    CACTUS("Cactus", true, 6016) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeCactus(varbitValue);
        }
    },
    MUSHROOM("Mushroom", false, 6004) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeMushroom(varbitValue);
        }
    },
    BELLADONNA("Belladonna", false, 2398) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeBelladonna(varbitValue);
        }
    },
    CALQUAT("Calquat", true, 5980) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeCalquat(varbitValue);
        }
    },
    CELASTRUS("Celastrus", true, 1391) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeCelastrus(varbitValue);
        }
    },
    REDWOOD("Redwood", true, 19669) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeRedwood(varbitValue);
        }
    },
    CRYSTAL_TREE("Crystal tree", true, 23962) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeCrystalTree(varbitValue);
        }
    },
    SPIRIT_TREE("Spirit tree", true, 6063) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeSpiritTree(varbitValue);
        }
    },
    HESPORI("Hespori", true, 22875) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeHespori(varbitValue);
        }
    },
    ANIMA("Anima", false, 22881) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeAnima(varbitValue);
        }
    },
    GRAPES("Grape", true, 1987) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeGrapes(varbitValue);
        }
    },
    CORAL("Coral", false, 31481) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeCoral(varbitValue);
        }
    },
    COMPOST("Compost", true, 6032) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeCompost(varbitValue);
        }
    },
    BIG_COMPOST("Big compost", true, 6032) {
        @Override
        public DecodedPatch decode(int varbitValue) {
            return decodeBigCompost(varbitValue);
        }
    };

    private final String displayName;
    private final boolean healthCheckRequired;
    private final int iconItemId;

    PatchType(String displayName, boolean healthCheckRequired, int iconItemId) {
        this.displayName = displayName;
        this.healthCheckRequired = healthCheckRequired;
        this.iconItemId = iconItemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether harvest needs a Check-health click first. For such patches the
     * check-health state decodes as GROWING at the crop's max stage.
     */
    public boolean isHealthCheckRequired() {
        return healthCheckRequired;
    }

    /** Representative produce item ID for this type's InfoBox / panel icon. */
    public int getIconItemId() {
        return iconItemId;
    }

    /** Decode a patch transmit-varbit byte; null when the value is not a known state. */
    public abstract DecodedPatch decode(int varbitValue);

    // The unplanted patch's growth value counts *down* as the player rakes weeds:
    // 3 = fully weedy, 0 = cleared. Every patch type shares this at values 0-3.
    private static final int WEEDS_STAGE = 3;

    private static boolean inRange(int value, int lo, int hi) {
        return value >= lo && value <= hi;
    }

    private static DecodedPatch weeds() {
        return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
    }

    // --- HERB ---

    /**
     * Growing/harvestable herb blocks, in varbit order. Each species owns a
     * 7-value block from its base: 4 GROWING values ({@code base .. base+3},
     * stage {@code v - base}) then 3 HARVESTABLE values ({@code base+4 .. base+6},
     * a lives countdown of stage {@code base+6 - v}). Value 67 falls between the
     * Huasca and Kwuarm blocks and decodes as weeds, not a herb.
     */
    private static final Crop[] HERB_GROW_ORDER = {
        Crop.GUAM, Crop.MARRENTILL, Crop.TARROMIN, Crop.HARRALANDER, Crop.RANARR,
        Crop.TOADFLAX, Crop.IRIT, Crop.AVANTOE, Crop.HUASCA, Crop.KWUARM,
        Crop.SNAPDRAGON, Crop.CADANTINE, Crop.LANTADYME, Crop.DWARF_WEED, Crop.TORSTOL,
    };
    private static final int[] HERB_GROW_BASE = {
        4, 11, 18, 25, 32, 39, 46, 53, 60, 68, 75, 82, 89, 96, 103,
    };

    /**
     * Diseased-herb species order, contiguous 3-value blocks from value 128.
     * Huasca is absent from this run (its diseased block sits later at 173-175),
     * and Goutweed has its own dedicated range, so neither appears here.
     */
    private static final Crop[] HERB_DISEASE_ORDER = {
        Crop.GUAM, Crop.MARRENTILL, Crop.TARROMIN, Crop.HARRALANDER, Crop.RANARR,
        Crop.TOADFLAX, Crop.IRIT, Crop.AVANTOE, Crop.KWUARM, Crop.SNAPDRAGON,
        Crop.CADANTINE, Crop.LANTADYME, Crop.DWARF_WEED, Crop.TORSTOL,
    };
    private static final int HERB_DISEASE_BASE = 128;

    private static DecodedPatch decodeHerb(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        for (int i = 0; i < HERB_GROW_ORDER.length; i++) {
            int base = HERB_GROW_BASE[i];
            if (inRange(v, base, base + 3)) {
                return new DecodedPatch(HERB_GROW_ORDER[i], CropState.GROWING, v - base);
            }
            if (inRange(v, base + 4, base + 6)) {
                return new DecodedPatch(HERB_GROW_ORDER[i], CropState.HARVESTABLE, base + 6 - v);
            }
        }
        if (v == 67) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
        }
        // 110-127 is an unused gap between the last harvestable herb and the diseased run.
        if (inRange(v, HERB_DISEASE_BASE, HERB_DISEASE_BASE + HERB_DISEASE_ORDER.length * 3 - 1)) {
            int idx = (v - HERB_DISEASE_BASE) / 3;
            int base = HERB_DISEASE_BASE + idx * 3;
            return new DecodedPatch(HERB_DISEASE_ORDER[idx], CropState.DISEASED, v - base + 1);
        }
        if (inRange(v, 170, 172)) {
            // Dead herbs collapse to one range: the species is unrecoverable.
            return new DecodedPatch(Crop.ANYHERB, CropState.DEAD, v - 169);
        }
        if (inRange(v, 173, 175)) {
            return new DecodedPatch(Crop.HUASCA, CropState.DISEASED, v - 172);
        }
        if (inRange(v, 176, 191)) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
        }
        // Goutweed (Trouble Brewing / My Arm's Big Adventure crop) has a self-contained block.
        if (inRange(v, 192, 195)) {
            return new DecodedPatch(Crop.GOUTWEED, CropState.GROWING, v - 192);
        }
        if (inRange(v, 196, 197)) {
            return new DecodedPatch(Crop.GOUTWEED, CropState.HARVESTABLE, 197 - v);
        }
        if (inRange(v, 198, 200)) {
            return new DecodedPatch(Crop.GOUTWEED, CropState.DISEASED, v - 197);
        }
        if (inRange(v, 201, 203)) {
            return new DecodedPatch(Crop.GOUTWEED, CropState.DEAD, v - 200);
        }
        if (inRange(v, 204, 219)) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
        }
        // 220 is an isolated gap in the weed filler.
        if (inRange(v, 221, 255)) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
        }
        return null;
    }

    // --- TREE ---

    /**
     * Regular-tree species, in varbit order. Each species is defined by its
     * GROWING base and its DISEASED base; the DEAD base sits a fixed 64 values
     * above the DISEASED base. Sub-ranges are derived from the crop's stage count
     * (see {@link #decodeTreeSpecies}).
     */
    private static final Crop[] TREE_ORDER = {
        Crop.OAK, Crop.WILLOW, Crop.MAPLE, Crop.YEW, Crop.MAGIC,
    };
    private static final int[] TREE_GROW_BASE = {8, 15, 24, 35, 48};
    private static final int[] TREE_DISEASE_BASE = {73, 80, 89, 100, 113};
    private static final int TREE_DEAD_OFFSET = 64;

    /**
     * Values that decode to nothing: the disease and dead runs each leave one hole
     * where the game skips a growth value. Everything else in the tree range that
     * no species claims is weed filler, so listing the holes explicitly lets the
     * fall-through default to weeds without misreporting these as weedy.
     */
    private static final int[] TREE_HOLES = {76, 85, 96, 109, 124, 140, 149, 160, 173, 188};

    private static DecodedPatch decodeTree(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        for (int i = 0; i < TREE_ORDER.length; i++) {
            DecodedPatch match = decodeTreeSpecies(v, TREE_ORDER[i],
                    TREE_GROW_BASE[i], TREE_DISEASE_BASE[i]);
            if (match != null) {
                return match;
            }
        }
        // Values 192-197 all report a grown Willow offering "Chop down", not its
        // stump, so the patch is still occupied.
        if (inRange(v, 192, 197)) {
            return new DecodedPatch(Crop.WILLOW, CropState.HARVESTABLE, 0, true);
        }
        for (int hole : TREE_HOLES) {
            if (v == hole) {
                return null;
            }
        }
        return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
    }

    /**
     * Decode one regular-tree species, or null if the value is outside all of its
     * ranges. With {@code s} stages: GROWING runs {@code grow .. grow+s-2}, the
     * value {@code grow+s-1} is the Check-health state (GROWING at max stage), and
     * {@code grow+s}/{@code grow+s+1} are the tree and its stump (HARVESTABLE 0).
     * DISEASED runs {@code dis .. dis+s-3} then skips a hole at {@code dis+s-2} and
     * resumes at {@code dis+s-1}; DEAD repeats that shape 64 values higher.
     *
     * <p>RuneLite treats the two HARVESTABLE values as one state; this splits them.
     * {@code grow+s} is the checked tree, which offers "Chop down". {@code grow+s+1} is
     * the stump, which offers "Clear", and only the stump can take a sapling. See
     * {@link DecodedPatch#isFellable()}.
     */
    private static DecodedPatch decodeTreeSpecies(int v, Crop crop, int grow, int dis) {
        int s = crop.getStages();
        if (inRange(v, grow, grow + s - 2)) {
            return new DecodedPatch(crop, CropState.GROWING, v - grow);
        }
        if (v == grow + s - 1) {
            return new DecodedPatch(crop, CropState.GROWING, s - 1);
        }
        if (v == grow + s) {
            return new DecodedPatch(crop, CropState.HARVESTABLE, 0, true);
        }
        if (v == grow + s + 1) {
            return new DecodedPatch(crop, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, dis, dis + s - 3)) {
            return new DecodedPatch(crop, CropState.DISEASED, v - dis + 1);
        }
        if (v == dis + s - 1) {
            return new DecodedPatch(crop, CropState.DISEASED, s - 1);
        }
        int dead = dis + TREE_DEAD_OFFSET;
        if (inRange(v, dead, dead + s - 3)) {
            return new DecodedPatch(crop, CropState.DEAD, v - dead + 1);
        }
        if (v == dead + s - 1) {
            return new DecodedPatch(crop, CropState.DEAD, s - 1);
        }
        return null;
    }

    // --- HARDWOOD_TREE ---

    /**
     * Hardwood species, in varbit order. The layout is fully regular from each
     * base {@code b} with {@code s} stages: GROWING {@code b .. b+s-2}, Check-health
     * at {@code b+s-1}, tree/stump HARVESTABLE at {@code b+s}/{@code b+s+1},
     * DISEASED {@code b+s+2 .. b+2s-1}, then DEAD {@code b+2s .. b+3s-3}. No holes.
     */
    private static final Crop[] HARDWOOD_ORDER = {
        Crop.TEAK, Crop.MAHOGANY, Crop.CAMPHOR, Crop.IRONWOOD, Crop.ROSEWOOD,
    };
    private static final int[] HARDWOOD_BASE = {8, 30, 55, 80, 105};

    private static DecodedPatch decodeHardwood(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        for (int i = 0; i < HARDWOOD_ORDER.length; i++) {
            DecodedPatch match = decodeHardwoodSpecies(v, HARDWOOD_ORDER[i], HARDWOOD_BASE[i]);
            if (match != null) {
                return match;
            }
        }
        // The species blocks run contiguously from 8 through 132; the rest is weed filler.
        if (inRange(v, 4, 7) || inRange(v, 133, 255)) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
        }
        return null;
    }

    /**
     * Decode one hardwood species from base {@code b}; see {@link #HARDWOOD_ORDER}.
     *
     * <p>The checked tree at {@code b+s} and its stump at {@code b+s+1} split as in
     * {@link #decodeTreeSpecies}.
     */
    private static DecodedPatch decodeHardwoodSpecies(int v, Crop crop, int b) {
        int s = crop.getStages();
        if (inRange(v, b, b + s - 2)) {
            return new DecodedPatch(crop, CropState.GROWING, v - b);
        }
        if (v == b + s - 1) {
            return new DecodedPatch(crop, CropState.GROWING, s - 1);
        }
        if (v == b + s) {
            return new DecodedPatch(crop, CropState.HARVESTABLE, 0, true);
        }
        if (v == b + s + 1) {
            return new DecodedPatch(crop, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, b + s + 2, b + 2 * s - 1)) {
            return new DecodedPatch(crop, CropState.DISEASED, v - (b + s + 1));
        }
        if (inRange(v, b + 2 * s, b + 3 * s - 3)) {
            return new DecodedPatch(crop, CropState.DEAD, v - (b + 2 * s - 1));
        }
        return null;
    }

    // --- ALLOTMENT ---

    /**
     * Allotment crops (potato, onion, cabbage, tomato, sweetcorn, strawberry,
     * watermelon, snape grass). Time Tracking interleaves narrow raked-weed fillers
     * of irregular width between the growing, diseased, and dead runs, and snape
     * grass has split diseased/dead ranges, so the layout is open-coded rather than
     * table-driven. Every value 0-255 decodes (no gaps).
     */
    private static DecodedPatch decodeAllotment(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 5)) {
            return weeds();
        }
        if (inRange(v, 6, 9)) {
            return new DecodedPatch(Crop.POTATO, CropState.GROWING, v - 6);
        }
        if (inRange(v, 10, 12)) {
            return new DecodedPatch(Crop.POTATO, CropState.HARVESTABLE, v - 10);
        }
        if (inRange(v, 13, 16)) {
            return new DecodedPatch(Crop.ONION, CropState.GROWING, v - 13);
        }
        if (inRange(v, 17, 19)) {
            return new DecodedPatch(Crop.ONION, CropState.HARVESTABLE, v - 17);
        }
        if (inRange(v, 20, 23)) {
            return new DecodedPatch(Crop.CABBAGE, CropState.GROWING, v - 20);
        }
        if (inRange(v, 24, 26)) {
            return new DecodedPatch(Crop.CABBAGE, CropState.HARVESTABLE, v - 24);
        }
        if (inRange(v, 27, 30)) {
            return new DecodedPatch(Crop.TOMATO, CropState.GROWING, v - 27);
        }
        if (inRange(v, 31, 33)) {
            return new DecodedPatch(Crop.TOMATO, CropState.HARVESTABLE, v - 31);
        }
        if (inRange(v, 34, 39)) {
            return new DecodedPatch(Crop.SWEETCORN, CropState.GROWING, v - 34);
        }
        if (inRange(v, 40, 42)) {
            return new DecodedPatch(Crop.SWEETCORN, CropState.HARVESTABLE, v - 40);
        }
        if (inRange(v, 43, 48)) {
            return new DecodedPatch(Crop.STRAWBERRY, CropState.GROWING, v - 43);
        }
        if (inRange(v, 49, 51)) {
            return new DecodedPatch(Crop.STRAWBERRY, CropState.HARVESTABLE, v - 49);
        }
        if (inRange(v, 52, 59)) {
            return new DecodedPatch(Crop.WATERMELON, CropState.GROWING, v - 52);
        }
        if (inRange(v, 60, 62)) {
            return new DecodedPatch(Crop.WATERMELON, CropState.HARVESTABLE, v - 60);
        }
        if (inRange(v, 63, 69)) {
            return new DecodedPatch(Crop.SNAPE_GRASS, CropState.GROWING, v - 63);
        }
        if (inRange(v, 70, 73)) {
            return new DecodedPatch(Crop.POTATO, CropState.GROWING, v - 70);
        }
        if (inRange(v, 74, 76)) {
            return weeds();
        }
        if (inRange(v, 77, 80)) {
            return new DecodedPatch(Crop.ONION, CropState.GROWING, v - 77);
        }
        if (inRange(v, 81, 83)) {
            return weeds();
        }
        if (inRange(v, 84, 87)) {
            return new DecodedPatch(Crop.CABBAGE, CropState.GROWING, v - 84);
        }
        if (inRange(v, 88, 90)) {
            return weeds();
        }
        if (inRange(v, 91, 94)) {
            return new DecodedPatch(Crop.TOMATO, CropState.GROWING, v - 91);
        }
        if (inRange(v, 95, 97)) {
            return weeds();
        }
        if (inRange(v, 98, 103)) {
            return new DecodedPatch(Crop.SWEETCORN, CropState.GROWING, v - 98);
        }
        if (inRange(v, 104, 106)) {
            return weeds();
        }
        if (inRange(v, 107, 112)) {
            return new DecodedPatch(Crop.STRAWBERRY, CropState.GROWING, v - 107);
        }
        if (inRange(v, 113, 115)) {
            return weeds();
        }
        if (inRange(v, 116, 123)) {
            return new DecodedPatch(Crop.WATERMELON, CropState.GROWING, v - 116);
        }
        if (inRange(v, 124, 127)) {
            return weeds();
        }
        if (inRange(v, 128, 134)) {
            return new DecodedPatch(Crop.SNAPE_GRASS, CropState.GROWING, v - 128);
        }
        if (inRange(v, 135, 137)) {
            return new DecodedPatch(Crop.POTATO, CropState.DISEASED, v - 134);
        }
        if (inRange(v, 138, 140)) {
            return new DecodedPatch(Crop.SNAPE_GRASS, CropState.HARVESTABLE, v - 138);
        }
        if (v == 141) {
            return weeds();
        }
        if (inRange(v, 142, 144)) {
            return new DecodedPatch(Crop.ONION, CropState.DISEASED, v - 141);
        }
        if (inRange(v, 145, 148)) {
            return weeds();
        }
        if (inRange(v, 149, 151)) {
            return new DecodedPatch(Crop.CABBAGE, CropState.DISEASED, v - 148);
        }
        if (inRange(v, 152, 155)) {
            return weeds();
        }
        if (inRange(v, 156, 158)) {
            return new DecodedPatch(Crop.TOMATO, CropState.DISEASED, v - 155);
        }
        if (inRange(v, 159, 162)) {
            return weeds();
        }
        if (inRange(v, 163, 167)) {
            return new DecodedPatch(Crop.SWEETCORN, CropState.DISEASED, v - 162);
        }
        if (inRange(v, 168, 171)) {
            return weeds();
        }
        if (inRange(v, 172, 176)) {
            return new DecodedPatch(Crop.STRAWBERRY, CropState.DISEASED, v - 171);
        }
        if (inRange(v, 177, 180)) {
            return weeds();
        }
        if (inRange(v, 181, 187)) {
            return new DecodedPatch(Crop.WATERMELON, CropState.DISEASED, v - 180);
        }
        if (inRange(v, 188, 192)) {
            return weeds();
        }
        if (inRange(v, 193, 195)) {
            return new DecodedPatch(Crop.SNAPE_GRASS, CropState.DEAD, v - 192);
        }
        if (inRange(v, 196, 198)) {
            return new DecodedPatch(Crop.SNAPE_GRASS, CropState.DISEASED, v - 195);
        }
        if (inRange(v, 199, 201)) {
            return new DecodedPatch(Crop.POTATO, CropState.DEAD, v - 198);
        }
        if (inRange(v, 202, 204)) {
            // Snape grass disease/dead states are split across two ranges; the game
            // continues the countdown, so stage picks up at 3 + offset.
            return new DecodedPatch(Crop.SNAPE_GRASS, CropState.DISEASED, 3 + v - 201);
        }
        if (v == 205) {
            return weeds();
        }
        if (inRange(v, 206, 208)) {
            return new DecodedPatch(Crop.ONION, CropState.DEAD, v - 205);
        }
        if (inRange(v, 209, 211)) {
            return new DecodedPatch(Crop.SNAPE_GRASS, CropState.DEAD, 3 + v - 208);
        }
        if (v == 212) {
            return weeds();
        }
        if (inRange(v, 213, 215)) {
            return new DecodedPatch(Crop.CABBAGE, CropState.DEAD, v - 212);
        }
        if (inRange(v, 216, 219)) {
            return weeds();
        }
        if (inRange(v, 220, 222)) {
            return new DecodedPatch(Crop.TOMATO, CropState.DEAD, v - 219);
        }
        if (inRange(v, 223, 226)) {
            return weeds();
        }
        if (inRange(v, 227, 231)) {
            return new DecodedPatch(Crop.SWEETCORN, CropState.DEAD, v - 226);
        }
        if (inRange(v, 232, 235)) {
            return weeds();
        }
        if (inRange(v, 236, 240)) {
            return new DecodedPatch(Crop.STRAWBERRY, CropState.DEAD, v - 235);
        }
        if (inRange(v, 241, 244)) {
            return weeds();
        }
        if (inRange(v, 245, 251)) {
            return new DecodedPatch(Crop.WATERMELON, CropState.DEAD, v - 244);
        }
        if (inRange(v, 252, 255)) {
            return weeds();
        }
        return null;
    }

    // --- FLOWER ---

    /**
     * Flower crops (marigold, rosemary, nasturtium, woad, limpwurt, scarecrow,
     * white lily). Each flower has a single harvestable value rather than a
     * countdown; the scarecrow is a decorative "crop" whose growth values run
     * downward. Every value 0-255 decodes (no gaps).
     */
    private static DecodedPatch decodeFlower(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return weeds();
        }
        if (inRange(v, 8, 11)) {
            return new DecodedPatch(Crop.MARIGOLD, CropState.GROWING, v - 8);
        }
        if (v == 12) {
            return new DecodedPatch(Crop.MARIGOLD, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 13, 16)) {
            return new DecodedPatch(Crop.ROSEMARY, CropState.GROWING, v - 13);
        }
        if (v == 17) {
            return new DecodedPatch(Crop.ROSEMARY, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 18, 21)) {
            return new DecodedPatch(Crop.NASTURTIUM, CropState.GROWING, v - 18);
        }
        if (v == 22) {
            return new DecodedPatch(Crop.NASTURTIUM, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 23, 26)) {
            return new DecodedPatch(Crop.WOAD, CropState.GROWING, v - 23);
        }
        if (v == 27) {
            return new DecodedPatch(Crop.WOAD, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 28, 31)) {
            return new DecodedPatch(Crop.LIMPWURT, CropState.GROWING, v - 28);
        }
        if (v == 32) {
            return new DecodedPatch(Crop.LIMPWURT, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 33, 35)) {
            // Scarecrow growth values count downward (35 = just planted).
            return new DecodedPatch(Crop.SCARECROW, CropState.GROWING, 35 - v);
        }
        if (v == 36) {
            return new DecodedPatch(Crop.SCARECROW, CropState.GROWING, 0);
        }
        if (inRange(v, 37, 40)) {
            return new DecodedPatch(Crop.WHITE_LILY, CropState.GROWING, v - 37);
        }
        if (v == 41) {
            return new DecodedPatch(Crop.WHITE_LILY, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 42, 71)) {
            return weeds();
        }
        if (inRange(v, 72, 75)) {
            return new DecodedPatch(Crop.MARIGOLD, CropState.GROWING, v - 72);
        }
        if (v == 76) {
            return weeds();
        }
        if (inRange(v, 77, 80)) {
            return new DecodedPatch(Crop.ROSEMARY, CropState.GROWING, v - 77);
        }
        if (v == 81) {
            return weeds();
        }
        if (inRange(v, 82, 85)) {
            return new DecodedPatch(Crop.NASTURTIUM, CropState.GROWING, v - 82);
        }
        if (v == 86) {
            return weeds();
        }
        if (inRange(v, 87, 90)) {
            return new DecodedPatch(Crop.WOAD, CropState.GROWING, v - 87);
        }
        if (v == 91) {
            return weeds();
        }
        if (inRange(v, 92, 95)) {
            return new DecodedPatch(Crop.LIMPWURT, CropState.GROWING, v - 92);
        }
        if (inRange(v, 96, 100)) {
            return weeds();
        }
        if (inRange(v, 101, 104)) {
            return new DecodedPatch(Crop.WHITE_LILY, CropState.GROWING, v - 101);
        }
        if (inRange(v, 105, 136)) {
            return weeds();
        }
        if (inRange(v, 137, 139)) {
            return new DecodedPatch(Crop.MARIGOLD, CropState.DISEASED, v - 136);
        }
        if (inRange(v, 140, 141)) {
            return weeds();
        }
        if (inRange(v, 142, 144)) {
            return new DecodedPatch(Crop.ROSEMARY, CropState.DISEASED, v - 141);
        }
        if (inRange(v, 145, 146)) {
            return weeds();
        }
        if (inRange(v, 147, 149)) {
            return new DecodedPatch(Crop.NASTURTIUM, CropState.DISEASED, v - 146);
        }
        if (inRange(v, 150, 151)) {
            return weeds();
        }
        if (inRange(v, 152, 154)) {
            return new DecodedPatch(Crop.WOAD, CropState.DISEASED, v - 151);
        }
        if (inRange(v, 155, 156)) {
            return weeds();
        }
        if (inRange(v, 157, 159)) {
            return new DecodedPatch(Crop.LIMPWURT, CropState.DISEASED, v - 156);
        }
        if (inRange(v, 160, 165)) {
            return weeds();
        }
        if (inRange(v, 166, 168)) {
            return new DecodedPatch(Crop.WHITE_LILY, CropState.DISEASED, v - 165);
        }
        if (inRange(v, 169, 200)) {
            return weeds();
        }
        if (inRange(v, 201, 204)) {
            return new DecodedPatch(Crop.MARIGOLD, CropState.DEAD, v - 200);
        }
        if (v == 205) {
            return weeds();
        }
        if (inRange(v, 206, 209)) {
            return new DecodedPatch(Crop.ROSEMARY, CropState.DEAD, v - 205);
        }
        if (v == 210) {
            return weeds();
        }
        if (inRange(v, 211, 214)) {
            return new DecodedPatch(Crop.NASTURTIUM, CropState.DEAD, v - 210);
        }
        if (v == 215) {
            return weeds();
        }
        if (inRange(v, 216, 219)) {
            return new DecodedPatch(Crop.WOAD, CropState.DEAD, v - 215);
        }
        if (v == 220) {
            return weeds();
        }
        if (inRange(v, 221, 224)) {
            return new DecodedPatch(Crop.LIMPWURT, CropState.DEAD, v - 220);
        }
        if (inRange(v, 225, 229)) {
            return weeds();
        }
        if (inRange(v, 230, 233)) {
            return new DecodedPatch(Crop.WHITE_LILY, CropState.DEAD, v - 229);
        }
        if (inRange(v, 234, 255)) {
            return weeds();
        }
        return null;
    }

    // --- HOPS ---

    /**
     * Hops crops (hammerstone, asgarnian, yanillian, krandorian, wildblood, barley,
     * jute, flax, hemp, cotton). Growing runs are followed by a short harvestable
     * countdown; the diseased runs sit contiguously after the first growing pass,
     * with a second growing pass and the dead runs interleaved with weed filler.
     * Every value 0-255 decodes (no gaps).
     */
    private static DecodedPatch decodeHops(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return new DecodedPatch(Crop.HAMMERSTONE, CropState.GROWING, v - 4);
        }
        if (inRange(v, 8, 10)) {
            return new DecodedPatch(Crop.HAMMERSTONE, CropState.HARVESTABLE, 10 - v);
        }
        if (inRange(v, 11, 13)) {
            return new DecodedPatch(Crop.HAMMERSTONE, CropState.DISEASED, v - 10);
        }
        if (inRange(v, 14, 18)) {
            return new DecodedPatch(Crop.ASGARNIAN, CropState.GROWING, v - 14);
        }
        if (inRange(v, 19, 21)) {
            return new DecodedPatch(Crop.ASGARNIAN, CropState.HARVESTABLE, 21 - v);
        }
        if (inRange(v, 22, 25)) {
            return new DecodedPatch(Crop.ASGARNIAN, CropState.DISEASED, v - 21);
        }
        if (inRange(v, 26, 31)) {
            return new DecodedPatch(Crop.YANILLIAN, CropState.GROWING, v - 26);
        }
        if (inRange(v, 32, 34)) {
            return new DecodedPatch(Crop.YANILLIAN, CropState.HARVESTABLE, 34 - v);
        }
        if (inRange(v, 35, 39)) {
            return new DecodedPatch(Crop.YANILLIAN, CropState.DISEASED, v - 34);
        }
        if (inRange(v, 40, 46)) {
            return new DecodedPatch(Crop.KRANDORIAN, CropState.GROWING, v - 40);
        }
        if (inRange(v, 47, 49)) {
            return new DecodedPatch(Crop.KRANDORIAN, CropState.HARVESTABLE, 49 - v);
        }
        if (inRange(v, 50, 55)) {
            return new DecodedPatch(Crop.KRANDORIAN, CropState.DISEASED, v - 49);
        }
        if (inRange(v, 56, 63)) {
            return new DecodedPatch(Crop.WILDBLOOD, CropState.GROWING, v - 56);
        }
        if (inRange(v, 64, 66)) {
            return new DecodedPatch(Crop.WILDBLOOD, CropState.HARVESTABLE, 66 - v);
        }
        if (inRange(v, 67, 73)) {
            return new DecodedPatch(Crop.WILDBLOOD, CropState.DISEASED, v - 66);
        }
        if (inRange(v, 74, 77)) {
            return new DecodedPatch(Crop.BARLEY, CropState.GROWING, v - 74);
        }
        if (inRange(v, 78, 80)) {
            return new DecodedPatch(Crop.BARLEY, CropState.HARVESTABLE, 80 - v);
        }
        if (inRange(v, 81, 83)) {
            return new DecodedPatch(Crop.BARLEY, CropState.DISEASED, v - 80);
        }
        if (inRange(v, 84, 88)) {
            return new DecodedPatch(Crop.JUTE, CropState.GROWING, v - 84);
        }
        if (inRange(v, 89, 91)) {
            return new DecodedPatch(Crop.JUTE, CropState.HARVESTABLE, 91 - v);
        }
        if (inRange(v, 92, 95)) {
            return new DecodedPatch(Crop.JUTE, CropState.DISEASED, v - 91);
        }
        if (inRange(v, 96, 98)) {
            return new DecodedPatch(Crop.FLAX, CropState.GROWING, v - 96);
        }
        if (inRange(v, 99, 101)) {
            return new DecodedPatch(Crop.FLAX, CropState.HARVESTABLE, 101 - v);
        }
        if (inRange(v, 102, 103)) {
            return new DecodedPatch(Crop.FLAX, CropState.DISEASED, v - 101);
        }
        if (inRange(v, 104, 107)) {
            return new DecodedPatch(Crop.HEMP, CropState.GROWING, v - 104);
        }
        if (inRange(v, 108, 110)) {
            return new DecodedPatch(Crop.HEMP, CropState.HARVESTABLE, 110 - v);
        }
        if (inRange(v, 111, 113)) {
            return new DecodedPatch(Crop.HEMP, CropState.DISEASED, v - 110);
        }
        if (inRange(v, 114, 118)) {
            return new DecodedPatch(Crop.COTTON, CropState.GROWING, v - 114);
        }
        if (inRange(v, 119, 121)) {
            return new DecodedPatch(Crop.COTTON, CropState.HARVESTABLE, 121 - v);
        }
        if (inRange(v, 122, 125)) {
            return new DecodedPatch(Crop.COTTON, CropState.DISEASED, v - 121);
        }
        if (inRange(v, 126, 131)) {
            return weeds();
        }
        if (inRange(v, 132, 135)) {
            return new DecodedPatch(Crop.HAMMERSTONE, CropState.GROWING, v - 132);
        }
        if (inRange(v, 136, 138)) {
            return weeds();
        }
        if (inRange(v, 139, 141)) {
            return new DecodedPatch(Crop.HAMMERSTONE, CropState.DEAD, v - 138);
        }
        if (inRange(v, 142, 146)) {
            return new DecodedPatch(Crop.ASGARNIAN, CropState.GROWING, v - 142);
        }
        if (inRange(v, 147, 149)) {
            return weeds();
        }
        if (inRange(v, 150, 153)) {
            return new DecodedPatch(Crop.ASGARNIAN, CropState.DEAD, v - 149);
        }
        if (inRange(v, 154, 159)) {
            return new DecodedPatch(Crop.YANILLIAN, CropState.GROWING, v - 154);
        }
        if (inRange(v, 160, 162)) {
            return weeds();
        }
        if (inRange(v, 163, 167)) {
            return new DecodedPatch(Crop.YANILLIAN, CropState.DEAD, v - 162);
        }
        if (inRange(v, 168, 174)) {
            return new DecodedPatch(Crop.KRANDORIAN, CropState.GROWING, v - 168);
        }
        if (inRange(v, 175, 177)) {
            return weeds();
        }
        if (inRange(v, 178, 183)) {
            return new DecodedPatch(Crop.KRANDORIAN, CropState.DEAD, v - 177);
        }
        if (inRange(v, 184, 191)) {
            return new DecodedPatch(Crop.WILDBLOOD, CropState.GROWING, v - 184);
        }
        if (inRange(v, 192, 194)) {
            return weeds();
        }
        if (inRange(v, 195, 201)) {
            return new DecodedPatch(Crop.WILDBLOOD, CropState.DEAD, v - 194);
        }
        if (inRange(v, 202, 205)) {
            return new DecodedPatch(Crop.BARLEY, CropState.GROWING, v - 202);
        }
        if (inRange(v, 206, 208)) {
            return weeds();
        }
        if (inRange(v, 209, 211)) {
            return new DecodedPatch(Crop.BARLEY, CropState.DEAD, v - 208);
        }
        if (inRange(v, 212, 216)) {
            return new DecodedPatch(Crop.JUTE, CropState.GROWING, v - 212);
        }
        if (inRange(v, 217, 219)) {
            return weeds();
        }
        if (inRange(v, 220, 223)) {
            return new DecodedPatch(Crop.JUTE, CropState.DEAD, v - 219);
        }
        if (inRange(v, 224, 226)) {
            return new DecodedPatch(Crop.FLAX, CropState.GROWING, v - 224);
        }
        if (inRange(v, 227, 229)) {
            return weeds();
        }
        if (inRange(v, 230, 231)) {
            return new DecodedPatch(Crop.FLAX, CropState.DEAD, v - 229);
        }
        if (inRange(v, 232, 235)) {
            return new DecodedPatch(Crop.HEMP, CropState.GROWING, v - 232);
        }
        if (inRange(v, 236, 238)) {
            return weeds();
        }
        if (inRange(v, 239, 240)) {
            return new DecodedPatch(Crop.HEMP, CropState.DEAD, v - 238);
        }
        if (v == 241) {
            return new DecodedPatch(Crop.HEMP, CropState.DEAD, 3);
        }
        if (inRange(v, 242, 246)) {
            return new DecodedPatch(Crop.COTTON, CropState.GROWING, v - 242);
        }
        if (inRange(v, 247, 249)) {
            return weeds();
        }
        if (inRange(v, 250, 253)) {
            return new DecodedPatch(Crop.COTTON, CropState.DEAD, v - 249);
        }
        if (inRange(v, 254, 255)) {
            return weeds();
        }
        return null;
    }

    // --- BUSH ---

    /**
     * Berry bushes (redberry, cadavaberry, dwellberry, jangerberry, whiteberry,
     * poison ivy). The check-health states are grouped together at the very top of
     * the range (250-255), so they are open-coded per species; poison ivy has an
     * extra one-off diseased value at 225. Every value 0-255 decodes (no gaps).
     */
    private static DecodedPatch decodeBush(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (v == 4) {
            return weeds();
        }
        if (inRange(v, 5, 9)) {
            return new DecodedPatch(Crop.REDBERRIES, CropState.GROWING, v - 5);
        }
        if (inRange(v, 10, 14)) {
            return new DecodedPatch(Crop.REDBERRIES, CropState.HARVESTABLE, v - 10);
        }
        if (inRange(v, 15, 20)) {
            return new DecodedPatch(Crop.CADAVABERRIES, CropState.GROWING, v - 15);
        }
        if (inRange(v, 21, 25)) {
            return new DecodedPatch(Crop.CADAVABERRIES, CropState.HARVESTABLE, v - 21);
        }
        if (inRange(v, 26, 32)) {
            return new DecodedPatch(Crop.DWELLBERRIES, CropState.GROWING, v - 26);
        }
        if (inRange(v, 33, 37)) {
            return new DecodedPatch(Crop.DWELLBERRIES, CropState.HARVESTABLE, v - 33);
        }
        if (inRange(v, 38, 45)) {
            return new DecodedPatch(Crop.JANGERBERRIES, CropState.GROWING, v - 38);
        }
        if (inRange(v, 46, 50)) {
            return new DecodedPatch(Crop.JANGERBERRIES, CropState.HARVESTABLE, v - 46);
        }
        if (inRange(v, 51, 58)) {
            return new DecodedPatch(Crop.WHITEBERRIES, CropState.GROWING, v - 51);
        }
        if (inRange(v, 59, 63)) {
            return new DecodedPatch(Crop.WHITEBERRIES, CropState.HARVESTABLE, v - 59);
        }
        if (inRange(v, 64, 69)) {
            return weeds();
        }
        if (inRange(v, 70, 74)) {
            return new DecodedPatch(Crop.REDBERRIES, CropState.DISEASED, v - 69);
        }
        if (inRange(v, 75, 79)) {
            return weeds();
        }
        if (inRange(v, 80, 85)) {
            return new DecodedPatch(Crop.CADAVABERRIES, CropState.DISEASED, v - 79);
        }
        if (inRange(v, 86, 90)) {
            return weeds();
        }
        if (inRange(v, 91, 97)) {
            return new DecodedPatch(Crop.DWELLBERRIES, CropState.DISEASED, v - 90);
        }
        if (inRange(v, 98, 102)) {
            return weeds();
        }
        if (inRange(v, 103, 110)) {
            return new DecodedPatch(Crop.JANGERBERRIES, CropState.DISEASED, v - 102);
        }
        if (inRange(v, 111, 115)) {
            return weeds();
        }
        if (inRange(v, 116, 123)) {
            return new DecodedPatch(Crop.WHITEBERRIES, CropState.DISEASED, v - 115);
        }
        if (inRange(v, 124, 133)) {
            return weeds();
        }
        if (inRange(v, 134, 138)) {
            return new DecodedPatch(Crop.REDBERRIES, CropState.DEAD, v - 133);
        }
        if (inRange(v, 139, 143)) {
            return weeds();
        }
        if (inRange(v, 144, 149)) {
            return new DecodedPatch(Crop.CADAVABERRIES, CropState.DEAD, v - 143);
        }
        if (inRange(v, 150, 154)) {
            return weeds();
        }
        if (inRange(v, 155, 161)) {
            return new DecodedPatch(Crop.DWELLBERRIES, CropState.DEAD, v - 154);
        }
        if (inRange(v, 162, 166)) {
            return weeds();
        }
        if (inRange(v, 167, 174)) {
            return new DecodedPatch(Crop.JANGERBERRIES, CropState.DEAD, v - 166);
        }
        if (inRange(v, 175, 179)) {
            return weeds();
        }
        if (inRange(v, 180, 187)) {
            return new DecodedPatch(Crop.WHITEBERRIES, CropState.DEAD, v - 179);
        }
        if (inRange(v, 188, 196)) {
            return weeds();
        }
        if (inRange(v, 197, 204)) {
            return new DecodedPatch(Crop.POISON_IVY, CropState.GROWING, v - 197);
        }
        if (inRange(v, 205, 209)) {
            return new DecodedPatch(Crop.POISON_IVY, CropState.HARVESTABLE, v - 205);
        }
        if (inRange(v, 210, 216)) {
            return new DecodedPatch(Crop.POISON_IVY, CropState.DISEASED, v - 209);
        }
        if (inRange(v, 217, 224)) {
            return new DecodedPatch(Crop.POISON_IVY, CropState.DEAD, v - 216);
        }
        if (v == 225) {
            return new DecodedPatch(Crop.POISON_IVY, CropState.DISEASED, 8);
        }
        if (inRange(v, 226, 249)) {
            return weeds();
        }
        if (v == 250) {
            return new DecodedPatch(Crop.REDBERRIES, CropState.GROWING, Crop.REDBERRIES.getStages() - 1);
        }
        if (v == 251) {
            return new DecodedPatch(Crop.CADAVABERRIES, CropState.GROWING, Crop.CADAVABERRIES.getStages() - 1);
        }
        if (v == 252) {
            return new DecodedPatch(Crop.DWELLBERRIES, CropState.GROWING, Crop.DWELLBERRIES.getStages() - 1);
        }
        if (v == 253) {
            return new DecodedPatch(Crop.JANGERBERRIES, CropState.GROWING, Crop.JANGERBERRIES.getStages() - 1);
        }
        if (v == 254) {
            return new DecodedPatch(Crop.WHITEBERRIES, CropState.GROWING, Crop.WHITEBERRIES.getStages() - 1);
        }
        if (v == 255) {
            return new DecodedPatch(Crop.POISON_IVY, CropState.GROWING, Crop.POISON_IVY.getStages() - 1);
        }
        return null;
    }

    // --- FRUIT_TREE ---

    /**
     * Fruit trees (apple, banana, orange, curry, pineapple, papaya, palm,
     * dragonfruit). Each species has a regular GROWING/HARVESTABLE/DISEASED/DEAD
     * block followed by a stump (HARVESTABLE 0) and a Check-health value; orange
     * has a one-off diseased value at 90. Every value 0-255 decodes (no gaps).
     *
     * <p>The stump and a picked-clean tree both decode as HARVESTABLE stage 0, but they
     * differ: the tree bears fruit again by the next run, and the stump waits for a
     * sapling. Only the bearing block is {@link DecodedPatch#isFellable() fellable}.
     */
    private static DecodedPatch decodeFruitTree(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return weeds();
        }
        if (inRange(v, 8, 13)) {
            return new DecodedPatch(Crop.APPLE, CropState.GROWING, v - 8);
        }
        if (inRange(v, 14, 20)) {
            return new DecodedPatch(Crop.APPLE, CropState.HARVESTABLE, v - 14, true);
        }
        if (inRange(v, 21, 26)) {
            return new DecodedPatch(Crop.APPLE, CropState.DISEASED, v - 20);
        }
        if (inRange(v, 27, 32)) {
            return new DecodedPatch(Crop.APPLE, CropState.DEAD, v - 26);
        }
        if (v == 33) {
            return new DecodedPatch(Crop.APPLE, CropState.HARVESTABLE, 0);
        }
        if (v == 34) {
            return new DecodedPatch(Crop.APPLE, CropState.GROWING, Crop.APPLE.getStages() - 1);
        }
        if (inRange(v, 35, 40)) {
            return new DecodedPatch(Crop.BANANA, CropState.GROWING, v - 35);
        }
        if (inRange(v, 41, 47)) {
            return new DecodedPatch(Crop.BANANA, CropState.HARVESTABLE, v - 41, true);
        }
        if (inRange(v, 48, 53)) {
            return new DecodedPatch(Crop.BANANA, CropState.DISEASED, v - 47);
        }
        if (inRange(v, 54, 59)) {
            return new DecodedPatch(Crop.BANANA, CropState.DEAD, v - 53);
        }
        if (v == 60) {
            return new DecodedPatch(Crop.BANANA, CropState.HARVESTABLE, 0);
        }
        if (v == 61) {
            return new DecodedPatch(Crop.BANANA, CropState.GROWING, Crop.BANANA.getStages() - 1);
        }
        if (inRange(v, 62, 71)) {
            return weeds();
        }
        if (inRange(v, 72, 77)) {
            return new DecodedPatch(Crop.ORANGE, CropState.GROWING, v - 72);
        }
        if (inRange(v, 78, 84)) {
            return new DecodedPatch(Crop.ORANGE, CropState.HARVESTABLE, v - 78, true);
        }
        if (inRange(v, 85, 89)) {
            return new DecodedPatch(Crop.ORANGE, CropState.DISEASED, v - 84);
        }
        if (v == 90) {
            return new DecodedPatch(Crop.ORANGE, CropState.DISEASED, 6);
        }
        if (inRange(v, 91, 96)) {
            return new DecodedPatch(Crop.ORANGE, CropState.DEAD, v - 90);
        }
        if (v == 97) {
            return new DecodedPatch(Crop.ORANGE, CropState.HARVESTABLE, 0);
        }
        if (v == 98) {
            return new DecodedPatch(Crop.ORANGE, CropState.GROWING, Crop.ORANGE.getStages() - 1);
        }
        if (inRange(v, 99, 104)) {
            return new DecodedPatch(Crop.CURRY, CropState.GROWING, v - 99);
        }
        if (inRange(v, 105, 111)) {
            return new DecodedPatch(Crop.CURRY, CropState.HARVESTABLE, v - 105, true);
        }
        if (inRange(v, 112, 117)) {
            return new DecodedPatch(Crop.CURRY, CropState.DISEASED, v - 111);
        }
        if (inRange(v, 118, 123)) {
            return new DecodedPatch(Crop.CURRY, CropState.DEAD, v - 117);
        }
        if (v == 124) {
            return new DecodedPatch(Crop.CURRY, CropState.HARVESTABLE, 0);
        }
        if (v == 125) {
            return new DecodedPatch(Crop.CURRY, CropState.GROWING, Crop.CURRY.getStages() - 1);
        }
        if (inRange(v, 126, 135)) {
            return weeds();
        }
        if (inRange(v, 136, 141)) {
            return new DecodedPatch(Crop.PINEAPPLE, CropState.GROWING, v - 136);
        }
        if (inRange(v, 142, 148)) {
            return new DecodedPatch(Crop.PINEAPPLE, CropState.HARVESTABLE, v - 142, true);
        }
        if (inRange(v, 149, 154)) {
            return new DecodedPatch(Crop.PINEAPPLE, CropState.DISEASED, v - 148);
        }
        if (inRange(v, 155, 160)) {
            return new DecodedPatch(Crop.PINEAPPLE, CropState.DEAD, v - 154);
        }
        if (v == 161) {
            return new DecodedPatch(Crop.PINEAPPLE, CropState.HARVESTABLE, 0);
        }
        if (v == 162) {
            return new DecodedPatch(Crop.PINEAPPLE, CropState.GROWING, Crop.PINEAPPLE.getStages() - 1);
        }
        if (inRange(v, 163, 168)) {
            return new DecodedPatch(Crop.PAPAYA, CropState.GROWING, v - 163);
        }
        if (inRange(v, 169, 175)) {
            return new DecodedPatch(Crop.PAPAYA, CropState.HARVESTABLE, v - 169, true);
        }
        if (inRange(v, 176, 181)) {
            return new DecodedPatch(Crop.PAPAYA, CropState.DISEASED, v - 175);
        }
        if (inRange(v, 182, 187)) {
            return new DecodedPatch(Crop.PAPAYA, CropState.DEAD, v - 181);
        }
        if (v == 188) {
            return new DecodedPatch(Crop.PAPAYA, CropState.HARVESTABLE, 0);
        }
        if (v == 189) {
            return new DecodedPatch(Crop.PAPAYA, CropState.GROWING, Crop.PAPAYA.getStages() - 1);
        }
        if (inRange(v, 190, 199)) {
            return weeds();
        }
        if (inRange(v, 200, 205)) {
            return new DecodedPatch(Crop.PALM, CropState.GROWING, v - 200);
        }
        if (inRange(v, 206, 212)) {
            return new DecodedPatch(Crop.PALM, CropState.HARVESTABLE, v - 206, true);
        }
        if (inRange(v, 213, 218)) {
            return new DecodedPatch(Crop.PALM, CropState.DISEASED, v - 212);
        }
        if (inRange(v, 219, 224)) {
            return new DecodedPatch(Crop.PALM, CropState.DEAD, v - 218);
        }
        if (v == 225) {
            return new DecodedPatch(Crop.PALM, CropState.HARVESTABLE, 0);
        }
        if (v == 226) {
            return new DecodedPatch(Crop.PALM, CropState.GROWING, Crop.PALM.getStages() - 1);
        }
        if (inRange(v, 227, 232)) {
            return new DecodedPatch(Crop.DRAGONFRUIT, CropState.GROWING, v - 227);
        }
        if (inRange(v, 233, 239)) {
            return new DecodedPatch(Crop.DRAGONFRUIT, CropState.HARVESTABLE, v - 233, true);
        }
        if (inRange(v, 240, 245)) {
            return new DecodedPatch(Crop.DRAGONFRUIT, CropState.DISEASED, v - 239);
        }
        if (inRange(v, 246, 251)) {
            return new DecodedPatch(Crop.DRAGONFRUIT, CropState.DEAD, v - 245);
        }
        if (v == 252) {
            return new DecodedPatch(Crop.DRAGONFRUIT, CropState.HARVESTABLE, 0);
        }
        if (v == 253) {
            return new DecodedPatch(Crop.DRAGONFRUIT, CropState.GROWING, Crop.DRAGONFRUIT.getStages() - 1);
        }
        if (inRange(v, 254, 255)) {
            return weeds();
        }
        return null;
    }

    // --- SEAWEED ---

    /** Underwater seaweed patch: a single species with the standard lifecycle. */
    private static DecodedPatch decodeSeaweed(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return new DecodedPatch(Crop.SEAWEED, CropState.GROWING, v - 4);
        }
        if (inRange(v, 8, 10)) {
            return new DecodedPatch(Crop.SEAWEED, CropState.HARVESTABLE, v - 8);
        }
        if (inRange(v, 11, 13)) {
            return new DecodedPatch(Crop.SEAWEED, CropState.DISEASED, v - 10);
        }
        if (inRange(v, 14, 16)) {
            return new DecodedPatch(Crop.SEAWEED, CropState.DEAD, v - 13);
        }
        if (inRange(v, 17, 255)) {
            return weeds();
        }
        return null;
    }

    // --- CACTUS ---

    /** Cactus patch: cactus and potato cactus, each with a Check-health state. */
    private static DecodedPatch decodeCactus(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return weeds();
        }
        if (inRange(v, 8, 14)) {
            return new DecodedPatch(Crop.CACTUS, CropState.GROWING, v - 8);
        }
        if (inRange(v, 15, 18)) {
            return new DecodedPatch(Crop.CACTUS, CropState.HARVESTABLE, v - 15);
        }
        if (inRange(v, 19, 24)) {
            return new DecodedPatch(Crop.CACTUS, CropState.DISEASED, v - 18);
        }
        if (inRange(v, 25, 30)) {
            return new DecodedPatch(Crop.CACTUS, CropState.DEAD, v - 24);
        }
        if (v == 31) {
            return new DecodedPatch(Crop.CACTUS, CropState.GROWING, Crop.CACTUS.getStages() - 1);
        }
        if (inRange(v, 32, 38)) {
            return new DecodedPatch(Crop.POTATO_CACTUS, CropState.GROWING, v - 32);
        }
        if (inRange(v, 39, 45)) {
            return new DecodedPatch(Crop.POTATO_CACTUS, CropState.HARVESTABLE, v - 39);
        }
        if (inRange(v, 46, 51)) {
            return new DecodedPatch(Crop.POTATO_CACTUS, CropState.DISEASED, v - 45);
        }
        if (inRange(v, 52, 57)) {
            return new DecodedPatch(Crop.POTATO_CACTUS, CropState.DEAD, v - 51);
        }
        if (v == 58) {
            return new DecodedPatch(Crop.POTATO_CACTUS, CropState.GROWING, Crop.POTATO_CACTUS.getStages() - 1);
        }
        if (inRange(v, 59, 255)) {
            return weeds();
        }
        return null;
    }

    // --- MUSHROOM ---

    /** Bittercap mushroom patch: a single species with the standard lifecycle. */
    private static DecodedPatch decodeMushroom(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 9)) {
            return new DecodedPatch(Crop.MUSHROOM, CropState.GROWING, v - 4);
        }
        if (inRange(v, 10, 15)) {
            return new DecodedPatch(Crop.MUSHROOM, CropState.HARVESTABLE, v - 10);
        }
        if (inRange(v, 16, 20)) {
            return new DecodedPatch(Crop.MUSHROOM, CropState.DISEASED, v - 15);
        }
        if (inRange(v, 21, 25)) {
            return new DecodedPatch(Crop.MUSHROOM, CropState.DEAD, v - 20);
        }
        if (inRange(v, 26, 255)) {
            return weeds();
        }
        return null;
    }

    // --- BELLADONNA ---

    /** Belladonna patch: a single species with a single harvestable value. */
    private static DecodedPatch decodeBelladonna(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return new DecodedPatch(Crop.BELLADONNA, CropState.GROWING, v - 4);
        }
        if (v == 8) {
            return new DecodedPatch(Crop.BELLADONNA, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 9, 11)) {
            return new DecodedPatch(Crop.BELLADONNA, CropState.DISEASED, v - 8);
        }
        if (inRange(v, 12, 14)) {
            return new DecodedPatch(Crop.BELLADONNA, CropState.DEAD, v - 11);
        }
        if (inRange(v, 15, 255)) {
            return weeds();
        }
        return null;
    }

    // --- CALQUAT ---

    /** Calquat patch: a single fruit tree with a Check-health state. */
    private static DecodedPatch decodeCalquat(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 11)) {
            return new DecodedPatch(Crop.CALQUAT, CropState.GROWING, v - 4);
        }
        if (inRange(v, 12, 18)) {
            return new DecodedPatch(Crop.CALQUAT, CropState.HARVESTABLE, v - 12);
        }
        if (inRange(v, 19, 25)) {
            return new DecodedPatch(Crop.CALQUAT, CropState.DISEASED, v - 18);
        }
        if (inRange(v, 26, 33)) {
            return new DecodedPatch(Crop.CALQUAT, CropState.DEAD, v - 25);
        }
        if (v == 34) {
            return new DecodedPatch(Crop.CALQUAT, CropState.GROWING, Crop.CALQUAT.getStages() - 1);
        }
        if (inRange(v, 35, 255)) {
            return weeds();
        }
        return null;
    }

    // --- CELASTRUS ---

    /**
     * Celastrus patch: a single tree, plus a Check-health value and both a harvested-tree
     * and a stump value that map to HARVESTABLE 0. Values 14-16 hold 1-3 bark (objects
     * 33719, 33718, 33717), so each harvest steps the value down while the decoded stage
     * {@code v - 14} keeps the count-up stock reading {@link DecodedPatch} documents.
     *
     * <p>Only the harvested tree at 17 is {@link DecodedPatch#isFellable() fellable}. The
     * bark-bearing values at 14-16 offer {@code [Harvest]} and no axe option, because the
     * bark must come off before the tree can be chopped. A celastrus in bark is therefore
     * a rooted tree that cannot be felled, the only such case in the catalogue.
     */
    private static DecodedPatch decodeCelastrus(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return weeds();
        }
        if (inRange(v, 8, 12)) {
            return new DecodedPatch(Crop.CELASTRUS, CropState.GROWING, v - 8);
        }
        if (v == 13) {
            return new DecodedPatch(Crop.CELASTRUS, CropState.GROWING, Crop.CELASTRUS.getStages() - 1);
        }
        if (inRange(v, 14, 16)) {
            return new DecodedPatch(Crop.CELASTRUS, CropState.HARVESTABLE, v - 14);
        }
        if (v == 17) {
            // "Harvested Celastrus tree[Chop,Inspect,Guide]" — bark taken, tree still up.
            return new DecodedPatch(Crop.CELASTRUS, CropState.HARVESTABLE, 0, true);
        }
        if (inRange(v, 18, 22)) {
            return new DecodedPatch(Crop.CELASTRUS, CropState.DISEASED, v - 17);
        }
        if (inRange(v, 23, 27)) {
            return new DecodedPatch(Crop.CELASTRUS, CropState.DEAD, v - 22);
        }
        if (v == 28) {
            return new DecodedPatch(Crop.CELASTRUS, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 29, 255)) {
            return weeds();
        }
        return null;
    }

    // --- REDWOOD ---

    /**
     * Redwood patch: a single tree. Beyond the Check-health value at 37, values
     * 38-40 and 56-255 decode to nothing (the game leaves them unused); the second
     * harvestable run (41-55) all reports a chopped tree.
     */
    private static DecodedPatch decodeRedwood(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return weeds();
        }
        if (inRange(v, 8, 17)) {
            return new DecodedPatch(Crop.REDWOOD, CropState.GROWING, v - 8);
        }
        if (v == 18) {
            return new DecodedPatch(Crop.REDWOOD, CropState.HARVESTABLE, 0);
        }
        if (inRange(v, 19, 27)) {
            return new DecodedPatch(Crop.REDWOOD, CropState.DISEASED, v - 18);
        }
        if (inRange(v, 28, 36)) {
            return new DecodedPatch(Crop.REDWOOD, CropState.DEAD, v - 27);
        }
        if (v == 37) {
            return new DecodedPatch(Crop.REDWOOD, CropState.GROWING, Crop.REDWOOD.getStages() - 1);
        }
        if (inRange(v, 41, 55)) {
            return new DecodedPatch(Crop.REDWOOD, CropState.HARVESTABLE, 0);
        }
        return null;
    }

    // --- CRYSTAL_TREE ---

    /**
     * Crystal tree patch: a single tree. Values 4-7 (between weeds and the growing
     * run) and 16-255 decode to nothing.
     */
    private static DecodedPatch decodeCrystalTree(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 8, 13)) {
            return new DecodedPatch(Crop.CRYSTAL_TREE, CropState.GROWING, v - 8);
        }
        if (v == 14) {
            return new DecodedPatch(Crop.CRYSTAL_TREE, CropState.GROWING, Crop.CRYSTAL_TREE.getStages() - 1);
        }
        if (v == 15) {
            return new DecodedPatch(Crop.CRYSTAL_TREE, CropState.HARVESTABLE, 0);
        }
        return null;
    }

    // --- SPIRIT_TREE ---

    /**
     * Spirit tree patch: a single tree. Value 20 is the fully-grown travel state
     * (GROWING at stage 12); values 64-255 decode to nothing.
     */
    private static DecodedPatch decodeSpiritTree(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return weeds();
        }
        if (inRange(v, 8, 19)) {
            return new DecodedPatch(Crop.SPIRIT_TREE, CropState.GROWING, v - 8);
        }
        if (v == 20) {
            return new DecodedPatch(Crop.SPIRIT_TREE, CropState.GROWING, 12);
        }
        if (inRange(v, 21, 31)) {
            return new DecodedPatch(Crop.SPIRIT_TREE, CropState.DISEASED, v - 20);
        }
        if (inRange(v, 32, 43)) {
            return new DecodedPatch(Crop.SPIRIT_TREE, CropState.DEAD, v - 31);
        }
        if (v == 44) {
            return new DecodedPatch(Crop.SPIRIT_TREE, CropState.GROWING, Crop.SPIRIT_TREE.getStages() - 1);
        }
        if (inRange(v, 45, 63)) {
            return weeds();
        }
        return null;
    }

    // --- HESPORI ---

    /** Hespori patch: a single boss-plant. Values 10-255 decode to nothing. */
    private static DecodedPatch decodeHespori(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 6)) {
            return new DecodedPatch(Crop.HESPORI, CropState.GROWING, v - 4);
        }
        if (inRange(v, 7, 8)) {
            return new DecodedPatch(Crop.HESPORI, CropState.HARVESTABLE, v - 7);
        }
        if (v == 9) {
            return weeds();
        }
        return null;
    }

    // --- ANIMA ---

    /**
     * Anima patch: attas, iasor, or kronos. These plants have no harvest or disease
     * encoding. Time Tracking folds each species' whole life into one GROWING run; here the
     * ninth value of each run, the dead plant (objects 33997, 33990 and 34005), decodes
     * DEAD, so a plant that needs clearing reads as one. The eighth, withering, still works.
     */
    private static DecodedPatch decodeAnima(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 3 - v);
        }
        if (inRange(v, 4, 7)) {
            return weeds();
        }
        if (inRange(v, 8, 16)) {
            return anima(Crop.ATTAS, v - 8);
        }
        if (inRange(v, 17, 25)) {
            return anima(Crop.IASOR, v - 17);
        }
        if (inRange(v, 26, 34)) {
            return anima(Crop.KRONOS, v - 26);
        }
        if (inRange(v, 35, 255)) {
            return weeds();
        }
        return null;
    }

    /** The last of an anima plant's nine stages is its death. */
    private static final int ANIMA_DEAD_STAGE = 8;

    private static DecodedPatch anima(Crop plant, int stage) {
        return stage == ANIMA_DEAD_STAGE
                ? new DecodedPatch(plant, CropState.DEAD, stage)
                : new DecodedPatch(plant, CropState.GROWING, stage);
    }

    // --- GRAPES ---

    /**
     * Vinery grapes: a single crop over a compact range. Values 0-1 are the empty
     * (optionally fertilized) trellis, value 10 is the fully-grown state, and
     * 16-255 decode to nothing.
     */
    private static DecodedPatch decodeGrapes(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (inRange(v, 0, 1)) {
            return weeds();
        }
        if (inRange(v, 2, 9)) {
            return new DecodedPatch(Crop.GRAPE, CropState.GROWING, v - 2);
        }
        if (v == 10) {
            return new DecodedPatch(Crop.GRAPE, CropState.GROWING, 7);
        }
        if (inRange(v, 11, 15)) {
            return new DecodedPatch(Crop.GRAPE, CropState.HARVESTABLE, v - 11);
        }
        return null;
    }

    // --- CORAL ---

    /**
     * Underwater coral nursery: elkhorn, pillar, or umbral coral. Unusually, the
     * empty nursery (0-3) reports weeds at stage 3 rather than a rake countdown,
     * and the trailing filler (37-255) reports weeds at stage 0. Each coral's
     * "collect" state is encoded as GROWING at stage 4, not a HARVESTABLE value.
     */
    private static DecodedPatch decodeCoral(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v <= 3) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, WEEDS_STAGE);
        }
        if (inRange(v, 4, 7)) {
            return new DecodedPatch(Crop.ELKHORN_CORAL, CropState.GROWING, v - 4);
        }
        if (v == 8) {
            return new DecodedPatch(Crop.ELKHORN_CORAL, CropState.GROWING, 4);
        }
        if (inRange(v, 9, 11)) {
            return new DecodedPatch(Crop.ELKHORN_CORAL, CropState.DISEASED, v - 8);
        }
        if (inRange(v, 12, 14)) {
            return new DecodedPatch(Crop.ELKHORN_CORAL, CropState.DEAD, v - 11);
        }
        if (inRange(v, 15, 18)) {
            return new DecodedPatch(Crop.PILLAR_CORAL, CropState.GROWING, v - 15);
        }
        if (v == 19) {
            return new DecodedPatch(Crop.PILLAR_CORAL, CropState.GROWING, 4);
        }
        if (inRange(v, 20, 22)) {
            return new DecodedPatch(Crop.PILLAR_CORAL, CropState.DISEASED, v - 19);
        }
        if (inRange(v, 23, 25)) {
            return new DecodedPatch(Crop.PILLAR_CORAL, CropState.DEAD, v - 22);
        }
        if (inRange(v, 26, 29)) {
            return new DecodedPatch(Crop.UMBRAL_CORAL, CropState.GROWING, v - 26);
        }
        if (v == 30) {
            return new DecodedPatch(Crop.UMBRAL_CORAL, CropState.GROWING, 4);
        }
        if (inRange(v, 31, 33)) {
            return new DecodedPatch(Crop.UMBRAL_CORAL, CropState.DISEASED, v - 30);
        }
        if (inRange(v, 34, 36)) {
            return new DecodedPatch(Crop.UMBRAL_CORAL, CropState.DEAD, v - 33);
        }
        if (inRange(v, 37, 255)) {
            return new DecodedPatch(Crop.WEEDS, CropState.GROWING, 0);
        }
        return null;
    }

    // --- COMPOST ---

    /**
     * Standard compost bin (Ardougne, Catherby, Civitas illa Fortis, Falador,
     * Hosidius, Canifis, Prifddinas). Unlike a crop patch, the byte encodes how the
     * bin is being used: EMPTY is the untouched bin, FILLING counts up the produce
     * dropped in (stage = item count - 1), a GROWING run is the closed bin
     * converting its contents, and HARVESTABLE counts the collectable buckets left.
     * Compost, supercompost, and rotten tomatoes each share the same 15-dose bin;
     * ultracompost is added ready-made so it only ever appears HARVESTABLE. Large
     * runs of the 0-255 space decode to nothing.
     */
    private static DecodedPatch decodeCompost(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v == 0) {
            return new DecodedPatch(Crop.EMPTY_COMPOST_BIN, CropState.EMPTY, 0);
        }
        if (inRange(v, 1, 15)) {
            return new DecodedPatch(Crop.COMPOST, CropState.FILLING, v - 1);
        }
        if (inRange(v, 16, 30)) {
            return new DecodedPatch(Crop.COMPOST, CropState.HARVESTABLE, v - 16);
        }
        if (inRange(v, 31, 32)) {
            return new DecodedPatch(Crop.COMPOST, CropState.GROWING, v - 31);
        }
        if (inRange(v, 33, 47)) {
            return new DecodedPatch(Crop.SUPERCOMPOST, CropState.FILLING, v - 33);
        }
        if (inRange(v, 48, 62)) {
            return new DecodedPatch(Crop.SUPERCOMPOST, CropState.HARVESTABLE, v - 48);
        }
        if (v == 94) {
            return new DecodedPatch(Crop.COMPOST, CropState.GROWING, Crop.COMPOST.getStages() - 1);
        }
        if (inRange(v, 95, 96)) {
            return new DecodedPatch(Crop.SUPERCOMPOST, CropState.GROWING, v - 95);
        }
        if (v == 126) {
            return new DecodedPatch(Crop.SUPERCOMPOST, CropState.GROWING, Crop.SUPERCOMPOST.getStages() - 1);
        }
        if (inRange(v, 129, 143)) {
            return new DecodedPatch(Crop.ROTTEN_TOMATO, CropState.FILLING, v - 129);
        }
        if (inRange(v, 144, 158)) {
            return new DecodedPatch(Crop.ROTTEN_TOMATO, CropState.HARVESTABLE, v - 144);
        }
        if (inRange(v, 159, 160)) {
            return new DecodedPatch(Crop.ROTTEN_TOMATO, CropState.GROWING, v - 159);
        }
        if (inRange(v, 176, 190)) {
            return new DecodedPatch(Crop.ULTRACOMPOST, CropState.HARVESTABLE, v - 176);
        }
        return null;
    }

    // --- BIG_COMPOST ---

    /**
     * The Farming Guild's big compost bin. Same lifecycle as the standard bin but
     * holds 30 collectable doses, so the FILLING and HARVESTABLE runs are split
     * across two byte blocks whose second half continues the count from 15 (e.g.
     * compost fills at 1-15 then 63-77, and is collected at 16-30 then 78-92).
     * As with the small bin, large stretches of 0-255 decode to nothing.
     */
    private static DecodedPatch decodeBigCompost(int v) {
        if (v < 0 || v > 255) {
            return null;
        }
        if (v == 0) {
            return new DecodedPatch(Crop.EMPTY_BIG_COMPOST_BIN, CropState.EMPTY, 0);
        }
        if (inRange(v, 1, 15)) {
            return new DecodedPatch(Crop.BIG_COMPOST, CropState.FILLING, v - 1);
        }
        if (inRange(v, 16, 30)) {
            return new DecodedPatch(Crop.BIG_COMPOST, CropState.HARVESTABLE, v - 16);
        }
        if (inRange(v, 33, 47)) {
            return new DecodedPatch(Crop.BIG_SUPERCOMPOST, CropState.FILLING, v - 33);
        }
        if (inRange(v, 48, 62)) {
            return new DecodedPatch(Crop.BIG_SUPERCOMPOST, CropState.HARVESTABLE, v - 48);
        }
        if (inRange(v, 63, 77)) {
            return new DecodedPatch(Crop.BIG_COMPOST, CropState.FILLING, 15 + v - 63);
        }
        if (inRange(v, 78, 92)) {
            return new DecodedPatch(Crop.BIG_COMPOST, CropState.HARVESTABLE, 15 + v - 78);
        }
        if (v == 93) {
            return new DecodedPatch(Crop.BIG_COMPOST, CropState.GROWING, Crop.BIG_COMPOST.getStages() - 1);
        }
        if (inRange(v, 97, 99)) {
            return new DecodedPatch(Crop.BIG_SUPERCOMPOST, CropState.GROWING, v - 97);
        }
        if (inRange(v, 100, 114)) {
            return new DecodedPatch(Crop.BIG_SUPERCOMPOST, CropState.HARVESTABLE, 15 + v - 100);
        }
        if (inRange(v, 127, 128)) {
            return new DecodedPatch(Crop.BIG_COMPOST, CropState.GROWING, v - 127);
        }
        if (inRange(v, 129, 143)) {
            return new DecodedPatch(Crop.BIG_ROTTEN_TOMATO, CropState.FILLING, v - 129);
        }
        if (inRange(v, 144, 158)) {
            return new DecodedPatch(Crop.BIG_ROTTEN_TOMATO, CropState.HARVESTABLE, v - 144);
        }
        if (inRange(v, 159, 160)) {
            return new DecodedPatch(Crop.BIG_ROTTEN_TOMATO, CropState.GROWING, v - 159);
        }
        if (inRange(v, 161, 175)) {
            return new DecodedPatch(Crop.BIG_SUPERCOMPOST, CropState.FILLING, 15 + v - 161);
        }
        if (inRange(v, 176, 205)) {
            return new DecodedPatch(Crop.BIG_ULTRACOMPOST, CropState.HARVESTABLE, v - 176);
        }
        if (inRange(v, 207, 221)) {
            return new DecodedPatch(Crop.BIG_ROTTEN_TOMATO, CropState.HARVESTABLE, 15 + v - 207);
        }
        if (v == 222) {
            return new DecodedPatch(Crop.BIG_ROTTEN_TOMATO, CropState.GROWING, Crop.BIG_ROTTEN_TOMATO.getStages() - 1);
        }
        if (inRange(v, 223, 237)) {
            return new DecodedPatch(Crop.BIG_ROTTEN_TOMATO, CropState.FILLING, 15 + v - 223);
        }
        return null;
    }
}
