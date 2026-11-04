package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.*;
import java.util.*;

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

    /** Every arrival's seat on the one circuit, which every book walks. */
    private static final Map<Arrival, Integer> SEATS = seats(CatalogueLoader.loaded().circuit);

    /** The circuit's seats, refusing an arrival listed twice or never. */
    private static Map<Arrival, Integer> seats(List<Arrival> circuit) {
        Map<Arrival, Integer> seats = new EnumMap<>(Arrival.class);
        for (int i = 0; i < circuit.size(); i++) {
            if (seats.put(circuit.get(i), i) != null) {
                throw new IllegalStateException(circuit.get(i) + " has two seats");
            }
        }
        for (Arrival arrival : Arrival.values()) {
            if (!seats.containsKey(arrival)) {
                throw new IllegalStateException(arrival + " has no seat");
            }
        }
        return Collections.unmodifiableMap(seats);
    }

    /**
     * Where this arrival sits on the circuit. Lower comes first. Seats run 0..n-1 with no
     * gaps, and the table refuses to load if an {@link Arrival} has none.
     */
    public static int seat(Arrival arrival) {
        return SEATS.get(arrival);
    }

    /**
     * How to reach this arrival when several of its patches are being worked, or empty
     * when its stops' own chains already agree.
     */
    static List<TravelOption> combinedChain(Arrival arrival) {
        return CatalogueLoader.loaded().combined.get(arrival);
    }
}
