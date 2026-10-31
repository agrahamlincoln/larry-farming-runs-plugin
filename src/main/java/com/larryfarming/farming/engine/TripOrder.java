package com.larryfarming.farming.engine;

import com.larryfarming.farming.model.Arrival;
import java.util.ArrayList;
import java.util.List;

/**
 * The order a single run's stops are walked in, and those stops grouped into arrivals.
 *
 * <p>Shared by the crop allocation, which deals seeds in walking order, and the walkthrough,
 * which draws one row per arrival. A merged trip walks {@link Itinerary}'s circuit instead.
 */
final class TripOrder {

    private TripOrder() {
    }

    /**
     * The stops in the order they will actually be walked, with each woven-in extra beside
     * the patch it shares a trip with.
     *
     * <p>A catalogue run lists its stops in seat order, which already puts each extra beside
     * the patch it shares an arrival with: the Catherby white lily beside the Catherby herb
     * patch, not after a lap of the map. This keeps that so for a list in any other order.
     *
     * <p>Matched on the {@link Arrival}, so "same trip" means one visit. An extra with no
     * matching stop (a cactus at Al Kharid, the Ardougne Monastery bushes) stays where the
     * list puts it.
     *
     * <p>Order only. Seeds are settled against the catalogue's order before this runs.
     */
    static List<Integer> inTripOrder(List<StopPlan> plans) {
        // Decided up front because the match looks both ways: an extra written last can
        // belong early.
        boolean[] movesToItsPatch = new boolean[plans.size()];
        for (int i = 0; i < plans.size(); i++) {
            movesToItsPatch[i] = plans.get(i).getStop().isOptional()
                    && sharesAnArrival(plans, arrivalOf(plans.get(i)));
        }

        List<Integer> ordered = new ArrayList<>(plans.size());
        boolean[] placed = new boolean[plans.size()];
        for (int i = 0; i < plans.size(); i++) {
            if (movesToItsPatch[i]) {
                continue;
            }
            ordered.add(i);
            if (plans.get(i).getStop().isOptional()) {
                continue;
            }
            Arrival arrival = arrivalOf(plans.get(i));
            for (int extra = 0; extra < plans.size(); extra++) {
                if (movesToItsPatch[extra] && !placed[extra]
                        && arrivalOf(plans.get(extra)) == arrival) {
                    ordered.add(extra);
                    placed[extra] = true;
                }
            }
        }
        return ordered;
    }

    /** Whether the run proper already goes to this arrival, so an extra there rides along. */
    private static boolean sharesAnArrival(List<StopPlan> plans, Arrival arrival) {
        for (StopPlan plan : plans) {
            if (!plan.getStop().isOptional() && arrivalOf(plan) == arrival) {
                return true;
            }
        }
        return false;
    }

    /**
     * The trip as arrivals rather than patches: consecutive stops at the same place become
     * one visit.
     *
     * <p>Two stops naming one {@link Arrival} are one journey; different arrivals are two
     * journeys however close on the map. Catherby's herb, flower and allotment patches are
     * one row, while Ardougne's patches and its Monastery bushes are two: one cloak serves
     * both but lands in a different place for each.
     *
     * <p><b>Skipped stops merge too</b>, or a skip between two visits to one place would
     * split them and the list would reshape around patch state. The set-aside lines inside
     * the row say which patch each reason is about.
     */
    static List<List<Integer>> groupByArrival(List<StopPlan> plans) {
        List<List<Integer>> groups = new ArrayList<>();
        List<Integer> current = null;
        Arrival currentArrival = null;
        for (int index : inTripOrder(plans)) {
            StopPlan plan = plans.get(index);
            Arrival arrival = arrivalOf(plan);
            if (current != null && arrival == currentArrival) {
                current.add(index);
                continue;
            }
            current = new ArrayList<>();
            current.add(index);
            currentArrival = arrival;
            groups.add(current);
        }
        return groups;
    }

    /**
     * The place a stop is worked from. Two stops with the same arrival are one visit.
     *
     * <p>Stated by the catalogue, not derived from map regions, which mislead both ways.
     * Catherby's herb cluster and its fruit tree are two regions but one arrival. Ardougne's
     * farm and its Monastery bushes share one cloak teleport but are two arrivals, 150 tiles
     * apart.
     */
    private static Arrival arrivalOf(StopPlan plan) {
        return plan.getStop().getArrival();
    }
}
