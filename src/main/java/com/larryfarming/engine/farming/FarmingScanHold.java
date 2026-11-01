package com.larryfarming.engine.farming;

import java.util.*;

/**
 * Holds one tick's patch scan until the next tick shows the player still among the patch
 * clusters it was read under.
 *
 * <p>Transmit varbits can lead the player's move: on the Ardougne cloak teleport from
 * Hosidius to the Monastery, the game sends the Monastery's values a tick before it moves
 * the player. A scan on that tick would record the Monastery bush's value on the Hosidius
 * north-east allotment and read the planted herb and flower patches as weeds (RuneLite's
 * Time Tracking makes the same mistake). The two-tick gate on region entry cannot catch
 * this, because the player has not moved yet. One tick is the lead on that teleport; other
 * teleports may differ.
 *
 * <p>Clusters, not map region ids, are compared: some varbits change meaning on a stair
 * climb or across a bounds line inside one map region (Catherby's fruit tree upstairs,
 * Fossil Island's ladders), and one cluster can span several map regions.
 */
public final class FarmingScanHold {

    /** One region's varbit values, read on one tick. Takes ownership of {@code values}. */
    public static final class Scan {
        private final FarmingWorldMap.RegionDef region;
        private final Map<Integer, Integer> values;
        private final boolean allowOffsetLearning;
        private final long observedAtEpochSec;

        public Scan(FarmingWorldMap.RegionDef region, Map<Integer, Integer> values, boolean allowOffsetLearning, long observedAtEpochSec) {
            this.region = region;
            this.values = values;
            this.allowOffsetLearning = allowOffsetLearning;
            this.observedAtEpochSec = observedAtEpochSec;
        }

        public FarmingWorldMap.RegionDef getRegion() {
            return region;
        }

        public Map<Integer, Integer> getValues() {
            return values;
        }

        public boolean isAllowOffsetLearning() {
            return allowOffsetLearning;
        }

        public long getObservedAtEpochSec() {
            return observedAtEpochSec;
        }
    }

    private Set<FarmingWorldMap.RegionDef> heldUnder = Set.of();
    private List<Scan> held = List.of();
    private boolean dropped;

    /** Holds this tick's scans, read while the player stood among {@code clusters}. */
    public void hold(Set<FarmingWorldMap.RegionDef> clusters, List<Scan> scans) {
        heldUnder = Set.copyOf(clusters);
        held = List.copyOf(scans);
    }

    /**
     * Releases the held scans if the player still stands among the clusters they were read
     * under, and drops them otherwise. Either way nothing stays held.
     *
     * @param clustersNow the clusters whose bounds hold the player this tick
     */
    public List<Scan> release(Set<FarmingWorldMap.RegionDef> clustersNow) {
        boolean keep = clustersNow.equals(heldUnder);
        List<Scan> scans = keep ? held : List.of();
        dropped = !keep && !held.isEmpty();
        clear();
        return scans;
    }

    /**
     * Whether the last {@link #release} threw a scan away. The next scan applied then follows
     * a gap, so it must not learn the farm-tick offset.
     */
    public boolean wasDropped() {
        return dropped;
    }

    public void clear() {
        heldUnder = Set.of();
        held = List.of();
    }
}
