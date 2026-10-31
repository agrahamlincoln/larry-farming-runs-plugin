/*
 * Copyright (c) 2018 Abex
 * Copyright (c) 2026, agrahamlincoln
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.larryfarming.engine.farming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Tracks every observed farming patch (herbs, trees, bushes, and the other timed
 * crops) and predicts when each finishes growing — or, for a crop that regrows
 * its produce, when it finishes restocking. Pure logic — no RuneLite dependency;
 * the plugin reads the transmit varbits + player region and folds them in.
 *
 * <p>Farming patches transmit only a health/stage varbit and only while the
 * player is nearby. A patch has no "ready" flag: a herb five minutes from
 * harvest is indistinguishable in that instant from one an hour away. Completion
 * is therefore pure wall-clock prediction from the last observation, exactly as
 * RuneLite's Time Tracking plugin does it. The growth grid is aligned to
 * wall-clock multiples of the crop's tick rate plus a per-player offset that we
 * learn by witnessing an exact growth tick.
 *
 * <p>Unlike RuneLite, this ignores leagues-world tick scaling and autoweed: the plugin
 * targets standard worlds without autoweed.
 */
public class FarmingTracker {

    /** A stored value is refreshed rather than kept once it is older than this. */
    private static final long STORE_STALE_SECONDS = 300;

    /** Guards against a stored timestamp from the future (clock skew). */
    private static final long STORE_FUTURE_GRACE_SECONDS = 30;

    /**
     * Above this precision the learned offset is trusted for any tick rate — a
     * 40-minute (or longer) observed tick nails the alignment for shorter crops
     * too. Mirrors RuneLite's magic 40 in {@code getTickTime}.
     */
    private static final int OFFSET_UNIVERSAL_PRECISION_MINS = 40;

    /** Sentinel for "no farming-tick offset has been learned yet". */
    private static final int OFFSET_UNKNOWN = -1;

    /**
     * Resolution of an offset this tracker learns itself, in seconds. Patch
     * varbits are sampled once per 600ms game tick, so a witnessed growth tick
     * pins the farming-tick beat to within one second.
     */
    private static final int OFFSET_RESOLUTION_SECONDS = 1;

    /** Persisted per-patch record: raw varbit byte + when we observed it. */
    public static final class PersistedEntry {
        private final String configKey;
        private final int varbitValue;
        private final long observedAtEpochSec;

        public PersistedEntry(String configKey, int varbitValue, long observedAtEpochSec) {
            this.configKey = configKey;
            this.varbitValue = varbitValue;
            this.observedAtEpochSec = observedAtEpochSec;
        }

        public String getConfigKey() {
            return configKey;
        }

        public int getVarbitValue() {
            return varbitValue;
        }

        public long getObservedAtEpochSec() {
            return observedAtEpochSec;
        }
    }

    /**
     * Live per-patch state. Immutable: the decoded form is computed once at store
     * time so the per-frame render-loop queries never re-decode or allocate. A
     * new instance replaces the old one on change; the map publishes the swap.
     */
    private static final class Observation {
        final int varbitValue;
        final long observedAtEpochSec;
        final DecodedPatch decoded;

        /**
         * When this value was <em>first</em> seen. An unchanged value re-stamps
         * {@code observedAtEpochSec} every {@link #STORE_STALE_SECONDS}; this field
         * survives the re-stamp because it bounds how late the crop can finish.
         */
        final long firstSeenAtEpochSec;

        /**
         * When this crop was seen going in, carried through its growth stages, or 0 when
         * no planting was seen. Live only: not persisted.
         */
        final long plantedAtEpochSec;

        Observation(int varbitValue, long observedAtEpochSec, DecodedPatch decoded,
                long firstSeenAtEpochSec) {
            this(varbitValue, observedAtEpochSec, decoded, firstSeenAtEpochSec, 0L);
        }

        Observation(int varbitValue, long observedAtEpochSec, DecodedPatch decoded,
                long firstSeenAtEpochSec, long plantedAtEpochSec) {
            this.varbitValue = varbitValue;
            this.observedAtEpochSec = observedAtEpochSec;
            this.decoded = decoded;
            this.firstSeenAtEpochSec = firstSeenAtEpochSec;
            this.plantedAtEpochSec = plantedAtEpochSec;
        }
    }

