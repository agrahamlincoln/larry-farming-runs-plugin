package com.larryfarming.farming.detection;

import com.larryfarming.data.Tables;
import java.util.*;
import java.util.function.IntUnaryOperator;

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

    /** Each slot's saved and working-copy varbits, slots 1 to 45 in order. */
    private static final List<Tables.Row> TABLE = Tables.read("/com/larryfarming/portal-nexus.tsv", 5);

    /** The saved attunement, slots 1 to 45 in order. */
    private static final int[] SLOTS = column(1);

    /** The interfaces' working copy, in the same slot order. */
    private static final int[] WORKING_COPY = column(3);

    private static int[] column(int column) {
        int[] ids = new int[TABLE.size()];
        for (int i = 0; i < ids.length; i++) {
            Tables.Row row = TABLE.get(i);
            if (row.integer(0) != i + 1) {
                throw row.error("slot out of order");
            }
            ids[i] = row.integer(column);
        }
        return ids;
    }

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
