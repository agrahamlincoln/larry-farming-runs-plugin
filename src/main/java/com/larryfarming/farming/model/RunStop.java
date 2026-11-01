package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

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
        KILL_HESPORI;

        /**
         * The ordinary working of a patch of this type.
         *
         * <p>Stops use these defaults unless a stop states its own technique.
         *
         * <p>Every supported type is named explicitly, so an unhandled type (a spirit tree,
         * a vinery) throws instead of getting a tree's actions.
         */
        public static List<StopAction> defaultsFor(PatchType type) {
            switch (type) {
                // Picked clean and then gone, with nothing to dig up. Mushrooms belong
                // here, not with the bushes: the patch has no regrow and goes straight to
                // weeds after the last picking.
                //
                // No PAY_PROTECTION: the wiki lists the mushroom patch "unprotectable", no
                // gardener stands by the belladonna, and InventoryDemands.collectProtection
                // reads this list to know that.
                case HERB:
                case ALLOTMENT:
                case FLOWER:
                case HOPS:
                case SEAWEED:
                case MUSHROOM:
                case BELLADONNA:
                    return List.of(HARVEST, COMPOST, PLANT);
                // A grown bush or cactus wants its health check before the first picking,
                // as a fruit tree does. Unlike a fruit tree, picking never makes it spent
                // ground (PatchWork.standsEmpty). Once grown it is dug up and replanted each
                // visit, so a fresh plant pays its check again (PatchWork.comesOut).
                //
                // Fruit trees, calquat and celastrus are checked and then picked: fruit trees
                // and calquat offer Pick-fruit once healthy, and celastrus offers Harvest.
                // Without HARVEST a grown fruit tree reads as empty ground wanting a replant.
                case BUSH:
                case CACTUS:
                case FRUIT_TREE:
                case CALQUAT:
                case CELASTRUS:
                    return List.of(CHECK_HEALTH, HARVEST, DIG_UP, COMPOST, PLANT);
                case HESPORI:
                    return List.of(KILL_HESPORI, COMPOST, PLANT);
                // Nothing to pick and no disease to compost against: an anima plant is
                // planted, and cleared once it dies.
                case ANIMA:
                    return List.of(PLANT);
                // The health check pays the Farming experience. Cutting the logs is a
                // Woodcutting errand, so it is not an action here.
                case TREE:
                case HARDWOOD_TREE:
                case REDWOOD:
                    return List.of(CHECK_HEALTH, DIG_UP, COMPOST, PLANT);
                default:
                    throw new IllegalArgumentException("no default actions for a " + type + " patch");
            }
        }
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
     * True when what is planted here is meant to stand rather than be harvested, so a
     * living one is a reason to leave the patch alone.
     *
     * <p>The white lily protects the neighbouring allotments from disease for as long as it
     * lives, so it is not replaced each run. Fruit trees get this flag from their patch type
     * (see {@code Builder.keepsProducing}).
     */
    boolean standingCrop;

    /**
     * Why this stop's patch does not decide whether to go, or null for an ordinary stop.
     *
     * <p>Used only by the farming contract, whose work is an errand with Guildmaster Jane.
     * The stop still names the guild herb patch, which the contract replants.
     *
     * <p>Keep this narrow: an errand stop is visited when its run says so rather than when
     * its patch does, but everything else stays patch work. It still plants, counts toward
     * the bag and draws seeds from the rotation.
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
                fixedCrop, optionalReason, everyNthRun, standingCrop, errandReason, accessGate);
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
        return fixedCrop != null;
    }

    /** Whether something is planted here, as against a contract errand alone. */
    public boolean isPlanting() {
        return actions != null && actions.contains(StopAction.PLANT);
    }

    /**
     * What kind of patch this stop works, or null if the patch is unknown. The builder
     * refuses mixed patch types, so the first patch answers for all.
     */
    public PatchType patchType() {
        FarmingWorldMap.PatchDef def = primaryPatch().resolve();
        return def != null ? def.getType() : null;
    }

    public boolean isOptional() {
        return optionalReason != null;
    }

    public static Builder builder(PatchRef... patches) {
        return new Builder(patches);
    }

    /**
     * Hand-written rather than Lombok's so it can derive the action list from the patch
     * type and reject a stop that does not hold together.
     */
    public static final class Builder {

        private final List<PatchRef> patches;
        private Arrival arrival;
        private final List<TravelOption> travel = new ArrayList<>();
        private TerminalBehavior terminal;
        private List<StopAction> actions;
        private UnlockGate gate;
        private Crop fixedCrop;
        private String optionalReason;
        private String errandReason;
        private int everyNthRun = 1;
        private boolean standingCrop;
        private Capability accessGate;

        private Builder(PatchRef... patches) {
            if (patches.length == 0) {
                throw new IllegalArgumentException("a stop must name at least one patch");
            }
            this.patches = List.of(patches);
        }

        /** Where the stop is worked from. Required. */
        public Builder at(Arrival arrival) {
            this.arrival = arrival;
            return this;
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

        /** What the player needs before the place is somewhere they can go at all. */
        public Builder accessGate(Capability capability) {
            this.accessGate = capability;
            return this;
        }

        public Builder gate(UnlockGate gate) {
            this.gate = gate;
            return this;
        }

        public Builder plants(Crop crop) {
            this.fixedCrop = crop;
            return this;
        }

        public Builder optional(String reason) {
            this.optionalReason = reason;
            return this;
        }

        /** @see RunStop#errandReason */
        public Builder errand(String reason) {
            this.errandReason = reason;
            return this;
        }

        /** What is planted here stands until it dies; a living one needs no replacing. */
        public Builder standingCrop() {
            this.standingCrop = true;
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
            PatchType type = resolvePatchType();
            if (arrival == null) {
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
            List<StopAction> resolved = actions != null ? actions : StopAction.defaultsFor(type);
            return new RunStop(patches, arrival, List.copyOf(travel), terminal, resolved, gate, fixedCrop, optionalReason, everyNthRun,
                    standingCrop || keepsProducing(type), errandReason, accessGate);
        }

        /**
         * Whether harvesting this kind of patch leaves the crop standing and still working.
         *
         * <p>Derived from the type so a new fruit tree stop cannot forget it. Only a fruit
         * tree keeps fruiting after a pick. Calquat and celastrus are used up by the
         * harvest: a calquat goes barren after its sixth fruit and is cleared with a spade.
         */
        private static boolean keepsProducing(PatchType type) {
            return type == PatchType.FRUIT_TREE;
        }

        /**
         * The patch type shared by every patch in this visit. Throws on a mix, since one
         * visit needs one action list.
         */
        private PatchType resolvePatchType() {
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
            return shared;
        }
    }
}