    /**
     * When the crop in {@code now} went in: this second, for a planting seen happen (weeds
     * one scan, a crop at its first stage the next), the old stamp while the same crop grows
     * on, and 0 otherwise.
     */
    private static long plantedAt(Observation old, DecodedPatch now, boolean held,
            long nowEpochSec) {
        if (old == null || now.getCrop() == null || now.getCrop() == Crop.WEEDS) {
            return 0L;
        }
        if (old.decoded.getCrop() == now.getCrop()) {
            return old.plantedAtEpochSec;
        }
        boolean seenGoingIn = !held && old.decoded.getCrop() == Crop.WEEDS
                && now.getState() == CropState.GROWING && now.getStage() == 0;
        return seenGoingIn ? nowEpochSec : 0L;
    }

    // PatchDefs are singletons, so identity-keyed. ConcurrentHashMap: the client
    // thread writes via observeRegion while the EDT (panel) and render thread read.
    private final Map<FarmingWorldMap.PatchDef, Observation> data = new ConcurrentHashMap<>();

    /** Patches the player has switched off in the settings, or null while none are. */
    private volatile Predicate<FarmingWorldMap.PatchDef> hidden;

    /** Tell the tracker which patches the player has switched off. Null clears it. */
    public void setHidden(Predicate<FarmingWorldMap.PatchDef> hidden) {
        this.hidden = hidden;
    }

    /** Whether the player switched this patch off, leaving it out of every aggregate. Any thread. */
    public boolean isHidden(FarmingWorldMap.PatchDef patch) {
        Predicate<FarmingWorldMap.PatchDef> off = hidden;
        return off != null && off.test(patch);
    }

    // When each patch was last read in range, refreshed every game tick the player
    // spends nearby; `data` re-stamps only on change or staleness. See anchorFor for
    // how it is used. Not persisted: the stored observation already carries a previous
    // session's sighting.
    private final Map<FarmingWorldMap.PatchDef, Long> lastSeen = new ConcurrentHashMap<>();

    // Patches holding a crop whose scan read 0, and when: the next scan confirms or replaces
    // it. A hold older than a couple of seconds is stale, and a fresh 0 is held again.
    private final Map<FarmingWorldMap.PatchDef, Long> overgrowthHeldAt =
            new ConcurrentHashMap<>();

    /** How long a held 0 waits for the scan that confirms it. Scans come every tick. */
    private static final long HOLD_CONFIRM_SECONDS = 2;

    // Counts the scans that stored a change, so a watcher can tell a planting has shown.
    private volatile long changes;

    // volatile: written on the client thread, read on the EDT / render thread.
    private volatile int tickOffsetSeconds = OFFSET_UNKNOWN;
    private volatile int tickOffsetPrecisionMins = OFFSET_UNKNOWN;
    private volatile int tickOffsetResolutionSeconds = OFFSET_RESOLUTION_SECONDS;
    private volatile boolean offsetDirty;

    // --- Observation ---

    /**
     * Fold in the live varbit values for one region's patches. Patches whose
     * varbit is absent from the map, or whose value doesn't decode to a known
     * state, are skipped (never stored). An unchanged value only re-stamps its
     * timestamp once the stored one goes stale, mirroring RuneLite's write rule.
     *
     * <p>Runs on the client thread.
     *
     * @param allowOffsetLearning true only when this scan is safe for tick-offset
     *     learning (not the first scan after a region load, no modal just closed) —
     *     a witnessed growth tick then reveals the player's farming-tick offset.
     * @return true if anything stored changed; the caller should persist + refresh UI.
     */
    public boolean observeRegion(FarmingWorldMap.RegionDef region, Map<Integer, Integer> varbitValues,
            boolean allowOffsetLearning, long nowEpochSec) {
        boolean changed = false;

        for (FarmingWorldMap.PatchDef patch : region.getPatches()) {
            Integer raw = varbitValues.get(patch.getVarbitId());
            if (raw == null) {
                continue;
            }

            DecodedPatch decoded = patch.getType().decode(raw);
            if (decoded == null) {
                // Unknown value: keep any prior observation.
                continue;
            }

            Observation old = data.get(patch);

            // A crop read as fully overgrown weeds is held for a second scan. Between two
            // scans a crop never turns straight to 0: clearing leaves 3, and weeds grow back a
            // stage at a time. Patches entered over a region line can read 0 for one tick
            // before their real values arrive, which would show a grown fruit tree as an
            // empty patch. A real 0 reads the same on the next scan and is taken then. A
            // compost bin's 0 is an empty bin, not weeds, and is not held.
            Long heldAt = overgrowthHeldAt.remove(patch);
            boolean held = heldAt != null && nowEpochSec - heldAt <= HOLD_CONFIRM_SECONDS;
            if (raw == 0 && decoded.getCrop() == Crop.WEEDS && old != null
                    && old.decoded.getCrop() != null && old.decoded.getCrop() != Crop.WEEDS
                    && !held) {
                overgrowthHeldAt.put(patch, nowEpochSec);
                continue;
            }

            lastSeen.put(patch, nowEpochSec);

            if (old != null && old.varbitValue == raw) {
                // Same value: keep the old entry while it is fresh; otherwise re-stamp.
                long storedTime = old.observedAtEpochSec;
                if (storedTime + STORE_STALE_SECONDS > nowEpochSec
                        && nowEpochSec + STORE_FUTURE_GRACE_SECONDS > storedTime) {
                    continue;
                }
                // Re-stamp, carrying the first sighting and the planting over.
                data.put(patch, new Observation(raw, nowEpochSec, decoded,
                        old.firstSeenAtEpochSec, old.plantedAtEpochSec));
                changed = true;
                continue;
            }

            // Changed value (or first sighting).
            // After a held scan, the stored value is from the last visit, so the change
            // from it witnesses no growth tick.
            if (old != null && allowOffsetLearning && !held) {
                learnOffset(old.decoded, decoded, patch.getType(), nowEpochSec);
            }
            data.put(patch, new Observation(raw, nowEpochSec, decoded, nowEpochSec,
                    plantedAt(old, decoded, held, nowEpochSec)));
            changed = true;
            changes++;
        }

        return changed;
    }

