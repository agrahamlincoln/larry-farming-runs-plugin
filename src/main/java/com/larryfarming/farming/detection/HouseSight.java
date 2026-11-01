package com.larryfarming.farming.detection;

import java.util.*;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * What the player's own house was last seen to hold, for the rules that answer a house
 * capability: the furniture of the last complete scan, and the Portal Nexus's attunement.
 *
 * <p>Absence means something only once it has been looked for. Furniture is absent when the
 * last scan of the house lacked it. A Nexus destination is absent when the last full read of
 * the Nexus lacked it. Before either, nothing is known and the rules keep their "not seen"
 * answer.
 *
 * <p>Immutable. Each observation returns a new sight.
 */
@EqualsAndHashCode
@ToString
public final class HouseSight {

    /** A stamp meaning "never". Real stamps are epoch seconds, so always above it. */
    private static final long NEVER = 0L;

    private static final HouseSight UNSEEN = new HouseSight(Collections.emptySet(), NEVER, Collections.emptySet(), NEVER);

    private final Set<HouseFeature> built;
    private final long scannedAtEpochSec;
    private final Set<Integer> attuned;
    private final long nexusReadAtEpochSec;

    private HouseSight(Set<HouseFeature> built, long scannedAtEpochSec, Set<Integer> attuned, long nexusReadAtEpochSec) {
        this.built = built;
        this.scannedAtEpochSec = scannedAtEpochSec;
        this.attuned = attuned;
        this.nexusReadAtEpochSec = nexusReadAtEpochSec;
    }

    /** A house never seen, which is every house until its owner walks in. */
    public static HouseSight unseen() {
        return UNSEEN;
    }

    /**
     * A sight as saved.
     *
     * @param scannedAtEpochSec when the furniture was scanned, or 0 for never
     * @param nexusReadAtEpochSec when the Nexus was fully read, or 0 for never
     */
    public static HouseSight of(Set<HouseFeature> built, long scannedAtEpochSec, Set<Integer> attuned, long nexusReadAtEpochSec) {
        return new HouseSight(copyOf(built), Math.max(NEVER, scannedAtEpochSec), keysOf(attuned), Math.max(NEVER, nexusReadAtEpochSec));
    }

    /**
     * This house as one complete scan found it. Replaces the furniture, so a portal taken
     * down since the last visit reads absent. Leaves the Nexus attunement alone: the slot
     * varbits answer that.
     */
    public HouseSight scanned(Set<HouseFeature> found, long nowEpochSec) {
        return new HouseSight(copyOf(found), stamp(nowEpochSec), attuned, nexusReadAtEpochSec);
    }

    /** This house with the Nexus read in full. Replaces the attunement. */
    public HouseSight nexusRead(Set<Integer> destinations, long nowEpochSec) {
        return new HouseSight(built, scannedAtEpochSec, keysOf(destinations), stamp(nowEpochSec));
    }

    /**
     * This house with these destinations proven attuned, by a slot read outside a full read.
     * Only adds. Returns this sight when nothing is new, so a caller can tell a change by
     * identity.
     */
    public HouseSight attuning(Collection<Integer> destinations) {
        if (destinations.isEmpty() || attuned.containsAll(destinations)) {
            return this;
        }
        Set<Integer> more = new TreeSet<>(attuned);
        more.addAll(destinations);
        return new HouseSight(built, scannedAtEpochSec, Collections.unmodifiableSet(more), nexusReadAtEpochSec);
    }

    /** Whether the house has been scanned at least once. */
    public boolean isScanned() {
        return scannedAtEpochSec != NEVER;
    }

    /** Whether the last scan found this. False for a house never scanned. */
    public boolean has(HouseFeature feature) {
        return built.contains(feature);
    }

    /** Whether the Nexus has been read in full at least once. */
    public boolean isNexusRead() {
        return nexusReadAtEpochSec != NEVER;
    }

    /**
     * Whether this destination key, from enum 1377, is known to be attuned. Never while the
     * last scan found no Portal Nexus: a slot read from before the Nexus came down counts for
     * nothing.
     */
    public boolean isAttuned(int destination) {
        return attuned.contains(destination) && !(isScanned() && !built.contains(HouseFeature.PORTAL_NEXUS));
    }

    public Set<HouseFeature> getBuilt() {
        return built;
    }

    /** When the furniture was last scanned, or 0 for never. */
    public long getScannedAtEpochSec() {
        return scannedAtEpochSec;
    }

    /** The destination keys known to be attuned, in key order. */
    public Set<Integer> getAttuned() {
        return attuned;
    }

    /** When the Nexus was last read in full, or 0 for never. */
    public long getNexusReadAtEpochSec() {
        return nexusReadAtEpochSec;
    }

    /** Stamps an observation. A clock at or before the epoch would read as "never". */
    private static long stamp(long nowEpochSec) {
        return Math.max(NEVER + 1, nowEpochSec);
    }

    private static Set<HouseFeature> copyOf(Set<HouseFeature> features) {
        return features == null || features.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(features));
    }

    private static Set<Integer> keysOf(Collection<Integer> destinations) {
        return destinations == null || destinations.isEmpty() ? Collections.emptySet()
                : Collections.unmodifiableSet(new TreeSet<>(destinations));
    }
}
