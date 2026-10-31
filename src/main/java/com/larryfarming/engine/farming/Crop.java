/*
 * Copyright (c) 2018 Abex
 * Copyright (c) 2018, NotFoxtrot <https://github.com/NotFoxtrot>
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

import java.util.EnumSet;
import java.util.Set;

/**
 * A crop that can occupy a tracked farming patch: every plantable species across
 * all patch types (allotment, flower, bush, hops, herb, tree, fruit tree, cactus,
 * hardwood, anima, coral, and the one-off special patches), plus WEEDS (the
 * unplanted state) and ANYHERB (the game collapses all dead herbs into one varbit
 * range, so the species is unknowable).
 *
 * <p>Growth constants mirror RuneLite's Time Tracking {@code Produce} table:
 * a crop advances one growth stage per farming tick and finishes growing after
 * {@code (stages - 1) * tickRateMinutes} minutes. Once HARVESTABLE, some patch
 * types (bushes, fruit trees, cacti, and a few specials) keep producing: they
 * hold {@code harvestStages} pickings and regrow one every
 * {@code regrowTickRateMinutes} minutes. A {@code regrowTickRateMinutes} of 0
 * means the crop does not regrow. The item ID is only used for the sidebar row
 * icon.
 */
public enum Crop {
    /** Tick rate 0: weeds regrow, but a weedy patch has no timer worth showing. */
    WEEDS("Weeds", 6055, 0, 4, 0, 4),

    // Allotment crops: 10-minute grow ticks; single harvest of 3 lives, no regrow.
    POTATO("Potato", 1942, 10, 5, 0, 3),
    ONION("Onion", 1957, 10, 5, 0, 3),
    CABBAGE("Cabbage", 1965, 10, 5, 0, 3),
    TOMATO("Tomato", 1982, 10, 5, 0, 3),
    SWEETCORN("Sweetcorn", 5986, 10, 7, 0, 3),
    STRAWBERRY("Strawberry", 5504, 10, 7, 0, 3),
    WATERMELON("Watermelon", 5982, 10, 9, 0, 3),
    SNAPE_GRASS("Snape grass", 231, 10, 8, 0, 3),

    // Flower crops: 5-minute grow ticks; picked once, do not regrow.
    MARIGOLD("Marigold", 6010, 5, 5),
    ROSEMARY("Rosemary", 6014, 5, 5),
    NASTURTIUM("Nasturtium", 6012, 5, 5),
    WOAD("Woad", 1793, 5, 5),
    LIMPWURT("Limpwurt", 225, 5, 5),
    WHITE_LILY("White lily", 22932, 5, 5),
    /** Occupies the flower patch; guards the adjacent allotments rather than being harvested. */
    SCARECROW("Scarecrow", 6059, 5, 4),

    // Bush crops: 20-minute grow and regrow ticks; regrow up to 5 berry pickings.
    REDBERRIES("Redberry", 1951, 20, 6, 20, 5),
    CADAVABERRIES("Cadavaberry", 753, 20, 7, 20, 5),
    DWELLBERRIES("Dwellberry", 2126, 20, 8, 20, 5),
    JANGERBERRIES("Jangerberry", 247, 20, 9, 20, 5),
    WHITEBERRIES("Whiteberry", 239, 20, 9, 20, 5),
    POISON_IVY("Poison ivy", 6018, 20, 9, 20, 5),

    // Hop crops: single harvest of 3 lives, no regrow.
    BARLEY("Barley", 6006, 10, 5, 0, 3),
    HAMMERSTONE("Hammerstone", 5994, 10, 5, 0, 3),
    ASGARNIAN("Asgarnian", 5996, 10, 6, 0, 3),
    JUTE("Jute", 5931, 10, 6, 0, 3),
    YANILLIAN("Yanillian", 5998, 10, 7, 0, 3),
    FLAX("Flax", 1779, 20, 4, 0, 3),
    KRANDORIAN("Krandorian", 6000, 10, 8, 0, 3),
    WILDBLOOD("Wildblood", 6002, 10, 9, 0, 3),
    HEMP("Hemp", 31457, 20, 5, 0, 3),
    COTTON("Cotton", 31460, 20, 6, 0, 3),

