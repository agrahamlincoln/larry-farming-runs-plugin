package com.larryfarming.farming.detection;

import com.larryfarming.data.Tables;
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
    private static final Map<Capability, Integer> GRANTED_BY = new EnumMap<>(Capability.class);

    /**
     * Each varbit counting a capability's uses spent today, upward from zero, and the
     * capability it counts. The login scan reads each, so a part-spent day is seen. The
     * Ardougne cloak has none, so {@link ArdougneFarmTeleportWatch} counts its uses.
     */
    public static final Map<Integer, Capability> USES_SPENT_BY_VARBIT;

    /** The diary tier that earns each reward, as a player names it: "the medium Lumbridge diary". */
    private static final Map<Capability, String> EARNED_BY = new EnumMap<>(Capability.class);

    static {
        Map<Integer, Capability> usesSpent = new HashMap<>();
        for (Tables.Row row : Tables.read("/com/larryfarming/diary-rewards.tsv", 5)) {
            Capability reward = row.constant(Capability.class, 0);
            GRANTED_BY.put(reward, DiaryTiers.varbitFor(row.text(1), row.text(2)));
            EARNED_BY.put(reward, "the " + row.text(2).toLowerCase(Locale.ROOT) + " " + row.text(1) + " diary");
            if (row.optionalText(3) != null) {
                usesSpent.put(row.integer(3), reward);
            }
        }
        USES_SPENT_BY_VARBIT = Collections.unmodifiableMap(usesSpent);
    }

    /** The diary tier that earns this reward, as a player names it, or null for no reward. */
    public static String earnedBy(Capability reward) {
        return EARNED_BY.get(reward);
    }

    /** The best tier of this family the player has earned, or null if none. */
    public static Capability bestEarnedTier(Capability.TierFamily family, Map<Integer, Integer> varbits) {
        if (varbits == null) {
            return null;
        }
        List<Capability> earned = new ArrayList<>();
        for (Map.Entry<Capability, Integer> entry : GRANTED_BY.entrySet()) {
            Integer done = varbits.get(entry.getValue());
            if (done != null && done != 0) {
                earned.add(entry.getKey());
            }
        }
        return Capability.bestHeldTier(family, earned);
    }
}
