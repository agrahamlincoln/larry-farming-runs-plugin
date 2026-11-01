/*
 * Copyright (c) 2019, Hydrox6 <ikada@protonmail.ch>
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
package com.larryfarming.farming.catalogue;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import java.util.*;
import lombok.Getter;

/**
 * What it takes to plant each crop the runs plant: the seed to carry, the Farming
 * level to plant it, the patch it goes in, and the tag that tells one seed from another
 * at a glance. One row of the crop data table per crop.
 *
 * <p>{@link Crop} mirrors RuneLite's table (its {@code itemId} is the produce, not the
 * seed), so the columns this plugin adds live here to keep that mirror diffable against
 * upstream.
 *
 * <p>Tree-family crops carry their potted-stage ids (seedling, watered seedling,
 * sapling) beside the seed. Crops planted straight from seed leave those at zero, which
 * {@link #isSaplingPlanted()} reads.
 */
@Getter
public final class CropData {

    private static final List<CropData> ALL = load();

    private final Crop crop;
    private final int seedItemId;
    private final int seedlingItemId;
    private final int wateredSeedlingItemId;
    private final int saplingItemId;
    private final int farmingLevel;
    private final PatchType patchType;
    private final String tag;
    private final String seedName;

    private CropData(Tables.Row row) {
        this.crop = row.constant(Crop.class, 0);
        this.farmingLevel = row.integer(1);
        this.patchType = row.constant(PatchType.class, 2);
        this.tag = row.text(3);
        this.seedName = row.text(4);
        this.seedItemId = row.integer(5);
        this.seedlingItemId = optionalId(row, 7);
        this.wateredSeedlingItemId = optionalId(row, 9);
        this.saplingItemId = optionalId(row, 11);
    }

    private static int optionalId(Tables.Row row, int column) {
        return row.text(column).isEmpty() ? 0 : row.integer(column);
    }

    private static List<CropData> load() {
        List<CropData> rows = new ArrayList<>();
        Set<Crop> seen = EnumSet.noneOf(Crop.class);
        for (Tables.Row row : Tables.read("/com/larryfarming/crop-data.tsv", 14)) {
            CropData data = new CropData(row);
            if (!seen.add(data.crop)) {
                throw row.error("a second row for " + data.crop);
            }
            rows.add(data);
        }
        return List.copyOf(rows);
    }

    /** Every row, in the table's order. */
    public static List<CropData> values() {
        return ALL;
    }

    /** "Oak sapling", or null for a crop planted straight from its seed. */
    public String getSaplingName() {
        return isSaplingPlanted() ? crop.getDisplayName() + " sapling" : null;
    }

    /** "Oak seedling", or null for a crop planted straight from its seed. */
    public String getSeedlingName() {
        return isSaplingPlanted() ? crop.getDisplayName() + " seedling" : null;
    }

    /** True when this crop is planted as a sapling rather than straight from the seed. */
    public boolean isSaplingPlanted() {
        return saplingItemId != 0;
    }

    /**
     * Every form this crop's planting stock comes in, earliest first.
     *
     * <p>Any of the four forms can fill the patch, so a player midway through potting a
     * stack still counts as holding planting stock.
     */
    public List<Integer> getPlantingForms() {
        if (!isSaplingPlanted()) {
            return List.of(seedItemId);
        }
        return List.of(seedItemId, seedlingItemId, wateredSeedlingItemId, saplingItemId);
    }

    /**
     * How many seeds one patch of this crop swallows.
     *
     * <p>Three for an allotment, one for everything else.
     */
    public int getSeedsPerPatch() {
        return seedsPerPatch(patchType);
    }

    /**
     * The one-to-five letter tag drawn over the seed, or "" for the crops that do not
     * take one.
     *
     * <p>Herb seeds and sapling-planted crops only: herb seeds differ by a few pixels of
     * colour and the saplings share one pot sprite. Everything else is distinguishable at a
     * glance, so a tag there is clutter. This also keeps snape grass ("SNA") from reading
     * like snapdragon ("S").
     */
    public String getTag() {
        return patchType == PatchType.HERB || isSaplingPlanted() ? tag : "";
    }

