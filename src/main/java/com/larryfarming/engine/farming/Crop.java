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

import com.larryfarming.data.Tables;
import java.util.EnumSet;
import java.util.Set;

/**
 * A crop that can occupy a tracked farming patch: every plantable species across
 * all patch types (allotment, flower, bush, hops, herb, tree, fruit tree, cactus,
 * hardwood, anima, coral, and the one-off special patches), plus WEEDS (the
 * unplanted state) and ANYHERB (the game collapses all dead herbs into one varbit
 * range, so the species is unknowable).
 *
 * <p>Growth constants mirror RuneLite's Time Tracking {@code Produce} table and come from
 * the crops table, one row per constant: a crop advances one growth stage per farming
 * tick and finishes growing after {@code (stages - 1) * tickRateMinutes} minutes. Once
 * HARVESTABLE, some patch types (bushes, fruit trees, cacti, and a few specials) keep
 * producing: they hold {@code harvestStages} pickings and regrow one every
 * {@code regrowTickRateMinutes} minutes. A {@code regrowTickRateMinutes} of 0 means the
 * crop does not regrow. The item ID is only used for the sidebar row icon.
 */
public enum Crop {
    /** Tick rate 0: weeds regrow, but a weedy patch has no timer worth showing. */
    WEEDS,

    // Allotment crops.
    POTATO,
    ONION,
    CABBAGE,
    TOMATO,
    SWEETCORN,
    STRAWBERRY,
    WATERMELON,
    SNAPE_GRASS,

    // Flower crops.
    MARIGOLD,
    ROSEMARY,
    NASTURTIUM,
    WOAD,
    LIMPWURT,
    WHITE_LILY,
    /** Occupies the flower patch; guards the adjacent allotments rather than being harvested. */
    SCARECROW,

    // Bush crops.
    REDBERRIES,
    CADAVABERRIES,
    DWELLBERRIES,
    JANGERBERRIES,
    WHITEBERRIES,
    POISON_IVY,

    // Hop crops.
    BARLEY,
    HAMMERSTONE,
    ASGARNIAN,
    JUTE,
    YANILLIAN,
    FLAX,
    KRANDORIAN,
    WILDBLOOD,
    HEMP,
    COTTON,

    // Herbs, in patch-varbit order.
    GUAM,
    MARRENTILL,
    TARROMIN,
    HARRALANDER,
    RANARR,
    TOADFLAX,
    IRIT,
    AVANTOE,
    HUASCA,
    KWUARM,
    SNAPDRAGON,
    CADANTINE,
    LANTADYME,
    DWARF_WEED,
    TORSTOL,
    GOUTWEED,
    /** Dead herbs share one varbit range; the species is lost. */
    ANYHERB,

    // Trees.
    OAK,
    WILLOW,
    MAPLE,
    YEW,
    MAGIC,

    // Fruit trees.
    APPLE,
    BANANA,
    ORANGE,
    CURRY,
    PINEAPPLE,
    PAPAYA,
    PALM,
    DRAGONFRUIT,

    // Cactus.
    CACTUS,
    POTATO_CACTUS,

    // Hardwood trees.
    TEAK,
    MAHOGANY,
    CAMPHOR,
    IRONWOOD,
    ROSEWOOD,

    // Anima.
    ATTAS,
    IASOR,
    KRONOS,

    // Coral (Sailing).
    ELKHORN_CORAL,
    PILLAR_CORAL,
    UMBRAL_CORAL,

    // Special single-patch crops.
    SEAWEED,
    GRAPE,
    MUSHROOM,
    BELLADONNA,
    CALQUAT,
    SPIRIT_TREE,
    CELASTRUS,
    REDWOOD,
    HESPORI,
    CRYSTAL_TREE,

    // Compost bins. The empty-bin entries stand in for Time Tracking's EMPTY state and are
    // never timed.
    EMPTY_COMPOST_BIN,
    COMPOST,
    SUPERCOMPOST,
    ULTRACOMPOST,
    ROTTEN_TOMATO,
    EMPTY_BIG_COMPOST_BIN,
    BIG_COMPOST,
    BIG_SUPERCOMPOST,
    BIG_ULTRACOMPOST,
    BIG_ROTTEN_TOMATO;

    static {
        Rows.TABLE.requireAll(values());
    }

    private final String displayName;
    private final int itemId;
    private final int tickRateMinutes;
    private final int stages;
    private final int regrowTickRateMinutes;
    private final int harvestStages;

    Crop() {
        Tables.Row row = Rows.TABLE.of(name());
        this.displayName = row.text(1);
        this.itemId = row.integer(2);
        this.tickRateMinutes = row.integer(4);
        this.stages = row.integer(5);
        this.regrowTickRateMinutes = row.integer(6);
        this.harvestStages = row.integer(7);
    }

    /** The crops table, held apart so the constructors can read it; see {@link Tables#byName}. */
    private static final class Rows {
        static final Tables.Named TABLE = Tables.byName("/com/larryfarming/crops.tsv", 8);
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
     * these has a HARVESTABLE value in {@link PatchType}.
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

    private static final Set<Crop> NEVER_PICKED = EnumSet.of(WEEDS, ANYHERB, SCARECROW, SPIRIT_TREE, ATTAS, IASOR, KRONOS,
            EMPTY_COMPOST_BIN, EMPTY_BIG_COMPOST_BIN);
}