    /**
     * Learn the player's farming-tick offset from a witnessed exact growth tick.
     * A longer tick rate pins the alignment more precisely, so a newly observed
     * offset only overwrites the stored one when its tick rate is at least as long
     * — or when the stored one is minute-granular and this one is not, since
     * refining the resolution closes the finish window for every crop at once.
     *
     * <p>Kept in seconds rather than RuneLite's whole minutes: the transmit varbit
     * is sampled every game tick, so the moment of the tick is known to 600ms, and
     * rounding that away would leave every prediction up to a minute early.
     */
    private void learnOffset(DecodedPatch prev, DecodedPatch cur, PatchType type, long nowEpochSec) {
        if (!isObservedGrowthTick(prev, cur, type)) {
            return;
        }
        int prevTickRate = prev.tickRateMinutes();
        long tickSeconds = (long) prevTickRate * 60L;
        // All offsets are negative but stored as positive: distance back to the tick grid.
        int offsetSeconds = (int) Math.abs((nowEpochSec % tickSeconds) - tickSeconds);

        if (tickOffsetPrecisionMins == OFFSET_UNKNOWN || prevTickRate >= tickOffsetPrecisionMins) {
            tickOffsetPrecisionMins = prevTickRate;
            tickOffsetSeconds = offsetSeconds;
            tickOffsetResolutionSeconds = OFFSET_RESOLUTION_SECONDS;
            offsetDirty = true;
            return;
        }

        // A shorter tick rate must not replace the stored offset, since it covers fewer
        // crops. It can still refine a minute-granular offset: every farming beat shares
        // one phase, so this tick's sub-minute part is the part the stored offset lacks.
        if (tickOffsetResolutionSeconds > OFFSET_RESOLUTION_SECONDS
                && tickOffsetSeconds != OFFSET_UNKNOWN) {
            tickOffsetSeconds -= Math.floorMod(tickOffsetSeconds - offsetSeconds, 60);
            tickOffsetResolutionSeconds = OFFSET_RESOLUTION_SECONDS;
            offsetDirty = true;
        }
    }

    /**
     * True when the transition from {@code prev} to {@code cur} is an exact
     * farming-tick boundary we can read the offset from. Mirrors RuneLite's
     * {@code isObservedGrowthTick}, including its DISEASED->DEAD arm, which is
     * unreachable because DISEASED has tick rate 0.
     */
    private boolean isObservedGrowthTick(DecodedPatch prev, DecodedPatch cur, PatchType type) {
        int prevTickRate = prev.tickRateMinutes();
        if (prev.getCrop() == Crop.WEEDS || cur.getCrop() == Crop.WEEDS
                || cur.getCrop() != prev.getCrop()
                || prevTickRate <= 0) {
            return false;
        }

        CropState prevState = prev.getState();
        CropState curState = cur.getState();

        if (prevState == CropState.GROWING) {
            if ((curState == CropState.GROWING && cur.getStage() - prev.getStage() == 1)
                    || curState == CropState.DISEASED) {
                return true;
            }
            if (curState == CropState.HARVESTABLE && !type.isHealthCheckRequired()) {
                return true;
            }
        }

        return prevState == CropState.DISEASED && curState == CropState.DEAD;
    }

