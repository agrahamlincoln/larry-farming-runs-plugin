package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.Arrivals;
import com.larryfarming.farming.catalogue.Seats;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import java.util.*;
import lombok.Value;

/**
 * One trip, made of every crop family that has work waiting.
 *
 * <p>Every run is planned, then the stops share one circuit, so a place is visited once
 * however many families sent the player there.
 *
 * <p>Per-run planning (rungs, seed allocation, seed shortfalls) happens before this, per run
 * and in run order, because the seed rotation is allocated in the catalogue's patch order.
 * Merging first would hand a patch the seed meant for another. This class takes planned
 * runs, drops duplicate patches, puts the stops in the seat order of the book the trip is
 * walked on, groups them by arrival, and moves the patches that earn the better seeds to the
 * front of the herb rotation.
 */
public final class Itinerary {

    /** No herb patch here takes a rotation seed, so the visit has no rank and stays put. */
    private static final int UNRANKED = Integer.MIN_VALUE;

    private Itinerary() {
    }

    /** One run's stops, already planned, with the crops its patches take. */
    @Value
    public static class Planned {

        RunId run;
        /** Parallel to {@link #cropFor}; never reordered, because the seeds were not. */
        List<StopPlan> plans;
        List<List<Crop>> cropFor;

        public Planned(RunId run, List<StopPlan> plans, List<List<Crop>> cropFor) {
            if (plans.size() != cropFor.size()) {
                throw new IllegalArgumentException(run + " planned " + plans.size() + " stops but named crops for " + cropFor.size());
            }
            this.run = run;
            this.plans = List.copyOf(plans);
            this.cropFor = List.copyOf(cropFor);
        }
    }

    /**
     * The trip as one flat list of stops in circuit order, plus how they group into
     * visits.
     *
     * <p>Flat because downstream code, such as {@code adviseOnGroup}, takes indices into a
     * plan list.
     */
    @Value
    public static class Trip {

        List<StopPlan> plans;
        List<List<Crop>> cropFor;
        /** Which family put each stop on the list, parallel to {@link #plans}. */
        List<RunId> runFor;
        /**
         * Indices into {@link #plans}, one group per arrival, in circuit order.
         *
         * <p>Read with {@link #runFor} to learn which families the trip covers. There is no
         * separate family set: it would be settled before the caller drops unreached
         * arrivals, and would name families the player is shown nothing for.
         */
        List<List<Integer>> visits;

        private Trip(List<StopPlan> plans, List<List<Crop>> cropFor, List<RunId> runFor, List<List<Integer>> visits) {
            this.plans = List.copyOf(plans);
            this.cropFor = List.copyOf(cropFor);
            this.runFor = List.copyOf(runFor);
            this.visits = List.copyOf(visits);
        }

        /**
         * The same trip with its routes decided again, once the circuit has said what order
         * the stops are reached in.
         *
         * <p>Routes only. The stops, their families and the visits are carried over
         * unchanged. The new lists must hold the same stops in the same order, since the
         * visits index them; a mismatch throws rather than showing a row with another
         * stop's route.
         *
         * @param cropFor carried alongside because a stop that loses its route plants
         *     nothing, and the bag is packed from this
         */
        public Trip rerouted(List<StopPlan> plans, List<List<Crop>> cropFor) {
            if (plans.size() != this.plans.size() || cropFor.size() != this.cropFor.size()) {
                throw new IllegalArgumentException("re-routing changed the trip's "
                        + this.plans.size() + " stops and " + this.cropFor.size()
                        + " crop lists into " + plans.size() + " and " + cropFor.size());
            }
            // Same stops in the same order, not merely the same count.
            for (int index = 0; index < plans.size(); index++) {
                if (plans.get(index).getStop() != this.plans.get(index).getStop()) {
                    throw new IllegalArgumentException("re-routing moved " + this.plans.get(index).getStop().primaryPatch().getLabel()
                            + " to where " + plans.get(index).getStop().primaryPatch().getLabel() + " now sits");
                }
            }
            return new Trip(plans, cropFor, runFor, visits);
        }

