package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingSkipReason;
import java.util.*;
import lombok.Value;

/**
 * Turns each stop's ranked routes into a decision: for each stop on a run, the best
 * route this player can take, or the catalogue's reason for dropping the patch.
 */
public final class RunPlanner {

    private final CapabilityResolver resolver;

    /**
     * The same resolver with the player's position withheld, for every stop but the one
     * they are travelling to.
     */
    private final CapabilityResolver blind;

    public RunPlanner(CapabilityResolver resolver) {
        this.resolver = resolver;
        this.blind = resolver.blindToPosition();
    }

    /**
     * The resolver a stop may be asked with.
     *
     * <p>Position answers only for the stop the player is heading to. A route that is free
     * from where they stand now is not free from where they will stand twenty minutes into
     * the trip, so answering a later stop from position would drop a teleport they need
     * from the packing list. See {@link CapabilityResolver#blindToPosition()}.
     */
    private CapabilityResolver resolverFor(boolean mayStandHere) {
        return mayStandHere ? resolver : blind;
    }

    /**
     * Plans every stop on a run, in the catalogue's order.
     *
     * <p>Stops are planned in sequence because a later rung can depend on an earlier stop
     * being visited: the Taverley route ends with a walk from the Falador tree
     * patch, which is only free if Falador is on the day's itinerary.
     *
     * <p>The player's position is spent on the first arrival that takes it. By the next
     * journey they are somewhere else. See {@link Allowance}.
     */
    public List<StopPlan> plan(FarmingRun run, PlayerState state) {
        List<StopPlan> plans = new ArrayList<>();
        Map<PatchRef, CapabilityState> reached = new LinkedHashMap<>();
        Allowance allowance = new Allowance();
        for (RunStop stop : run.getStops()) {
            // A stop gated behind something the player definitely lacks (Harmony Island
            // behind the Elite Morytania diary) is not on their route yet, so it drops out
            // instead of becoming a SKIP or UNREACHABLE row. An unresolved gate leaves the
            // stop in place.
            if (RunAvailability.of(stop.getGate(), state) == CapabilityState.LACK) {
                continue;
            }
            StopPlan plan = planStop(stop, state, reached, allowance.covers(stop));
            plans.add(plan);
            allowance.spendOn(plan);
            // Recorded with the visit's confidence, so a rung that leans on an uncertain
            // stop inherits its doubt.
            stood(reached, plan);
        }
        return Collections.unmodifiableList(plans);
    }

    /**
     * Which stops may be routed from where the player is standing, over one walk of the
     * stops.
     *
     * <p><b>The allowance belongs to an arrival, not to a stop.</b> Catherby's herb patch,
     * flower patch and allotment are three stops reached on one teleport. Planning the
     * siblings blind would pack a teleport the lead stop already got free. So every stop
     * at the first arrival the player travels to gets the allowance, and every stop after
     * that arrival does not.
     *
     * <p>Only a visit spends position: a skipped stop leaves the player where they were.
     * Rebuilt per walk instead of recorded on the plan, so a stop dropped later for what is
     * in its ground hands the allowance to whichever stop inherits the walk.
     */
    public static final class Allowance {

        private Arrival spentAt;

        /** Whether this stop may be routed from where the player stands. */
        public boolean covers(RunStop stop) {
            return spentAt == null || spentAt == stop.getArrival();
        }

        /**
         * Spends the allowance on this plan's arrival if it is the first visit. A skipped
         * stop leaves the player where they were and spends nothing.
         */
        public void spendOn(StopPlan plan) {
            if (spentAt == null && plan.isVisit()) {
                spentAt = plan.getStop().getArrival();
            }
        }
    }

    /**
     * Records that a plan's patches have been stood at, with the plan's confidence. Shared
     * with the trip, which must record visits the same way.
     *
     * <p>A plan that is not a visit records nothing: the resolver reads an absent patch as
     * {@code LACK}. A patch recorded twice keeps the surer visit, so the result does not
     * depend on caller order.
     */
    public static void stood(Map<PatchRef, CapabilityState> reached, StopPlan plan) {
        if (!plan.isVisit()) {
            return;
        }
        for (PatchRef patch : plan.getStop().getPatches()) {
            reached.merge(patch, plan.getConfidence(), (already, now) -> CapabilityState.any(List.of(already, now)));
        }
    }

