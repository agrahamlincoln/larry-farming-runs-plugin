package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.model.PlayerState;
import java.util.Map;
import lombok.AllArgsConstructor;

/**
 * What a farmer takes to protect a stop's patches, planned only from what the player already
 * owns.
 *
 * <p>Protection is worth paying for only with the payment to spare, so a player short of it
 * is never sent to gather it. Owned counts the bank, as the bag does: the trip starts at a
 * bank, so a banked payment comes along.
 *
 * <p>Paid per patch planted this visit. A patch left growing was paid for when it went in,
 * and a player owning one patch's fee of two protects one.
 */
final class Protection {

    /** Fossil Island's currency. Stackable, so any amount takes one slot. */
    static final int NUMULITE = 21555;

    /** Mernia's fee for one seaweed patch. */
    private static final int NUMULITE_PER_SEAWEED_PATCH = 200;

    private static final Map<PatchType, Fee> PER_PATCH = Map.of(PatchType.SEAWEED,
            new Fee("Mernia", NUMULITE, "Numulite", NUMULITE_PER_SEAWEED_PATCH, 1));

    private Protection() {
    }

    /** One farmer's payment: who takes it, what, how many, and for how many patches. */
    @AllArgsConstructor
    static final class Fee {
        final String farmer;
        final int itemId;
        final String name;
        final int amount;
        final int patches;
    }

    /**
     * What this stop pays its farmer this visit, or null where it pays nobody: the stop does
     * not ask to, no fee is known for its patch type, nothing goes in, or the player owns too
     * little for one patch.
     *
     * @param planting how many of the stop's patches are planted this visit
     */
    static Fee paymentAt(RunStop stop, int planting, PlayerState state) {
        if (planting <= 0 || !stop.getActions().contains(RunStop.StopAction.PAY_PROTECTION)) {
            return null;
        }
        PatchType type = stop.patchType();
        Fee each = type == null ? null : PER_PATCH.get(type);
        if (each == null) {
            return null;
        }
        int affordable = ItemCounts.countOwned(state, each.itemId, null) / each.amount;
        int patches = Math.min(Math.min(planting, stop.getPatches().size()), affordable);
        return patches == 0 ? null : new Fee(each.farmer, each.itemId, each.name, each.amount * patches, patches);
    }
}
