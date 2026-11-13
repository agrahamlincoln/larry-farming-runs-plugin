package com.larryfarming.farming.detection;

import com.larryfarming.data.Tables;
import com.larryfarming.model.PlayerState;
import java.util.*;

/**
 * Achievement diary tiers: the game's completion flag for each tier, read as a
 * {@link CapabilityState}.
 *
 * <p>Every area keeps one varbit per tier, set when the tier is finished and readable from
 * login onwards. Unlike the one-shot chat message announcing the diary, the varbit also
 * reveals tiers finished before the plugin was installed. A flag reading zero is
 * {@link CapabilityState#LACK}, and a flag nobody has read yet is
 * {@link CapabilityState#UNKNOWN}. {@link CapabilitySources#LOGIN_SCAN_VARBIT_IDS} includes
 * {@link #allVarbits()}, so most diary unknowns resolve at login.
 *
 * <p>The ids live in the diary-tiers table, transcribed from RuneLite's {@code Varbits}
 * (DIARY_&lt;AREA&gt;_&lt;TIER&gt;,
 * {@code runelite-api/src/main/java/net/runelite/api/Varbits.java}; Copyright (c) 2017, Adam;
 * BSD 2-Clause License).
 */
public final class DiaryTiers {

    private DiaryTiers() {
    }

    public static final String EASY = "Easy";
    public static final String MEDIUM = "Medium";
    public static final String HARD = "Hard";
    public static final String ELITE = "Elite";

    /** The tiers in the order the table's id columns hold them. */
    private static final String[] TIERS = {EASY, MEDIUM, HARD, ELITE};

    /**
     * Long-form area names folded onto the short key the table is written with.
     *
     * <p>The table keys each area by its one-word name; the wiki uses the full title.
     * Without the alias, a catalogue entry naming "Lumbridge &amp; Draynor" would read as
     * unknown forever. Both "&amp;" and "and" are accepted.
     */
    private static final Map<String, String> AREA_ALIASES = new HashMap<>();

    /**
     * Completion varbit per area and tier, keyed case-insensitively because the callers
     * are hand-written catalogue entries.
     */
    private static final Map<String, Integer> BY_AREA_AND_TIER = new HashMap<>();

    static {
        for (Tables.Row row : Tables.read("/com/larryfarming/diary-tiers.tsv", 10)) {
            String area = row.text(0).toLowerCase(Locale.ROOT);
            String fullTitle = row.optionalText(9);
            if (fullTitle != null) {
                String folded = fullTitle.toLowerCase(Locale.ROOT);
                AREA_ALIASES.put(folded, area);
                AREA_ALIASES.put(folded.replace(" & ", " and "), area);
            }
            for (int tier = 0; tier < TIERS.length; tier++) {
                BY_AREA_AND_TIER.put(key(area, TIERS[tier]), row.integer(1 + 2 * tier));
            }
        }
    }

    private static String key(String area, String tier) {
        String folded = area.toLowerCase(Locale.ROOT).trim();
        return AREA_ALIASES.getOrDefault(folded, folded) + "/" + tier.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * The completion varbit for one diary tier, or null when the area or tier is not one
     * the game keeps a flag for.
     *
     * <p>Accepts both the short name ("Kourend") and the full title via {@link #AREA_ALIASES}.
     */
    public static Integer varbitFor(String area, String tier) {
        if (area == null || tier == null) {
            return null;
        }
        return BY_AREA_AND_TIER.get(key(area, tier));
    }

    /**
     * Whether this diary tier is finished: {@code HAVE} when the flag is set, {@code LACK}
     * when it reads zero, and {@code UNKNOWN} when nothing has reported the varbit or no id
     * is known for the area and tier. An absent varbit means nobody has looked yet, not
     * "not done".
     */
    public static CapabilityState completed(String area, String tier, PlayerState state) {
        Integer varbit = state == null ? null : varbitFor(area, tier);
        Integer value = varbit == null ? null : state.getVarbits().get(varbit);
        if (value == null) {
            return CapabilityState.UNKNOWN;
        }
        return value > 0 ? CapabilityState.HAVE : CapabilityState.LACK;
    }

    /** Every completion varbit, for the login scan that makes past diaries visible. */
    public static int[] allVarbits() {
        return BY_AREA_AND_TIER.values().stream().mapToInt(Integer::intValue).toArray();
    }
}
