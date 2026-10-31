package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.CropState;
import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.model.PlayerState;
import java.util.List;
import java.util.Locale;

/**
 * Whether Larry is keeping the compost stock up, and where each bin's fill comes from.
 *
 * <p>A farm bin is filled from the allotments beside it: they grow watermelons, and the
 * player harvests them straight into the bin. The Farming Guild's big bin has no allotment
 * of its own, since the guild's allotments hold contract preplants. It is filled from a
 * watermelon contract's harvest, and from supercompostable items the player already owns,
 * withdrawn at the guild's bank.
 *
 * <p>The bag, the inventory simulation, the skip rules and the row all read the answers
 * here, so the items packed, the slots charged and the lines printed agree.
 */
final class CompostUpkeep {

    /**
     * What to fetch for the guild's big bin, best first. Supercompostable produce only, so a
     * bin of either grade takes it, ordered by how much of it a farming account tends to
     * have spare. Only what the player owns is asked for.
     */
    static final List<Crop> BANK_FILL = List.of(Crop.PINEAPPLE, Crop.WATERMELON, Crop.PALM,
            Crop.PAPAYA, Crop.POISON_IVY, Crop.JANGERBERRIES);

    /**
     * The fewest items one ready patch is sure to yield: an allotment's three harvest lives
     * with no compost on it, each taken. Compost adds lives, but the game state does not
     * show what went on a patch, so only this many is certain.
     */
    private static final int FEWEST_FROM_ONE_PATCH = 3;

    private CompostUpkeep() {
    }

    /**
     * Whether Larry works the bins: fills them, plants their allotments, and adds the ash to
     * and empties a finished bin. Off because the stock is high, he leaves every bin and its
     * allotments as they stand ({@link #leavesAlone}). A full bin keeps its compost until
     * upkeep comes back on.
     *
     * <p>On while the Compost bins run is on, the player can grow watermelon, the bins' one
     * fill source, and the stock has not yet reached the player's high threshold since it
     * last fell below the low one ({@link CompostStock#upkeepAfter}). An unread Farming level
     * counts as able. This is the one place the decision is made.
     *
     * <p>Switching the run off silences every bin. Upkeep off leaves them alone instead
     * ({@link #leavesAlone}), so the runs list keeps the bins' row and its stock line.
     */
    static boolean isOn(FarmingInputs inputs, PlayerState state) {
        return canWork(inputs, state) && inputs.getCompostStock().isUpkeepOn();
    }

    /**
     * Whether Larry skips this stop because upkeep is off: a compost bin, or an allotment
     * that fills one. The trip and the runs list both ask this, so a bin left full never
     * makes a run due.
     *
     * <p>A bin is left alone only when the stock turned upkeep off. Below watermelon's level
     * the player fills a bin by hand, so Larry still finishes one they closed.
     */
    static boolean leavesAlone(RunStop stop, FarmingInputs inputs, PlayerState state) {
        if (stop.feedsAbin()) {
            return !isOn(inputs, state);
        }
        return PlantingGate.worksAbin(stop) && canWork(inputs, state)
                && !inputs.getCompostStock().isUpkeepOn();
    }

    /** Whether the run is on and the player can grow the bins' fill, whatever the stock. */
    private static boolean canWork(FarmingInputs inputs, PlayerState state) {
        if (!inputs.getPreferences().worksCompostBins()) {
            return false;
        }
        CropData watermelon = CropData.forCrop(Crop.WATERMELON);
        return watermelon == null || !FixedCrops.belowLevel(state, watermelon);
    }

    /**
     * The Compost bins run row's line where the stock explains the run, or null: Larry asks
     * for a reading of a bottomless bucket he has no figure for, and otherwise says the stock
     * is why the bins have left the trip.
     */
    static String stockLine(FarmingInputs inputs, PlayerState state) {
        if (!canWork(inputs, state)) {
            return null;
        }
        CompostStock stock = inputs.getCompostStock();
        if (stock.isBucketUnread()) {
            return "Check your bottomless bucket";
        }
        if (!stock.isUpkeepOn()) {
            // A bare count: a "Stocked: " prefix does not fit the row beside the chevron.
            return String.format(Locale.ENGLISH, "%,d", stock.getPatches())
                    + " uses of ultracompost";
        }
        return null;
    }

    /**
     * How many items this bin will take on this visit: its room now, or for a bin about to
     * be emptied, its whole capacity once the last bucket is out. 0 for anything else.
     */
    static int roomThisVisit(RunStop bin, FarmingInputs inputs) {
        int room = StopSkipRules.roomLeftIn(bin, inputs);
        if (room > 0) {
            return room;
        }
        for (PatchRef ref : bin.getPatches()) {
            if (PatchWork.at(inputs.patchAt(ref), bin, inputs.getNowEpochSec())
                    == PatchWork.Act.EMPTY_BIN) {
                return capacityOf(bin);
            }
        }
        return 0;
    }

