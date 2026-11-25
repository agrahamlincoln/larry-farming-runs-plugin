package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import java.util.*;
import lombok.Value;

/**
 * Every seed a run plants, resolved together against one reading of the stock.
 */
@Value
public class SeedPlan {

    /**
     * The better seeds, for however many of the run's best-ranked patches the rotation sets
     * aside. Which patches those are is scored per player rather than declared here.
     */
    SeedAllocation good;

    /** The filler, for every patch the good end does not reach. */
    SeedAllocation bad;

    /**
     * What the trip is actually going to put in the ground, one entry per crop with the
     * patches taking it.
     *
     * <p>The allocation records the seeds a run was <em>dealt</em>, and a dealt seed can be
     * declined:
     * {@code dealOutUpgrades} will not fell a living fruit tree for a worse sapling. Six
     * papaya saplings against five bearing palms and one bare patch is an allocation of six
     * and a planting of one, so the bag answers from the planting.
     *
     * <p>Stops naming their own crop are left out: the bag's fixed-seed line counts them
     * per patch.
     *
     * @param cropFor what each stop is planting, index-aligned with {@code plans}
     */
    public static List<SeedAllocation.Tally> goingInTheGround(List<StopPlan> plans, List<List<Crop>> cropFor) {
        Map<Crop, Integer> perCrop = new LinkedHashMap<>();
        for (int index = 0; index < plans.size(); index++) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit() || plan.getStop().isFixedCrop()) {
                continue;
            }
            for (Crop crop : cropFor.get(index)) {
                perCrop.merge(crop, 1, Integer::sum);
            }
        }
        List<SeedAllocation.Tally> tallies = new ArrayList<>(perCrop.size());
        for (Map.Entry<Crop, Integer> wanted : perCrop.entrySet()) {
            tallies.add(new SeedAllocation.Tally(wanted.getKey(), wanted.getValue()));
        }
        return tallies;
    }
}