    // Herbs: 80 minutes (4 ticks of 20), in patch-varbit order; single harvest of 3 lives.
    GUAM("Guam", 249, 20, 5, 0, 3),
    MARRENTILL("Marrentill", 251, 20, 5, 0, 3),
    TARROMIN("Tarromin", 253, 20, 5, 0, 3),
    HARRALANDER("Harralander", 255, 20, 5, 0, 3),
    RANARR("Ranarr", 257, 20, 5, 0, 3),
    TOADFLAX("Toadflax", 2998, 20, 5, 0, 3),
    IRIT("Irit", 259, 20, 5, 0, 3),
    AVANTOE("Avantoe", 261, 20, 5, 0, 3),
    HUASCA("Huasca", 30097, 20, 5, 0, 3),
    KWUARM("Kwuarm", 263, 20, 5, 0, 3),
    SNAPDRAGON("Snapdragon", 3000, 20, 5, 0, 3),
    CADANTINE("Cadantine", 265, 20, 5, 0, 3),
    LANTADYME("Lantadyme", 2481, 20, 5, 0, 3),
    DWARF_WEED("Dwarf weed", 267, 20, 5, 0, 3),
    TORSTOL("Torstol", 269, 20, 5, 0, 3),
    GOUTWEED("Goutweed", 3261, 20, 5, 0, 2),
    /** Dead herbs share one varbit range; the species is lost. */
    ANYHERB("Herb", 249, 20, 5, 0, 3),

    // Trees: 40-minute ticks; stage count varies by species; harvested once.
    OAK("Oak", 1521, 40, 5),
    WILLOW("Willow", 1519, 40, 7),
    MAPLE("Maple", 1517, 40, 9),
    YEW("Yew", 1515, 40, 11),
    MAGIC("Magic", 1513, 40, 13),

    // Fruit trees: 160-minute grow ticks; regrow a fruit every 45 minutes.
    APPLE("Apple", 1955, 160, 7, 45, 7),
    BANANA("Banana", 1963, 160, 7, 45, 7),
    ORANGE("Orange", 2108, 160, 7, 45, 7),
    CURRY("Curry", 5970, 160, 7, 45, 7),
    PINEAPPLE("Pineapple", 2114, 160, 7, 45, 7),
    PAPAYA("Papaya", 5972, 160, 7, 45, 7),
    PALM("Palm", 5974, 160, 7, 45, 7),
    DRAGONFRUIT("Dragonfruit", 22929, 160, 7, 45, 7),

    // Cactus: spines/potatoes regrow after the first harvest.
    CACTUS("Cactus", 6016, 80, 8, 20, 4),
    POTATO_CACTUS("Potato cactus", 3138, 10, 8, 5, 7),

    // Hardwood trees: 640-minute ticks; harvested once.
    TEAK("Teak", 6333, 640, 8),
    MAHOGANY("Mahogany", 6332, 640, 9),
    CAMPHOR("Camphor", 32904, 640, 9),
    IRONWOOD("Ironwood", 32907, 640, 9),
    ROSEWOOD("Rosewood", 32910, 640, 10),

    // Anima: 640-minute ticks; harvested once.
    ATTAS("Attas", 22940, 640, 9),
    IASOR("Iasor", 22939, 640, 9),
    KRONOS("Kronos", 22938, 640, 9),

    // Coral (Sailing): 40-minute ticks; harvested once.
    ELKHORN_CORAL("Elkhorn", 31481, 40, 5),
    PILLAR_CORAL("Pillar", 31484, 40, 5),
    UMBRAL_CORAL("Umbral", 31487, 40, 5),

    // Special single-patch crops; regrow only where Time Tracking's table gives one.
    SEAWEED("Seaweed", 21504, 10, 5, 0, 4),
    GRAPE("Grape", 1987, 5, 8, 0, 5),
    MUSHROOM("Mushroom", 6004, 40, 7, 0, 7),
    BELLADONNA("Belladonna", 2398, 80, 5),
    CALQUAT("Calquat", 5980, 160, 9, 0, 7),
    SPIRIT_TREE("Spirit tree", 6063, 320, 13),
    CELASTRUS("Celastrus", 1391, 160, 6, 0, 4),
    REDWOOD("Redwood", 19669, 640, 11),
    HESPORI("Hespori", 23044, 640, 4, 0, 2),
    CRYSTAL_TREE("Crystal tree", 23866, 80, 7),