    // --- Prediction ---

    /**
     * Predict one patch's current state, or null if it was never observed or its
     * stored value no longer decodes. Safe to call from any thread.
     */
    public PatchPrediction predict(FarmingWorldMap.PatchDef patch, long nowEpochSec) {
        Observation obs = data.get(patch);
        if (obs == null) {
            return null;
        }
        DecodedPatch decoded = obs.decoded;
        if (decoded == null) {
            return null;
        }

        Crop crop = decoded.getCrop();
        CropState state = decoded.getState();
        int stage = decoded.getStage();
        int stages = decoded.getStages();
        int tickRate = decoded.tickRateMinutes();
        long observedAt = obs.observedAtEpochSec;

        long doneEstimate = finishWindowEnd(patch, obs);
        long earliestDone = finishWindowStart(patch, obs);

        if (tickRate > 0) {
            long anchor = anchorFor(patch, obs);
            long tickNow = getTickTime(tickRate, 0, nowEpochSec);
            long tickAnchor = getTickTime(tickRate, 0, anchor);
            stage += (int) ((tickNow - tickAnchor) / ((long) tickRate * 60));
            if (stage >= stages) {
                stage = stages - 1;
            }
            // The stage must not reach full before the window's far edge. Advanced off the
            // tick grid, it can saturate up to a tick early, and PatchPrediction.readinessAt
            // reads a restocking crop at full stage as READY without consulting the window.
            if (stages > 1 && stage >= stages - 1 && nowEpochSec < doneEstimate) {
                stage = stages - 2;
            }
        }

        return new PatchPrediction(crop, state, stage, stages, earliestDone, doneEstimate,
                observedAt, decoded.isFellable()).withPlantedAt(obs.plantedAtEpochSec);
    }

    /**
     * Far edge of one observation's finish window: the latest wall-clock second the
     * patch's timer can still be running, or 0 when it carries no timer.
     *
     * <p>Farm ticks fall on a global grid. Seeing stage N first at T0 puts the next tick in
     * (T0, T0+rate], so T0 alone fixes this edge, and watching the patch longer cannot push
     * it back.
     *
     * <p>One exception: a sighting taken after the estimate expired, of a value that still
     * has a tick ahead of it, proves the estimate wrong. The window is then re-measured from
     * that sighting (see {@link #sightingPastTheEstimate}). This depends on the stored
     * sighting, not the current second, so it holds while an open interface pauses the
     * per-tick scan.
     *
     * <p>While the player stands at such a patch the anchor advances a second per second,
     * so the panel's countdown holds steady.
     *
     * <p>Returns a bare second so that {@link #readiness} can share it without allocating
     * per frame. The overlay and the panel must agree, so neither keeps its own copy of
     * this logic.
     */
    private long finishWindowEnd(FarmingWorldMap.PatchDef patch, Observation obs) {
        long latest = latestDone(obs);
        if (latest <= 0) {
            return 0;
        }
        long outlived = sightingPastTheEstimate(patch, obs);
        return outlived > 0 ? doneFrom(obs, outlived) : latest;
    }

    /**
     * The sighting that proved the estimate wrong, or 0 where none has.
     *
     * <p>A sighting taken at or after the arithmetic finish, of a value that still has a
     * tick ahead of it. Both window edges re-measure from it, so they stay consistent.
     */
    private long sightingPastTheEstimate(FarmingWorldMap.PatchDef patch, Observation obs) {
        if (!stagesRemain(obs.decoded)) {
            return 0;
        }
        long anchor = anchorFor(patch, obs);
        return latestDone(obs) <= anchor ? anchor : 0;
    }

    /**
     * The far edge measured from a given origin: at most one tick per remaining stage, on
     * the learned grid wherever there is one.
     *
     * <p>Without the grid the edge could land up to a whole tick past the real beat (over
     * ten hours for a hardwood).
     */
    private long doneFrom(Observation obs, long origin) {
        DecodedPatch decoded = obs.decoded;
        int tickRate = decoded.tickRateMinutes();
        if (tickRate <= 0) {
            return 0;
        }
        int remaining = decoded.getStages() - 1 - decoded.getStage();
        long byOrigin = origin + (long) remaining * tickRate * 60L;
        if (!offsetCovers(tickRate)) {
            return byOrigin;
        }
        return Math.min(getTickTime(tickRate, remaining, origin), byOrigin);
    }

