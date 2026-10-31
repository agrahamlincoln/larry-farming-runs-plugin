package com.larryfarming.farming.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * A way onto a travel network that rungs ask for as an "or" of ways in, such as standing at
 * a fairy ring by house, quest cape or Salve graveyard teleport. Each way in carries its
 * walk from its landing to the network.
 *
 * <p>A rung asking for an approach leaves the approach's walk out of its own figure, because
 * which way in a player takes depends on the account: the first they certainly have, else
 * the first they might. The planner adds that leg's walk and cost per account, so a ring
 * rung can rank above a teleport for one player and below it for another.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Approach {

    /** The "or" the rungs write, one part per leg in the legs' order. */
    Requirement way;

    List<Leg> legs;

    /** One way in, with the tiles walked from its landing to the network. */
    @Value
    public static class Leg {
        Requirement requires;
        int walk;
        TravelOption.Cost cost;
    }

    /** One way in. */
    public static Leg leg(Requirement requires, int walk, TravelOption.Cost cost) {
        if (walk < 0) {
            throw new IllegalArgumentException("a walk cannot be negative");
        }
        if (cost == null) {
            throw new IllegalArgumentException("a leg has to say what it spends");
        }
        return new Leg(requires, walk, cost);
    }

    /** The approach whose ways in are these legs, tried in this order. */
    public static Approach of(Leg... legs) {
        List<Requirement> parts = new ArrayList<>(legs.length);
        for (Leg leg : legs) {
            parts.add(leg.getRequires());
        }
        return new Approach(Requirement.any(parts.toArray(new Requirement[0])),
                List.of(legs));
    }

    /** The leg whose requirement is this part of {@link #getWay()}, or null for none. */
    public Leg legFor(Requirement part) {
        for (Leg leg : legs) {
            if (leg.getRequires().equals(part)) {
                return leg;
            }
        }
        return null;
    }
}
