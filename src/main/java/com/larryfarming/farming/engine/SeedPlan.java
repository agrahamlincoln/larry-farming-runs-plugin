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

    public boolean isConfident() {
        return good.isConfident() && bad.isConfident();
    }

    /**
     * What to carry out of the bank for a trip: every crop across both ends of every run
     * with its patch count, in the order the trip first meets each, good seeds first.
     *
     * <p>Each run allocates against its own rotation, so a trip has one plan per run. The
     * crops merge because late in the rotation both ends can land on one crop, and
     * "2 avantoe, 5 avantoe" describes one handful of seeds. Both the bag and the sentence
     * above it read this one answer.
     */
    public static List<SeedAllocation.Tally> shoppingList(List<SeedPlan> plans) {
        Map<Crop, Integer> perCrop = new LinkedHashMap<>();
        for (SeedPlan plan : plans) {
            for (Crop crop : plan.good.getPerPatch()) {
                perCrop.merge(crop, 1, Integer::sum);
            }
            for (Crop crop : plan.bad.getPerPatch()) {
                perCrop.merge(crop, 1, Integer::sum);
            }
        }
        return tallies(perCrop);
    }

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
        return tallies(perCrop);
    }

    private static List<SeedAllocation.Tally> tallies(Map<Crop, Integer> perCrop) {
        List<SeedAllocation.Tally> tallies = new ArrayList<>(perCrop.size());
        for (Map.Entry<Crop, Integer> wanted : perCrop.entrySet()) {
            tallies.add(new SeedAllocation.Tally(wanted.getKey(), wanted.getValue()));
        }
        return tallies;
    }

    /** How many patches the trip is trying to fill, across both ends of every run. */
    public static int wanted(List<SeedPlan> plans) {
        int wanted = 0;
        for (SeedPlan plan : plans) {
            wanted += plan.good.getWanted() + plan.bad.getWanted();
        }
        return wanted;
    }

    /** How many of them the stock actually covers. */
    public static int planted(List<SeedPlan> plans) {
        int planted = 0;
        for (SeedPlan plan : plans) {
            planted += plan.good.getPerPatch().size() + plan.bad.getPerPatch().size();
        }
        return planted;
    }

    /** False as soon as one run's answer rests on a container we have not been shown. */
    public static boolean confident(List<SeedPlan> plans) {
        for (SeedPlan plan : plans) {
            if (!plan.isConfident()) {
                return false;
            }
        }
        return true;
    }
}
