package com.larryfarming.engine;

import java.util.Map;

/**
 * The quests whose stage counter gates a farm-run route, and the var each counter lives in.
 *
 * <p>Names match RuneLite's {@code Quest} enum, which the planner looks quests up by. Ids
 * from Quest Helper's {@code QuestVarbits} and {@code QuestVarPlayer}.
 */
public final class QuestStages {

    private QuestStages() {
    }

    /** Quests counted in a varbit, by varbit id. */
    public static final Map<Integer, String> BY_VARBIT = Map.of(2326, "Fairytale II - Cure a Queen", 18321, "Troubled Tortugans");

    /** Quests counted in a varp, by varp id. */
    public static final Map<Integer, String> BY_VARP = Map.of(517, "Mourning's End Part I", 980, "The Great Brain Robbery");
}
