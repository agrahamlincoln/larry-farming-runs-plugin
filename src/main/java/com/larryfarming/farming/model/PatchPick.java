package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchType;
import java.util.List;
import lombok.Value;

/**
 * An allotment or flower patch at a herb farm whose crop the player picks: the site it is,
 * the keys its pick and its For compost are saved under, and its names in the settings.
 */
@Value
public class PatchPick {

    /** The site's key in the stops table. */
    String site;
    /** Holds a crop's constant name, or {@link FarmingKeys#OFF}. */
    String key;
    /**
     * Holds whether the patch plants for compost, or null for a patch with no compost bin
     * beside it: the flower patches and the Harmony allotment.
     */
    String compostKey;
    /** The farm it sits at, as its settings row is named: "Catherby". */
    String farm;
    /** The patch as its picker is titled: "Catherby north". */
    String title;
    /**
     * The crop switches read for this patch while its pick is unsaved, best first. When one
     * of them is saved, the first one on is the pick.
     */
    List<Crop> switches;

    /** The type of the patch the site works, which shares the site's key. */
    public PatchType getPatchType() {
        return FarmingWorldMap.byCatalogueKey(site).getType();
    }
}
