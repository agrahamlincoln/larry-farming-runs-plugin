package com.larryfarming.farming.engine;

import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import java.util.*;
import lombok.Value;

/**
 * Which version of a run to show a player, and which spellbook to plan it on.
 *
 * <p>The default herb run is written for the Arceuus book, which is the best route only for a
 * player who can cast its spells. So every variant is planned against the book the player
 * is on and the book the variant is written for, and the pairing that reaches the most
 * patches wins. The same pairing decides both the walkthrough and the run's readiness, so
 * the two screens agree.
 *
 * <p>Ties go to the book already open: a swap costs a trip to an altar, so it has to earn
 * its keep in patches.
 */
public final class RunVariants {

    private RunVariants() {
    }

    /** A run, the book to plan it on, and what that pairing is worth. */
    @Value
    public static class Choice {
        FarmingRun run;
        Spellbook planFor;
        /**
         * Patches on stops we can see a route to, which is the strongest evidence.
         *
         * <p><b>Counted as though the player were nowhere in particular</b>, unlike
         * {@code plans}. Standing at an arrival makes it free on every book at once, which
         * erases the differences between books. Changing book is a whole-run commitment, so
         * it must not turn on one arrival the player will have left by the second stop.
         */
        int certainPatches;
        /** Those plus the ones whose route rests on something we cannot check. */
        int reachablePatches;

        /**
         * The pairing already planned, so the caller does not plan it again on a path that
         * reruns for every game event.
         *
         * <p><b>Planned knowing where the player is</b>, unlike the counts above.
         * {@code RunScheduler} walks these to decide whether a run is due and skips any stop
         * that is not a visit, so a blind plan would hide work the player can do from where
         * they stand.
         *
         * <p>Empty on a {@link Choice} built without scoring (the preview path, which names
         * a book outright); its caller plans for itself.
         */
        List<StopPlan> plans;
    }

    /**
     * The best pairing of variant and book among runs that share an id.
     *
     * @param variants every version of one run; never empty
     * @param openBook the book the player is on, which wins ties because a swap it does
     *                 not pay for is advice the player is right to ignore
     * @param resolvers a cache of one resolver per book, since building one is the
     *                  expensive half of answering this and a schedule asks it repeatedly
     */
    public static Choice best(List<FarmingRun> variants, PlayerState state, Spellbook openBook, FarmingInputs inputs,
            Map<Spellbook, CapabilityResolver> resolvers) {
        Choice best = null;
        for (FarmingRun run : variants) {
            for (Spellbook book : booksWorthTrying(run, openBook)) {
                CapabilityResolver resolver = resolvers.computeIfAbsent(book, inputs::resolverOn);
                Choice candidate = score(run, book, state, resolver, inputs.getPreferences());
                if (best == null || beats(candidate, best, state, inputs.getPreferences(), openBook)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * The books this variant is worth measuring on: the one the player has open, and the
     * one the variant is written for.
     *
     * <p>The open book says what the run is worth if they set off now. The variant's own
     * book says what it would be worth after the swap it asks for.
     */
    private static List<Spellbook> booksWorthTrying(FarmingRun run, Spellbook openBook) {
        Spellbook book = openBook != null ? openBook : Spellbook.STANDARD;
        List<Spellbook> books = new ArrayList<>(2);
        books.add(book);
        if (run.getSpellbook() != Spellbook.ANY && run.getSpellbook() != book) {
            books.add(run.getSpellbook());
        }
        return books;
    }

    /**
     * Whether this pairing is the better one to put in front of the player.
     *
     * <p>Locked last, then the variant written for the open book, then patches, then the
     * open book as the tie-break. A run the player cannot start is worth nothing however
     * many patches it names: a Lunar variant gated behind Lunar Diplomacy must not win on
     * patches alone.
     */
    private static boolean beats(Choice candidate, Choice best, PlayerState state, FarmingPreferences prefs, Spellbook openBook) {
        boolean candidateOpen = isAvailable(candidate.run, state, prefs);
        boolean bestOpen = isAvailable(best.run, state, prefs);
        if (candidateOpen != bestOpen) {
            return candidateOpen;
        }
        // A player on a book a run is written for has already chosen it. The Lunar run adds
        // Harmony Island, which a tablet reaches from any book, so it can name one patch
        // more than the Arceuus run for a player on Arceuus. Patch count must not overturn
        // the book they are on.
        boolean candidateIsTheirs = candidateOpen && candidate.run.getSpellbook() == openBook;
        boolean bestIsTheirs = bestOpen && best.run.getSpellbook() == openBook;
        if (candidateIsTheirs != bestIsTheirs) {
            return candidateIsTheirs;
        }
        // Certain patches decide it before possible ones, so a book whose every arrival is a
        // maybe cannot beat one whose arrivals are known to work.
        if (candidate.certainPatches != best.certainPatches) {
            return candidate.certainPatches > best.certainPatches;
        }
        if (candidate.reachablePatches != best.reachablePatches) {
            return candidate.reachablePatches > best.reachablePatches;
        }
        return candidate.planFor == openBook && best.planFor != openBook;
    }

    private static boolean isAvailable(FarmingRun run, PlayerState state, FarmingPreferences prefs) {
        return RunAvailability.of(RunAvailability.gateOf(run, prefs), state)
                != CapabilityState.LACK;
    }

    /**
     * Patches this player could work, counted on the route proper. Optional stops are no
     * reason to prefer one route over another, and neither are stops the player has
     * switched off: Harmony, once dropped, must not tip the herb run to the Lunar variant.
     */
    private static Choice score(FarmingRun run, Spellbook book, PlayerState state, CapabilityResolver resolver, FarmingPreferences prefs) {
        // Sometimes two plans: the counts are blind to position and the kept plans are not
        // (see the fields on Choice). A sighted and a blind resolver differ only where a
        // zone covers the player's region or a teleport is spent on the way, because
        // STANDING_IN answers LACK elsewhere and TeleportPlanner's short-circuit uses the
        // same test. Otherwise the two plans are provably the same list, so the second is
        // skipped.
        List<StopPlan> plans = new RunPlanner(resolver).plan(run, state);
        boolean positionCouldMatter = resolver.readsPosition() && (Zone.covering(state == null ? null : state.getRegionId()) != null
                        || resolver.hasTravelTaken());
        List<StopPlan> anywhere = positionCouldMatter ? new RunPlanner(resolver.blindToPosition()).plan(run, state) : plans;
        int certain = 0;
        int reachable = 0;
        for (StopPlan plan : anywhere) {
            if (!plan.isVisit() || plan.getStop().isOptional() || PlantingGate.silences(run, plan.getStop(), prefs)) {
                continue;
            }
            int patches = plan.getStop().getPatches().size();
            reachable += patches;
            if (!plan.isUncertain()) {
                certain += patches;
            }
        }
        return new Choice(run, book, certain, reachable, plans);
    }

    /** A fresh cache, for a caller that has no schedule to share one with. */
    public static Map<Spellbook, CapabilityResolver> newResolverCache() {
        return new EnumMap<>(Spellbook.class);
    }
}