        /**
         * The same trip walked in another order: the same visits, each kept whole, put in
         * a different sequence. Anything else throws, since a visit split or dropped here
         * would lose or duplicate a stop's row.
         */
        Trip revisited(List<List<Integer>> visits) {
            if (visits.size() != this.visits.size() || !new HashSet<>(visits).equals(new HashSet<>(this.visits))) {
                throw new IllegalArgumentException("re-ordering changed the trip's visits " + this.visits + " into " + visits);
            }
            return new Trip(plans, cropFor, runFor, visits);
        }
    }

    /**
     * Merge planned runs into one trip.
     *
     * @param planned one entry per run with work waiting, in the order the scheduler
     *     ranked them — which decides who wins a duplicated patch
     * @param state the player, whose diaries and quests decide which patches earn the
     *     better seeds and therefore which are reached first. Null before anything has
     *     been reported, which scores every patch alike and leaves the circuit as authored
     * @param book the one book the whole trip is walked on, whose seats order every family's
     *     stops, trees and herbs alike
     */
    public static Trip merge(List<Planned> planned, PlayerState state, Spellbook book) {
        List<StopPlan> plans = new ArrayList<>();
        List<List<Crop>> cropFor = new ArrayList<>();
        List<RunId> runFor = new ArrayList<>();
        Set<PatchRef> alreadyWorked = new HashSet<>();

        for (Planned run : planned) {
            for (int i = 0; i < run.getPlans().size(); i++) {
                StopPlan plan = run.getPlans().get(i);
                // Some earlier run already claimed a patch this stop works. The run passed
                // first (the scheduler's higher-ranked run) keeps it: arbitrary but stable.
                // Tested against the whole patch list, so a multi-patch stop is dropped whole.
                if (!Collections.disjoint(plan.getStop().getPatches(), alreadyWorked)) {
                    continue;
                }
                alreadyWorked.addAll(plan.getStop().getPatches());
                plans.add(plan);
                cropFor.add(run.getCropFor().get(i));
                runFor.add(run.getRun());
            }
        }

        List<List<Integer>> visits = groupByArrival(inSeatOrder(plans, book), plans);
        return new Trip(plans, cropFor, runFor, bestHomesFirst(visits, plans, state));
    }

    /**
     * One run walked on its own, in the catalogue's order rather than the seats', with its
     * best homes re-dealt first as a merged trip's are.
     */
    static Trip alone(Planned run, PlayerState state) {
        List<List<Integer>> visits = TripOrder.groupByArrival(run.getPlans());
        return new Trip(run.getPlans(), run.getCropFor(), Collections.nCopies(run.getPlans().size(), run.getRun()),
                bestHomesFirst(visits, run.getPlans(), state));
    }

    /**
     * Stop indices in the order the trip walks them.
     *
     * <p>Sorted by the book's seats. The sort is stable, so ties keep each run's own patch
     * order and the trip does not reshuffle between snapshots.
     *
     * <p>Every {@link Arrival} constant has a seat on every book in {@link Seats}, which
     * refuses to build otherwise.
     */
    private static List<Integer> inSeatOrder(List<StopPlan> plans, Spellbook book) {
        Seats seats = Arrivals.seats();
        List<Integer> order = new ArrayList<>(plans.size());
        for (int i = 0; i < plans.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(index -> seats.seat(book, plans.get(index).getStop().getArrival())));
        return order;
    }

    /**
     * The trip as visits: every stop at one arrival becomes one group.
     *
     * <p>Grouping consecutive stops suffices: an arrival has a single circuit position, so
     * the sort has already put its stops together.
     */
    private static List<List<Integer>> groupByArrival(List<Integer> order, List<StopPlan> plans) {
        List<List<Integer>> visits = new ArrayList<>();
        List<Integer> current = null;
        Arrival at = null;
        for (int index : order) {
            Arrival arrival = plans.get(index).getStop().getArrival();
            if (current != null && arrival == at) {
                current.add(index);
                continue;
            }
            current = new ArrayList<>();
            current.add(index);
            at = arrival;
            visits.add(current);
        }
        return visits;
    }