    /**
     * Picks a route for one stop, with position allowed.
     *
     * <p>Package-private because it grants position: a caller outside this package must
     * use the overload that names the allowance.
     */
    StopPlan planStop(RunStop stop, PlayerState state, Map<PatchRef, CapabilityState> reached) {
        return planStop(stop, state, reached, true);
    }

    /**
     * Picks a route for one stop.
     *
     * <p>Takes the first rung the player certainly has. Failing that, it takes the best
     * rung that <em>might</em> work. It never skips on uncertainty: the things we cannot
     * see are the things a well-equipped player most likely owns, and telling them to skip
     * a reachable patch is the worst mistake this can make. Only when every rung is a
     * definite no does the chain's terminal decision apply.
     *
     * <p>One uncertainty is never guessed: a rung that states a fact about the account,
     * such as "with your house in Hosidius" (see {@link TravelOption#isCertainOnly()}).
     * When that rung is the only maybe left, the stop has no route yet rather than a skip,
     * and the row says what is unread: "not sure where your house is".
     *
     * @param mayStandHere true for the stop the player is travelling to, so a route free
     *     from where they stand is offered; false for every later stop
     */
    public StopPlan planStop(RunStop stop, PlayerState state, Map<PatchRef, CapabilityState> reached, boolean mayStandHere) {
        return planStop(stop, state, reached, mayStandHere, null);
    }

    /**
     * The same, for a stop whose route can begin where the previous stop left the player.
     *
     * @param start what the previous stop already gives this one, or null for nothing
     */
    StopPlan planStop(RunStop stop, PlayerState state, Map<PatchRef, CapabilityState> reached, boolean mayStandHere, Start start) {
        StopPlan shutOut = shutOut(stop, state);
        if (shutOut != null) {
            return shutOut;
        }
        CapabilityResolver asked = resolverFor(mayStandHere);
        List<TravelOption> ladder = ladderFor(stop.getTravel(), start, state, reached, asked);
        TravelOption certain = firstCertain(ladder, state, reached, asked);
        if (certain != null) {
            return visit(stop, certain, CapabilityState.HAVE, state, reached, asked);
        }
        TravelOption guess = bestGuess(ladder, state, reached, asked);
        if (guess != null) {
            return visit(stop, guess, CapabilityState.UNKNOWN, state, reached, asked);
        }
        StopPlan unread = waitingOnAfact(stop, state, reached, asked);
        return unread != null ? unread : giveUp(stop, state, asked);
    }

    /**
     * No route yet, because the one rung that might hold states an account fact not read
     * yet. Null when no such rung is in doubt.
     *
     * <p>Its confidence is {@code UNKNOWN}, not {@code LACK}: the rung may well hold once the
     * fact is read, so the runs list must not report the run as having no route.
     */
    private static StopPlan waitingOnAfact(RunStop stop, PlayerState state,
            Map<PatchRef, CapabilityState> reached, CapabilityResolver asked) {
        for (TravelOption option : stop.getTravel()) {
            if (!option.isCertainOnly() || asked.evaluate(option.getRequires(), state, reached) != CapabilityState.UNKNOWN) {
                continue;
            }
            return new StopPlan(stop, StopPlan.Outcome.UNROUTED, null, CapabilityState.UNKNOWN, unreadFact(option, state, asked),
                    FarmingSkipReason.NO_ROUTE_LISTED, 0L, Collections.emptySet(), null);
        }
        return null;
    }

    /**
     * Plans an arrival with position allowed. Package-private for the reason the matching
     * {@link #planStop} overload is.
     */
    StopPlan planArrival(RunStop lead, PlayerState state, Map<PatchRef, CapabilityState> reached) {
        return planArrival(lead, state, reached, true);
    }

    /**
     * How to reach a place when the visit works several of its patches, or null when the
     * arrival has no ranking of its own or the player can take none of its rungs.
     *
     * <p>A stop's chain ranks routes by their cost to reach <em>that</em> patch. Where an
     * arrival's patches are far apart, the best route to one is the worst to the other, so
     * {@code Arrivals} carries a third chain ranked for the pair. The plan comes back on
     * the lead stop; only the rung, the sentence and the teleport are the arrival's.
     *
     * <p>Returns null rather than a give-up when nothing resolves, because each stop's own
     * chain still has its own terminal. Falling back is the caller's job.
     *
     * @param mayStandHere as on {@link #planStop(RunStop, PlayerState, Map, boolean)}
     */
    public StopPlan planArrival(RunStop lead, PlayerState state, Map<PatchRef, CapabilityState> reached, boolean mayStandHere) {
        return planArrival(lead, state, reached, mayStandHere, null);
    }

