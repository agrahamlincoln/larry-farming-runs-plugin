package com.larryfarming.farming.model;

import java.util.List;

/**
 * The repeatable farming activities.
 *
 * <p>Tree running is four separate runs. Tree patches have no single order: they are
 * grouped by category, each with its own travel chains. The scheduler
 * combines runs that fall due together.
 */
public enum RunId {

    HARDWOOD("Hardwood run"),
    CONTRACT("Farming contract"),
    HESPORI("Hespori"),
    ANIMA("Anima plant"),
    CORAL("Coral"),
    CALQUAT("Calquat run"),
    FRUIT_TREE("Fruit tree run"),
    HERB("Herb run"),
    REGULAR_TREE("Tree run"),
    SEAWEED("Seaweed run"),
    ULTRACOMPOST("Compost bins");

    /**
     * The order the Farming tab lists the runs in, fixed so no row moves when a run comes
     * due or is switched off. Display only: {@link #getPriority()} still orders the planning.
     */
    public static final List<RunId> DISPLAY_ORDER = List.of(HERB, REGULAR_TREE, FRUIT_TREE,
            HARDWOOD, CALQUAT, SEAWEED, HESPORI, ANIMA, CORAL, CONTRACT, ULTRACOMPOST);

    private final String displayName;

    RunId(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Lower sorts first when several runs are due at once. Follows each run's value, not
     * which timer expired first, as {@link Preset#getRunPriority()} ranks it. The player's
     * own order, which may lead with the herb run, is {@link FarmingPreferences#priorityOf}.
     */
    public int getPriority() {
        return Preset.DEFAULT.priorityOf(this);
    }
}
