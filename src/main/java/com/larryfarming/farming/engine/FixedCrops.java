package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.Skill;
import java.util.ArrayList;
import java.util.List;

/**
 * What goes in a stop with one default crop: that crop or another crop of the same
 * patch, whichever of them the player has switched on.
 *
 * <p>Every reader of a fixed-crop stop's crop asks here, so the walkthrough, the bag, the
 * runs list and the switches agree on one answer.
 */
final class FixedCrops {

    private FixedCrops() {
    }

    /**
     * The stop's crops the player has ticked, best first. Empty silences the stop.
     */
    static List<Crop> ticked(RunStop stop, FarmingPreferences prefs) {
        List<Crop> ticked = new ArrayList<>();
        for (Crop crop : FarmingCatalogue.cropChoices(stop)) {
            if (prefs.allows(crop)) {
                ticked.add(crop);
            }
        }
        return ticked;
    }

    /**
     * The crop this stop plants: the highest-level ticked crop the player can grow and is not
     * known to be out of, falling to the next ticked crop as each runs out.
     *
     * <p>With every growable crop out, the best growable one, so the walkthrough's seed skip
     * names the seed to fetch. With none growable, the lowest-level ticked crop, so its level
     * skip names the level to reach (see {@link #belowLevel}). Null only for a stop with
     * nothing ticked, which the planting gate silences.
     */
    static Crop cropFor(RunStop stop, FarmingPreferences prefs, PlayerState state) {
        List<Crop> ticked = ticked(stop, prefs);
        if (ticked.isEmpty()) {
            return null;
        }
        Crop growable = null;
        for (Crop crop : ticked) {
            CropData data = CropData.forCrop(crop);
            // An unread Farming level grows anything, so it never moves the choice.
            if (data == null || belowLevel(state, data)) {
                continue;
            }
            if (!SeedSelector.definitelyOutOf(crop, state, data.getSeedsPerPatch())) {
                return crop;
            }
            if (growable == null) {
                growable = crop;
            }
        }
        return growable != null ? growable : ticked.get(ticked.size() - 1);
    }

    /**
     * Whether this crop is one the stop may grow, ticked or not. A crop the player has
     * since unticked is still theirs while it stands in the patch.
     */
    static boolean grows(RunStop stop, Crop crop) {
        return crop != null && FarmingCatalogue.cropChoices(stop).contains(crop);
    }

    /**
     * Whether the player's Farming level is known and short of this crop's. An unread level
     * is never short, so it never skips a stop.
     */
    static boolean belowLevel(PlayerState state, CropData data) {
        if (state == null || state.getSkills() == null || !state.getSkills().containsKey(Skill.FARMING)) {
            return false;
        }
        return state.getSkillLevel(Skill.FARMING) < data.getFarmingLevel();
    }
}
