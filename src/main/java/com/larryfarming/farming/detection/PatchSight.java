package com.larryfarming.farming.detection;

import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.farming.model.PatchRef;

/**
 * What the player has been seen to have in a farming patch, for the rules that answer a
 * capability from one, such as a grown spirit tree or an unlocked Weiss patch.
 *
 * <p>{@code FarmingTracker} decodes and persists patch observations. This one-method view
 * stands in for {@code FarmingInputs} so the detection layer knows nothing about run planning.
 */
@FunctionalInterface
public interface PatchSight {

    /**
     * What this patch was last seen holding, or null when the player has not walked past it
     * since the plugin was installed.
     */
    PatchPrediction at(PatchRef patch);

    /**
     * Sees nothing, for callers with no patch observations to offer.
     *
     * <p>Rules treat "never seen" as absent, so a blind sight answers every patch capability
     * with a no. Pass the real observations wherever a {@code FarmingInputs} is in scope.
     */
    static PatchSight blind() {
        return patch -> null;
    }
}
