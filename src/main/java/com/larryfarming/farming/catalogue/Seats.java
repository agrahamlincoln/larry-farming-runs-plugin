package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Spellbook;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The order a farm trip visits arrivals in, per spellbook.
 *
 * <p>Every book walks the circuit unless it is given an order of its own. A book's teleports
 * can make another order cheaper, so the order is keyed by book, as a site's ladder is. A
 * run reads the seats of the book it is written for, so a family written for any book walks
 * {@link Spellbook#ANY}'s. A merged trip reads the seats of the one book it is walked on.
 *
 * <p>Each order holds every {@link Arrival} exactly once, and refuses to build otherwise.
 */
public final class Seats {

    private final Order circuit;
    private final Map<Spellbook, Order> ownOrders;

    private Seats(Order circuit, Map<Spellbook, Order> ownOrders) {
        this.circuit = circuit;
        this.ownOrders = Collections.unmodifiableMap(ownOrders);
    }

    /** Every book on this one order. */
    static Seats circuit(List<Arrival> circuit) {
        return new Seats(new Order(circuit), new EnumMap<>(Spellbook.class));
    }

    /**
     * The same seats with this book walking its own order. Public so the engine's trips can
     * be walked on an order the catalogue has not given any book.
     */
    public Seats withOrder(Spellbook book, List<Arrival> order) {
        Objects.requireNonNull(book, "book");
        Map<Spellbook, Order> orders = new EnumMap<>(Spellbook.class);
        orders.putAll(ownOrders);
        if (orders.put(book, new Order(order)) != null) {
            throw new IllegalStateException(book + " has two orders");
        }
        return new Seats(circuit, orders);
    }

    /**
     * Where this arrival sits in this book's order. Lower comes first. Seats run 0..n-1 with
     * no gaps.
     */
    public int seat(Spellbook book, Arrival arrival) {
        return orderOf(book).seats.get(arrival);
    }

    /** Every arrival, in the order a trip on this book visits them. */
    public List<Arrival> inSeatOrder(Spellbook book) {
        return orderOf(book).arrivals;
    }

    private Order orderOf(Spellbook book) {
        // EnumMap answers null for a null key, which would quietly fall back to the circuit.
        Order own = ownOrders.get(Objects.requireNonNull(book, "book"));
        return own != null ? own : circuit;
    }

    private static final class Order {
        final List<Arrival> arrivals;
        final Map<Arrival, Integer> seats;

        Order(List<Arrival> arrivals) {
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
            this.arrivals = List.copyOf(arrivals);
            this.seats = Collections.unmodifiableMap(seats);
        }
    }
}
