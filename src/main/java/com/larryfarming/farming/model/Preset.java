package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.farming.Crop;
import java.util.*;
import lombok.Getter;

/**
 * One full set of answers to the choices a farmer could reasonably make differently: which
 * crops to plant, how the herb rotation ranks its seeds, what the Farming Guild is kept for,
 * and which run goes first.
 *
 * <p>Holds opinions only. Game facts (levels, unlocks, growth times, diary bonuses) are read
 * from the game or the catalogue, never from here.
 *
 * <p>Two kinds of opinion live here. The ones the player can change are the
 * {@link #preferences() settings}, which "Reset to the default route" puts back. The rest
 * are fixed; the planner reads each from {@link #DEFAULT}.
 */
@Getter
public final class Preset {

    /**
     * The Farming level from which the default route includes herb runs. A real level, not
     * a boosted one: Larry reads base levels only.
     */
    public static final int HERB_RUN_LEVEL = 82;

    /** The default route, which an untouched install runs on. */
    public static final Preset DEFAULT = new Preset(Tables.byName("/com/larryfarming/preset.tsv", 2));

    /**
     * Every herb, best first. Ranked by experience per seed including the time to gather
     * secondaries, so it disagrees with level order: snapdragon sits below irit.
     */
    private final List<Crop> herbRanking;

    /**
     * Herbs ranked at or above the cutoff that still start unticked. Torstol tops the
     * ranking but is hard to gather in bulk, so it is kept for hard contracts.
     */
    private final Set<Crop> herbsKeptForContracts;

    /** The worst herb worth planting: it and every herb ranked above it start ticked. */
    private final Crop herbCutoff;

    /**
     * How many good seeds a herb run plants, in the patches with the best yield bonus. The
     * other patches take the worst seed at or above the cutoff, so the scarce seeds go
     * where they yield most.
     */
    private final int goodSeedsPerRun;

    /**
     * Whether the Farming Guild herb patch is kept out of herb runs. Its yield bonus goes to
     * contracts instead, which pay in seed packs.
     *
     * <p>Needs no level gate. The patch stands in the guild's west wing, which opens at 65
     * Farming (62 with a boost), and every herb contract also needs 65, boostable. So no
     * player reaches the patch before the contracts can use it.
     */
    private final boolean guildHerbPatchReserved;

    /**
     * Whether a ticked magic goes only to the Falador tree patch once the elite Falador
     * diary protects it, while every other tree patch takes the ticked trees below magic.
     * Before the diary a ticked magic is planted at every tree patch.
     */
    private final boolean magicAtFaladorWithEliteDiary;

    /** The tree-family crops ticked: regular, fruit and hardwood trees. */
    private final Set<Crop> treesOn;

    /** The crops switched on outright: the extras a run plants in passing, and whole runs. */
    private final Set<Crop> switchedOn;

    /** Whether a fruit tree is worth stopping at only to pick it. */
    private final boolean picksFruitInPassing;

    /** The travel methods left unused. */
    private final Set<TravelMethod> travelOff;

    /** The sites left off the runs, by {@link FarmingKeys#SITES} key. */
    private final Set<String> sitesOff;

    /** The house items declared built, as their capabilities. */
    private final Set<Capability> houseDeclared;

    /** Every run, most valuable first, for ordering runs that are due together. */
    private final List<RunId> runPriority;

    /** The herb planted before every other, or null to follow {@link #herbRanking}. */
    private final Crop favouriteHerb;

    /**
     * Whether every herb patch takes the best seeds, rather than only the
     * {@link #goodSeedsPerRun} patches with the best yield bonus.
     */
    private final boolean goodSeedsInEveryPatch;

    /** Whether the herb run goes ahead of {@link #runPriority}'s first run. */
    private final boolean herbRunFirst;

    /**
     * Whether a run waits until every patch on it needs a visit, rather than falling due
     * when any one does.
     */
    private final boolean waitsForEveryPatch;

    /** Whether the fruit tree run waits for a sapling for every patch. */
    private final boolean fruitTreesWaitForSaplings;

    /** Whether a grown fruit tree is felled for a sapling no worse than itself. */
    private final boolean replantsGrownTrees;

    /** Whether the herb run waits for {@link #HERB_RUN_LEVEL} Farming. */
    private final boolean herbRunsWaitForLevel;

    /** Whether patches are treated with Fertile Soil and ash instead of ultracompost. */
    private final boolean compostsWithFertileSoil;

