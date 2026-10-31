package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.Skill;
import java.util.ArrayList;
import java.util.List;

/**
 * Which anima plant goes in, best first, by the default route's rule.
 *
 * <ul>
 * <li>Below 78 Magic, Iasor: without Resurrect Crops, fewer herbs dying matters most.</li>
 * <li>From 78, Kronos for a player who hands in about four contracts a day or more.</li>
 * <li>Otherwise Attas. The route weighs it against Iasor by how fast the player gathers
 *     seeds, which Larry cannot read, and Attas makes the most of a limited supply.</li>
 * </ul>
 *
 * <p>The rule names only the best plant. The rest follow in the order the same tests
 * prefer them, for a player who lacks the best one's seed.
 *
 * <p>The player's ticks decide which plants are in the running; this only orders them.
 *
 * <p>The route lets a boost reach 78 Magic. Larry reads real levels only, as for every
 * gate, so a player who boosts to cast the spell still ranks Iasor first.
 */
final class AnimaChoice {

    /** The Magic level from which Resurrect Crops is cast, and the rule weighs Kronos and Attas. */
    static final int RESURRECTING_MAGIC = Resurrection.MAGIC_LEVEL;

    /** Contracts a day from which Kronos is worth more than Attas. */
    static final double KRONOS_CONTRACTS_A_DAY = 4.0;

    private AnimaChoice() {
    }

    /**
     * These crops in the order this stop should plant them: the anima plants by the rule,
     * and every other stop's crops as given.
     */
    static List<Crop> ranked(List<Crop> ticked, RunStop stop, PlayerState state) {
        if (stop.patchType() != PatchType.ANIMA || ticked.size() < 2) {
            return ticked;
        }
        List<Crop> ranked = new ArrayList<>(ticked.size());
        for (Crop plant : order(state)) {
            if (ticked.contains(plant)) {
                ranked.add(plant);
            }
        }
        return ranked;
    }

    /** Every anima plant, best first for this player. */
    static List<Crop> order(PlayerState state) {
        boolean kronosPays = contractsAday(state) >= KRONOS_CONTRACTS_A_DAY;
        if (!resurrects(state)) {
            return kronosPays ? List.of(Crop.IASOR, Crop.KRONOS, Crop.ATTAS)
                    : List.of(Crop.IASOR, Crop.ATTAS, Crop.KRONOS);
        }
        return kronosPays ? List.of(Crop.KRONOS, Crop.ATTAS, Crop.IASOR)
                : List.of(Crop.ATTAS, Crop.IASOR, Crop.KRONOS);
    }

    /** Whether the real Magic level reaches Resurrect Crops. An unread level does not. */
    private static boolean resurrects(PlayerState state) {
        return state != null && state.getSkills() != null
                && state.getSkills().containsKey(Skill.MAGIC)
                && state.getSkillLevel(Skill.MAGIC) >= RESURRECTING_MAGIC;
    }

    /** Contracts handed in a day, or zero while too few days have been watched to say. */
    private static double contractsAday(PlayerState state) {
        Double perDay = state == null ? null : state.getContractsPerDay();
        return perDay == null ? 0 : perDay;
    }
}