    /** Fifteen items, or thirty for the guild's big bin. */
    static int capacityOf(RunStop bin) {
        return isTheBigBin(bin)
                ? Crop.BIG_COMPOST.getHarvestStages() : Crop.COMPOST.getHarvestStages();
    }

    /** Whether this is the Farming Guild's big bin, which no allotment feeds. */
    static boolean isTheBigBin(RunStop stop) {
        return stop.patchType() == PatchType.BIG_COMPOST;
    }

    /**
     * Whether this feeding allotment holds watermelons ready to go into its bin. The
     * harvest goes into the bin, so the row says to pick it there and not as a patch's own
     * work.
     */
    static boolean holdsReadyFill(RunStop allotment, FarmingInputs inputs) {
        if (!allotment.feedsAbin()) {
            return false;
        }
        for (PatchRef ref : allotment.getPatches()) {
            PatchPrediction seen = inputs.patchAt(ref);
            if (seen != null && seen.getCrop() == allotment.getFixedCrop()
                    && PatchWork.at(seen, allotment, inputs.getNowEpochSec())
                            == PatchWork.Act.HARVEST) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this feeding allotment's work is a reason to travel. Only an allotment beside a
     * bin with its own route qualifies, as at Prifddinas: no herb visit passes that bin, so
     * nothing else would send the player there to start the loop. It also needs upkeep on, a
     * bin that will take fill on this visit, and no crop of the player's own in the
     * allotment.
     *
     * <p>The runs list and the trip both ask this, so a run due for the allotment is drawn
     * with the bin's route.
     */
    static boolean bringsThePlayer(RunStop allotment, FarmingInputs inputs, PlayerState state) {
        if (!allotment.feedsAbin()) {
            return false;
        }
        RunStop bin = FarmingCatalogue.binStop(allotment.getFeeds());
        return bin != null && !bin.isOptional() && isOn(inputs, state)
                && roomThisVisit(bin, inputs) > 0
                && !StopSkipRules.holdsAnotherCrop(allotment, inputs);
    }

    /**
     * Whether watermelons are still growing in an allotment that feeds this bin, so the bin
     * waits for them. Read off the catalogue, because a growing allotment has left the trip.
     * An allotment some other stop grows its own crop in is not the bin's: Catherby north's
     * watermelons, ticked in snape grass's place, go into the bag.
     */
    static boolean watermelonsGrowingFor(RunStop bin, FarmingInputs inputs) {
        for (RunStop allotment : FarmingCatalogue.feedersOf(bin.primaryPatch())) {
            if (PlantingGate.grownForItsOwnCrop(allotment, inputs.getPreferences())) {
                continue;
            }
            for (PatchRef ref : allotment.getPatches()) {
                PatchPrediction seen = inputs.patchAt(ref);
                if (seen != null && seen.getCrop() == allotment.getFixedCrop()
                        && StopSkipRules.isStillGrowing(seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a watermelon contract's crop stands ready in a guild allotment. Its harvest
     * finishes the contract and fills the big bin in one harvest.
     */
    static boolean watermelonContractReady(FarmingInputs inputs) {
        if (inputs.getContractCrop() != Crop.WATERMELON) {
            return false;
        }
        for (PatchRef ref : FarmingCatalogue.contractPatches()) {
            if (ref.resolve() == null || ref.resolve().getType() != PatchType.ALLOTMENT) {
                continue;
            }
            PatchPrediction seen = inputs.patchAt(ref);
            if (seen != null && seen.getCrop() == Crop.WATERMELON
                    && seen.getState() != CropState.DISEASED
                    && seen.getState() != CropState.DEAD
                    && seen.isReadyAt(inputs.getNowEpochSec())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether harvesting this many ready patches is sure to fill this much room. A bin
     * closes only once full, so the row says to close it outright only when this holds.
     */
    static boolean harvestsFill(int room, int readyPatches) {
        return room <= FEWEST_FROM_ONE_PATCH * readyPatches;
    }

    /**
     * Supercompostable items to carry to the guild's big bin from the bank before it, never
     * more than the player owns, so Larry never asks them to buy any. 0 for every other bin,
     * which its allotments fill, and on a watermelon contract, whose harvest goes in first:
     * whatever room it leaves is topped up at the guild's own bank afterwards. That top-up is
     * withdrawn and spent at the guild, so on purpose no bank stop charges slots for it.
     *
     * @param state null where ownership is unknown, which asks for nothing
     */
    static int bankFillFor(RunStop bin, FarmingInputs inputs, PlayerState state) {
        if (!isTheBigBin(bin) || state == null || !isOn(inputs, state)
                || watermelonContractReady(inputs)) {
            return 0;
        }
        int room = roomThisVisit(bin, inputs);
        return room == 0 ? 0 : Math.min(room, ownedBankFill(state));
    }

    /** How many of the {@link #BANK_FILL} items the player owns, all kinds together. */
    static int ownedBankFill(PlayerState state) {
        if (state == null) {
            return 0;
        }
        int owned = 0;
        for (Crop crop : BANK_FILL) {
            owned += ItemCounts.countOwned(state, crop.getItemId(), null);
        }
        return owned;
    }
}
