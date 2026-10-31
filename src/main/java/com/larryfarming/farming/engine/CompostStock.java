package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.detection.BottomlessBucket;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.model.PlayerState;
import lombok.Value;

/**
 * How much ultracompost the player has, counted in patches it can treat, and whether that is
 * few enough for Larry to work the compost bins.
 *
 * <p>One use of the bottomless bucket treats one patch. Poured into the bottomless bucket, a
 * bucket of compost gives two uses, so a player who owns one counts each loose bucket twice.
 * Without it, or with it stored holding another compost, a loose bucket treats one patch.
 *
 * <p>Compost still in a bin does not count. The player cannot put it on a patch until the bin
 * is emptied, and with upkeep off Larry leaves every bin as it stands, full or rotting. So a
 * stock at the high threshold is always compost the player can use.
 *
 * <p>Upkeep is a thermostat. It switches on when the stock falls below the player's low
 * threshold, off when it reaches the high one, and keeps its last state in between, so it
 * does not flick on and off around one number.
 */
@Value
public class CompostStock {

    /** What a plan made without a stock reading assumes: upkeep on, nothing counted. */
    public static final CompostStock UNREAD = new CompostStock(0, true, false);

    private static final int ULTRACOMPOST = Crop.ULTRACOMPOST.getItemId();

    /** Patches the player's ultracompost can treat. */
    int patches;

    /** Whether Larry is to work the bins towards more compost. */
    boolean upkeepOn;

    /**
     * Whether the player owns a bottomless bucket away from the leprechaun and Larry has never
     * read its uses, so it counts as empty.
     */
    boolean bucketUnread;

    /**
     * The stock this player holds: loose ultracompost carried, banked or at the leprechaun,
     * and the bottomless bucket's uses.
     *
     * <p>The count never reads high: a reading counts only while a filled bucket is seen
     * among the player's items, so a bucket since emptied, sold or not yet seen in the bank
     * adds nothing.
     *
     * @param bucketReading the uses of ultracompost a carried or banked bucket was last read
     *     to hold, or null when it has never been read; it then counts as none
     */
    public static int count(PlayerState state, Integer bucketReading) {
        if (state == null) {
            return 0;
        }
        int bucket;
        if (BottomlessBucket.isStored(state)) {
            bucket = BottomlessBucket.storedUltracompostUses(state);
        } else if (bucketReading != null
                && ItemCounts.ownsAny(state, BottomlessBucket.FILLED_ITEM_ID)) {
            bucket = bucketReading;
        } else {
            bucket = 0;
        }
        // countOwned already includes what the tool leprechaun holds.
        // A bucket stored holding another compost cannot take a bin of ultracompost.
        boolean intoTheBucket = ownsBucket(state)
                && !BottomlessBucket.storedWithOtherCompost(state);
        int loose = ItemCounts.countOwned(state, ULTRACOMPOST, null);
        return (intoTheBucket ? 2 * loose : loose) + bucket;
    }

    /**
     * Whether any compost bin was seen within one trip's span. A bin reports only while the
     * player stands near it, so a fresh sighting means they are at the bins on this trip.
     */
    public static boolean aBinSeenThisTrip(FarmingInputs inputs) {
        for (FarmingWorldMap.PatchDef bin : FarmingWorldMap.allPatches()) {
            if (bin.getType() != PatchType.COMPOST && bin.getType() != PatchType.BIG_COMPOST) {
                continue;
            }
            PatchPrediction seen = inputs.patchAt(
                    PatchRef.of(bin.getRegionId(), bin.getVarbitId(), bin.displayName()));
            if (seen != null && RunScheduler.seenThisTrip(seen.getObservedAtEpochSec(),
                    inputs.getNowEpochSec())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player owns a bottomless bucket anywhere, the leprechaun included. */
    public static boolean ownsBucket(PlayerState state) {
        return BottomlessBucket.isStored(state)
                || ItemCounts.ownsAny(state, BottomlessBucket.FILLED_ITEM_ID)
                || ItemCounts.ownsAny(state, BottomlessBucket.EMPTY_ITEM_ID);
    }

    /**
     * Whether the player owns a bottomless bucket Larry has no figure for: one away from the
     * leprechaun, never read.
     */
    public static boolean bucketUnread(PlayerState state, Integer bucketReading) {
        return bucketReading == null && !BottomlessBucket.isStored(state)
                && ItemCounts.ownsAny(state, BottomlessBucket.FILLED_ITEM_ID);
    }

    /**
     * Whether the player's only bottomless bucket is seen empty: the bank has been seen, no
     * bucket is at the leprechaun, and the empty bucket is owned with no filled one. The
     * bucket's "Empty" option says nothing in chat, so this is how a reading learns it.
     */
    public static boolean bucketSeenEmpty(PlayerState state) {
        return state != null && state.getBankLastSeen() != null
                && !BottomlessBucket.isStored(state)
                && ItemCounts.ownsAny(state, BottomlessBucket.EMPTY_ITEM_ID)
                && !ItemCounts.ownsAny(state, BottomlessBucket.FILLED_ITEM_ID);
    }

    /**
     * Whether upkeep is on after this reading of the stock: on below the low threshold, off
     * at the high one or above, and as it was in between.
     *
     * <p>A count that may read short, from a bank not yet seen or a bucket with no figure,
     * is a floor on the stock. Reaching the high threshold still switches upkeep off, since
     * the true stock is at least as large. Falling below the low one proves nothing, so
     * upkeep keeps its state.
     *
     * @param wasOn the state last decided, or null when none has been; upkeep starts on
     * @param whole whether the count saw every place the stock can be
     */
    public static boolean upkeepAfter(Boolean wasOn, int patches, boolean whole,
            FarmingPreferences prefs) {
        if (patches >= prefs.stopCompostAt()) {
            return false;
        }
        if (whole && patches < prefs.makeCompostBelow()) {
            return true;
        }
        return wasOn == null || wasOn;
    }
}
