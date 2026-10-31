package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * One repeatable farming activity: an ordered set of stops, what unlocks it, and the
 * crop whose growth sets its rhythm.
 */
@Value
@Builder
public class FarmingRun {

    /** Not crop-driven; {@link #cadenceSeconds()} returns this. */
    public static final long NO_CROP_CADENCE = -1L;

    @NonNull
    RunId id;

    @NonNull
    Spellbook spellbook;

    @NonNull
    UnlockGate unlock;

    /**
     * The crop that paces the run, or null when no crop decides when to go again.
     * Cadence derives from this crop's grow time, so it cannot drift from the game's
     * crop table.
     *
     * <p>Null for contracts and ultracompost, which wait on no crop.
     */
    Crop pacingCrop;

    /**
     * The town this run prefers its respawn point set to, or null when it has no
     * preference.
     *
     * <p>Advisory, not a requirement. Runs prefer different respawn points, and switching costs a trip across the map. The run is planned
     * around wherever the respawn point actually is.
     */
    String respawnTown;

    @Singular("stop")
    List<RunStop> stops;

    /**
     * The patch type whose ticked crops this run plants, or null when the shared seed
     * policy decides. The player's ticks pick the crops, best first; a player out of the
     * best seed drops a tier rather than skipping the run.
     *
     * <p>A run states its seeds in at most one way: this type, or a stop that names its
     * crop outright ({@link RunStop.Builder#plants}). Stating neither hands the patch to the
     * shared herb rotation. Stating both packs the same seed twice.
     */
    PatchType seedPatchType;

    /**
     * How long the pacing crop takes to finish growing, or {@link #NO_CROP_CADENCE}
     * when this run is not paced by a crop at all.
     */
    public long cadenceSeconds() {
        return pacingCrop == null ? NO_CROP_CADENCE : pacingCrop.totalGrowSeconds();
    }

    /** The stops that make up the route proper, excluding the woven-in extras. */
    public List<RunStop> requiredStops() {
        List<RunStop> required = new ArrayList<>();
        for (RunStop stop : stops) {
            if (!stop.isOptional()) {
                required.add(stop);
            }
        }
        return required;
    }
}
