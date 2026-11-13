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
     * (4 × 20 minutes). Growth times are the wiki's: whiteberries and poison ivy 8 × 20 (Bush
     * patch, Seeds); cactus 7 × 80 and potato cactus 7 × 10 (Special patches, Cactus
     * patches). A bush or cactus is replanted each visit, so its growth time, not its
     * regrowth, sets the pacing. The picked allotments and flower patches need no figure:
     * every crop of theirs grows within a herb run.
     *
     * <p>Every crop here keys a patch family, but its figure is read only where the crop
     * stands in for the default crop. A stop's own default crop takes the stop's figure
     * ({@link FarmingCatalogue#everyNthRun}), so the catalogue has one source for it.
     */
    private static final Map<Crop, Integer> EVERY_NTH_RUN = Map.of(Crop.WHITEBERRIES, 2,
            Crop.POISON_IVY, 2, Crop.POTATO_CACTUS, 1, Crop.CACTUS, 7);

    private static final Map<PatchType, List<Crop>> BY_PATCH = byPatch();

    private CropChoices() {
    }

    private static Map<PatchType, List<Crop>> byPatch() {
        Map<PatchType, List<Crop>> byPatch = new EnumMap<>(PatchType.class);
        for (Crop crop : EVERY_NTH_RUN.keySet()) {
            PatchType type = CropData.forCrop(crop).getPatchType();
            // The patch's crops best first, less those offered nowhere.
            List<Crop> crops = new ArrayList<>(CropData.plantableIn(type));
            crops.retainAll(EVERY_NTH_RUN.keySet());
            byPatch.put(type, List.copyOf(crops));
        }
        return Collections.unmodifiableMap(byPatch);
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
