package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Spellbook;
import java.util.*;

/**
 * The order a farm trip visits arrivals in.
 *
 * <p>Every book walks the one circuit. A merged trip reads the seats of the one book it is
 * walked on, so the order is asked for by book.
 *
 * <p>The order holds every {@link Arrival} exactly once, and refuses to build otherwise.
 */
public final class Seats {

    private final Map<Arrival, Integer> seats;

    private Seats(List<Arrival> arrivals) {
        Map<Arrival, Integer> seats = new EnumMap<>(Arrival.class);
        for (int i = 0; i < arrivals.size(); i++) {
            if (seats.put(arrivals.get(i), i) != null) {
                throw new IllegalStateException(arrivals.get(i) + " has two seats");
            }
        }
        for (Arrival arrival : Arrival.values()) {
            if (!seats.containsKey(arrival)) {
                throw new IllegalStateException(arrival + " has no seat");
            }
        }
        this.seats = Collections.unmodifiableMap(seats);
    }

    /** Every book on this one order. */
    static Seats circuit(List<Arrival> circuit) {
        return new Seats(circuit);
    }

    /**
     * Where this arrival sits in this book's order. Lower comes first. Seats run 0..n-1 with
     * no gaps.
     */
    public int seat(Spellbook book, Arrival arrival) {
        return seats.get(arrival);
    }
}
