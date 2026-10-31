package com.larryfarming.farming.engine;

import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Requirement;
import com.larryfarming.farming.model.TravelOption;
import com.larryfarming.model.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Ardougne cloak's Monastery teleport as the way onto the fairy rings, with the
 * Monastery bushes worked on the way.
 *
 * <p>The cloak lands nine tiles from the bushes, and the bushes are 43 tiles from ring DJP
 * against 50 straight from the landing, so working them costs two tiles on a walk a player
 * entering the network by cloak makes anyway. A player
 * who visits the bushes by cloak is therefore standing at a ring for nothing, and two
 * things follow:
 *
 * <ul>
 * <li>The leg straight after such a visit starts at the ring: no approach teleport is
 * packed, and its sentence opens with the walk to DJP.</li>
 * <li>On a trip, the Monastery visit moves to just before the first leg taken by fairy
 * ring, so that leg is the one it serves. The circuit's own seat for the bushes, beside the
 * Ardougne farm, saves nothing: the farm is reached by its own teleport.</li>
 * </ul>
 *
 * <p>Only while the player may use the cloak to stand at a ring: they own cloak 1 and have
 * not switched the cloak off under "Travel". With the switch off they have said never to use
 * the cloak, so the bushes keep their seat.
 *
 * <p>The Mort Myre mushroom patch leaves the player beside ring CKS the same way, by
 * whatever route they reached it, so the leg after it starts there too. The mushrooms are
 * never moved: the circuit already seats them just before Canifis.
 */
final class CloakToTheRings {

    private CloakToTheRings() {
    }

    /**
     * What a Monastery visit by cloak gives the leg after it: 43 walked tiles from the
     * bushes' west face (2616, 3226), where the walk from the cloak arrives, to DJP.
     */
    static final RunPlanner.Start FROM_THE_BUSHES = new RunPlanner.Start(
            FarmingCatalogue.toAfairyRing(),
            "Walk east past the Tower of Life to fairy ring DJP. ", 43);

    /**
     * What a Mort Myre mushroom visit gives the leg after it, however the player got there.
     * The patch (3452, 3473) is two walked tiles from fairy ring CKS (3447, 3470), from the
     * face nearest the ring.
     */
    static final RunPlanner.Start FROM_THE_MUSHROOMS = new RunPlanner.Start(
            FarmingCatalogue.toAfairyRing(),
            "Walk west to fairy ring CKS. ", 2);

    /**
     * The cloak as a way to a ring, the catalogue's own arm. The resolver fails it while the
     * player has the cloak switched off.
     */
    private static final Requirement CLOAK_TO_A_RING =
            Requirement.cap(Capability.ARDOUGNE_CLOAK_1);

    /**
     * What the next leg starts from once this group is walked: the bushes' start after a
     * Monastery visit by cloak, the mushrooms' start after a Mort Myre visit, nothing after
     * any other journey, and {@code before} unchanged after a group the player does not
     * travel to ({@link StopRowWriter#isJourney}).
     */
    static RunPlanner.Start after(RunPlanner.Start before, List<Integer> group,
            List<StopPlan> plans, CapabilityResolver resolver, PlayerState state) {
        if (!StopRowWriter.isJourney(group, plans)) {
            return before;
        }
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (plan.isVisit() && reachedByCloak(plan) && mayEnterByCloak(resolver, state)) {
                return FROM_THE_BUSHES;
            }
            if (plan.isVisit() && plan.getStop().getArrival() == Arrival.MORT_MYRE) {
                return FROM_THE_MUSHROOMS;
            }
        }
        return null;
    }

    /**
     * The trip with its Monastery visit moved to just before the first leg taken by fairy
     * ring, when that visit is made by cloak. Otherwise the same trip.
     *
     * <p>Whole visits move, as {@link Itinerary#bestHomesFirst} moves them. The bushes are
     * never a herb rotation seat, so moving them leaves the seed order alone.
     *
     * <p>A ring leg the walkthrough will not draw is passed over: the Farming Guild's bin
     * carries the guild's own chain, CIR included, and seating the bushes before a bin nobody
     * passes would hand the start to whatever leg follows it.
     *
     * <p>Reads each run's own routes, before the circuit re-decides them. A leg that went by
     * ring only for its run's order can lose the ring on re-planning, and then the bushes
     * start a leg with no use for it. That costs nothing against their old seat: both legs
     * are reached by their own teleports. Re-decided routes are no better a guide, since a
     * leg already started from the bushes no longer names a way to a ring.
     */
    static Itinerary.Trip bushesBeforeTheFirstRing(Itinerary.Trip trip,
            CapabilityResolver resolver, PlayerState state, FarmingInputs inputs) {
        List<List<Integer>> visits = trip.getVisits();
        List<StopPlan> plans = trip.getPlans();
        int bushes = -1;
        for (int position = 0; position < visits.size(); position++) {
            if (after(null, visits.get(position), plans, resolver, state) == FROM_THE_BUSHES) {
                bushes = position;
                break;
            }
        }
        if (bushes < 0) {
            return trip;
        }
        Set<Integer> undrawn = RunPlanning.undrawnGroups(visits, plans, inputs, state, true);
        int ring = -1;
        for (int position = 0; position < visits.size(); position++) {
            if (position != bushes && !undrawn.contains(position)
                    && byFairyRing(StopRowWriter.leadOf(visits.get(position), plans))) {
                ring = position;
                break;
            }
        }
        if (ring < 0 || ring == bushes + 1) {
            return trip;
        }
        List<List<Integer>> moved = new ArrayList<>(visits);
        List<Integer> monastery = moved.remove(bushes);
        moved.add(bushes < ring ? ring - 1 : ring, monastery);
        return trip.revisited(moved);
    }

    /** Whether a stop is reached on the cloak's own Monastery teleport, not by a ring. */
    private static boolean reachedByCloak(StopPlan plan) {
        TravelOption option = plan.getRoute();
        return plan.getStop().getArrival() == Arrival.ARDOUGNE_MONASTERY && option != null
                && !option.getRequires().contains(FarmingCatalogue.toAfairyRing())
                && option.getRequires().leafCapabilities()
                        .contains(Capability.ARDOUGNE_CLOAK_1);
    }

    private static boolean mayEnterByCloak(CapabilityResolver resolver, PlayerState state) {
        return resolver.evaluate(CLOAK_TO_A_RING, state, Map.of()) != CapabilityState.LACK;
    }

    /** Whether a visit's route enters the fairy ring network. */
    private static boolean byFairyRing(StopPlan plan) {
        return plan.isVisit() && plan.getRoute() != null
                && plan.getRoute().getRequires().contains(FarmingCatalogue.toAfairyRing());
    }
}
