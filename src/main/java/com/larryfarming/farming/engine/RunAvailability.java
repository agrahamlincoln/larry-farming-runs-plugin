package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.detection.DiaryTiers;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.Skill;
import java.util.*;

/**
 * Whether an {@link UnlockGate} lets a run or stop into this player's game at all.
 *
 * <p>Unlike a travel rung in {@link com.larryfarming.farming.detection.CapabilityResolver}, a
 * gate decides whether a patch is part of the account at all, so it keeps its own
 * three-valued certainty rather than degrading to a best guess.
 *
 * <p>Reads base skill levels. Where the gate names a boosted floor
 * ({@link UnlockGate#getBoostedFromLevel()}), it also checks whether the player owns the
 * boosting item; see {@code boostState}.
 *
 * <p>{@link UnlockGate#getRecommendedFromLevel()} remains advisory and plays no part.
 */
public final class RunAvailability {

    private RunAvailability() {
    }

    /**
     * Whether the gate is satisfied. Three-valued: an unreported diary or skill is
     * UNKNOWN, not LACK. A gate naming both a skill and a diary needs both; one certain
     * shortfall gives LACK, and HAVE mixed with UNKNOWN gives UNKNOWN. A null gate is HAVE.
     */
    public static CapabilityState of(UnlockGate gate, PlayerState state) {
        if (gate == null) {
            return CapabilityState.HAVE;
        }
        List<CapabilityState> parts = new ArrayList<>(2);
        if (gate.getSkill() != null) {
            parts.add(skillState(gate, state));
        }
        if (gate.getDiaryArea() != null) {
            parts.add(DiaryTiers.completed(gate.getDiaryArea(), gate.getDiaryTier(), state));
        }
        // An empty list is an empty gate: available from the start.
        return parts.isEmpty() ? CapabilityState.HAVE : CapabilityState.all(parts);
    }

    /**
     * The gate a run actually puts in front of this player.
     *
     * <p>A run naming its own seeds opens at the Farming level of the cheapest crop the
     * player has ticked for it: below that it can plant nothing, and from there it plants
     * something. So a player who ticks oak has a tree run at 15, and one who ticks only
     * magic has none before 75.
     *
     * <p>The catalogue's gate answers for every other run, and for a seed run with nothing
     * ticked, which {@link PlantingGate#silencesWholeRun} switches off regardless.
     */
    public static UnlockGate gateOf(FarmingRun run, FarmingPreferences prefs) {
        UnlockGate written = run.getUnlock();
        if (prefs == null || run.getSeedPatchType() == null || !isPlainFarmingGate(written)) {
            return written;
        }
        int cheapest = Integer.MAX_VALUE;
        for (Crop crop : SeedSelector.plantingLadder(prefs, run.getSeedPatchType())) {
            cheapest = Math.min(cheapest, CropData.forCrop(crop).getFarmingLevel());
        }
        return cheapest == Integer.MAX_VALUE ? written : UnlockGate.skill(Skill.FARMING, cheapest);
    }

    /**
     * A Farming level and nothing else: no diary, boost or recommendation, so a level can
     * replace it whole.
     */
    private static boolean isPlainFarmingGate(UnlockGate gate) {
        return gate.getSkill() == Skill.FARMING && gate.getDiaryArea() == null
                && !gate.isBoostable() && gate.getRecommendedFromLevel() == 0;
    }

    /**
     * The base level against {@link UnlockGate#getLevel()}. UNKNOWN when the skill is
     * missing from the state, which only a profile built without its constructor (by Gson,
     * for one) can be: {@link PlayerState}'s constructor seeds every skill at level 1.
     */
    private static CapabilityState skillState(UnlockGate gate, PlayerState state) {
        if (state == null || !state.getSkills().containsKey(gate.getSkill())) {
            return CapabilityState.UNKNOWN;
        }
        int level = state.getSkillLevel(gate.getSkill());
        if (level >= gate.getLevel()) {
            return CapabilityState.HAVE;
        }
        if (gate.isBoostable() && level >= gate.getBoostedFromLevel()) {
            return boostState(gate, state);
        }
        return CapabilityState.LACK;
    }

    /**
     * Whether a player short of the gate could raise themselves over it today.
     *
     * <p>Only boosts from an owned item count, such as a garden pie for the Farming Guild
     * door. An owned boost item gives HAVE; an unseen bank gives UNKNOWN rather than LACK.
     */
    private static CapabilityState boostState(UnlockGate gate, PlayerState state) {
        int[] boosts = BOOSTS_FOR.get(gate.getSkill());
        // A skill with no modelled boost answers on the base level alone. The Lunar herb
        // run's 96 Magic gate is deliberately unboosted: the run casts throughout, so a
        // short boost does not make it the player's. A door needs one boosted step.
        if (boosts == null) {
            return CapabilityState.LACK;
        }
        for (int itemId : boosts) {
            if (ItemCounts.countOwned(state, itemId, null) > 0) {
                return CapabilityState.HAVE;
            }
        }
        return state.getBankLastSeen() == null ? CapabilityState.UNKNOWN : CapabilityState.LACK;
    }

    /**
     * What raises a skill far enough to matter to a gate, by skill.
     *
     * <p>Only Farming, and only the garden pie, whole and half (each bite boosts). A pie
     * gives +3, matching every boosted floor in the catalogue; a gate needing a different
     * margin needs its own entry.
     */
    private static final Map<Skill, int[]> BOOSTS_FOR = Map.of(Skill.FARMING, new int[]{7178, 7180});

    /**
     * One player-facing clause naming what stands between this player and the gate, or
     * the empty string once it is satisfied. Names every part not definitely held, so an
     * unresolved diary reads the same as a missing one.
     */
    public static String shortfall(UnlockGate gate, PlayerState state) {
        if (gate == null || of(gate, state) == CapabilityState.HAVE) {
            return "";
        }
        List<String> needs = new ArrayList<>(2);
        if (gate.getSkill() != null && skillState(gate, state) != CapabilityState.HAVE) {
            needs.add(gate.getLevel() + " " + gate.getSkill().getDisplayName());
        }
        if (gate.getDiaryArea() != null && DiaryTiers.completed(gate.getDiaryArea(), gate.getDiaryTier(), state) != CapabilityState.HAVE) {
            needs.add("the " + gate.getDiaryTier().toLowerCase(Locale.ROOT) + " " + gate.getDiaryArea() + " diary");
        }
        return needs.isEmpty() ? "" : "needs " + String.join(" and ", needs);
    }
}
