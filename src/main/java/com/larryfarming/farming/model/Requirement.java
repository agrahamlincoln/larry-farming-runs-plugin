package com.larryfarming.farming.model;

import java.util.*;
import java.util.function.Function;
import lombok.*;

/**
 * What a single travel rung asks of the player, as a boolean expression. Usually one
 * {@link Capability}, but some rungs need an "or" inside an "and": "Royal seed pod/slayer
 * ring to Grand Tree spirit tree to Port Sarim" needs either of two items <em>and</em> a
 * grown spirit tree.
 *
 * <p>A rung can also depend on having reached another stop. The Taverley chain
 * ends with "run from Falador tree", which is free after the Falador patch and impossible
 * if it was skipped ({@link Kind#REACHED_PATCH}).
 *
 * <p>This type carries no evaluation. Answering it needs three-valued logic, where
 * {@code any(HAVE, UNKNOWN)} is HAVE but {@code all(HAVE, UNKNOWN)} is UNKNOWN, and the
 * resolver owns that logic.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Requirement {

    public enum Kind {
        CAPABILITY,
        /**
         * Being able to reach a place, by whatever means this player has. Distinct from a
         * capability because the means depend on the open spellbook: the same arrival is a
         * cast on one book and a tablet on another.
         */
        DESTINATION,
        /**
         * Depends on having already worked another stop: within the run when a run is
         * walked alone, or in any family when a combined trip is re-checked in circuit
         * order.
         */
        REACHED_PATCH,
        /**
         * Depends on where the game reports the player standing now, unlike
         * {@link #REACHED_PATCH}, which depends on where the plan routed them. Never
         * {@code UNKNOWN}: an unread position is a no. See {@link Zone}.
         */
        STANDING_IN,
        /** Every part must hold. An empty ALL is vacuously satisfied. */
        ALL,
        /** At least one part must hold. An empty ANY can never be satisfied. */
        ANY
    }

    Kind kind;

    /** Set only when {@link #kind} is {@link Kind#CAPABILITY}. */
    Capability capability;

    /** Set only when {@link #kind} is {@link Kind#DESTINATION}. */
    Destination destination;

    /** Set only when {@link #kind} is {@link Kind#REACHED_PATCH}. */
    PatchRef patch;

    /** Set only when {@link #kind} is {@link Kind#STANDING_IN}. */
    Zone zone;

    /**
     * True on a {@link Kind#DESTINATION} reached by Spellbook Swap and that place's spell
     * from another book. Only this kind of rung offers the swap.
     */
    boolean bySwap;

    /** Immutable; empty unless {@link #kind} is {@link Kind#ALL} or {@link Kind#ANY}. */
    List<Requirement> parts;

    private static Requirement leaf(Kind kind, Capability capability, Destination destination, PatchRef patch, Zone zone) {
        return new Requirement(kind, capability, destination, patch, zone, false, List.of());
    }

    private static Requirement of(Kind kind, List<Requirement> parts) {
        return new Requirement(kind, null, null, null, null, false, parts);
    }

    public static Requirement cap(Capability capability) {
        if (capability == null) {
            throw new IllegalArgumentException("a capability requirement needs a capability");
        }
        return leaf(Kind.CAPABILITY, capability, null, null, null);
    }

    /** Being able to get to a place, however this player gets there. */
    public static Requirement reach(Destination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("a destination requirement needs a destination");
        }
        return leaf(Kind.DESTINATION, null, destination, null, null);
    }

    /**
     * Being able to get to a place by Spellbook Swap, then its spell from another book.
     * Never answered by a tablet or by a spell on the open book; a rung beside it asks for
     * those.
     */
    public static Requirement reachBySwap(Destination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("a destination requirement needs a destination");
        }
        return new Requirement(Kind.DESTINATION, null, destination, null, null, true, List.of());
    }

    public static Requirement reachedPatch(PatchRef patch) {
        if (patch == null) {
            throw new IllegalArgumentException("a reached-patch requirement needs a patch");
        }
        return leaf(Kind.REACHED_PATCH, null, null, patch, null);
    }

    /**
     * Being somewhere already, as the game reports it.
     *
     * <p>Used on a rung that is free only from that ground, such as the Stronghold's spirit
     * tree for a player standing beside it. Rank the rung by its real cost, not at the top
     * for being free: a whistle still beats a short walk to a tree.
     */
    public static Requirement standingIn(Zone zone) {
        if (zone == null) {
            throw new IllegalArgumentException("a standing-in requirement needs a zone");
        }
        return leaf(Kind.STANDING_IN, null, null, null, zone);
    }

    public static Requirement all(Requirement... parts) {
        return of(Kind.ALL, List.of(parts));
    }

    public static Requirement any(Requirement... parts) {
        return of(Kind.ANY, List.of(parts));
    }

    /** The common shape: any one of several interchangeable items will do. */
    public static Requirement any(Capability... capabilities) {
        Requirement[] parts = new Requirement[capabilities.length];
        for (int i = 0; i < capabilities.length; i++) {
            parts[i] = cap(capabilities[i]);
        }
        return of(Kind.ANY, List.of(parts));
    }

    /** A rung that asks for nothing — the floor of a chain with no skip. */
    public static Requirement none() {
        return of(Kind.ALL, List.of());
    }

    /**
     * This expression with every occurrence of {@code part} read as held, for a player the
     * trip has already put where {@code part} would take them. Returns this same object when
     * {@code part} does not occur.
     */
    public Requirement granting(Requirement part) {
        if (equals(part)) {
            return none();
        }
        if (parts.isEmpty()) {
            return this;
        }
        List<Requirement> granted = new ArrayList<>(parts.size());
        boolean changed = false;
        for (Requirement each : parts) {
            Requirement after = each.granting(part);
            changed |= after != each;
            granted.add(after);
        }
        return changed ? new Requirement(kind, capability, destination, patch, zone, bySwap, List.copyOf(granted)) : this;
    }

    /** Whether this expression, or any part of it, is exactly {@code other}. */
    public boolean contains(Requirement other) {
        if (equals(other)) {
            return true;
        }
        for (Requirement part : parts) {
            if (part.contains(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Every capability mentioned anywhere in this expression.
     */
    public Set<Capability> leafCapabilities() {
        return leaves(Kind.CAPABILITY, Requirement::getCapability);
    }

    /**
     * Every patch this expression asks the player to have already worked. These rungs start
     * where the trip has already put the player, which makes them free.
     */
    public Set<PatchRef> leafPatches() {
        return leaves(Kind.REACHED_PATCH, Requirement::getPatch);
    }

    /** What every leaf of this kind anywhere in this expression holds, first found first. */
    public <T> Set<T> leaves(Kind leafKind, Function<Requirement, T> value) {
        Set<T> found = new LinkedHashSet<>();
        collect(leafKind, value, found);
        return Collections.unmodifiableSet(found);
    }

    private <T> void collect(Kind leafKind, Function<Requirement, T> value, Set<T> into) {
        if (kind == leafKind) {
            into.add(value.apply(this));
            return;
        }
        for (Requirement part : parts) {
            part.collect(leafKind, value, into);
        }
    }

    /**
     * Whether anything in here can be answered from where the player is standing — either
     * a zone named outright, or a place whose ground is known.
     *
     * <p>Answers what the zone and destination leaves answer together, without allocating,
     * because the planner calls this for every rung on every snapshot.
     */
    public boolean readableFromPosition() {
        if (kind == Kind.STANDING_IN) {
            return true;
        }
        if (kind == Kind.DESTINATION) {
            return destination.getZone() != null;
        }
        for (Requirement part : parts) {
            if (part.readableFromPosition()) {
                return true;
            }
        }
        return false;
    }

    /** True for {@link #none()} — a rung every player can take. */
    public boolean isUnconditional() {
        return kind == Kind.ALL && parts.isEmpty();
    }
}
