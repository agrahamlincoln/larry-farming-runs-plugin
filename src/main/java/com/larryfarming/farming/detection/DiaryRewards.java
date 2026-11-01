package com.larryfarming.farming.detection;

import com.larryfarming.farming.model.Capability;
import java.util.*;

/**
 * How to tell, from game flags alone, which tier of a diary reward item a player has
 * earned and how much of today's allowance they have spent.
 *
 * <p>The tier is read from the diary rather than from the item. The plugin sees the bank only
 * once the player opens it, so a banked ring is invisible for most of a session. The diary
 * flag is readable at login wherever the item is. The cost runs the safe way: a player who
 * never reclaimed the reward is offered a teleport they lack, notices at once and drops to
 * the next rung. Reading the item instead would silently skip patches.
 */
public final class DiaryRewards {

    private DiaryRewards() {
    }

    /**
     * The diary completion varbit that grants each tier. The highest completed tier names
     * what the player has.
     */
    private static final Map<Capability, Integer> GRANTED_BY = grantedBy();

    /**
     * Varbits counting uses spent today, upward from zero.
     *
     * <p>Only the Explorer's ring has one: 4552 counts its cabbage-patch teleports (4553
     * counts energy restores, 4554 free alchs). Varbit 6069 ({@code ARDOUGNE_CLOAK_LOWBITS})
     * is a single bit and cannot hold the cloak's three or five daily farm teleports, so the
     * cloak's uses are counted as they happen (see {@link ArdougneFarmTeleportWatch}).
     */
    private static final Map<Capability, Integer> USES_SPENT_VARBIT = usesSpentVarbits();

    private static Map<Capability, Integer> grantedBy() {
        Map<Capability, Integer> granted = new EnumMap<>(Capability.class);
        // Lumbridge & Draynor: medium, hard, elite give Explorer's ring 2, 3 and 4.
        granted.put(Capability.EXPLORERS_RING_2, DiaryTiers.varbitFor("Lumbridge", DiaryTiers.MEDIUM));
        granted.put(Capability.EXPLORERS_RING_3, DiaryTiers.varbitFor("Lumbridge", DiaryTiers.HARD));
        granted.put(Capability.EXPLORERS_RING_4, DiaryTiers.varbitFor("Lumbridge", DiaryTiers.ELITE));
        // Ardougne: easy gives cloak 1, then the same three tiers give 2, 3 and 4. Cloak 1
        // matters because its Monastery teleport lands beside a fairy ring.
        granted.put(Capability.ARDOUGNE_CLOAK_1, DiaryTiers.varbitFor("Ardougne", DiaryTiers.EASY));
        granted.put(Capability.ARDOUGNE_CLOAK_2, DiaryTiers.varbitFor("Ardougne", DiaryTiers.MEDIUM));
        granted.put(Capability.ARDOUGNE_CLOAK_3, DiaryTiers.varbitFor("Ardougne", DiaryTiers.HARD));
        granted.put(Capability.ARDOUGNE_CLOAK_4, DiaryTiers.varbitFor("Ardougne", DiaryTiers.ELITE));
        return Collections.unmodifiableMap(granted);
    }

    private static Map<Capability, Integer> usesSpentVarbits() {
        Map<Capability, Integer> varbits = new EnumMap<>(Capability.class);
        varbits.put(Capability.EXPLORERS_RING_2, 4552);
        return Collections.unmodifiableMap(varbits);
    }

    /** The best tier of this family the player has earned, or null if none. */
    public static Capability bestEarnedTier(Capability.TierFamily family, Map<Integer, Integer> varbits) {
        if (family == Capability.TierFamily.NONE || varbits == null) {
            return null;
        }
        Capability best = null;
        for (Map.Entry<Capability, Integer> entry : GRANTED_BY.entrySet()) {
            Capability capability = entry.getKey();
            if (capability.getTierFamily() != family) {
                continue;
            }
            Integer done = varbits.get(entry.getValue());
            if (done == null || done == 0) {
                continue;
            }
            if (best == null || capability.getTier() > best.getTier()) {
                best = capability;
            }
        }
        return best;
    }

    /**
     * The varbit counting today's spent uses upward from zero, or 0 if the game does not
     * report it and the uses must be counted instead.
     */
    public static int usesSpentVarbit(Capability capability) {
        return USES_SPENT_VARBIT.getOrDefault(capability, 0);
    }

    public static boolean reportsUsesSpent(Capability capability) {
        return usesSpentVarbit(capability) != 0;
    }

    /** Every uses-spent counter, for the login scan that reads a part-spent day. */
    public static int[] usesSpentVarbitIds() {
        int[] ids = new int[USES_SPENT_VARBIT.size()];
        int i = 0;
        for (Integer id : USES_SPENT_VARBIT.values()) {
            ids[i++] = id;
        }
        return ids;
    }
}
