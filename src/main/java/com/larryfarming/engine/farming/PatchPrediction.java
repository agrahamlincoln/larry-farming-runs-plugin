package com.larryfarming.engine.farming;

/**
 * An immutable snapshot of one farming patch's predicted state at a moment in
 * time: what is planted, its health, how far it has grown, and (for a living,
 * timed crop) when it finishes growing.
 *
 * <p>Produced by {@link FarmingTracker#predict}. The {@code stage} here is the
 * <em>predicted</em> growth stage — the observed stage advanced by however many
 * farming ticks have elapsed since the observation, clamped to the final stage.
 * Disease and death are never predicted forward (the game only transmits them
 * from a direct observation), so a patch last seen GROWING is always predicted
 * straight through to harvest.
 *
 * <p>The finish time is a <em>window</em>, not an instant: patch state is only
 * readable while the player stands nearby, so between sightings the tracker is
 * dead-reckoning against the game's farming-tick beat. Where that beat falls is
 * only known once a growth tick has been witnessed in person, so until then the
 * window is a full tick wide. {@link #getEarliestDoneEpochSec()} and
 * {@link #getDoneEstimateEpochSec()} bound it, and {@link #readinessAt(long)}
 * turns it into the three positions a caller can actually be in.
 */
public final class PatchPrediction {

    /**
     * How confident the tracker is that a patch has finished.
     *
     * <p>{@code GROWING} means the finish time cannot have arrived yet; {@code READY}
     * means it cannot still be pending; {@code LIKELY_READY} is the window between,
     * which is non-empty only while the farming-tick beat is unpinned for this crop's
     * tick rate.
     */
    public enum Readiness {
        /** Still growing (or restocking): the finish time is provably in the future. */
        GROWING,
        /** Past the earliest possible finish but not the latest — it may or may not be done. */
        LIKELY_READY,
        /** Provably finished. */
        READY
    }

    private final Crop crop;
    private final CropState state;
    private final int stage;
    private final int stages;
    private final long earliestDoneEpochSec;
    private final long doneEstimateEpochSec;
    private final long observedAtEpochSec;
    private final boolean fellable;
    private final long plantedAtEpochSec;

    /** A patch stated outright with no timing. {@link FarmingTracker} never builds one. */
    public static PatchPrediction of(Crop crop, CropState state) {
        return of(crop, state, 0, 0, 0L, 0L);
    }

    /**
     * A patch stated in full, with timing. The finish time is exact, so the finish window
     * has zero width.
     *
     * <p>Public so the farming advisor package can use it. The constructor stays
     * package-private so real predictions come from {@link FarmingTracker}.
     */
    public static PatchPrediction of(Crop crop, CropState state, int stage, int stages,
            long doneEstimateEpochSec, long observedAtEpochSec) {
        return new PatchPrediction(crop, state, stage, stages, doneEstimateEpochSec, doneEstimateEpochSec, observedAtEpochSec, false, 0L);
    }

    PatchPrediction(Crop crop, CropState state, int stage, int stages,
            long earliestDoneEpochSec, long doneEstimateEpochSec, long observedAtEpochSec, boolean fellable, long plantedAtEpochSec) {
        this.plantedAtEpochSec = plantedAtEpochSec;
        this.crop = crop;
        this.state = state;
        this.stage = stage;
        this.stages = stages;
        this.earliestDoneEpochSec = earliestDoneEpochSec;
        this.doneEstimateEpochSec = doneEstimateEpochSec;
        this.observedAtEpochSec = observedAtEpochSec;
        this.fellable = fellable;
    }

    /**
     * When the tracker saw this crop go in, or 0 when it did not: the crop was already
     * growing at its first sighting, or the planting was in an earlier session. Exact where
     * set, unlike a planting worked back from the finish estimate.
     */
    public long getPlantedAtEpochSec() {
        return plantedAtEpochSec;
    }

    public Crop getCrop() {
        return crop;
    }

    /**
     * Whether a tree is still rooted here, so the patch cannot take a sapling until it has
     * been chopped down. See {@link DecodedPatch#isFellable()}, which this carries forward.
     */
    public boolean isFellable() {
        return fellable;
    }

    public CropState getState() {
        return state;
    }

    /** Predicted growth stage, clamped to {@code stages - 1}. */
    public int getStage() {
        return stage;
    }

    public int getStages() {
        return stages;
    }

    /**
     * Earliest wall-clock second the crop could have finished — the near edge of
     * the finish window. Equal to {@link #getDoneEstimateEpochSec()} whenever the
     * farming-tick beat is pinned down, which is the normal case once any growth
     * tick has been witnessed.
     */
    public long getEarliestDoneEpochSec() {
        return earliestDoneEpochSec;
    }

    /**
     * Latest wall-clock second the crop can still be pending — the far edge of the
     * finish window, and the value to show in a countdown so it never under-promises.
     * Zero when there is no timer at all (a terminal harvestable herb/tree, diseased,
     * dead, or a weedy patch).
     */
    public long getDoneEstimateEpochSec() {
        return doneEstimateEpochSec;
    }

    public long getObservedAtEpochSec() {
        return observedAtEpochSec;
    }

    /**
     * True when nothing is effectively in progress at the patch: weeds, or a
     * compost bin standing empty or still being filled. Mirrors
     * {@link DecodedPatch#isIdle()} for callers holding a prediction.
     */
    public boolean isIdle() {
        return crop == Crop.WEEDS || state == CropState.FILLING || state == CropState.EMPTY;
    }

    /**
     * Where {@code nowEpochSec} sits relative to this patch's finish window.
     *
     * <p>An observed HARVESTABLE patch is ground truth, not a prediction: it is
     * READY the instant it is seen, either because it carries no regrow timer
     * (herbs, chopped trees) or because its produce has fully restocked.
     */
    public PatchPrediction.Readiness readinessAt(long nowEpochSec) {
        if (state == CropState.HARVESTABLE && (doneEstimateEpochSec <= 0 || stage >= stages - 1)) {
            return Readiness.READY;
        }
        if (state != CropState.HARVESTABLE && state != CropState.GROWING) {
            return Readiness.GROWING;
        }
        if (doneEstimateEpochSec <= 0) {
            return Readiness.GROWING;
        }
        if (nowEpochSec >= doneEstimateEpochSec) {
            return Readiness.READY;
        }
        return nowEpochSec >= earliestDoneEpochSec ? Readiness.LIKELY_READY : Readiness.GROWING;
    }

    /**
     * True only when the patch is provably finished. A patch inside its finish
     * window reads false here — callers that want to surface the difference
     * should ask {@link #readinessAt(long)} instead of inferring it.
     */
    public boolean isReadyAt(long nowEpochSec) {
        return readinessAt(nowEpochSec) == Readiness.READY;
    }
}
