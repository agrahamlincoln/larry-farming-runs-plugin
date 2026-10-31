package com.larryfarming.engine.farming;

/**
 * The decoded meaning of a farming-patch transmit varbit: which crop occupies
 * the patch, its health/lifecycle {@link CropState}, and how far it has grown.
 *
 * <p>Immutable value object produced by {@link PatchType#decode(int)}. The stage
 * is the raw growth index the game reports for the current state — for GROWING it
 * counts up from 0 to {@code stages - 1}; for DISEASED/DEAD it is the game's 1-based
 * progression through that state. Callers that only care about "how grown" should
 * read {@link #getState()} first, since the stage number means different things
 * per state.
 *
 * <p>The HARVESTABLE stage has no single direction across decoders. A crop that restocks
 * (fruit trees, bushes, allotments) counts <em>up</em> toward {@code getStages() - 1}: the
 * stage is how much produce is on the plant, and a picked-clean fruit tree reads 0 of 7. A
 * crop picked a fixed number of times (herbs, hops) counts the remaining lives
 * <em>down</em>. {@link PatchPrediction#readinessAt} and {@code FarmingTracker.stagesRemain}
 * assume the count-up reading, so a caller building one of these by hand must use it too:
 * a picked fruit tree written at a full stage reads back as ready.
 */
public final class DecodedPatch {

    private final Crop crop;
    private final CropState state;
    private final int stage;
    private final boolean fellable;

    /** A patch whose crop, if any, needs no axe taking out — the common case. */
    public DecodedPatch(Crop crop, CropState state, int stage) {
        this(crop, state, stage, false);
    }

    /**
     * @param fellable whether a tree is still rooted in this patch, so the ground under it
     *                 cannot take a sapling until it has been chopped down. See
     *                 {@link #isFellable()}
     */
    public DecodedPatch(Crop crop, CropState state, int stage, boolean fellable) {
        this.crop = crop;
        this.state = state;
        this.stage = stage;
        this.fellable = fellable;
    }

    public Crop getCrop() {
        return crop;
    }

    /**
     * Whether a tree is still standing in this patch, as opposed to its stump or bare
     * ground.
     *
     * <p>{@link CropState} cannot say this. The game gives a checked tree ("Chop down") and
     * its stump ("Clear") different varbit values, but both decode to HARVESTABLE stage 0
     * because neither carries a timer. A standing tree must be felled before the patch can
     * take the next sapling.
     *
     * <p>True exactly where the patch object offers "Chop down": a checked regular or
     * hardwood tree, and a fruit tree at any point in its bearing life. False for the
     * stumps, for a redwood — which is cleared with a spade rather than felled — and for
     * everything that is not a tree.
     */
    public boolean isFellable() {
        return fellable;
    }

    public CropState getState() {
        return state;
    }

    public int getStage() {
        return stage;
    }

    /**
     * Number of stages for the current state: harvest "lives" once HARVESTABLE
     * (regrowing crops cycle through these), otherwise the growth stage count.
     */
    public int getStages() {
        return state == CropState.HARVESTABLE ? crop.getHarvestStages() : crop.getStages();
    }

    /**
     * Minutes per tick while the patch still advances; 0 = no timer. GROWING
     * counts down the grow tick rate; HARVESTABLE counts the regrow tick rate
     * (0 for crops that are picked once and never regrow); DISEASED, DEAD, and
     * the idle bin states (FILLING/EMPTY) never tick.
     */
    public int tickRateMinutes() {
        switch (state) {
            case GROWING:
                return crop.getTickRateMinutes();
            case HARVESTABLE:
                return crop.getRegrowTickRateMinutes();
            default:
                return 0;
        }
    }

    /**
     * True when nothing is effectively in progress at the patch: weeds, or a
     * compost bin standing empty or still being filled. Idle patches carry no
     * timer and are hidden from the timer displays, exactly like unplanted soil.
     */
    public boolean isIdle() {
        return crop == Crop.WEEDS || state == CropState.FILLING || state == CropState.EMPTY;
    }
}
