package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.RunId;
import java.util.*;

/**
 * The item that stands for each run, for the infobox and the run list.
 *
 * <p>Each run shows one fixed piece of what it harvests, whatever the player has ticked.
 * Seed piles all look alike in a list, and produce does not. Hespori keeps its seed, which
 * is the one sprite players know it by. The contract shows the seed pack it pays in.
 *
 * <p>Ids are checked against the game's item table ({@code gameval.ItemID}); unnoted
 * forms throughout.
 */
public final class RunIcons {

    private RunIcons() {
    }

    private static final Map<RunId, Integer> ICONS = icons();

    private static Map<RunId, Integer> icons() {
        Map<RunId, Integer> icons = new EnumMap<>(RunId.class);
        icons.put(RunId.HARDWOOD, 6332);       // Mahogany logs
        icons.put(RunId.CONTRACT, 22993);      // Seed pack
        icons.put(RunId.HESPORI, 22875);       // Hespori seed
        icons.put(RunId.ANIMA, 22881);         // Attas seed
        icons.put(RunId.CALQUAT, 5980);        // Calquat fruit
        icons.put(RunId.FRUIT_TREE, 5972);     // Papaya fruit
        icons.put(RunId.HERB, 207);            // Grimy ranarr weed
        icons.put(RunId.REGULAR_TREE, 1515);   // Yew logs
        icons.put(RunId.SEAWEED, 21504);       // Giant seaweed
        return Collections.unmodifiableMap(icons);
    }

    /** The item to draw for this run, or 0 when there is nothing to draw. */
    public static int of(RunId id) {
        return id == null ? 0 : ICONS.getOrDefault(id, 0);
    }
}
