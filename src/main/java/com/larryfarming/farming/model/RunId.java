package com.larryfarming.farming.model;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The repeatable farming activities.
 *
 * <p>Tree running is four separate runs. Tree patches have no single order: they are
 * grouped by category, each with its own travel chains. The scheduler
 * combines runs that fall due together.
 */
@Getter
@RequiredArgsConstructor
public enum RunId {

    HARDWOOD("Hardwood run", 6332),       // Mahogany logs
    CONTRACT("Farming contract", 22993),  // Seed pack
    HESPORI("Hespori", 22875),            // Hespori seed
    ANIMA("Anima plant", 22881),          // Attas seed
    CALQUAT("Calquat run", 5980),         // Calquat fruit
    FRUIT_TREE("Fruit tree run", 5972),   // Papaya fruit
    HERB("Herb run", 207),                // Grimy ranarr weed
    REGULAR_TREE("Tree run", 1515),       // Yew logs
    SEAWEED("Seaweed run", 21504);        // Giant seaweed

    /**
     * The order the side panel lists the runs in, fixed so no row moves when a run comes
     * due or is switched off. Display only: {@link #getPriority()} still orders the planning.
     */
    public static final List<RunId> DISPLAY_ORDER = List.of(HERB, REGULAR_TREE, FRUIT_TREE,
            HARDWOOD, CALQUAT, SEAWEED, HESPORI, ANIMA, CONTRACT);

    private final String displayName;

    /**
     * The item that stands for this run, for the infobox and the run list: one fixed piece
     * of what it harvests, whatever the player has ticked. Seed piles all look alike in a
     * list, and produce does not. Hespori keeps its seed, which is the one sprite players
     * know it by, and the anima plant shows the Attas seed. The contract shows the seed pack
     * it pays in. Ids come from the game's item table ({@code gameval.ItemID}), unnoted forms
     * throughout.
     */
    private final int iconItemId;

    /**
     * Lower sorts first when several runs are due at once. Follows each run's value, not
     * which timer expired first, as {@link Preset#getRunPriority()} ranks it. The player's
     * own order, which may lead with the herb run, is {@link FarmingPreferences#priorityOf}.
     */
    public int getPriority() {
        return Preset.DEFAULT.priorityOf(this);
    }
}
