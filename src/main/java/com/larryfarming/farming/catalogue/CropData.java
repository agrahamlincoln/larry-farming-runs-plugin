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

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What it takes to plant each crop the runs plant: the seed to carry, the Farming
 * level to plant it, the patch it goes in, and the tag that tells one seed from another
 * at a glance.
 *
 * <p>{@link Crop} mirrors RuneLite's table (its {@code itemId} is the produce, not the
 * seed), so the columns this plugin adds live here to keep that mirror diffable against
 * upstream.
 *
 * <p>Every constant is named for its {@link Crop}, so a shifted row shows by name.
 *
 * <p>Levels are the real requirement. Where a boosted figure is quoted, the boost
 * belongs in the advice, not in the gate.
 *
 * <p>Tree-family crops carry their potted-stage ids (seedling, watered seedling,
 * sapling) beside the seed. Crops planted straight from seed leave those columns at zero,
 * which {@link #isSaplingPlanted()} reads.
 *
 * <p>Tags are copied from RuneLite's Item Identification plugin so the sidebar matches
 * the inventory labels. That plugin keeps its table package-private, so they cannot be
 * imported.
 */
public enum CropData {

    // Herbs.
    GUAM(Crop.GUAM, 5291, 9, PatchType.HERB, "G", "Guam seed"),
    MARRENTILL(Crop.MARRENTILL, 5292, 14, PatchType.HERB, "M", "Marrentill seed"),
    TARROMIN(Crop.TARROMIN, 5293, 19, PatchType.HERB, "TAR", "Tarromin seed"),
    HARRALANDER(Crop.HARRALANDER, 5294, 26, PatchType.HERB, "HAR", "Harralander seed"),
    RANARR(Crop.RANARR, 5295, 32, PatchType.HERB, "R", "Ranarr seed"),
    TOADFLAX(Crop.TOADFLAX, 5296, 38, PatchType.HERB, "TOA", "Toadflax seed"),
    IRIT(Crop.IRIT, 5297, 44, PatchType.HERB, "I", "Irit seed"),
    AVANTOE(Crop.AVANTOE, 5298, 50, PatchType.HERB, "A", "Avantoe seed"),
    KWUARM(Crop.KWUARM, 5299, 56, PatchType.HERB, "K", "Kwuarm seed"),
    SNAPDRAGON(Crop.SNAPDRAGON, 5300, 62, PatchType.HERB, "S", "Snapdragon seed"),
    CADANTINE(Crop.CADANTINE, 5301, 67, PatchType.HERB, "C", "Cadantine seed"),
    LANTADYME(Crop.LANTADYME, 5302, 73, PatchType.HERB, "L", "Lantadyme seed"),
    DWARF_WEED(Crop.DWARF_WEED, 5303, 79, PatchType.HERB, "D", "Dwarf weed seed"),
    TORSTOL(Crop.TORSTOL, 5304, 85, PatchType.HERB, "TOR", "Torstol seed"),

    // The tree family: four id columns for seed, seedling, watered seedling and sapling.
    // The ids are not evenly spaced (redwood runs 22850, 22854, 22859), so each is
    // transcribed, never stepped.

    // Regular trees.
    OAK(Crop.OAK, 5312, 5358, 5364, 5370, 15, PatchType.TREE, "OAK", "Acorn"),
    WILLOW(Crop.WILLOW, 5313, 5359, 5365, 5371, 30, PatchType.TREE, "WIL", "Willow seed"),
    MAPLE(Crop.MAPLE, 5314, 5360, 5366, 5372, 45, PatchType.TREE, "MAP", "Maple seed"),
    YEW(Crop.YEW, 5315, 5361, 5367, 5373, 60, PatchType.TREE, "YEW", "Yew seed"),
    MAGIC(Crop.MAGIC, 5316, 5362, 5368, 5374, 75, PatchType.TREE, "MAG", "Magic seed"),

    // Hardwoods.
    TEAK(Crop.TEAK, 21486, 21469, 21473, 21477, 35, PatchType.HARDWOOD_TREE, "TEAK",
            "Teak seed"),
    MAHOGANY(Crop.MAHOGANY, 21488, 21471, 21475, 21480, 55, PatchType.HARDWOOD_TREE, "MAH",
            "Mahogany seed"),

    // Fruit trees.
    APPLE(Crop.APPLE, 5283, 5480, 5488, 5496, 27, PatchType.FRUIT_TREE, "APP",
            "Apple tree seed"),
    BANANA(Crop.BANANA, 5284, 5481, 5489, 5497, 33, PatchType.FRUIT_TREE, "BAN",
            "Banana tree seed"),
    ORANGE(Crop.ORANGE, 5285, 5482, 5490, 5498, 39, PatchType.FRUIT_TREE, "ORA",
            "Orange tree seed"),
    CURRY(Crop.CURRY, 5286, 5483, 5491, 5499, 42, PatchType.FRUIT_TREE, "CUR",
            "Curry tree seed"),
    PINEAPPLE(Crop.PINEAPPLE, 5287, 5484, 5492, 5500, 51, PatchType.FRUIT_TREE, "PINE",
            "Pineapple seed"),
    PAPAYA(Crop.PAPAYA, 5288, 5485, 5493, 5501, 57, PatchType.FRUIT_TREE, "PAPA",
            "Papaya tree seed"),
    PALM(Crop.PALM, 5289, 5486, 5494, 5502, 68, PatchType.FRUIT_TREE, "PALM",
            "Palm tree seed"),
    DRAGONFRUIT(Crop.DRAGONFRUIT, 22877, 22862, 22864, 22866, 81, PatchType.FRUIT_TREE, "DRA",
            "Dragonfruit tree seed"),

    // Single-patch specials. The three tree-shaped ones are potted like any other tree.
    CALQUAT(Crop.CALQUAT, 5290, 5487, 5495, 5503, 72, PatchType.CALQUAT, "CAL",
            "Calquat tree seed"),
    CELASTRUS(Crop.CELASTRUS, 22869, 22848, 22852, 22856, 85, PatchType.CELASTRUS, "CEL",
            "Celastrus seed"),
    REDWOOD(Crop.REDWOOD, 22871, 22850, 22854, 22859, 90, PatchType.REDWOOD, "RED",
            "Redwood tree seed"),
    HESPORI(Crop.HESPORI, 22875, 65, PatchType.HESPORI, "HES", "Hespori seed"),
    SEAWEED(Crop.SEAWEED, 21490, 23, PatchType.SEAWEED, "SW", "Seaweed spore"),
    // Bittercaps, planted from a spore.
    MUSHROOM(Crop.MUSHROOM, 5282, 53, PatchType.MUSHROOM, "MUSH", "Mushroom spore"),
    BELLADONNA(Crop.BELLADONNA, 5281, 63, PatchType.BELLADONNA, "BELL", "Belladonna seed"),
    // The anima plants, whose seeds drop only from Hespori.
    ATTAS(Crop.ATTAS, 22881, 76, PatchType.ANIMA, "AT", "Attas seed"),
    IASOR(Crop.IASOR, 22883, 76, PatchType.ANIMA, "IA", "Iasor seed"),
    KRONOS(Crop.KRONOS, 22885, 76, PatchType.ANIMA, "KRO", "Kronos seed"),
    // Coral, planted from a frag, one to a nursery.
    ELKHORN_CORAL(Crop.ELKHORN_CORAL, 31511, 28, PatchType.CORAL, "ELK", "Elkhorn frag"),
    PILLAR_CORAL(Crop.PILLAR_CORAL, 31513, 52, PatchType.CORAL, "PIL", "Pillar frag"),
    UMBRAL_CORAL(Crop.UMBRAL_CORAL, 31515, 77, PatchType.CORAL, "UMB", "Umbral frag"),

    // Cacti. The Al Kharid patch grows potato cactus; the guild patch is held for contracts.
    CACTUS(Crop.CACTUS, 5280, 55, PatchType.CACTUS, "CAC", "Cactus seed"),
    POTATO_CACTUS(Crop.POTATO_CACTUS, 22873, 64, PatchType.CACTUS, "P.CAC",
            "Potato cactus seed"),

    // Bushes.
    JANGERBERRIES(Crop.JANGERBERRIES, 5104, 48, PatchType.BUSH, "JANG", "Jangerberry seed"),
    WHITEBERRIES(Crop.WHITEBERRIES, 5105, 59, PatchType.BUSH, "WHIT", "Whiteberry seed"),
    POISON_IVY(Crop.POISON_IVY, 5106, 70, PatchType.BUSH, "POI", "Poison ivy seed"),

    // Flowers and allotments.
    LIMPWURT(Crop.LIMPWURT, 5100, 26, PatchType.FLOWER, "LIMP", "Limpwurt seed"),
    WHITE_LILY(Crop.WHITE_LILY, 22887, 58, PatchType.FLOWER, "LILY", "White lily seed"),
    STRAWBERRY(Crop.STRAWBERRY, 5323, 31, PatchType.ALLOTMENT, "STRA", "Strawberry seed"),
    WATERMELON(Crop.WATERMELON, 5321, 47, PatchType.ALLOTMENT, "MEL", "Watermelon seed"),
    SNAPE_GRASS(Crop.SNAPE_GRASS, 22879, 61, PatchType.ALLOTMENT, "SNA", "Snape grass seed");

    private final Crop crop;
    private final int seedItemId;
    private final int seedlingItemId;
    private final int wateredSeedlingItemId;
    private final int saplingItemId;
    private final int farmingLevel;
    private final PatchType patchType;
    private final String tag;
    private final String seedName;

    /** A crop planted straight from its seed, with no potting stages. */
    CropData(Crop crop, int seedItemId, int farmingLevel, PatchType patchType, String tag,
            String seedName) {
        this(crop, seedItemId, 0, 0, 0, farmingLevel, patchType, tag, seedName);
    }

    CropData(Crop crop, int seedItemId, int seedlingItemId, int wateredSeedlingItemId,
            int saplingItemId, int farmingLevel, PatchType patchType, String tag,
            String seedName) {
        this.crop = crop;
        this.seedItemId = seedItemId;
        this.seedlingItemId = seedlingItemId;
        this.wateredSeedlingItemId = wateredSeedlingItemId;
        this.saplingItemId = saplingItemId;
        this.farmingLevel = farmingLevel;
        this.patchType = patchType;
        this.tag = tag;
        this.seedName = seedName;
    }

    public Crop getCrop() {
        return crop;
    }

    public int getSeedItemId() {
        return seedItemId;
    }

    /**
     * The seed's name as the game spells it: "Acorn", "Seaweed spore", "Apple tree seed".
     * Transcribed from the item table (RuneLite's {@code gameval.ItemID}), because the
     * names follow no rule a crop name could be turned into.
     */
    public String getSeedName() {
        return seedName;
    }

    /** "Oak sapling", or null for a crop planted straight from its seed. */
    public String getSaplingName() {
        return isSaplingPlanted() ? crop.getDisplayName() + " sapling" : null;
    }

    /** "Oak seedling", or null for a crop planted straight from its seed. */
    public String getSeedlingName() {
        return isSaplingPlanted() ? crop.getDisplayName() + " seedling" : null;
    }

    /**
     * The potted, watered form that actually goes in the ground, or 0 where the seed does.
     * A sapling is a later stage of the seed, so "can this patch be filled" must count both;
     * see {@link #getPlantingForms()}.
     */
    public int getSaplingItemId() {
        return saplingItemId;
    }

    /**
     * The seed once potted, before it has been watered, or 0 where the seed is planted
     * straight.
     */
    public int getSeedlingItemId() {
        return seedlingItemId;
    }

    /** The potted seed after watering, which becomes the sapling on its own in minutes. */
    public int getWateredSeedlingItemId() {
        return wateredSeedlingItemId;
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

    public int getFarmingLevel() {
        return farmingLevel;
    }

    /**
     * How many seeds one patch of this crop swallows.
     *
     * <p>Three for an allotment, one for everything else.
     */
    public int getSeedsPerPatch() {
        return seedsPerPatch(patchType);
    }

    public PatchType getPatchType() {
        return patchType;
    }

    /**
     * The two-to-five letter tag drawn over the seed, or "" for the crops that do not
     * take one.
     *
     * <p>Herb seeds and sapling-planted crops only: herb seeds differ by a few pixels of
     * colour and the saplings share one pot sprite. Everything else is distinguishable at a
     * glance, so a tag there is clutter. This also keeps snape grass ("SNA") from reading
     * like snapdragon ("S").
     */
    public String getTag() {
        return isTagged() ? tag : "";
    }

    /**
     * The tag as RuneLite's Item Identification writes it, for every crop. {@link #getTag}
     * draws it only where a bag needs it; a lone square in a settings grid always does.
     */
    public String getStoredTag() {
        return tag;
    }

    /**
     * Whether this crop's seed is labelled. The table keeps every crop's tag as RuneLite
     * writes it; this decides only which categories are drawn.
     */
    private boolean isTagged() {
        return patchType == PatchType.HERB || isSaplingPlanted();
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
        for (CropData data : values()) {
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
        for (CropData data : values()) {
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
     * <p>Mushroom spores box by a game exception and are covered by the
     * {@link #MUSHROOM} row. Coral frags box too, and their rows cover them.
     */
    public static boolean boxesAsAseed(int itemId) {
        return itemId != 0 && SEED_ITEM_IDS.contains(itemId);
    }

    /** Computed once: this is asked per carry line. */
    private static final Set<Integer> SEED_ITEM_IDS = seedItemIds();

    private static Set<Integer> seedItemIds() {
        Set<Integer> ids = new HashSet<>();
        for (CropData data : values()) {
            ids.add(data.seedItemId);
        }
        return Collections.unmodifiableSet(ids);
    }

    /** The row for a crop, or null if no run plants it. */
    public static CropData forCrop(Crop crop) {
        for (CropData data : values()) {
            if (data.crop == crop) {
                return data;
            }
        }
        return null;
    }
}
