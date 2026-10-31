package com.larryfarming.farming.detection;

import com.larryfarming.diary.DiaryVarbits;
import com.larryfarming.model.PlayerState;

/**
 * Achievement diary tiers in the capability model's own vocabulary.
 *
 * <p>{@link DiaryVarbits} owns the ids and how to read them. This class translates its
 * three-valued answer into {@link CapabilityState}: a flag reading zero is
 * {@link CapabilityState#LACK}, and a flag nobody has read yet is
 * {@link CapabilityState#UNKNOWN}. {@link CapabilitySources#LOGIN_SCAN_VARBIT_IDS} includes
 * {@link #allVarbits()}, so most diary unknowns resolve at login.
 */
public final class DiaryTiers {

    private DiaryTiers() {
    }

    public static final String EASY = DiaryVarbits.EASY;
    public static final String MEDIUM = DiaryVarbits.MEDIUM;
    public static final String HARD = DiaryVarbits.HARD;
    public static final String ELITE = DiaryVarbits.ELITE;

    /** The completion varbit for one diary tier, or null when the game keeps no flag. */
    public static Integer varbitFor(String area, String tier) {
        return DiaryVarbits.varbitFor(area, tier);
    }

    /**
     * Whether this diary tier is finished: {@code HAVE} when the flag is set, {@code LACK}
     * when it is clear, and {@code UNKNOWN} when nothing has reported it or no id is known
     * for the area and tier.
     */
    public static CapabilityState completed(String area, String tier, PlayerState state) {
        Boolean done = DiaryVarbits.completed(area, tier, state);
        if (done == null) {
            return CapabilityState.UNKNOWN;
        }
        return done ? CapabilityState.HAVE : CapabilityState.LACK;
    }

    /** Every completion varbit, for the login scan that makes past diaries visible. */
    public static int[] allVarbits() {
        return DiaryVarbits.allVarbits();
    }
}
