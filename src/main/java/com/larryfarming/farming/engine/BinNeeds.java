package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.farming.catalogue.CompostUpgrade;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.model.PlayerState;
import java.util.List;

/**
 * What the compost bins in a set of stops want carried to them.
 *
 * <p>{@link CarryPlanner} names the items, {@link InventoryDemands} charges their inventory
 * slots, and {@code WithdrawalPlanner} slices the same answer. Sharing one answer keeps the
 * bag from listing items the simulation never charged for.
 *
 * <p>The caller sets the scope: a whole trip for the packing list, one arrival's stops for a
 * bank row.
 *
 * <p><b>Reads bins through {@link PatchWork}, not the raw patch state.</b> The game shows a
 * closed bin as {@code HARVESTABLE} only to a player standing next to it. Seen from a bank, a
 * closed bin reads {@code GROWING} with its timer run out. The crop names the grade either way.
 */
final class BinNeeds {

    private static final BinNeeds NONE = new BinNeeds(0, 0, 0);

    private final int fillItems;
    private final int ash;
    private final int doses;

    private BinNeeds(int fillItems, int ash, int doses) {
        this.fillItems = fillItems;
        this.ash = ash;
        this.doses = doses;
    }

    static BinNeeds none() {
        return NONE;
    }

    /**
     * Summed over every bin these stops are visiting.
     *
     * @param plans the stops of one trip, one leg, or one arrival
     * @param state the player, whose own items are the only fill asked for; null asks for
     *     none
     */
    static BinNeeds over(List<StopPlan> plans, FarmingInputs inputs, PlayerState state) {
        int fill = 0;
        int ash = 0;
        int doses = 0;
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            // Carried only to the guild's big bin. Every other bin is filled from the
            // allotments beside it.
            fill += CompostUpkeep.bankFillFor(plan.getStop(), inputs, state);
            for (PatchRef ref : plan.getStop().getPatches()) {
                PatchPrediction seen = inputs.patchAt(ref);
                if (PatchWork.at(seen, plan.getStop(), inputs.getNowEpochSec())
                        != PatchWork.Act.EMPTY_BIN) {
                    continue;
                }
                ash += CompostUpgrade.ashFor(seen.getCrop());
                if (CompostUpgrade.wantsPotion(seen.getCrop())) {
                    // One dose a bin whatever its size, unlike ash, which scales with the bin.
                    doses++;
                }
            }
        }
        return fill == 0 && ash == 0 && doses == 0 ? NONE : new BinNeeds(fill, ash, doses);
    }

    /**
     * Produce to carry in for the guild's big bin, one slot each because un-noted produce
     * does not stack. The simulation frees their slots at the guild.
     */
    int getFillItems() {
        return fillItems;
    }

    /** Volcanic ash across every finished bin here: 25 a bin, 50 for the guild's big one. */
    int getAsh() {
        return ash;
    }

    /** Doses of compost potion: one per bin that finished as plain compost. */
    int getDoses() {
        return doses;
    }
}