    /**
     * The herb rotation's own visits re-dealt best home first, so the seeds land where they
     * were meant to even when a seed box is doing the planting.
     *
     * <p>A seed box plants the first seed in it that the patch will take, in the order the
     * seeds were put in the box; it only skips seeds the patch would refuse. So the order
     * the patches are reached decides which seed each gets. {@code assignCrops} deals the
     * better seeds down the {@link PatchPriority} ranking, and this makes the walk follow
     * that ranking too.
     *
     * <p><b>Whole visits move, never single stops</b>, so an arrival's other patches are not
     * left behind for a second trip. An arrival ranks by the best herb patch it holds.
     *
     * <p><b>Only the herb rotation's seats are re-dealt</b>: arrivals with a herb patch the
     * rotation plants. Every other arrival keeps its position, except one that rides with a
     * seat: a circuit neighbour reached by the same teleport moves with it, so the Falador
     * park tree stays beside the Falador farm. The Farming Guild's herb patch
     * is {@linkplain PatchPriority#isReservedForContracts reserved}, so the Guild is never a
     * seat and keeps its lead for banking.
     *
     * <p><b>A seat is a fact of the catalogue, never of the ground.</b> A patch found growing,
     * or past the end of the seeds, keeps its seat, so the order holds still while the player
     * walks round seeing patches for the first time: a finished stop drops off and nothing
     * else moves. The patches that do get a seed are a subset of the seats, so they are still
     * reached best first and the seed box still lands each seed where it was dealt.
     *
     * <p>The extra travel is accepted: a misplanted seed is lost for the whole grow, and the
     * walk is paid once. The order follows {@link PatchPriority#scoreOf}, so it changes with
     * a diary or a quest, not with what is in the ground.
     *
     * <p>A moved stop can be reached before a rung that leans on it.
     * {@code replanAcrossTheCircuit} handles that, on a trip and on a run walked alone.
     *
     * <p>Two paths reach it: {@link #merge} for a trip, and {@link #alone} for a single run
     * in catalogue order. Both must re-deal, or a seed box would plant the seeds in walking
     * order on one of them.
     *
     * @param visits stops grouped by arrival <b>in the order the caller walks them</b>,
     *     which is what the ranking's ties fall back to and therefore what the allocation
     *     has to be keyed on as well. Returned as a permutation with the groups themselves
     *     untouched — or returned as the very same list when there is nothing to reorder,
     *     so a caller that intends to keep hold of it should copy
     */
    static List<List<Integer>> bestHomesFirst(List<List<Integer>> visits, List<StopPlan> plans, PlayerState state) {
        int[] rank = new int[visits.size()];
        List<Integer> seats = new ArrayList<>();
        for (int visit = 0; visit < visits.size(); visit++) {
            rank[visit] = rankOf(visits.get(visit), plans, state);
            if (rank[visit] != UNRANKED) {
                seats.add(visit);
            }
        }
        // One seat cannot be out of order with itself, and neither can none.
        if (seats.size() < 2) {
            return visits;
        }

        List<Integer> byRank = new ArrayList<>(seats);
        // Stable, so equal ranks keep the caller's walking order and an account that has
        // earned nothing walks the identity permutation. Ties stay unbroken by seat: a run
        // walked alone follows the catalogue's order, not the circuit, and the allocation
        // shares the caller's walk.
        byRank.sort(Comparator.comparingInt((Integer visit) -> rank[visit]).reversed());

        int[] walk = new int[visits.size()];
        for (int position = 0; position < walk.length; position++) {
            walk[position] = position;
        }
        for (int seat = 0; seat < seats.size(); seat++) {
            walk[seats.get(seat)] = byRank.get(seat);
        }
        return withRidersBesideTheirSeats(walk, visits, ridersOf(seats, visits, plans));
    }

