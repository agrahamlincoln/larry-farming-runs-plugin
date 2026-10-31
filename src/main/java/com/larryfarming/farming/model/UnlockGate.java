package com.larryfarming.farming.model;

import com.larryfarming.model.Skill;
import lombok.Builder;
import lombok.Value;

/**
 * What has to be true before a run or stop is available, and separately, when it
 * becomes worth doing.
 *
 * <p>The two differ. Seaweed spores plant at 23 Farming, but the default route waits until
 * an Attas plant can run alongside at 76. {@link #level} holds what the game enforces and
 * {@link #recommendedFromLevel} holds what the default route advises.
 *
 * <p>Gates record the true level, never the boosted one. Recommendations are often quoted
 * as boosted numbers (a garden pie covers the difference), but the plugin reads base
 * levels only, so a gate at the boosted number would never fire. The boosted figure is
 * kept only to tell the player.
 */
@Value
@Builder
public class UnlockGate {

    /** Null when there is no skill requirement. */
    Skill skill;

    /** The real requirement, as the game enforces it. Zero when there is no skill gate. */
    int level;

    /**
     * The level from which a boost reaches {@link #level}, or zero when the requirement
     * cannot be boosted. Advisory only: never gates availability.
     */
    int boostedFromLevel;

    /**
     * The level from which the run is worth doing, or zero when that
     * is simply {@link #level}. Never gates availability.
     */
    int recommendedFromLevel;

    /** Diary area and tier, both null unless a diary tier is required. */
    String diaryArea;
    String diaryTier;

    /** Available to everyone from the start. */
    public static UnlockGate none() {
        return UnlockGate.builder().build();
    }

    public static UnlockGate skill(Skill skill, int level) {
        return UnlockGate.builder().skill(skill).level(level).build();
    }

    public static UnlockGate boostableSkill(Skill skill, int level, int boostedFromLevel) {
        return UnlockGate.builder()
                .skill(skill)
                .level(level)
                .boostedFromLevel(boostedFromLevel)
                .build();
    }

    public static UnlockGate diary(String area, String tier) {
        return UnlockGate.builder().diaryArea(area).diaryTier(tier).build();
    }

    public boolean isBoostable() {
        return boostedFromLevel > 0;
    }
}
