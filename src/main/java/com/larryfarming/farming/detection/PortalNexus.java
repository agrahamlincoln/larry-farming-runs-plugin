package com.larryfarming.farming.detection;

import java.util.*;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;

/**
 * What the Portal Nexus is attuned to, read from its slot varbits.
 *
 * <p>The Nexus holds up to 45 slots. Each slot is a varbit holding a destination key from
 * enum 1377, or zero for an empty slot. The enum keys each destination's alternate arrival at
 * its key plus 150, reached through the destination's struct. A slot is six bits wide, so it
 * never holds such a key, but one would still fold to its destination. Destinations are
 * matched on keys, never on names: other plugins rename
 * the Nexus's destinations at runtime.
 *
 * <p>Two sets of slot varbits exist. The plain set is the attunement the player saved, which the
 * game reads when it checks whether a destination is already in the Nexus. The
 * {@code _TEMP} set is a working copy, which the teleport menu redraws from. So the plain
 * set is authoritative, and the working copy stands in for it only on the teleport menu of
 * the player's own Nexus, when every plain slot reads zero.
 */
public final class PortalNexus {

    /** Enum 1377's keys for the destinations the farm runs use. */
    public static final int TROLL_STRONGHOLD = 15;
    public static final int WEISS = 19;
    public static final int HARMONY_ISLAND = 27;

    /** Added to a key for the destination's alternate arrival. */
    private static final int ALTERNATE_ARRIVAL = 150;

    /** The saved attunement, slots 1 to 45 in order. */
    private static final int[] SLOTS = {
        VarbitID.POH_NEXUS_TELE_1, VarbitID.POH_NEXUS_TELE_2, VarbitID.POH_NEXUS_TELE_3,
        VarbitID.POH_NEXUS_TELE_4, VarbitID.POH_NEXUS_TELE_5, VarbitID.POH_NEXUS_TELE_6,
        VarbitID.POH_NEXUS_TELE_7, VarbitID.POH_NEXUS_TELE_8, VarbitID.POH_NEXUS_TELE_9,
        VarbitID.POH_NEXUS_TELE_10, VarbitID.POH_NEXUS_TELE_11, VarbitID.POH_NEXUS_TELE_12,
        VarbitID.POH_NEXUS_TELE_13, VarbitID.POH_NEXUS_TELE_14, VarbitID.POH_NEXUS_TELE_15,
        VarbitID.POH_NEXUS_TELE_16, VarbitID.POH_NEXUS_TELE_17, VarbitID.POH_NEXUS_TELE_18,
        VarbitID.POH_NEXUS_TELE_19, VarbitID.POH_NEXUS_TELE_20, VarbitID.POH_NEXUS_TELE_21,
        VarbitID.POH_NEXUS_TELE_22, VarbitID.POH_NEXUS_TELE_23, VarbitID.POH_NEXUS_TELE_24,
        VarbitID.POH_NEXUS_TELE_25, VarbitID.POH_NEXUS_TELE_26, VarbitID.POH_NEXUS_TELE_27,
        VarbitID.POH_NEXUS_TELE_28, VarbitID.POH_NEXUS_TELE_29, VarbitID.POH_NEXUS_TELE_30,
        VarbitID.POH_NEXUS_TELE_31, VarbitID.POH_NEXUS_TELE_32, VarbitID.POH_NEXUS_TELE_33,
        VarbitID.POH_NEXUS_TELE_34, VarbitID.POH_NEXUS_TELE_35, VarbitID.POH_NEXUS_TELE_36,
        VarbitID.POH_NEXUS_TELE_37, VarbitID.POH_NEXUS_TELE_38, VarbitID.POH_NEXUS_TELE_39,
        VarbitID.POH_NEXUS_TELE_40, VarbitID.POH_NEXUS_TELE_41, VarbitID.POH_NEXUS_TELE_42,
        VarbitID.POH_NEXUS_TELE_43, VarbitID.POH_NEXUS_TELE_44, VarbitID.POH_NEXUS_TELE_45,
    };