    /** The near edge measured from a given origin, the width {@link #earliestDone} uses. */
    private long earliestFrom(Observation obs, long origin) {
        int tickRate = obs.decoded.tickRateMinutes();
        if (tickRate <= 0) {
            return 0;
        }
        long latest = doneFrom(obs, origin);
        if (offsetCovers(tickRate)) {
            return latest - (tickOffsetResolutionSeconds - 1L);
        }
        return latest - (long) tickRate * 60L + 1L;
    }

    /**
     * Near edge of one observation's finish window: the earliest wall-clock second the
     * patch could already have finished, or 0 when it carries no timer.
     *
     * <p>Raised to the last sighting, because still seeing stage N at T1 rules out the
     * part of the window already gone by. Clamped to the far edge.
     *
     * <p>Re-measured from the same origin as {@link #finishWindowEnd}. Otherwise, where a
     * sighting has outlived the estimate, this edge would sit in the past and a crop seen
     * growing would read as LIKELY_READY.
     */
    private long finishWindowStart(FarmingWorldMap.PatchDef patch, Observation obs) {
        long latest = latestDone(obs);
        if (latest <= 0) {
            return 0;
        }
        long outlived = sightingPastTheEstimate(patch, obs);
        if (outlived > 0) {
            return Math.min(earliestFrom(obs, outlived), doneFrom(obs, outlived));
        }
        return Math.min(Math.max(earliestDone(obs), anchorFor(patch, obs)), latest);
    }

    /**
     * True while the patch still has a tick ahead of it — a crop growing toward harvest,
     * or a bush, fruit tree or cactus restocking its produce.
     *
     * <p>Restocking crops decode HARVESTABLE at partial stock and still tick.
     */
    private static boolean stagesRemain(DecodedPatch decoded) {
        CropState state = decoded.getState();
        return (state == CropState.GROWING || state == CropState.HARVESTABLE)
                && decoded.getStage() < decoded.getStages() - 1;
    }

    /**
     * The moment a patch's stored value was last known to still be true: the later
     * of when it was stored and when the patch was last read in range. The stored
     * value only re-stamps on change or every {@link #STORE_STALE_SECONDS}, so
     * without this a patch the player is standing next to could be predicted past
     * a growth tick it visibly has not taken.
     *
     * <p>Anchors <em>stage</em> prediction and the near edge of the finish window. The far
     * edge is fixed by the first sighting instead, except in the case
     * {@link #sightingPastTheEstimate} handles.
     */
    private long anchorFor(FarmingWorldMap.PatchDef patch, Observation obs) {
        Long seen = lastSeen.get(patch);
        return seen == null ? obs.observedAtEpochSec : Math.max(seen, obs.observedAtEpochSec);
    }

    /**
     * Earliest wall-clock second the patch's pending timer could have elapsed on the
     * arithmetic alone, or 0 when it carries no timer. Callers want
     * {@link #finishWindowStart}, which also folds in what a later sighting proves.
     *
     * <p>With the farming beat pinned down this is the estimate itself. Without it,
     * all that is known is that the next tick falls somewhere in the tick-length
     * window before {@link #latestDone}, which puts the finish anywhere in there.
     */
    private long earliestDone(Observation obs) {
        int tickRate = obs.decoded.tickRateMinutes();
        if (tickRate <= 0) {
            return 0;
        }
        long latest = latestDone(obs);
        if (offsetCovers(tickRate)) {
            // A minute-granular offset sits up to a minute ahead of the true beat,
            // which biases the estimate early by exactly that much and no more.
            return latest - (tickOffsetResolutionSeconds - 1L);
        }
        return latest - (long) tickRate * 60L + 1L;
    }

    /**
     * Latest wall-clock second the patch's pending timer can still be running, or 0
     * when it carries no timer. Bounded by the first sighting regardless of where the
     * beat falls: the crop cannot need more than one full tick per remaining stage.
     *
     * <p>The origin is always the first sighting, so it is not a parameter. Measuring from
     * any other origin goes through {@link #doneFrom}, and only
     * {@link #sightingPastTheEstimate} supplies one.
     */
    private long latestDone(Observation obs) {
        DecodedPatch decoded = obs.decoded;
        int tickRate = decoded.tickRateMinutes();
        if (tickRate <= 0) {
            return 0;
        }
        int remaining = decoded.getStages() - 1 - decoded.getStage();
        long firstSeen = obs.firstSeenAtEpochSec;
        long byFirstSeen = firstSeen + (long) remaining * tickRate * 60L;
        if (!offsetCovers(tickRate)) {
            return byFirstSeen;
        }
        return Math.min(getTickTime(tickRate, remaining, firstSeen), byFirstSeen);
    }

