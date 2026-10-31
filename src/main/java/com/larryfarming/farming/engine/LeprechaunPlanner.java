package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.detection.BottomlessBucket;
import com.larryfarming.farming.model.Arrival;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingCarryItem;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which farming tools to leave with the tool leprechaun, and where: at the last arrival that
 * uses each one.
 *
 * <p>Every leprechaun opens one shared store, so a spade left at the last patch comes back
 * out at the first patch of the next trip, and the rest of this trip carries it no further.
 * A tool is left only where a leprechaun stands, and only once nothing after that arrival
 * wants it.
 */
final class LeprechaunPlanner {

    private LeprechaunPlanner() {
    }

    /** Farming places the wiki lists no tool leprechaun at. */
    private static final Set<Arrival> NO_LEPRECHAUN =
            EnumSet.of(Arrival.ORTUS_FARM, Arrival.GREAT_CONCH);

    private static final int SEED_DIBBER = 5343;
    private static final int COMPOST = 6032;
    private static final int SUPERCOMPOST = 6034;
    private static final int ULTRACOMPOST = 21483;

    /**
     * Used only to plant, so free once the last planting is done: the dibber and loose
     * compost. Not the bottomless bucket, which a compost bin after the last planting may
     * still empty into.
     */
    private static final Set<Integer> PLANTING_ONLY =
            Set.of(SEED_DIBBER, COMPOST, SUPERCOMPOST, ULTRACOMPOST);

    /** The two bottomless buckets: the leprechaun holds one, so a second is not taken. */
    private static final Set<Integer> BOTTOMLESS_BUCKETS =
            Set.of(BottomlessBucket.EMPTY_ITEM_ID, BottomlessBucket.FILLED_ITEM_ID);

    /**
     * What to leave at each arrival, keyed by its index in {@code made}.
     *
     * @param made    each arrival's stop indices into {@code plans}, in walk order
     * @param carry   the trip's packing list; only storable lines the player brings count
     * @param underway whether the player is partway round, when only what the bag holds
     *     now can still be left
     */
    static Map<Integer, List<FarmingCarryItem>> leaveAt(List<List<Integer>> made,
            List<StopPlan> plans, List<List<Crop>> cropFor, List<FarmingCarryItem> carry,
            boolean underway, PlayerState state) {
        Map<Integer, List<FarmingCarryItem>> leave = new HashMap<>();
        for (FarmingCarryItem line : carry) {
            if (!ItemCounts.leprechaunStores(line.getItemId()) || !broughtAlong(line, underway)) {
                continue;
            }
            if (BOTTOMLESS_BUCKETS.contains(line.getItemId()) && storesABucket(state)) {
                continue;
            }
            int last = lastUse(made, plans, cropFor, PLANTING_ONLY.contains(line.getItemId()));
            if (last < 0 || !hasLeprechaun(made.get(last), plans)) {
                continue;
            }
            leave.computeIfAbsent(last, at -> new ArrayList<>()).add(line);
        }
        return leave;
    }

    /** In the bag now, or, before setting off, about to be. */
    private static boolean broughtAlong(FarmingCarryItem line, boolean underway) {
        return line.getAvailability() == FarmingCarryItem.Availability.CARRIED
                || (!underway && line.getAvailability() == FarmingCarryItem.Availability.BANKED);
    }

    /**
     * The last arrival that uses the tool: any visit for the spade and the like, a planting
     * for the dibber and the composts. -1 when none does.
     */
    private static int lastUse(List<List<Integer>> made, List<StopPlan> plans,
            List<List<Crop>> cropFor, boolean plantingOnly) {
        for (int arrival = made.size() - 1; arrival >= 0; arrival--) {
            for (int index : made.get(arrival)) {
                if (!plans.get(index).isVisit()) {
                    continue;
                }
                if (!plantingOnly || !cropFor.get(index).isEmpty()) {
                    return arrival;
                }
            }
        }
        return -1;
    }

    /** Whether a tool leprechaun stands at this arrival. */
    static boolean hasLeprechaun(List<Integer> arrival, List<StopPlan> plans) {
        return !NO_LEPRECHAUN.contains(plans.get(arrival.get(0)).getStop().getArrival());
    }

    private static boolean storesABucket(PlayerState state) {
        return state != null && BottomlessBucket.isStored(state);
    }

    /** "Leave your spade and seed dibber with the leprechaun." */
    static String sentence(List<FarmingCarryItem> tools) {
        return "Leave your " + LoadoutPlanner.inSentence(tools, "and") + " with the leprechaun.";
    }
}