    // Compost bins: a closed bin converts over 40-minute ticks into collectable
    // buckets (15, or 30 in the big Farming Guild bin). The empty-bin entries stand
    // in for Time Tracking's EMPTY state and are never timed; they borrow the
    // compost-bucket icon (6032) because Time Tracking's placeholder item has no
    // usable sprite. Ultracompost is added ready-made, so its tick rate is 0.
    EMPTY_COMPOST_BIN("Empty bin", 6032, 0, 1, 0, 0),
    COMPOST("Compost", 6032, 40, 3, 0, 15),
    SUPERCOMPOST("Supercompost", 6034, 40, 3, 0, 15),
    ULTRACOMPOST("Ultracompost", 21483, 0, 3, 0, 15),
    ROTTEN_TOMATO("Rotten tomato", 2518, 40, 3, 0, 15),
    EMPTY_BIG_COMPOST_BIN("Empty bin", 6032, 0, 1, 0, 0),
    BIG_COMPOST("Compost", 6032, 40, 3, 0, 30),
    BIG_SUPERCOMPOST("Supercompost", 6034, 40, 3, 0, 30),
    BIG_ULTRACOMPOST("Ultracompost", 21483, 0, 3, 0, 30),
    BIG_ROTTEN_TOMATO("Rotten tomato", 2518, 40, 3, 0, 30);

    private final String displayName;
    private final int itemId;
    private final int tickRateMinutes;
    private final int stages;
    private final int regrowTickRateMinutes;
    private final int harvestStages;

    Crop(String displayName, int itemId, int tickRateMinutes, int stages,
            int regrowTickRateMinutes, int harvestStages) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.tickRateMinutes = tickRateMinutes;
        this.stages = stages;
        this.regrowTickRateMinutes = regrowTickRateMinutes;
        this.harvestStages = harvestStages;
    }

    /** Convenience form for crops that are harvested once and never regrow. */
    Crop(String displayName, int itemId, int tickRateMinutes, int stages) {
        this(displayName, itemId, tickRateMinutes, stages, 0, 1);
    }

    public String getDisplayName() { return displayName; }
    public int getItemId() { return itemId; }
    public int getTickRateMinutes() { return tickRateMinutes; }
    public int getStages() { return stages; }

    /** Minutes between regrown pickings while HARVESTABLE; 0 if the crop does not regrow. */
    public int getRegrowTickRateMinutes() { return regrowTickRateMinutes; }

    /** Number of harvest "lives" (berry/fruit pickings) once the crop is HARVESTABLE. */
    public int getHarvestStages() { return harvestStages; }

    /** Total wall-clock grow time from planting to done, in seconds. */
    public long totalGrowSeconds() {
        return (long) (stages - 1) * tickRateMinutes * 60L;
    }

    /**
     * Whether there is ever anything on this to pick.
     *
     * <p>False for everything that occupies a patch without producing: scarecrow, anima
     * plants, spirit tree, empty compost bins, weeds, and the unknown dead herb. None of
     * these has a HARVESTABLE value in {@link PatchType}, and the list must stay that way.
     *
     * <p>Coral is produce although it has no HARVESTABLE value either: the game reports
     * grown coral as its last growing stage, and the player collects it there.
     *
     * <p>The tracker never predicts a crop forward into HARVESTABLE, so an expired timer is
     * the only "ready" signal a stale patch gives. A scarecrow's timer expires exactly like
     * a herb's, and this method tells the two apart.
     */
    public boolean isProduce() {
        return !NEVER_PICKED.contains(this);
    }

    private static final Set<Crop> NEVER_PICKED = EnumSet.of(
            WEEDS, ANYHERB, SCARECROW, SPIRIT_TREE, ATTAS, IASOR, KRONOS,
            EMPTY_COMPOST_BIN, EMPTY_BIG_COMPOST_BIN);
}