    /**
     * The tag as RuneLite's Item Identification writes it, for every crop. {@link #getTag}
     * draws it only where a bag needs it; a lone square in a settings grid always does.
     */
    public String getStoredTag() {
        return tag;
    }

    /**
     * The tag for any form of a crop's planting stock, or empty for anything else. Every
     * potted stage is tagged because all of them share the same pot sprite.
     */
    public static String tagForPlantable(int itemId) {
        // Seed-planted crops store 0 in their potted columns, so 0 must not match.
        if (itemId <= 0) {
            return "";
        }
        for (CropData data : ALL) {
            if (data.getPlantingForms().contains(itemId)) {
                return data.getTag();
            }
        }
        return "";
    }

    /**
     * Every crop that goes in this kind of patch, best first.
     *
     * <p>A fact about the patch with no route opinion; the player's ticks are applied by
     * {@link com.larryfarming.farming.model.FarmingPreferences#ticked}.
     *
     * <p>Best-first means descending level, a true ranking only for the tree families.
     * Herbs rank by the seed policy's tier list, and the other families are not rankings.
     *
     * <p>Unfiltered by the player's level; callers apply that.
     */
    public static List<Crop> plantableIn(PatchType patchType) {
        return BY_PATCH_TYPE.getOrDefault(patchType, List.of());
    }

    /** Computed once: every planting stop asks this on every snapshot rebuild. */
    private static final Map<PatchType, List<Crop>> BY_PATCH_TYPE = indexByPatchType();

    private static Map<PatchType, List<Crop>> indexByPatchType() {
        Map<PatchType, List<CropData>> matching = new EnumMap<>(PatchType.class);
        for (CropData data : ALL) {
            matching.computeIfAbsent(data.patchType, type -> new ArrayList<>()).add(data);
        }
        Map<PatchType, List<Crop>> index = new EnumMap<>(PatchType.class);
        for (Map.Entry<PatchType, List<CropData>> entry : matching.entrySet()) {
            List<CropData> rows = entry.getValue();
            rows.sort(Comparator.comparingInt(CropData::getFarmingLevel).reversed());
            List<Crop> crops = new ArrayList<>(rows.size());
            for (CropData data : rows) {
                crops.add(data.crop);
            }
            index.put(entry.getKey(), List.copyOf(crops));
        }
        return Collections.unmodifiableMap(index);
    }

    /**
     * How many seeds one patch of this kind swallows. Three for an allotment, one for
     * everything else.
     */
    public static int seedsPerPatch(PatchType patchType) {
        return patchType == PatchType.ALLOTMENT ? 3 : 1;
    }

    /**
     * Whether a seed box would take this item.
     *
     * <p>The box holds seeds only. Seedlings and saplings are not seeds, so each takes its
     * own inventory slot however many boxes the player carries. Asked of the item, not the
     * crop: an acorn boxes, an oak sapling does not.
     *
     * <p>Mushroom spores box by a game exception and are covered by the mushroom row.
     */
    public static boolean boxesAsAseed(int itemId) {
        return itemId != 0 && SEED_ITEM_IDS.contains(itemId);
    }

    /** Computed once: this is asked per carry line. */
    private static final Set<Integer> SEED_ITEM_IDS = seedItemIds();

    private static Set<Integer> seedItemIds() {
        Set<Integer> ids = new HashSet<>();
        for (CropData data : ALL) {
            ids.add(data.seedItemId);
        }
        return Collections.unmodifiableSet(ids);
    }

    /** The row for a crop, or null if no run plants it. */
    public static CropData forCrop(Crop crop) {
        return BY_CROP.get(crop);
    }

    private static final Map<Crop, CropData> BY_CROP = byCrop();

    private static Map<Crop, CropData> byCrop() {
        Map<Crop, CropData> rows = new EnumMap<>(Crop.class);
        for (CropData data : ALL) {
            rows.put(data.crop, data);
        }
        return Collections.unmodifiableMap(rows);
    }
}
