package com.larryfarming.farming.detection;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notices a seed going into the ground before the patch says so, and holds the plan until
 * it does.
 *
 * <p>Planting takes the seed from the bag, or the seed box, a few ticks before the patch's
 * varbit shows the crop. In between, a plan sees the seed gone and the patch still empty, and
 * asks the player to fetch another from the bank. Holding the plan over those ticks keeps
 * the one already on screen, which still counted the seed.
 *
 * <p>The hold ends when the tracker records a patch change, or after {@link #HOLD_TICKS}. A
 * seed leaving in a tick when the bank changed was banked, and holds nothing.
 *
 * <p>No RuneLite types.
 */
public final class PlantingWatch {

    /** The patch shows a planting two to four ticks after the seed leaves. */
    static final int HOLD_TICKS = 5;

    private Map<Integer, Integer> carriedBefore;
    private List<ItemStack> bankBefore;
    private long patchChangesBefore;
    private int holdLeft;

    /** Whether a plan built now would miss a seed that is going into the ground. */
    public boolean holding() {
        return holdLeft > 0;
    }

    /** Forget every reading, as at logout. */
    public void clear() {
        carriedBefore = null;
        bankBefore = null;
        patchChangesBefore = 0;
        holdLeft = 0;
    }

    /**
     * One game tick.
     *
     * @param patchChanges how many times the tracker has recorded a patch change, which
     *     moves when the planting shows
     * @return whether the hold ended this tick
     */
    public boolean tick(PlayerState state, long patchChanges) {
        boolean was = holding();
        Map<Integer, Integer> carried = seedsCarried(state);
        boolean banked = bankBefore != null && state.getBank() != bankBefore;
        if (holdLeft > 0) {
            holdLeft = patchChanges != patchChangesBefore ? 0 : holdLeft - 1;
        }
        if (carriedBefore != null && !banked && lostASeed(carriedBefore, carried)) {
            holdLeft = HOLD_TICKS;
        }
        carriedBefore = carried;
        bankBefore = state.getBank();
        patchChangesBefore = patchChanges;
        return was && !holding();
    }

    private static boolean lostASeed(Map<Integer, Integer> before, Map<Integer, Integer> after) {
        for (Map.Entry<Integer, Integer> seed : before.entrySet()) {
            if (after.getOrDefault(seed.getKey(), 0) < seed.getValue()) {
                return true;
            }
        }
        return false;
    }

    /** Every seed and sapling on the player, the seed box included. */
    private static Map<Integer, Integer> seedsCarried(PlayerState state) {
        Map<Integer, Integer> carried = new HashMap<>();
        for (CropData data : CropData.values()) {
            int held = ItemCounts.countCarried(state, data.getSeedItemId(), null);
            if (held > 0) {
                carried.merge(data.getSeedItemId(), held, Math::max);
            }
        }
        return carried;
    }
}