    /** True when the learned offset is precise enough to align this tick rate's grid. */
    private boolean offsetCovers(int tickRateMinutes) {
        return tickOffsetSeconds != OFFSET_UNKNOWN && tickOffsetPrecisionMins != OFFSET_UNKNOWN
                && (tickOffsetPrecisionMins >= tickRateMinutes
                    || tickOffsetPrecisionMins >= OFFSET_UNIVERSAL_PRECISION_MINS);
    }

    /**
     * Wall-clock second of the {@code ticks}-th farming tick at or after
     * {@code requestedTimeEpochSec}, on the grid aligned by the learned offset.
     * Mirrors RuneLite's {@code getTickTime}.
     */
    long getTickTime(int tickRateMinutes, int ticks, long requestedTimeEpochSec) {
        long tickSeconds = (long) tickRateMinutes * 60;

        // Only trust the offset once it was learned at a precision that covers this
        // tick rate (or the universal threshold). All offsets are stored positive.
        long offsetSeconds = offsetCovers(tickRateMinutes) ? tickOffsetSeconds % tickSeconds : 0L;

        // Shift into offset space, floor to the tick boundary, step to the goal
        // tick, then shift back to real time.
        long shifted = requestedTimeEpochSec + offsetSeconds;
        long timeOfCurrentTick = shifted - (shifted % tickSeconds);
        long timeOfGoalTick = timeOfCurrentTick + ticks * tickSeconds;
        return timeOfGoalTick - offsetSeconds;
    }

    // --- Render-loop queries (allocate no PatchPrediction) ---

    /**
     * Count of observed patches of {@code type} provably ready to harvest. A patch
     * still inside its finish window is not counted — see
     * {@link #countLikelyReadyOfType} for those.
     */
    public int countReadyOfType(PatchType type, long nowEpochSec) {
        return countReadiness(type, nowEpochSec, PatchPrediction.Readiness.READY);
    }

    /**
     * Count of observed patches of {@code type} that may or may not have finished —
     * past their earliest possible finish but not yet past their latest. Always zero
     * once a growth tick has been witnessed at a precision covering {@code type}.
     */
    public int countLikelyReadyOfType(PatchType type, long nowEpochSec) {
        return countReadiness(type, nowEpochSec, PatchPrediction.Readiness.LIKELY_READY);
    }

    private int countReadiness(PatchType type, long nowEpochSec,
            PatchPrediction.Readiness wanted) {
        int count = 0;
        for (Map.Entry<FarmingWorldMap.PatchDef, Observation> e : data.entrySet()) {
            if (e.getKey().getType() == type && !isHidden(e.getKey())
                    && readiness(e.getKey(), e.getValue(), nowEpochSec) == wanted) {
                count++;
            }
        }
        return count;
    }

    /** Count of observed patches of {@code type} with something in progress (any live state). */
    public int countPlantedOfType(PatchType type) {
        int count = 0;
        for (Map.Entry<FarmingWorldMap.PatchDef, Observation> e : data.entrySet()) {
            if (e.getKey().getType() == type && !isHidden(e.getKey())
                    && !e.getValue().decoded.isIdle()) {
                count++;
            }
        }
        return count;
    }

    /** Count of patches of {@code type} last observed DISEASED. */
    public int countDiseasedOfType(PatchType type) {
        return countState(type, CropState.DISEASED);
    }

    /** Count of patches of {@code type} last observed DEAD. */
    public int countDeadOfType(PatchType type) {
        return countState(type, CropState.DEAD);
    }

