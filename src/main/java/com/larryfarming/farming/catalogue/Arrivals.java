package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.*;
import java.util.List;

/**
 * The arrival table: each arrival's seat on the circuit, and how to reach it when more than
 * one patch there is being worked.
 *
 * <p>The seats are the order a farm trip visits places in, authored once over every arrival
 * so a player walks the same learnable circuit each time, on every spellbook. Lunar keeps it
 * too: every Lunar herb stop is a teleport that works from anywhere, and the circuit keeps
 * walked neighbours from other families together. A trip is this order with the arrivals it
 * has no work at removed. The order is fixed; the travel is not.
 *
 * <p>Most arrivals need no combined chain. The exception is an arrival whose landing spots
 * fall between its patches, so the rung closest to one is furthest from the other. Combined
 * chains do not replace the stops' own chains: a trip working one patch still uses that
 * patch's ranking.
 */
public final class Arrivals {

    private Arrivals() {
    }

    private static final Seats SEATS = Seats.circuit(CatalogueLoader.loaded().circuit);

    /** Every book's seats, for a caller that walks one order against another. */
    public static Seats seats() {
        return SEATS;
    }

    /**
     * Where this arrival sits in this book's order. Lower comes first. Seats run 0..n-1 with
     * no gaps, and the table refuses to load if an {@link Arrival} has none.
     */
    public static int seat(Spellbook book, Arrival arrival) {
        return SEATS.seat(book, arrival);
    }

    /**
     * How to reach this arrival when several of its patches are being worked, or empty
     * when its stops' own chains already agree.
     */
    static List<TravelOption> combinedChain(Arrival arrival) {
        return CatalogueLoader.loaded().combined.get(arrival);
    }
}
