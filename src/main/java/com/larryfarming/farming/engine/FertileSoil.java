package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.Teleports;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.*;
import java.util.List;
import java.util.Map;

/**
 * Whether the player treats their patches with the Lunar Fertile Soil spell instead of
 * carrying ultracompost, and what that costs.
 *
 * <p>The spell alone gives supercompost, worse than the ultracompost the bucket holds. A
 * player who has read the Ash covered tome casts it with two volcanic ash in the bag to get
 * ultracompost instead. Nothing in the game state says whether the tome has been read, so
 * the player says so with the setting, and Larry plans the spell only then.
 *
 * <p>The seaweed patches keep their compost: Larry does not plan the spell underwater.
 *
 * <p>Not modelled: the spell's rune stacks taking bag slots for a player without a rune
 * pouch, as for Cure Plant.
 */
final class FertileSoil {

    /** The Magic level Fertile Soil needs. */
    static final int MAGIC_LEVEL = 83;

    /** Volcanic ash each cast consumes to give ultracompost. */
    static final int ASH_PER_CAST = 2;

    private FertileSoil() {
    }

    /**
     * Whether the trip casts the spell rather than carrying compost: the player has asked
     * for it, has the real Magic level, and walks the trip on the Lunar book. A trip planned
     * for no particular book reads the book the player has open.
     *
     * @param tripBook the book the trip is planned for
     */
    static boolean inUse(FarmingPreferences prefs, PlayerState state, Spellbook tripBook) {
        if (prefs == null || !prefs.compostsWithFertileSoil() || state == null || state.getSkillLevel(Skill.MAGIC) < MAGIC_LEVEL) {
            return false;
        }
        if (tripBook != Spellbook.ANY) {
            return tripBook == Spellbook.LUNAR;
        }
        return FarmingAdviceBuilder.openBookOf(state) == Spellbook.LUNAR;
    }

    /**
     * Whether the player could cast the spell after switching to the Lunar book: 83 Magic
     * and Lunar Diplomacy done. The setting shows only then, since it does nothing otherwise.
     */
    static boolean couldCast(PlayerState state) {
        return state != null && state.getSkillLevel(Skill.MAGIC) >= MAGIC_LEVEL
                && state.getQuests().get("Lunar Diplomacy") == QuestStatus.COMPLETED;
    }

    /**
     * One cast for every patch planted at a stop the spell treats: one that composts, and
     * not a seaweed patch. So the limpwurt patch, which takes no compost, costs nothing.
     * Zero when {@code cropFor} is null, as it is for a bag planned without the patches.
     *
     * @param cropFor what each stop is planting, index-aligned with {@code plans}
     */
    static int castsFor(List<StopPlan> plans, List<List<Crop>> cropFor) {
        if (cropFor == null) {
            return 0;
        }
        int casts = 0;
        for (int i = 0; i < plans.size() && i < cropFor.size(); i++) {
            if (treats(plans.get(i))) {
                casts += cropFor.get(i).size();
            }
        }
        return casts;
    }

    /** Whether the spell would treat a patch at any of these stops, so ash is carried. */
    static boolean treatsAny(List<StopPlan> plans) {
        for (StopPlan plan : plans) {
            if (treats(plan) && plan.getStop().isPlanting()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the bag still carries its compost on a Fertile Soil trip: the trip plants a
     * seaweed patch, which keeps its compost.
     */
    static boolean keepsTheCompost(List<StopPlan> plans) {
        for (StopPlan plan : plans) {
            RunStop stop = plan.getStop();
            if (plan.isVisit() && stop.patchType() == PatchType.SEAWEED && stop.getActions().contains(RunStop.StopAction.COMPOST)) {
                return true;
            }
        }
        return false;
    }

    /** The runes one cast takes. */
    static Map<Rune, Integer> runesPerCast() {
        return Teleports.runesFor(Capability.FERTILE_SOIL);
    }

    private static boolean treats(StopPlan plan) {
        RunStop stop = plan.getStop();
        return plan.isVisit() && stop.getActions().contains(RunStop.StopAction.COMPOST) && stop.patchType() != PatchType.SEAWEED;
    }
}