    /** The same, with what the previous stop gives this one, as on {@link #planStop}. */
    StopPlan planArrival(RunStop lead, PlayerState state, Map<PatchRef, CapabilityState> reached, boolean mayStandHere, Start start) {
        CapabilityResolver asked = resolverFor(mayStandHere);
        List<TravelOption> combined = ladderFor(FarmingCatalogue.combinedRoute(lead.getArrival()), start, state, reached, asked);
        TravelOption certain = firstCertain(combined, state, reached, asked);
        if (certain != null) {
            return visit(lead, certain, CapabilityState.HAVE, state, reached, asked);
        }
        TravelOption guess = bestGuess(combined, state, reached, asked);
        return guess == null ? null : visit(lead, guess, CapabilityState.UNKNOWN, state, reached, asked);
    }

    /**
     * Part of a journey the previous stop already made, such as standing beside a fairy
     * ring: the part reads as held on every rung asking for it, and the walk from there
     * opens the rung's sentence.
     */
    @Value
    static class Start {
        Requirement granted;
        /** A whole sentence, ending in ". ", which the rung's own sentence follows. */
        String walk;
        /** The walk's length in tiles, which a started rung counts in place of an approach. */
        int tiles;
    }

    /**
     * The ladder as this player, starting from {@code start}, climbs it: each rung ranked by
     * its whole walk for this account ({@link #ranked}).
     */
    static List<TravelOption> ladderFor(List<TravelOption> ladder, Start start, PlayerState state,
            Map<PatchRef, CapabilityState> reached, CapabilityResolver asked) {
        List<TravelOption> from = ladderFrom(ladder, start);
        // Planning runs on every snapshot. With no start and no approach, every figure is the
        // catalogue's own, so its ranking stands and ranking again would change nothing.
        // A started rung has a walk the catalogue could not know, so a start always re-ranks.
        return start == null && !asksForAnApproach(from) ? from : ranked(from, state, reached, asked);
    }

