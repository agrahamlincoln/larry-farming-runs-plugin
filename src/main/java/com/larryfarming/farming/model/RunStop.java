package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * One stop on a run: somewhere the player travels to, and what they do once there.
 *
 * <p>A stop is a visit, not a patch. Fossil Island's three hardwood patches are one stop
 * over three {@link PatchRef}s. Conversely, Weiss and Trollheim share one travel chain but
 * sit apart in the Lunar run's order, so they are two stops holding the same chain.
 *
 * <p>Stops keep the default route's order, which is already travel-optimised.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RunStop {

    /**
     * What happens when the chain runs out of rungs the player can take.
     *
     * <p>A chain ends one of three ways. Most end in an explicit "skip patch". A few say
     * no alternative exists, so a player who cannot take the route is missing part of the
     * run. The rest say nothing, which must not be read as either of the other two.
     */
    public enum TerminalBehavior {
        /** Drop the patch this run. */
        SKIP_PATCH,
        /** No alternative exists; without it the run is incomplete. */
        NO_ALTERNATIVE,
        /** The chain states no fallback for when it runs out. */
        UNSTATED
    }

    /** An action performed at the patch, in the order a run performs them. */
    public enum StopAction {
        CHECK_HEALTH,
        HARVEST,
        DIG_UP,
        COMPOST,
        PLANT,
        PAY_PROTECTION,
        KILL_HESPORI
    }

    /** One or more patches worked as a single visit. Never empty. */
    List<PatchRef> patches;

    /**
     * Where this stop is worked from. Two stops naming the same arrival are one visit,
     * whichever runs they belong to and whatever map regions their patches sit in. A
     * combined trip interleaves runs on this, not on region equality, which misgroups
     * Catherby and Ardougne.
     */
    Arrival arrival;

    /** Ordered best-first; the first rung the player can take is the one to use. */
    List<TravelOption> travel;

    TerminalBehavior terminal;

    /** What the player does at the patch, in order. Every stop states its own. */
    List<StopAction> actions;

    /**
     * What the player needs before this stop is worth including, or null when the run's
     * own gate is the only one. Harmony Island, for example, sits in the Lunar run but
     * needs the Elite Morytania diary.
     */
    UnlockGate gate;

    /**
     * The crop this patch grows by default, or null when the run's seeds decide.
     *
     * <p>Which of the run's seeds a patch without a fixed crop gets depends on the player's
     * diaries and quests, so {@code PatchPriority} scores it at planning time.
     */
    Crop fixedCrop;

    /** Whether the player picks this patch's crop, so it has no default of its own here. */
    boolean picked;

    /**
     * Why this stop is worth weaving in rather than part of the route proper, or null
     * for an ordinary stop. Optional stops do not count toward a run's stop total.
     */
    String optionalReason;

    /**
     * Visit this stop on every nth run, for the default crop. Normally 1. Slow crops such as
     * whiteberries use more, since their regrow does not line up with a herb run.
     */
    int everyNthRun;

    /**
     * Why this stop's patch does not, on its own, decide whether to go, or null for an
     * ordinary stop.
     *
     * <p>Used only by the farming contract, whose work is an errand with Guildmaster Jane.
     * The stop still names the guild herb patch, which the contract replants.
     *
     * <p>An errand stop is visited when its run says so. The contract's run also comes due
     * for herb patch work while the contract waits on a crop elsewhere. Everything else stays
     * patch work: the stop still plants, counts toward the bag and draws seeds from the
     * rotation.
     */
    String errandReason;

    /**
     * What the player needs before this patch is theirs to work at all, or null when
     * anyone who gets there can farm it.
     *
     * <p>The planner checks this before the travel chain. It covers a place that cannot be
     * entered (Lletya, before Mourning's End Part I) and a patch that cannot be farmed
     * (Weiss, before the fire of nourishment).
     */
    Capability accessGate;

    /** The patch that identifies this stop; the first when a visit works several. */
    public PatchRef primaryPatch() {
        return patches.get(0);
    }

    /**
     * The same stop with its rungs in another order, as {@link RungOrder} ranks them.
     *
     * @throws IllegalArgumentException unless {@code ladder} holds exactly this stop's rungs
     */
    public RunStop withLadder(List<TravelOption> ladder) {
        List<TravelOption> left = new ArrayList<>(travel);
        boolean same = true;
        for (TravelOption rung : ladder) {
            same &= left.remove(rung);
        }
        if (!same || !left.isEmpty()) {
            throw new IllegalArgumentException("a reordered ladder may only reorder at " + primaryPatch().getLabel());
        }
        return new RunStop(patches, arrival, List.copyOf(ladder), terminal, actions, gate,
                fixedCrop, picked, optionalReason, everyNthRun, errandReason, accessGate);
    }

    /** @see #errandReason */
    public boolean isErrand() {
        return errandReason != null;
    }

    /**
     * Whether this stop names its crop, rather than leaving it to the run's seeds. The crop
     * the stop plants can still differ from the default: see
     * {@code FarmingCatalogue.cropChoices}.
     */
    public boolean isFixedCrop() {
        return fixedCrop != null || picked;
    }

    /** Whether something is planted here, as against a contract errand alone. */
    public boolean isPlanting() {
        return actions.contains(StopAction.PLANT);
    }

    /**
     * What kind of patch this stop works, or null if the patch is unknown. The builder
     * refuses mixed patch types, so the first patch answers for all.
     */
    public PatchType patchType() {
        return primaryPatch().type();
    }

    /**
     * Whether what grows here is meant to stand rather than be harvested, so a living one is
     * a reason to leave the patch alone. Only a fruit tree keeps fruiting after a pick;
     * calquat and celastrus are used up by the harvest: a calquat goes barren after its
     * sixth fruit and is cleared with a spade.
     */
    public boolean isStandingCrop() {
        return patchType() == PatchType.FRUIT_TREE;
    }

    public boolean isOptional() {
        return optionalReason != null;
    }

    /** Whether a player in this region stands at one of this stop's patches. */
    public boolean isIn(Integer regionId) {
        return regionId != null && patches.stream().anyMatch(ref -> ref.getRegionId() == regionId);
    }

    public static Builder builder(PatchRef... patches) {
        return new Builder(patches);
    }

    /**
     * Hand-written rather than Lombok's so it can reject a stop that does not hold
     * together. Lombok writes the plain setters, each named for its field.
     */
    @Accessors(fluent = true)
    public static final class Builder {

        private final List<PatchRef> patches;
        /** Where the stop is worked from. Required. */
        @Setter
        private Arrival at;
        private final List<TravelOption> travel = new ArrayList<>();
        private TerminalBehavior terminal;
        private List<StopAction> actions;
        @Setter
        private UnlockGate gate;
        /** @see RunStop#fixedCrop */
        @Setter
        private Crop plants;
        private boolean picked;
        /** @see RunStop#optionalReason */
        @Setter
        private String optional;
        /** @see RunStop#errandReason */
        @Setter
        private String errand;
        private int everyNthRun = 1;
        /** What the player needs before the place is somewhere they can go at all. */
        @Setter
        private Capability accessGate;

        private Builder(PatchRef... patches) {
            if (patches.length == 0) {
                throw new IllegalArgumentException("a stop must name at least one patch");
            }
            this.patches = List.of(patches);
        }

        /** The stop's route, every rung in ranking order, each already carrying its figures. */
        public Builder chain(List<TravelOption> shared) {
            for (TravelOption option : shared) {
                add(option);
            }
            return this;
        }

        private Builder add(TravelOption option) {
            // A destination rung supplies its own opening clause once the teleport is
            // chosen, so an empty instruction is normal there.
            if (option.getDestination() == null && (option.getInstruction() == null || option.getInstruction().isBlank())) {
                throw new IllegalArgumentException("a route needs an instruction at " + patches.get(0).getLabel());
            }
            travel.add(option);
            return this;
        }

        public Builder skipIfUnreachable() {
            this.terminal = TerminalBehavior.SKIP_PATCH;
            return this;
        }

        public Builder noAlternative() {
            this.terminal = TerminalBehavior.NO_ALTERNATIVE;
            return this;
        }

        public Builder noFallbackGiven() {
            this.terminal = TerminalBehavior.UNSTATED;
            return this;
        }

        public Builder actions(StopAction... actions) {
            this.actions = List.of(actions);
            return this;
        }

        public Builder picked() {
            this.picked = true;
            return this;
        }

        public Builder everyNthRun(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("a stop cannot be visited every " + n + " runs");
            }
            this.everyNthRun = n;
            return this;
        }

        public RunStop build() {
            requireOnePatchType();
            if (at == null) {
                throw new IllegalStateException("no arrival named for " + patches.get(0).getLabel());
            }
            if (travel.isEmpty()) {
                throw new IllegalStateException("no way to reach " + patches.get(0).getLabel());
            }
            if (terminal == null) {
                throw new IllegalStateException("unstated fallback behaviour for " + patches.get(0).getLabel());
            }
            // A chain anyone can complete never reaches its terminal, so a stated terminal
            // on one is a catalogue error.
            if (terminal != TerminalBehavior.UNSTATED) {
                for (TravelOption option : travel) {
                    if (option.getRequires().isUnconditional()) {
                        throw new IllegalStateException(patches.get(0).getLabel() + " can always be reached, so it "
                                        + "never falls through to " + terminal);
                    }
                }
            }
            if (actions == null) {
                throw new IllegalStateException("no actions named for " + patches.get(0).getLabel());
            }
            return new RunStop(patches, at, List.copyOf(travel), terminal, actions, gate, plants, picked, optional, everyNthRun,
                    errand, accessGate);
        }

        /**
         * Throws unless every patch in this visit is known and of one type, since one visit
         * holds one action list.
         */
        private void requireOnePatchType() {
            PatchType shared = null;
            for (PatchRef patch : patches) {
                FarmingWorldMap.PatchDef def = patch.resolve();
                if (def == null) {
                    throw new IllegalStateException("unknown patch " + patch.getLabel());
                }
                if (shared == null) {
                    shared = def.getType();
                } else if (shared != def.getType()) {
                    throw new IllegalStateException("one visit mixes " + shared + " and "
                            + def.getType() + " patches at " + patches.get(0).getLabel());
                }
            }
        }
    }
}
