package com.larryfarming.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * How many farming contracts the player hands in a day, counted as Larry sees them handed
 * in, over the last week.
 *
 * <p>Only hand-ins seen count: a contract finished with the plugin off is missed, so the
 * rate errs low.
 */
public final class ContractTally {

    /** The days the rate averages over, and how long a hand-in is kept. */
    public static final int WINDOW_DAYS = 7;

    /**
     * Fewer days watched than this give no rate. Until the window fills the rate averages
     * over the days watched, so one busy evening must not stand for a habit.
     */
    static final int LEAST_DAYS = 3;

    private static final Duration WINDOW = Duration.ofDays(WINDOW_DAYS);
    private static final Duration LEAST = Duration.ofDays(LEAST_DAYS);

    private ContractTally() {
    }

    /** Starts the count from now, unless it has already started. */
    public static void startWatching(PlayerState state, Instant now) {
        if (state.getContractsWatchedSince() == null) {
            state.setContractsWatchedSince(now);
        }
    }

    /** Records a contract handed in now, and forgets hand-ins older than the window. */
    public static void handIn(PlayerState state, Instant now) {
        startWatching(state, now);
        List<Instant> kept = new ArrayList<>();
        for (Instant at : state.getContractsHandedIn()) {
            if (!at.isBefore(now.minus(WINDOW))) {
                kept.add(at);
            }
        }
        kept.add(now);
        state.setContractsHandedIn(kept);
    }

    /**
     * Contracts handed in a day, averaged over the window, or over the days watched while
     * that is shorter than the window. Null until {@link #LEAST_DAYS} have been watched.
     */
    public static Double perDay(PlayerState state, Instant now) {
        Instant since = state.getContractsWatchedSince();
        if (since == null) {
            return null;
        }
        Duration watched = Duration.between(since, now);
        if (watched.compareTo(LEAST) < 0) {
            return null;
        }
        Duration window = watched.compareTo(WINDOW) < 0 ? watched : WINDOW;
        Instant from = now.minus(window);
        int count = 0;
        for (Instant at : state.getContractsHandedIn()) {
            if (!at.isBefore(from) && !at.isAfter(now)) {
                count++;
            }
        }
        return count / (window.getSeconds() / (double) Duration.ofDays(1).getSeconds());
    }
}