    /** Whether any rung leaves out an approach whose walk this account decides. */
    private static boolean asksForAnApproach(List<TravelOption> ladder) {
        for (TravelOption rung : ladder) {
            if (!rung.hasWalkFigure()) {
                continue;
            }
            for (Approach approach : FarmingCatalogue.approaches()) {
                if (rung.getRequires().contains(approach.getWay())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The ladder as a player starting from {@code start} climbs it. A rung waiting for a
     * start ({@link TravelOption#isStartOnly()}) stays on only when this start grants it.
     * A started rung counts the start's walk in place of its approach.
     */
    private static List<TravelOption> ladderFrom(List<TravelOption> ladder, Start start) {
        List<TravelOption> from = new ArrayList<>(ladder.size());
        for (TravelOption rung : ladder) {
            TravelOption started = start == null ? rung : rung.startingFrom(start.getGranted(), start.getWalk(), start.getTiles());
            if (!started.isStartOnly()) {
                from.add(started);
            }
        }
        return from;
    }

    /**
     * The ladder ranked by walk for this account ({@link RungOrder}). The catalogue has
     * already ranked every rung whose walk is the same for everyone. A rung asking for an
     * approach ({@link FarmingCatalogue#approaches()}) adds the walk of the way in this player
     * takes, the one the bag packs for, and costs the dearer of the two. So whether a fairy
     * ring rung ranks above a teleport depends on whether the player reaches a ring by a house
     * ring or by a Salve graveyard tablet.
     *
     * <p>A rung whose every way in is a definite no has no total, so it keeps its place
     * among the rest: it cannot be taken.
     */
    private static List<TravelOption> ranked(List<TravelOption> ladder, PlayerState state,
            Map<PatchRef, CapabilityState> reached, CapabilityResolver asked) {
        if (ladder.size() < 2) {
            return ladder;
        }
        int[] walks = new int[ladder.size()];
        TravelOption.Cost[] costs = new TravelOption.Cost[ladder.size()];
        for (int i = 0; i < walks.length; i++) {
            TravelOption rung = ladder.get(i);
            walks[i] = rung.getWalk();
            costs[i] = rung.getCost();
            if (!rung.hasWalkFigure()) {
                continue;
            }
            for (Approach approach : FarmingCatalogue.approaches()) {
                if (!rung.getRequires().contains(approach.getWay())) {
                    continue;
                }
                Approach.Leg leg = approach.legFor(asked.takenBranch(approach.getWay(), state, reached));
                if (leg == null) {
                    walks[i] = TravelOption.UNMEASURED;
                    break;
                }
                walks[i] += leg.getWalk();
                costs[i] = RungOrder.dearer(costs[i], leg.getCost());
            }
        }
        return RungOrder.rank(ladder, new RungOrder.Figures() {
            @Override
            public int walk(int rung) {
                return walks[rung];
            }

            @Override
            public TravelOption.Cost cost(int rung) {
                return costs[rung];
            }
        });
    }

    /** The resolver every stop is asked with, before position is withheld. */
    CapabilityResolver resolver() {
        return resolver;
    }

    /** The first rung on this ladder the player certainly holds, or null. */
    private TravelOption firstCertain(List<TravelOption> ladder, PlayerState state,
            Map<PatchRef, CapabilityState> reached, CapabilityResolver asked) {
        for (TravelOption option : ladder) {
            if (asked.evaluate(option.getRequires(), state, reached) == CapabilityState.HAVE) {
                return option;
            }
        }
        return null;
    }

    /**
     * The detector's own words for the unread fact, such as "not sure where your house is",
     * rather than the resolver's generic "not sure whether you have".
     */
    private static String unreadFact(TravelOption option, PlayerState state, CapabilityResolver asked) {
        for (Capability capability : option.getRequires().leafCapabilities()) {
            if (asked.resolve(capability, state).getState() != CapabilityState.UNKNOWN) {
                continue;
            }
            CapabilityVerdict own = CapabilitySources.resolve(capability, state);
            if (own.getState() == CapabilityState.UNKNOWN) {
                return own.getBecause();
            }
        }
        String shortfall = asked.explainShortfall(option.getRequires(), state);
        return shortfall.isEmpty() ? "no route you can use right now" : shortfall;
    }

    /**
     * The best rung that merely <em>might</em> work, or null when every one is a definite no.
     *
     * <p>A rung whose only doubt is an earlier stop on this run, itself routed on a guess,
     * beats a rung with a doubt of its own. The player already sees the inherited doubt on
     * an earlier row, and resolving it answers both. Taverley shows why: with the bank
     * unread, its chain ranks an uncertain ring of dueling and balloon above "run north from
     * the Falador tree patch", and the free walk should win whenever Falador was a guess.
     *
     * <p>A rung marked {@link TravelOption#isCertainOnly()} is never a guess. Its sentence
     * states a fact about the account, which is wrong advice until the fact is read.
     *
     * <p>Chain order decides everything else, because it is the route ranking.
     */
    private TravelOption bestGuess(List<TravelOption> ladder, PlayerState state,
            Map<PatchRef, CapabilityState> reached, CapabilityResolver asked) {
        Map<PatchRef, CapabilityState> asIfSettled = asIfThoseVisitsHold(reached);
        TravelOption independent = null;
        for (TravelOption option : ladder) {
            if (option.isCertainOnly()) {
                continue;
            }
            if (asked.evaluate(option.getRequires(), state, reached) != CapabilityState.UNKNOWN) {
                continue;
            }
            if (asked.evaluate(option.getRequires(), state, asIfSettled) == CapabilityState.HAVE) {
                // Certain but for the guess it inherited, so there is nothing else to ask.
                return option;
            }
            if (independent == null) {
                independent = option;
            }
        }
        return independent;
    }

    /**
     * The reached set with every uncertain visit read as though it had happened, used to
     * separate a doubt a rung inherited from one it owns. Absent patches stay absent: a
     * stop the run is not making is a {@code LACK}, not a maybe.
     */
    private static Map<PatchRef, CapabilityState> asIfThoseVisitsHold(Map<PatchRef, CapabilityState> reached) {
        if (reached == null || reached.isEmpty()) {
            return reached;
        }
        Map<PatchRef, CapabilityState> settled = new LinkedHashMap<>(reached);
        settled.replaceAll((patch, held) -> held == CapabilityState.UNKNOWN ? CapabilityState.HAVE : held);
        return settled;
    }

    /**
     * A stop the player cannot work whatever route they hold, or null when the place is
     * open to them or we cannot tell.
     *
     * <p>Checked before the chain. Weiss is reached by an icy basalt, whose requirement
     * knows nothing of the fire of nourishment the patch needs, so the chain alone would
     * never report the gate. Only a certain {@code LACK} shuts a stop out, and the reason
     * comes from the gate rather than from a rung.
     */
    private StopPlan shutOut(RunStop stop, PlayerState state) {
        if (stop.getAccessGate() == null) {
            return null;
        }
        CapabilityVerdict access = resolver.resolve(stop.getAccessGate(), state);
        if (access.getState() != CapabilityState.LACK) {
            return null;
        }
        // The gate's own words, unprefixed: the heading already says "no way in".
        return new StopPlan(stop, StopPlan.Outcome.UNREACHABLE, null, CapabilityState.LACK, access.getBecause(),
                FarmingSkipReason.NO_ROUTE, 0L, Collections.emptySet(), null);
    }

    /**
     * A settled stop, with the teleport picked before the instruction is written: a rung
     * named by its destination opens its instruction with whatever the player casts or
     * breaks.
     *
     * <p>A rung that only <em>leans</em> on a place also gets its teleport worked out, so
     * the means is packed. "Fairy ring ALQ" is one: its requirement accepts a Salve
     * graveyard teleport as a way to a ring. It keeps its own wording, because
     * {@link TravelOption#instructionFor} reads the rung's declared destination and naming
     * the means here cannot rewrite the sentence.
     */
    private StopPlan visit(RunStop stop, TravelOption option, CapabilityState confidence,
            PlayerState state, Map<PatchRef, CapabilityState> reached, CapabilityResolver asked) {
        Destination arriveAt = option.getDestination() != null ? option.getDestination()
                : asked.reliedOnDestination(option.getRequires(), state, reached);
        TeleportPlanner.Choice arriving = arriveAt == null ? null : asked.teleportFor(option.getRequires(), arriveAt, state);
        // Null for a player already standing there. CarryPlanner counts runes and tablets
        // from this, so null keeps the teleport off the packing list.
        TeleportMeans means = arriving == null ? null : arriving.getMeans();
        // Only a certainty with no means says "you are there". An unread bank also returns
        // no means while its rung is still the best guess, so a null means alone must not
        // read as arrival.
        boolean alreadyThere = arriving != null && means == null && arriving.getState() == CapabilityState.HAVE;
        return new StopPlan(stop, StopPlan.Outcome.VISIT, option, confidence,
                sayWhatItLeansOn(option.instructionFor(means, alreadyThere), option, reached),
                null, 0L, asked.reliedOn(option.getRequires(), state, reached), means);
    }

    /**
     * The route sentence, with the stop it depends on named when that stop is itself a guess.
     *
     * <p>A hedged row is drawn in orange. Most routes name their own doubt: in "House
     * spirit tree to the Grand Tree" the house tree is right there. A route leaning on an
     * earlier stop does not: "Run west to the Stronghold spirit tree" says nothing about the
     * Grand Tree visit it assumes. The sentence states the dependency; it does not ask the
     * player whether that stop will happen.
     */
    private static String sayWhatItLeansOn(String because, TravelOption option, Map<PatchRef, CapabilityState> reached) {
        if (reached == null || because == null) {
            return because;
        }
        for (PatchRef patch : option.getRequires().leafPatches()) {
            if (reached.get(patch) != CapabilityState.UNKNOWN) {
                continue;
            }
            String name = nameOf(patch);
            // Don't name the patch twice when the rung already does ("Run north-west from
            // the Falador tree patch").
            // A rung written as sentences gets a sentence; a bare clause gets a clause.
            if (because.endsWith(".")) {
                return because + " This assumes you stopped at the " + name + " patch.";
            }
            return because.contains(name) ? because + ", if you stopped there" : because + ", if you stopped at the " + name + " patch";
        }
        return because;
    }

    /**
     * A patch as the player would say it: "Falador tree", "Gnome Stronghold fruit tree".
     * {@code PatchRef.getLabel()} prints the enum spelling ("Falador TREE"), and
     * {@code StopRowWriter.patchLabel} drops the location, which this sentence needs.
     */
    private static String nameOf(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        if (def == null) {
            return patch.getLabel();
        }
        // Hespori is a name, so it keeps its capital.
        String kind = def.getType() == PatchType.HESPORI ? def.getType().getDisplayName()
                : def.getType().name().toLowerCase().replace('_', ' ');
        return def.getLocation() + " " + kind;
    }

    /**
     * What the chain says to do when the player has none of the routes.
     *
     * <p>The three terminals mean different things. "Drop it" means the patch is reachable
     * but not worth the walk. "No alternative" means the stop is shut. No terminal means
     * the chain gives no advice, which says nothing about the patch. All three carry the
     * bare shortfall, and the heading is written from {@link FarmingSkipReason}.
     */
    private StopPlan giveUp(RunStop stop, PlayerState state, CapabilityResolver asked) {
        boolean noRing = lostForWantOfAring(stop, state, asked);
        String shortfall = noRing ? NO_RING_BECAUSE : shortfallOf(stop, state, asked);
        StopPlan.Outcome outcome;
        FarmingSkipReason reason;
        switch (stop.getTerminal()) {
            case SKIP_PATCH:
                outcome = StopPlan.Outcome.SKIP;
                reason = FarmingSkipReason.NOT_WORTH_TRIP;
                break;
            case NO_ALTERNATIVE:
                outcome = StopPlan.Outcome.UNREACHABLE;
                reason = FarmingSkipReason.NO_ROUTE;
                break;
            default:
                outcome = StopPlan.Outcome.UNROUTED;
                reason = FarmingSkipReason.NO_ROUTE_LISTED;
        }
        return new StopPlan(stop, outcome, null, CapabilityState.LACK, shortfall,
                noRing ? FarmingSkipReason.NO_FAIRY_RING_ROUTE : reason, 0L, Collections.emptySet(), null);
    }

    private static final String NO_RING_BECAUSE = "nothing you own and have switched on reaches a fairy ring. " + "See Travel";

    /**
     * Whether a fairy ring rung would have reached this stop but for a way to stand at a
     * ring. Not when the network itself is shut (Fairytale II, or no staff): that rung reads
     * LACK even with the approach granted, so its own reason stands.
     */
    private static boolean lostForWantOfAring(RunStop stop, PlayerState state, CapabilityResolver asked) {
        Requirement approach = FarmingCatalogue.toAfairyRing();
        for (TravelOption option : stop.getTravel()) {
            if (option.isCertainOnly() || !option.getRequires().contains(approach)) {
                continue;
            }
            if (asked.evaluateGranting(option.getRequires(), approach, state, Collections.emptyMap()) != CapabilityState.LACK) {
                return true;
            }
        }
        return false;
    }

    /**
     * The reason drawn from the rung the player is nearest to holding, which is the one worth
     * working toward. Nearest is {@link CapabilityResolver.Gap} order: a restock of something
     * they have, then a teleport to a place, then items, then unlocks. Ties go to the higher
     * rung.
     * A rung that wants only a stop the run is not making has nothing to name.
     *
     * <p>Asked with the resolver that made the decision. The sighted resolver answers a
     * destination the player stands in with {@code HAVE}, and {@code explainShortfall}
     * skips anything that holds, so explaining a blind refusal with it would read "no route
     * you can use right now" instead of naming the missing tablet.
     */
    private String shortfallOf(RunStop stop, PlayerState state, CapabilityResolver asked) {
        TravelOption nearest = null;
        CapabilityResolver.Gap nearestGap = CapabilityResolver.Gap.NONE;
        for (TravelOption option : stop.getTravel()) {
            // Each start-only rung has a plain twin lower down, which speaks for it.
            if (option.isStartOnly()) {
                continue;
            }
            CapabilityResolver.Gap gap = asked.gapTo(option.getRequires(), state);
            if (gap != CapabilityResolver.Gap.NONE && (nearest == null || gap.compareTo(nearestGap) < 0)) {
                nearest = option;
                nearestGap = gap;
            }
        }
        String shortfall = nearest == null ? "" : asked.explainShortfall(nearest.getRequires(), state);
        return shortfall.isEmpty() ? "no route you can use right now" : shortfall;
    }
}