    /** The interfaces' working copy, in the same slot order. */
    private static final int[] WORKING_COPY = {
        VarbitID.POH_NEXUS_TELE_1_TEMP, VarbitID.POH_NEXUS_TELE_2_TEMP,
        VarbitID.POH_NEXUS_TELE_3_TEMP, VarbitID.POH_NEXUS_TELE_4_TEMP,
        VarbitID.POH_NEXUS_TELE_5_TEMP, VarbitID.POH_NEXUS_TELE_6_TEMP,
        VarbitID.POH_NEXUS_TELE_7_TEMP, VarbitID.POH_NEXUS_TELE_8_TEMP,
        VarbitID.POH_NEXUS_TELE_9_TEMP, VarbitID.POH_NEXUS_TELE_10_TEMP,
        VarbitID.POH_NEXUS_TELE_11_TEMP, VarbitID.POH_NEXUS_TELE_12_TEMP,
        VarbitID.POH_NEXUS_TELE_13_TEMP, VarbitID.POH_NEXUS_TELE_14_TEMP,
        VarbitID.POH_NEXUS_TELE_15_TEMP, VarbitID.POH_NEXUS_TELE_16_TEMP,
        VarbitID.POH_NEXUS_TELE_17_TEMP, VarbitID.POH_NEXUS_TELE_18_TEMP,
        VarbitID.POH_NEXUS_TELE_19_TEMP, VarbitID.POH_NEXUS_TELE_20_TEMP,
        VarbitID.POH_NEXUS_TELE_21_TEMP, VarbitID.POH_NEXUS_TELE_22_TEMP,
        VarbitID.POH_NEXUS_TELE_23_TEMP, VarbitID.POH_NEXUS_TELE_24_TEMP,
        VarbitID.POH_NEXUS_TELE_25_TEMP, VarbitID.POH_NEXUS_TELE_26_TEMP,
        VarbitID.POH_NEXUS_TELE_27_TEMP, VarbitID.POH_NEXUS_TELE_28_TEMP,
        VarbitID.POH_NEXUS_TELE_29_TEMP, VarbitID.POH_NEXUS_TELE_30_TEMP,
        VarbitID.POH_NEXUS_TELE_31_TEMP, VarbitID.POH_NEXUS_TELE_32_TEMP,
        VarbitID.POH_NEXUS_TELE_33_TEMP, VarbitID.POH_NEXUS_TELE_34_TEMP,
        VarbitID.POH_NEXUS_TELE_35_TEMP, VarbitID.POH_NEXUS_TELE_36_TEMP,
        VarbitID.POH_NEXUS_TELE_37_TEMP, VarbitID.POH_NEXUS_TELE_38_TEMP,
        VarbitID.POH_NEXUS_TELE_39_TEMP, VarbitID.POH_NEXUS_TELE_40_TEMP,
        VarbitID.POH_NEXUS_TELE_41_TEMP, VarbitID.POH_NEXUS_TELE_42_TEMP,
        VarbitID.POH_NEXUS_TELE_43_TEMP, VarbitID.POH_NEXUS_TELE_44_TEMP,
        VarbitID.POH_NEXUS_TELE_45_TEMP,
    };

    /** {@link #SLOTS}, sorted for lookup on every varbit change. */
    private static final int[] SLOTS_SORTED = sorted(SLOTS);

    private PortalNexus() {
    }

    private static int[] sorted(int[] ids) {
        int[] copy = ids.clone();
        Arrays.sort(copy);
        return copy;
    }

    /** Whether this varbit is one of the saved attunement's slots. */
    public static boolean isSlot(int varbitId) {
        return Arrays.binarySearch(SLOTS_SORTED, varbitId) >= 0;
    }

    /** The destination a slot value names, with an alternate arrival folded in, or 0. */
    public static int destination(int slotValue) {
        return slotValue > ALTERNATE_ARRIVAL ? slotValue - ALTERNATE_ARRIVAL : slotValue;
    }

    /**
     * The destinations the saved slots name, read anywhere. Zero proves nothing here, since
     * a slot may not have been sent yet, so the answer only ever adds.
     */
    public static Set<Integer> attunedIn(IntUnaryOperator varbits) {
        return keys(SLOTS, varbits);
    }

    /**
     * Every destination the Nexus holds, read while its teleport menu is open in the
     * player's own house, where a zero slot is a real empty slot. Falls back to the working
     * copy when every saved slot reads zero, since the menu is drawn from that copy.
     *
     * @return the destinations, or null when both sets read empty, which this cannot tell
     *     apart from slots not yet sent, so it settles nothing
     */
    public static Set<Integer> fullRead(IntUnaryOperator varbits) {
        Set<Integer> saved = keys(SLOTS, varbits);
        if (!saved.isEmpty()) {
            return saved;
        }
        Set<Integer> working = keys(WORKING_COPY, varbits);
        return working.isEmpty() ? null : working;
    }

    private static Set<Integer> keys(int[] slots, IntUnaryOperator varbits) {
        Set<Integer> keys = new TreeSet<>();
        for (int slot : slots) {
            int key = destination(varbits.applyAsInt(slot));
            if (key > 0) {
                keys.add(key);
            }
        }
        return Collections.unmodifiableSet(keys);
    }
}
