package com.larryfarming.farming.detection;

/**
 * Notices when an Ardougne cloak has actually spent one of its farm teleports.
 *
 * <p>The farm teleport is capped at three a day on a cloak 2 and five on a cloak 3. The game
 * reports no usable count: varbit 6069, the one named for the cloak, is a single bit and
 * cannot hold either number. So {@link com.larryfarming.farming.engine.DailyTeleportLedger}
 * counts uses as they happen, and this watch feeds it.
 *
 * <p><b>A click alone is not counted.</b> Clicking the option in the Wilderness, or with
 * the allowance gone, spends nothing, and overcounting would skip a patch silently. The
 * click only arms the watch. The use is recorded when the player arrives at the farm patch
 * within a few ticks, and an unanswered click times out. An arrival with no click behind it
 * (walking, fairy ring) is ignored.
 *
 * <p>No RuneLite types.
 */
public final class ArdougneFarmTeleportWatch {

    /** The option's name in the inventory, and when worn. */
    private static final String[] TELEPORT_OPTIONS = {"Farm Teleport", "Ardougne Farm"};

    private static final int[] CLOAK_ITEM_IDS = {13121, 13122, 13123, 13124};

    /**
     * Where the cloak drops the player: between the wiki's (2665, 3376) and RuneLite's world
     * map (2673, 3375), since the game scatters arrivals anyway.
     */
    private static final int LANDING_X = 2669;
    private static final int LANDING_Y = 3376;

    /**
     * Covers both published tiles and the scatter. It can be generous because nothing fires
     * without a {@link #clicked} from outside the square.
     */
    private static final int LANDING_RADIUS = 16;

    /**
     * A teleport resolves in three or four ticks. Ten allows for lag and still expires
     * before the player could walk in from outside the square.
     */
    private static final int WINDOW_TICKS = 10;

    private static final int NOT_ARMED = Integer.MIN_VALUE;

    private int armedAtTick = NOT_ARMED;

    /**
     * Whether this option is the farm teleport, under either name the game gives it.
     *
     * <p>Separate from {@link #isCloak} on purpose. The option is on the event, but the item
     * id is on the menu entry for an inventory click and on the equipment widget for a worn
     * cloak. The id on {@code MenuOptionClicked} is wrong for a worn cloak, so the caller
     * must look up the worn id itself. Check this cheap half first.
     */
    public static boolean isFarmTeleportOption(String option) {
        if (option == null) {
            return false;
        }
        for (String known : TELEPORT_OPTIONS) {
            if (known.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    /** Whether this item is an Ardougne cloak of any tier. */
    public static boolean isCloak(int itemId) {
        for (int id : CLOAK_ITEM_IDS) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Arms the watch for a click made from {@code (x, y)}. Ignored when the player is already
     * at the patch, where an arrival would otherwise confirm itself at once.
     */
    public void clicked(int tick, int x, int y) {
        if (atLanding(x, y)) {
            return;
        }
        armedAtTick = tick;
    }

    /**
     * Offers the player's current position, and answers whether a teleport just landed.
     * True once per teleport, because the watch disarms as it answers.
     */
    public boolean arrived(int tick, int x, int y) {
        if (armedAtTick == NOT_ARMED) {
            return false;
        }
        if (tick - armedAtTick > WINDOW_TICKS || tick < armedAtTick) {
            armedAtTick = NOT_ARMED;
            return false;
        }
        if (!atLanding(x, y)) {
            return false;
        }
        armedAtTick = NOT_ARMED;
        return true;
    }

    /** Whether a click is waiting to be confirmed, so a quiet tick can skip the rest. */
    public boolean isArmed() {
        return armedAtTick != NOT_ARMED;
    }

    /** Forgets a pending click, for a logout or an account switch. */
    public void clear() {
        armedAtTick = NOT_ARMED;
    }

    private static boolean atLanding(int x, int y) {
        return Math.abs(x - LANDING_X) <= LANDING_RADIUS && Math.abs(y - LANDING_Y) <= LANDING_RADIUS;
    }
}
