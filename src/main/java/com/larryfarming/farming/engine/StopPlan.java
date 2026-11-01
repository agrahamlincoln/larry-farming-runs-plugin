package com.larryfarming.farming.engine;

import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.*;
import com.larryfarming.snapshot.FarmingSkipReason;
import java.util.Collections;
import java.util.Set;
import lombok.Value;

/**
 * What to do about one stop on a run: how to get there, or why not to bother.
 */
@Value
public class StopPlan {

    public enum Outcome {
        /** Go, by the named route. */
        VISIT,
        /** The route says to drop this patch when it cannot be reached cheaply. */
        SKIP,
        /**
         * Nothing reaches this patch, and the catalogue states that nothing does. The only
         * outcome that makes a claim about the game.
         */
        UNREACHABLE,
        /**
         * Every listed route has failed and the catalogue gives no fallback. A claim about
         * the catalogue, not the game: the patch may be reachable
         * by an unlisted route, so never present this as a dead end.
         */
        UNROUTED
    }

    RunStop stop;
    Outcome outcome;

    /** The route to take, or null when not visiting. */
    TravelOption route;

    /**
     * How sure we are the route works. Present a route offered on
     * {@link CapabilityState#UNKNOWN} as unsettled.
     *
     * <p>On a stop not visited, {@link CapabilityState#LACK} means every rung is a definite
     * no, and {@link CapabilityState#UNKNOWN} means a rung waits on an unread fact.
     */
    CapabilityState confidence;

    /** One clause for the player, whichever way this went. */
    String because;

    /**
     * Which kind of "not this one" this is, or null for a visit. The walkthrough sorts and
     * colours on this; {@link #because} is the sentence the player reads.
     */
    FarmingSkipReason skipReason;

    /**
     * When whatever is growing here finishes, as an epoch second, or 0 when nothing is
     * growing or the tracker has never seen the patch.
     *
     * <p>A raw instant rather than a worded countdown: it holds still, so the panel can
     * key its rebuilds on it and re-word the countdown in place without rebuilding the row
     * every minute.
     *
     * <p>Carried whenever it is known, including on {@link FarmingSkipReason#NO_SEED}
     * skips: a patch can be both growing and short of a seed for the next planting.
     */
    long readyAtEpochSec;

    /**
     * What the chosen route leans on, with any "or" already decided. The packing list is
     * built from this. Empty when not visiting or when the route asks for nothing.
     */
    Set<Capability> needs;

    /**
     * The teleport this player is making, when the route is written as a place. Null for
     * a route that names its own item, and null when not visiting.
     * It decides whether the bag packs a tablet or runes.
     */
    TeleportMeans teleport;

    /** A skip for a reason found in the ground or the stock, after the route was settled. */
    static StopPlan skip(RunStop stop, String because, FarmingSkipReason reason, long readyAtEpochSec) {
        return new StopPlan(stop, Outcome.SKIP, null, CapabilityState.HAVE, because, reason, readyAtEpochSec, Collections.emptySet(), null);
    }

    public boolean isVisit() {
        return outcome == Outcome.VISIT;
    }

    /** True when the route is worth flagging as a guess rather than stated flatly. */
    public boolean isUncertain() {
        return outcome == Outcome.VISIT && confidence != CapabilityState.HAVE;
    }
}