    /** Read from the preset table, one row per field, keyed by the field's name. */
    private Preset(Tables.Named row) {
        herbRanking = row.of("herbRanking").constants(Crop.class, 1);
        herbsKeptForContracts = setOf(row.of("herbsKeptForContracts"), Crop.class);
        herbCutoff = row.of("herbCutoff").constant(Crop.class, 1);
        goodSeedsPerRun = row.of("goodSeedsPerRun").integer(1);
        guildHerbPatchReserved = row.of("guildHerbPatchReserved").flag(1, YES);
        magicAtFaladorWithEliteDiary = row.of("magicAtFaladorWithEliteDiary").flag(1, YES);
        treesOn = setOf(row.of("treesOn"), Crop.class);
        switchedOn = setOf(row.of("switchedOn"), Crop.class);
        picksFruitInPassing = row.of("picksFruitInPassing").flag(1, YES);
        travelOff = setOf(row.of("travelOff"), TravelMethod.class);
        String sites = row.of("sitesOff").text(1);
        sitesOff = sites.isEmpty() ? Set.of() : Set.of(sites.split(" "));
        houseDeclared = setOf(row.of("houseDeclared"), Capability.class);
        runPriority = row.of("runPriority").constants(RunId.class, 1);
        if (runPriority.size() != RunId.values().length || !EnumSet.copyOf(runPriority).equals(EnumSet.allOf(RunId.class))) {
            throw row.of("runPriority").error("run priority must name every run once");
        }
        if (!herbRanking.contains(herbCutoff)) {
            throw row.of("herbCutoff").error("herb cutoff " + herbCutoff + " is unranked");
        }
        favouriteHerb = row.of("favouriteHerb").optionalConstant(Crop.class, 1);
        goodSeedsInEveryPatch = row.of("goodSeedsInEveryPatch").flag(1, YES);
        herbRunFirst = row.of("herbRunFirst").flag(1, YES);
        waitsForEveryPatch = row.of("waitsForEveryPatch").flag(1, YES);
        fruitTreesWaitForSaplings = row.of("fruitTreesWaitForSaplings").flag(1, YES);
        replantsGrownTrees = row.of("replantsGrownTrees").flag(1, YES);
        herbRunsWaitForLevel = row.of("herbRunsWaitForLevel").flag(1, YES);
        compostsWithFertileSoil = row.of("compostsWithFertileSoil").flag(1, YES);
        row.requireAllRead();
    }

    /** A switch's value in the preset table; the other is {@link Tables#EMPTY}. */
    private static final String YES = "yes";

    private static <E extends Enum<E>> Set<E> setOf(Tables.Row row, Class<E> type) {
        EnumSet<E> set = EnumSet.noneOf(type);
        set.addAll(row.constants(type, 1));
        return Collections.unmodifiableSet(set);
    }

    /** The herbs ticked: the cutoff and every herb ranked above it but those kept for contracts. */
    public Set<Crop> herbsOn() {
        EnumSet<Crop> on = EnumSet.copyOf(herbRanking.subList(0, herbRanking.indexOf(herbCutoff) + 1));
        on.removeAll(herbsKeptForContracts);
        return Collections.unmodifiableSet(on);
    }

    /** Where this run sorts among runs due together; lower goes first, starting at 1. */
    public int priorityOf(RunId run) {
        return runPriority.indexOf(run) + 1;
    }

    /**
     * Where this run sorts with the herb run lifted to the top, or not: the herb run takes
     * 1 and every run it passed moves down one, so the rest keep their order.
     */
    public int priorityOf(RunId run, boolean herbsFirst) {
        int preset = priorityOf(run);
        if (!herbsFirst) {
            return preset;
        }
        if (run == RunId.HERB) {
            return 1;
        }
        return preset < priorityOf(RunId.HERB) ? preset + 1 : preset;
    }

    /** The settings this preset sets, as the planner reads them. */
    public FarmingPreferences preferences() {
        EnumSet<Crop> on = EnumSet.copyOf(herbsOn());
        on.addAll(treesOn);
        on.addAll(switchedOn);
        return new FarmingPreferences.FarmingPreferencesBuilder().enabled(on).picksFruitInPassing(picksFruitInPassing).travelOff(travelOff)
                .sitesOff(sitesOff).houseDeclared(houseDeclared).favouriteHerb(favouriteHerb)
                .plantsBestSeedsEverywhere(goodSeedsInEveryPatch).herbRunFirst(herbRunFirst).waitsForEveryPatch(waitsForEveryPatch)
                .fruitTreesWaitForSaplings(fruitTreesWaitForSaplings).replantsGrownTrees(replantsGrownTrees)
                .herbRunsWaitForLevel(herbRunsWaitForLevel).compostsWithFertileSoil(compostsWithFertileSoil).build();
    }
}