    /**
     * Each visit that rides with a seat: a circuit neighbour reached by the same teleport,
     * such as the Falador park tree beside the Falador farm. Keyed by the rider, valued by
     * its seat. Moving the seat without it would cast the same teleport twice, nine stops
     * apart.
     */
    private static Map<Integer, Integer> ridersOf(List<Integer> seats, List<List<Integer>> visits, List<StopPlan> plans) {
        Set<Integer> isSeat = new HashSet<>(seats);
        Map<Integer, Integer> riders = new HashMap<>();
        for (int seat : seats) {
            Destination landing = landingOf(visits.get(seat), plans);
            if (landing == null) {
                continue;
            }
            for (int neighbour : new int[] {seat - 1, seat + 1}) {
                if (neighbour >= 0 && neighbour < visits.size() && !isSeat.contains(neighbour) && !riders.containsKey(neighbour)
                        && landing == landingOf(visits.get(neighbour), plans)) {
                    riders.put(neighbour, seat);
                }
            }
        }
        return riders;
    }

    /** The place a visit's teleport lands at, or null for a route that names none. */
    private static Destination landingOf(List<Integer> visit, List<StopPlan> plans) {
        for (int index : visit) {
            StopPlan plan = plans.get(index);
            if (plan.isVisit() && plan.getRoute() != null && plan.getRoute().getDestination() != null) {
                return plan.getRoute().getDestination();
            }
        }
        return null;
    }

    /**
     * The walk with every rider taken out of its own place and put back beside its seat, on
     * the side of it the circuit had it.
     */
    private static List<List<Integer>> withRidersBesideTheirSeats(int[] walk, List<List<Integer>> visits, Map<Integer, Integer> riders) {
        List<List<Integer>> placed = new ArrayList<>(visits.size());
        for (int visit : walk) {
            if (riders.containsKey(visit)) {
                continue;
            }
            addRiders(placed, visit, visits, riders, true);
            placed.add(visits.get(visit));
            addRiders(placed, visit, visits, riders, false);
        }
        return placed;
    }

    private static void addRiders(List<List<Integer>> placed, int seat,
            List<List<Integer>> visits, Map<Integer, Integer> riders, boolean before) {
        for (Map.Entry<Integer, Integer> rider : new TreeMap<>(riders).entrySet()) {
            if (rider.getValue() == seat && rider.getKey() < seat == before) {
                placed.add(visits.get(rider.getKey()));
            }
        }
    }

    /**
     * How good a home for a seed the best herb patch at this arrival is, or
     * {@link #UNRANKED} when the herb rotation plants nothing here.
     *
     * <p>The best rather than a sum or average: an arrival is one journey.
     *
     * <p>Read off the stop alone: whether it is visited today, or what its ground holds,
     * would move it as patches are seen (see {@link #bestHomesFirst}).
     *
     * <p>Stops with a fixed crop are not seats; their seed cannot be misdealt.
     *
     * <p>Herbs only, by name. Herbs are the one family planted from a shared seed rotation,
     * and the only one {@link com.larryfarming.farming.catalogue.PatchAttributes} ranks. Tree
     * patches have no better or worse home, so ranking them would only add travel.
     */
    private static int rankOf(List<Integer> visit, List<StopPlan> plans, PlayerState state) {
        int best = UNRANKED;
        for (int index : visit) {
            StopPlan plan = plans.get(index);
            if (plan.getStop().isFixedCrop() || !plan.getStop().isPlanting() || !isHerbPatch(plan.getStop().primaryPatch())) {
                continue;
            }
            for (PatchRef patch : plan.getStop().getPatches()) {
                // A patch reserved for contracts is not a rotation seat. Ranking it would
                // move the Farming Guild out of its banking lead.
                if (!PatchPriority.isReservedForContracts(patch)) {
                    best = Math.max(best, PatchPriority.scoreOf(patch, state));
                }
            }
        }
        return best;
    }

    /**
     * Whether this is a herb patch, per the world map rather than the run that named it.
     * An unresolvable reference answers false, so it never reorders the trip. A stop's
     * patches all share one type ({@code RunStop}'s builder enforces it), so callers pass
     * the primary.
     */
    private static boolean isHerbPatch(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        return def != null && def.getType() == PatchType.HERB;
    }
}
