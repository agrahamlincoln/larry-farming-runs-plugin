package com.larryfarming.diary;

import com.larryfarming.model.PlayerState;

import java.util.*;

/**
 * The game's own completion flag for each achievement diary tier.
 *
 * <p>Every area keeps one varbit per tier, set when the tier is finished and readable from
 * login onwards. Unlike the one-shot chat message announcing the diary, the varbit also
 * reveals tiers finished before the plugin was installed.
 *
 * <p>The farming planner's diary gates read this table, through {@code DiaryTiers}. It
 * knows nothing about them.
 *
 * <p>Ids transcribed from RuneLite's {@code Varbits} (DIARY_&lt;AREA&gt;_&lt;TIER&gt;,
 * {@code runelite-api/src/main/java/net/runelite/api/Varbits.java}). Anything else needing
 * a diary flag asks here.
 */
public final class DiaryVarbits {

    private DiaryVarbits() {
    }

    public static final String EASY = "Easy";
    public static final String MEDIUM = "Medium";
    public static final String HARD = "Hard";
    public static final String ELITE = "Elite";

    /**
     * Long-form area names folded onto the short key the table is written with.
     *
     * <p>The table uses the one-word name; the wiki uses the full title. Without the alias,
     * a catalogue entry naming "Lumbridge &amp; Draynor" would read as unknown forever. Both
     * "&amp;" and "and" are accepted.
     *
     * <p>Declared above {@link #BY_AREA_AND_TIER} because building that table runs every
     * area name through {@link #key}, which reads this one.
     */
    private static final Map<String, String> AREA_ALIASES = areaAliases();

    private static Map<String, String> areaAliases() {
        Map<String, String> aliases = new HashMap<>();
        alias(aliases, "Lumbridge & Draynor", "Lumbridge");
        alias(aliases, "Kourend & Kebos", "Kourend");
        alias(aliases, "Western Provinces", "Western");
        return Collections.unmodifiableMap(aliases);
    }

    private static void alias(Map<String, String> aliases, String longName, String shortName) {
        String canonical = shortName.toLowerCase(Locale.ROOT);
        String folded = longName.toLowerCase(Locale.ROOT);
        aliases.put(folded, canonical);
        aliases.put(folded.replace(" & ", " and "), canonical);
    }

    /**
     * Completion varbit per area and tier, keyed case-insensitively because the callers
     * are hand-written catalogue entries.
     */
    private static final Map<String, Integer> BY_AREA_AND_TIER = table();

    private static Map<String, Integer> table() {
        Map<String, Integer> ids = new HashMap<>();
        put(ids, "Ardougne", 4458, 4459, 4460, 4461);
        put(ids, "Desert", 4483, 4484, 4485, 4486);
        put(ids, "Falador", 4462, 4463, 4464, 4465);
        put(ids, "Fremennik", 4491, 4492, 4493, 4494);
        put(ids, "Kandarin", 4475, 4476, 4477, 4478);
        // Karamja's four ids are not consecutive.
        put(ids, "Karamja", 3578, 3599, 3611, 4566);
        put(ids, "Kourend", 7925, 7926, 7927, 7928);
        put(ids, "Lumbridge", 4495, 4496, 4497, 4498);
        put(ids, "Morytania", 4487, 4488, 4489, 4490);
        put(ids, "Varrock", 4479, 4480, 4481, 4482);
        put(ids, "Western", 4471, 4472, 4473, 4474);
        put(ids, "Wilderness", 4466, 4467, 4468, 4469);
        return Collections.unmodifiableMap(ids);
    }

    private static void put(Map<String, Integer> ids, String area, int easy, int medium, int hard, int elite) {
        ids.put(key(area, EASY), easy);
        ids.put(key(area, MEDIUM), medium);
        ids.put(key(area, HARD), hard);
        ids.put(key(area, ELITE), elite);
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
     * Whether this diary tier is finished: TRUE when the flag is set, FALSE when it reads
     * zero, and null when the varbit has not been reported or the area or tier has no id.
     *
     * <p>Keep the null distinct from FALSE: an absent varbit means nobody has looked yet,
     * not "not done".
     */
    public static Boolean completed(String area, String tier, PlayerState state) {
        if (state == null) {
            return null;
        }
        Integer varbit = varbitFor(area, tier);
        if (varbit == null) {
            return null;
        }
        Integer value = state.getVarbits().get(varbit);
        if (value == null) {
            return null;
        }
        return value > 0;
    }

    /** Every completion varbit, for the login scan that makes past diaries visible. */
    public static int[] allVarbits() {
        int[] all = new int[BY_AREA_AND_TIER.size()];
        int i = 0;
        for (Integer id : BY_AREA_AND_TIER.values()) {
            all[i++] = id;
        }
        return all;
    }
}
