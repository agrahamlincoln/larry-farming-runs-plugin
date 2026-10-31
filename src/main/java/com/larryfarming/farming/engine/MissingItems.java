package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.model.Bank;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingCarryItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Partway round, the warning on the next stop's card when the bag lacks something the stop
 * cannot be done without. It also catches a player who walked past the bank that held it.
 */
final class MissingItems {

    private MissingItems() {
    }

    private static final int SPADE = 952;
    private static final int SEED_DIBBER = 5343;
    private static final int PLAIN_AXE = 1351;
    private static final int COINS = 995;

    /**
     * What the stop cannot be done without: the spade, the dibber, an axe for a tree to
     * fell, the gardener's coins, and the seeds. The bucket, the energy potion, the seed box
     * and the like make the stop easier but never stop it, so they raise no warning.
     */
    private static final Set<Integer> NEEDED = needed();

    private static Set<Integer> needed() {
        Set<Integer> ids = new HashSet<>(CarryPlanner.formsOf(PLAIN_AXE));
        ids.add(SPADE);
        ids.add(SEED_DIBBER);
        ids.add(COINS);
        return ids;
    }

    /**
     * What the next stop needs that the bag lacks: "No axe in your bag. Bank at Catherby
     * first.", or "Not enough ranarr seed" where some are carried. Empty when nothing is
     * missing.
     *
     * <p>Only what the stop itself uses: the way there is left out, since a player standing
     * at the stop has spent the tablet that brought them. Only what is owned: an item owned
     * nowhere is already greyed on the packing list, and no bank holds it. A tool stored with
     * the leprechaun counts as missing only where no leprechaun stands.
     *
     * <p>A seed line's carried count includes saplings, so a carried seed of a tree crop
     * reads as some in the bag.
     *
     * @param here        the next arrival's stops, its plantings index for index
     * @param leprechaun  whether a tool leprechaun stands at that arrival
     * @param bank        the bank to name, or null to name none
     */
    static String at(List<StopPlan> here, List<List<Crop>> plantedHere, boolean leprechaun,
            Bank bank, PlayerState state, CapabilityResolver resolver, FarmingInputs inputs) {
        List<StopPlan> standingThere = new ArrayList<>(here.size());
        for (StopPlan plan : here) {
            standingThere.add(CarryPlanner.withoutTravel(plan));
        }
        List<FarmingCarryItem> missing = new ArrayList<>();
        List<FarmingCarryItem> tooFew = new ArrayList<>();
        for (FarmingCarryItem line : CarryPlanner.plan(standingThere, List.of(), plantedHere,
                state, resolver, inputs)) {
            boolean needed = line.getKind() == FarmingCarryItem.Kind.SEED
                    || NEEDED.contains(line.getItemId());
            boolean away = line.getAvailability() == FarmingCarryItem.Availability.BANKED
                    || (!leprechaun
                            && line.getAvailability() == FarmingCarryItem.Availability.AT_LEPRECHAUN);
            if (!needed || !away) {
                continue;
            }
            if (line.getCarriedCount() > 0) {
                tooFew.add(line);
            } else {
                missing.add(line);
            }
        }
        if (missing.isEmpty() && tooFew.isEmpty()) {
            return "";
        }
        StringBuilder said = new StringBuilder();
        if (!missing.isEmpty()) {
            said.append("No ").append(LoadoutPlanner.inSentence(missing, "or"))
                    .append(" in your bag.");
        }
        if (!tooFew.isEmpty()) {
            said.append(said.length() == 0 ? "" : " ").append("Not enough ")
                    .append(LoadoutPlanner.inSentence(tooFew, "and")).append(" in your bag.");
        }
        if (bank != null) {
            said.append(' ').append(bank.getRowTitle()).append(" first.");
        }
        return said.toString();
    }
}