    public boolean hasAnyReady(long nowEpochSec) {
        for (Map.Entry<FarmingWorldMap.PatchDef, Observation> e : data.entrySet()) {
            if (!isHidden(e.getKey())
                    && readiness(e.getKey(), e.getValue(), nowEpochSec)
                        == PatchPrediction.Readiness.READY) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyDiseasedOrDead() {
        for (Map.Entry<FarmingWorldMap.PatchDef, Observation> e : data.entrySet()) {
            Observation obs = e.getValue();
            if (obs.decoded.isIdle() || isHidden(e.getKey())) {
                continue;
            }
            CropState state = obs.decoded.getState();
            if (state == CropState.DISEASED || state == CropState.DEAD) {
                return true;
            }
        }
        return false;
    }

    private int countState(PatchType type, CropState wanted) {
        int count = 0;
        for (Map.Entry<FarmingWorldMap.PatchDef, Observation> e : data.entrySet()) {
            Observation obs = e.getValue();
            if (e.getKey().getType() == type
                    && !isHidden(e.getKey())
                    && !obs.decoded.isIdle()
                    && obs.decoded.getState() == wanted) {
                count++;
            }
        }
        return count;
    }

    /**
     * The allocation-free equivalent of {@link PatchPrediction#readinessAt}: weeds
     * never count; harvestable is ready when terminal (no regrow) or fully
     * restocked; and a growing crop is placed against its finish window.
     *
     * <p>The two must agree: this backs the in-game overlay while {@code readinessAt}
     * backs the farming tab. Both take the window from {@link #finishWindowEnd} and
     * {@link #finishWindowStart}. This runs per observed patch per rendered frame, so it
     * must not build a {@link PatchPrediction}.
     */
    private PatchPrediction.Readiness readiness(FarmingWorldMap.PatchDef patch, Observation obs,
            long nowEpochSec) {
        DecodedPatch decoded = obs.decoded;
        if (decoded.isIdle()) {
            return PatchPrediction.Readiness.GROWING;
        }
        CropState state = decoded.getState();
        int tickRate = decoded.tickRateMinutes();
        if (state == CropState.HARVESTABLE) {
            // Observed harvestable is ground truth, not a prediction: terminal
            // harvests carry no regrow timer, and a restocking crop that has run
            // out of stages is already full.
            if (tickRate <= 0 || decoded.getStages() - 1 - decoded.getStage() <= 0) {
                return PatchPrediction.Readiness.READY;
            }
        } else if (state != CropState.GROWING || tickRate <= 0) {
            return PatchPrediction.Readiness.GROWING;
        }
        long latest = finishWindowEnd(patch, obs);
        if (latest <= 0) {
            return PatchPrediction.Readiness.GROWING;
        }
        if (nowEpochSec >= latest) {
            return PatchPrediction.Readiness.READY;
        }
        return nowEpochSec >= finishWindowStart(patch, obs)
                ? PatchPrediction.Readiness.LIKELY_READY
                : PatchPrediction.Readiness.GROWING;
    }


    // --- Persistence ---

    /**
     * Restore persisted patches and the learned tick offset on login, before the
     * first {@link #observeRegion}. Unknown config keys and undecodable values are
     * dropped.
     *
     * @param offsetSeconds -1 together with {@code offsetPrecisionMins} -1 means no
     *     offset has been learned yet.
     * @param offsetResolutionSeconds how coarsely {@code offsetSeconds} was recorded;
     *     anything above one second leaves a finish window open that wide until a
     *     growth tick is witnessed again.
     */
    public void loadPersisted(Collection<PersistedEntry> entries,
            int offsetSeconds, int offsetPrecisionMins, int offsetResolutionSeconds) {
        clear();
        tickOffsetSeconds = offsetSeconds;
        tickOffsetPrecisionMins = offsetPrecisionMins;
        tickOffsetResolutionSeconds = Math.max(OFFSET_RESOLUTION_SECONDS, offsetResolutionSeconds);
        if (entries != null) {
            for (PersistedEntry e : entries) {
                FarmingWorldMap.PatchDef patch = FarmingWorldMap.byConfigKey(e.getConfigKey());
                if (patch == null) {
                    continue;
                }
                DecodedPatch decoded = patch.getType().decode(e.getVarbitValue());
                if (decoded == null) {
                    continue;
                }
                // The first sighting is not persisted, so the stored stamp stands in for
                // it. That errs late, never early.
                data.put(patch, new Observation(e.getVarbitValue(), e.getObservedAtEpochSec(),
                        decoded, e.getObservedAtEpochSec()));
            }
        }
    }

    /** Snapshot for saving to config. */
    public Collection<PersistedEntry> exportForPersist() {
        List<PersistedEntry> out = new ArrayList<>();
        for (Map.Entry<FarmingWorldMap.PatchDef, Observation> e : data.entrySet()) {
            out.add(new PersistedEntry(e.getKey().configKey(), e.getValue().varbitValue,
                    e.getValue().observedAtEpochSec));
        }
        return out;
    }

    /**
     * One patch as the tracker holds it this session, beyond what is saved: when its value
     * was first seen, and when the patch was last read in range. A bug report holds it, so
     * the patch can be predicted from the report as the session predicted it.
     */
    public static final class LiveEntry {
        private final String configKey;
        private final int varbitValue;
        private final long observedAtEpochSec;
        private final long firstSeenAtEpochSec;
        /** Zero when the patch has not been read in range this session. */
        private final long lastSeenEpochSec;
        /** Zero when no planting was seen; see {@link PatchPrediction#getPlantedAtEpochSec}. */
        private final long plantedAtEpochSec;

        public LiveEntry(String configKey, int varbitValue, long observedAtEpochSec,
                long firstSeenAtEpochSec, long lastSeenEpochSec) {
            this(configKey, varbitValue, observedAtEpochSec, firstSeenAtEpochSec,
                    lastSeenEpochSec, 0L);
        }

        public LiveEntry(String configKey, int varbitValue, long observedAtEpochSec,
                long firstSeenAtEpochSec, long lastSeenEpochSec, long plantedAtEpochSec) {
            this.configKey = configKey;
            this.varbitValue = varbitValue;
            this.observedAtEpochSec = observedAtEpochSec;
            this.firstSeenAtEpochSec = firstSeenAtEpochSec;
            this.lastSeenEpochSec = lastSeenEpochSec;
            this.plantedAtEpochSec = plantedAtEpochSec;
        }

        public long getPlantedAtEpochSec() {
            return plantedAtEpochSec;
        }

        public String getConfigKey() {
            return configKey;
        }

        public int getVarbitValue() {
            return varbitValue;
        }

        public long getObservedAtEpochSec() {
            return observedAtEpochSec;
        }

        public long getFirstSeenAtEpochSec() {
            return firstSeenAtEpochSec;
        }

        public long getLastSeenEpochSec() {
            return lastSeenEpochSec;
        }
    }

    /** The raw value held for a patch, or null when it has none. */
    public Integer rawValue(FarmingWorldMap.PatchDef patch) {
        Observation held = data.get(patch);
        return held != null ? held.varbitValue : null;
    }

    /** Every patch held, with its in-session stamps. */
    public List<LiveEntry> exportLive() {
        List<LiveEntry> out = new ArrayList<>();
        for (Map.Entry<FarmingWorldMap.PatchDef, Observation> e : data.entrySet()) {
            Long seen = lastSeen.get(e.getKey());
            out.add(new LiveEntry(e.getKey().configKey(), e.getValue().varbitValue,
                    e.getValue().observedAtEpochSec, e.getValue().firstSeenAtEpochSec,
                    seen != null ? seen : 0L, e.getValue().plantedAtEpochSec));
        }
        return out;
    }

    /**
     * Replaces every patch with the entries given, stamps included, keeping the learned
     * offset. The inverse of {@link #exportLive}. Unknown keys and undecodable values are
     * dropped, as on a load.
     */
    public void restoreLive(Collection<LiveEntry> entries) {
        data.clear();
        lastSeen.clear();
        overgrowthHeldAt.clear();
        for (LiveEntry e : entries) {
            FarmingWorldMap.PatchDef patch = FarmingWorldMap.byConfigKey(e.getConfigKey());
            if (patch == null) {
                continue;
            }
            DecodedPatch decoded = patch.getType().decode(e.getVarbitValue());
            if (decoded == null) {
                continue;
            }
            data.put(patch, new Observation(e.getVarbitValue(), e.getObservedAtEpochSec(),
                    decoded, e.getFirstSeenAtEpochSec(), e.getPlantedAtEpochSec()));
            if (e.getLastSeenEpochSec() > 0) {
                lastSeen.put(patch, e.getLastSeenEpochSec());
            }
        }
    }

    /** How many patch values have changed so far, re-stamps of an unchanged value aside. */
    public long getChanges() {
        return changes;
    }

    public int getTickOffsetSeconds() {
        return tickOffsetSeconds;
    }

    public int getTickOffsetPrecisionMins() {
        return tickOffsetPrecisionMins;
    }

    /** How coarsely the stored offset was recorded; one second once self-learned. */
    public int getTickOffsetResolutionSeconds() {
        return tickOffsetResolutionSeconds;
    }

    /**
     * True exactly once after a new offset was learned, clearing the flag so the
     * caller knows to persist the offset then stops re-persisting it.
     */
    public boolean consumeOffsetDirty() {
        if (offsetDirty) {
            offsetDirty = false;
            return true;
        }
        return false;
    }

    /** Forget all patch observations and the learned offset. */
    public void clear() {
        data.clear();
        lastSeen.clear();
        overgrowthHeldAt.clear();
        tickOffsetSeconds = OFFSET_UNKNOWN;
        tickOffsetPrecisionMins = OFFSET_UNKNOWN;
        tickOffsetResolutionSeconds = OFFSET_RESOLUTION_SECONDS;
        offsetDirty = false;
    }
}
