package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import java.util.*;

/**
 * The crops a player may grow instead of the default crop at a stop with one default crop,
 * and how often a herb run should call at the patch for each.
 *
 * <p>A stop takes this list when its patch type is here and the default crop is in it. The
 * list is best first by Farming level, so a stop plants the highest-level ticked crop the
 * player can grow and has the seed for.
 */
final class CropChoices {

    /**
     * Herb runs between visits, from each crop's growth time against a herb's 80 minutes
     * (4 × 20 minutes). Growth times are the wiki's: snape grass 7 × 10 minutes and
     * watermelon 8 × 10 (Allotment patch, Seeds); whiteberries and poison ivy 8 × 20 (Bush
     * patch, Seeds); cactus 7 × 80 and potato cactus 7 × 10 (Special patches, Cactus
     * patches). A bush or cactus is replanted each visit, so its growth time, not its
     * regrowth, sets the pacing.
     *
     * <p>Every crop here keys a patch family, but its figure is read only where the crop
     * stands in for the default crop. A stop's own default crop takes the stop's figure
     * ({@link FarmingCatalogue#everyNthRun}), so the catalogue has one source for it.
     */
    private static final Map<Crop, Integer> EVERY_NTH_RUN = Map.of(Crop.SNAPE_GRASS, 1, Crop.WATERMELON, 1, Crop.WHITEBERRIES, 2,
            Crop.POISON_IVY, 2, Crop.POTATO_CACTUS, 1, Crop.CACTUS, 7);

    private static final Map<PatchType, List<Crop>> BY_PATCH = byPatch();

    private CropChoices() {
    }

    private static Map<PatchType, List<Crop>> byPatch() {
        Map<PatchType, List<Crop>> byPatch = new EnumMap<>(PatchType.class);
        for (Crop crop : EVERY_NTH_RUN.keySet()) {
            byPatch.computeIfAbsent(CropData.forCrop(crop).getPatchType(), type -> new ArrayList<>()).add(crop);
        }
        Map<PatchType, List<Crop>> sorted = new EnumMap<>(PatchType.class);
        for (Map.Entry<PatchType, List<Crop>> family : byPatch.entrySet()) {
            List<Crop> crops = family.getValue();
            crops.sort(Comparator.comparingInt((Crop c) -> CropData.forCrop(c).getFarmingLevel()).reversed());
            sorted.put(family.getKey(), List.copyOf(crops));
        }
        return Collections.unmodifiableMap(sorted);
    }

    /**
     * The anima plants, in the order the settings show them. A player who ticks several
     * plants the first of them they hold seeds for.
     */
    private static final List<Crop> ANIMA_PLANTS = List.of(Crop.ATTAS, Crop.IASOR, Crop.KRONOS);

    /** The crops this kind of patch offers in place of the default crop, best first, or empty. */
    static List<Crop> forPatch(PatchType type) {
        if (type == PatchType.ANIMA) {
            return ANIMA_PLANTS;
        }
        return BY_PATCH.getOrDefault(type, List.of());
    }

    /** How many herb runs pass between visits for this crop, or 0 when it has no choice. */
    static int everyNthRun(Crop crop) {
        return EVERY_NTH_RUN.getOrDefault(crop, 0);
    }
}
