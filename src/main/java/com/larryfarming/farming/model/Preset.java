package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
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
 * {@link #preferences() settings}, which the built-in settings profile holds. The rest are
 * fixed; the planner reads each from {@link #DEFAULT}.
 */
@Getter
public final class Preset {

    /**
     * The name of {@link #DEFAULT} and of the built-in settings profile that holds it: the
     * guide the default route follows.
     */
    public static final String DEFAULT_LABEL = "BRUHsailer";

    /**
     * The Farming level from which the default route includes herb runs. A real level, not
     * a boosted one: Larry reads base levels only.
     */
    public static final int HERB_RUN_LEVEL = 82;

    /**
     * The Farming level from which the default route counts coral as worth farming, with an
     * Attas plant growing beside it.
     */
    public static final int CORAL_LEVEL = 99;

    /** The default route, which an untouched install runs on. */
    public static final Preset DEFAULT = defaultRoute();

    /** The name of {@link #TANGLEROOT}. */
    public static final String TANGLEROOT_LABEL = "Tangleroot";

    /**
     * The default route set to hunt the Tangleroot pet: every crop the route names for the
     * pet switched on, at the cost of some experience and yield.
     */
    public static final Preset TANGLEROOT = tangleroot();

    /** Every preset a player can pick, the default route first. */
    public static final List<Preset> BUILT_IN = List.of(DEFAULT, TANGLEROOT);

    /** What the settings call this preset. */
    private final String label;

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

    /** Whether the route fills and empties compost bins. */
    private final boolean worksCompostBins;

    /** The travel methods left unused. */
    private final Set<TravelMethod> travelOff;

    /** The sites left off the runs, by {@link FarmingKeys#SITES} key. */
    private final Set<String> sitesOff;

    /** The house items declared built, as their capabilities. */
    private final Set<Capability> houseDeclared;

    /** What to keep growing in the Farming Guild so a contract is often already done. */
    private final ContractStrategy contractPreplants;

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

    /** Whether the coral run waits for {@link #CORAL_LEVEL} Farming and an Attas plant. */
    private final boolean coralWaitsForAttas;

    /** Whether patches are treated with Fertile Soil and ash instead of ultracompost. */
    private final boolean compostsWithFertileSoil;

    /** The compost stock, in patches, below which the bins are worked again. */
    private final int makeCompostBelow;

    /** The compost stock at which the bins are left alone. */
    private final int stopCompostAt;

    @Builder(access = AccessLevel.PRIVATE, toBuilder = true)
    private Preset(String label, List<Crop> herbRanking, Set<Crop> herbsKeptForContracts,
            Crop herbCutoff, int goodSeedsPerRun, boolean guildHerbPatchReserved,
            boolean magicAtFaladorWithEliteDiary, Set<Crop> treesOn, Set<Crop> switchedOn,
            boolean picksFruitInPassing, boolean worksCompostBins,
            Set<TravelMethod> travelOff, Set<String> sitesOff, Set<Capability> houseDeclared,
            ContractStrategy contractPreplants,
            List<RunId> runPriority, Crop favouriteHerb, boolean goodSeedsInEveryPatch,
            boolean herbRunFirst, boolean waitsForEveryPatch,
            boolean fruitTreesWaitForSaplings, boolean replantsGrownTrees,
            boolean herbRunsWaitForLevel,
            boolean coralWaitsForAttas, boolean compostsWithFertileSoil,
            int makeCompostBelow, int stopCompostAt) {
        this.label = label;
        this.herbRanking = List.copyOf(herbRanking);
        this.herbsKeptForContracts = Collections.unmodifiableSet(copy(herbsKeptForContracts));
        this.herbCutoff = herbCutoff;
        this.goodSeedsPerRun = goodSeedsPerRun;
        this.guildHerbPatchReserved = guildHerbPatchReserved;
        this.magicAtFaladorWithEliteDiary = magicAtFaladorWithEliteDiary;
        this.treesOn = Collections.unmodifiableSet(copy(treesOn));
        this.switchedOn = Collections.unmodifiableSet(copy(switchedOn));
        this.picksFruitInPassing = picksFruitInPassing;
        this.worksCompostBins = worksCompostBins;
        this.travelOff = Collections.unmodifiableSet(travelOff.isEmpty()
                ? EnumSet.noneOf(TravelMethod.class) : EnumSet.copyOf(travelOff));
        this.sitesOff = Set.copyOf(sitesOff);
        this.houseDeclared = Set.copyOf(houseDeclared);
        this.contractPreplants = contractPreplants;
        if (runPriority.size() != RunId.values().length
                || !EnumSet.copyOf(runPriority).equals(EnumSet.allOf(RunId.class))) {
            throw new IllegalArgumentException("run priority must name every run once");
        }
        this.runPriority = List.copyOf(runPriority);
        if (!herbRanking.contains(herbCutoff)) {
            throw new IllegalArgumentException("herb cutoff " + herbCutoff + " is unranked");
        }
        this.favouriteHerb = favouriteHerb;
        this.goodSeedsInEveryPatch = goodSeedsInEveryPatch;
        this.herbRunFirst = herbRunFirst;
        this.waitsForEveryPatch = waitsForEveryPatch;
        this.fruitTreesWaitForSaplings = fruitTreesWaitForSaplings;
        this.replantsGrownTrees = replantsGrownTrees;
        this.herbRunsWaitForLevel = herbRunsWaitForLevel;
        this.coralWaitsForAttas = coralWaitsForAttas;
        this.compostsWithFertileSoil = compostsWithFertileSoil;
        this.makeCompostBelow = makeCompostBelow;
        this.stopCompostAt = stopCompostAt;
    }

    private static EnumSet<Crop> copy(Set<Crop> crops) {
        return crops.isEmpty() ? EnumSet.noneOf(Crop.class) : EnumSet.copyOf(crops);
    }

    private static Preset defaultRoute() {
        return builder()
                .label(DEFAULT_LABEL)
                .herbRanking(List.of(
                        Crop.TORSTOL, Crop.TOADFLAX, Crop.LANTADYME, Crop.CADANTINE,
                        Crop.KWUARM, Crop.AVANTOE, Crop.IRIT, Crop.SNAPDRAGON, Crop.RANARR,
                        Crop.DWARF_WEED, Crop.HARRALANDER, Crop.TARROMIN, Crop.MARRENTILL,
                        Crop.GUAM))
                .herbsKeptForContracts(Set.of(Crop.TORSTOL))
                .herbCutoff(Crop.IRIT)
                .goodSeedsPerRun(2)
                .guildHerbPatchReserved(true)
                .magicAtFaladorWithEliteDiary(true)
                // Yew and up among regular trees, papaya and up among fruit trees.
                // Mahogany, which gives more experience per day and per minute, with teak
                // for when the mahogany seeds run out.
                .treesOn(Set.of(Crop.MAGIC, Crop.YEW, Crop.DRAGONFRUIT, Crop.PALM,
                        Crop.PAPAYA, Crop.MAHOGANY, Crop.TEAK))
                // Off: mushrooms and belladonna, which earn little per trip, and watermelon,
                // poison ivy and cactus, the alternatives to snape grass, whiteberries and
                // potato cactus. Every anima plant is on, so the route's rule picks among them.
                // Every coral is on, so the best frag the player holds goes in: umbral, then
                // pillar, then elkhorn.
                .switchedOn(Set.of(Crop.SNAPE_GRASS, Crop.WHITE_LILY, Crop.LIMPWURT,
                        Crop.POTATO_CACTUS, Crop.WHITEBERRIES, Crop.CALQUAT, Crop.SEAWEED,
                        Crop.HESPORI, Crop.ATTAS, Crop.IASOR, Crop.KRONOS,
                        Crop.ELKHORN_CORAL, Crop.PILLAR_CORAL, Crop.UMBRAL_CORAL))
                .picksFruitInPassing(false)
                .worksCompostBins(true)
                // Every travel method on: the route ranks them itself.
                .travelOff(Set.of())
                // Every site on but the Prifddinas compost bin, which no herb patch stands
                // beside, so it would be a journey for the bin alone. Harmony needs no
                // default per book: only the Lunar run lists it, so its one switch is on that
                // run alone.
                .sitesOff(Set.of(FarmingKeys.VISIT_PRIFDDINAS_COMPOST_BIN))
                // Nothing declared: a house counts what Larry has seen in it.
                .houseDeclared(Set.of())
                .contractPreplants(defaultPreplants())
                // Hardwood leads: the most Farming experience of any run here, on the
                // longest cadence, so an idle patch costs the most. The anima plant follows
                // Hespori in the same guild, and its effect reaches every patch planted after
                // it. Coral follows the anima plant it waits for. Regular trees ride along
                // with fruit trees, so they rank low.
                .runPriority(List.of(RunId.HARDWOOD, RunId.CONTRACT, RunId.HESPORI,
                        RunId.ANIMA, RunId.CORAL, RunId.CALQUAT, RunId.FRUIT_TREE, RunId.HERB,
                        RunId.REGULAR_TREE, RunId.SEAWEED, RunId.ULTRACOMPOST))
                // The ranking above as it stands: no favourite herb, good seeds only where
                // the yield bonus is, hardwood first, and a run due on its first patch.
                .favouriteHerb(null)
                .goodSeedsInEveryPatch(false)
                .herbRunFirst(false)
                .waitsForEveryPatch(false)
                // A fruit tree run goes only once there are saplings for every patch, so one
                // sapling never sends the player round them all.
                .fruitTreesWaitForSaplings(true)
                // On: the route waits until there are saplings for all five patches, which
                // only makes sense if every visit plants them. A grown tree is chopped down
                // and replanted, and the new tree's health check pays again.
                .replantsGrownTrees(true)
                // Off: herb runs are optional below 82, not ruled out. A player who would
                // rather level on tree runs first, as yield grows with level, ticks it.
                .herbRunsWaitForLevel(false)
                // On: the route counts coral worth farming only at 99 with an Attas plant up.
                // The run stays switched on, so a player who has both sees it.
                .coralWaitsForAttas(true)
                // Off: the spell needs the Ash covered tome, which the game does not show.
                .compostsWithFertileSoil(false)
                // Make more below 300 and stop at 800. Six bins make 90 buckets a herb run,
                // so 800 leaves more than one run's output of room under the leprechaun's
                // 1,000.
                .makeCompostBelow(300)
                .stopCompostAt(800)
                .build();
    }

    /**
     * The default route with the route's pet-hunting answers in place of its own:
     *
     * <ul>
     * <li>Every fruit tree, since each tier has the same pet chance: a player out of the
     *     best saplings plants a lower tier rather than skip the run.</li>
     * <li>Teak alone among the hardwoods: the same chance as mahogany, but it grows faster.</li>
     * <li>Yew and up among regular trees, as the default route has: the lower tiers'
     *     chance is poor.</li>
     * <li>Plain cactus at Al Kharid in place of potato cactus, whose chance is far lower. The
     *     guild's cactus patch stays with the contracts.</li>
     * <li>Mushrooms and belladonna, which the default route leaves off.</li>
     * <li>No Attas: it lengthens the seaweed harvest, and seaweed rolls for the pet only once,
     *     on the last picking.</li>
     * </ul>
     *
     * <p>Herb runs stay: their own pet chance is poor, but they are worth doing for the
     * herbs. Calquat, seaweed and Hespori are on already.
     */
    private static Preset tangleroot() {
        Set<Crop> switchedOn = EnumSet.copyOf(DEFAULT.switchedOn);
        switchedOn.remove(Crop.POTATO_CACTUS);
        switchedOn.remove(Crop.ATTAS);
        switchedOn.addAll(Set.of(Crop.CACTUS, Crop.MUSHROOM, Crop.BELLADONNA));
        return DEFAULT.toBuilder()
                .label(TANGLEROOT_LABEL)
                .treesOn(Set.of(Crop.MAGIC, Crop.YEW, Crop.DRAGONFRUIT, Crop.PALM,
                        Crop.PAPAYA, Crop.PINEAPPLE, Crop.CURRY, Crop.ORANGE, Crop.BANANA,
                        Crop.APPLE, Crop.TEAK))
                .switchedOn(switchedOn)
                .build();
    }

    /**
     * The medium list's seven crops take seven of the beginner and intermediate patches. The
     * hard list's ten take every patch a contract can name; contracts never ask for the
     * spirit tree, anima or compost bin.
     */
    private static ContractStrategy defaultPreplants() {
        return ContractStrategy.builder()
                .mediumPreplant(slot(PatchType.TREE, Crop.MAPLE))
                .mediumPreplant(slot(PatchType.HERB, Crop.IRIT))
                .mediumPreplant(slot(PatchType.CACTUS, Crop.CACTUS))
                .mediumPreplant(slot(PatchType.BUSH, Crop.JANGERBERRIES))
                .mediumPreplant(slot(PatchType.ALLOTMENT, Crop.STRAWBERRY))
                .mediumPreplant(slot(PatchType.ALLOTMENT, Crop.WATERMELON))
                .mediumPreplant(slot(PatchType.FLOWER, Crop.LIMPWURT))
                .hardPreplant(slot(PatchType.REDWOOD, Crop.REDWOOD))
                .hardPreplant(slot(PatchType.FRUIT_TREE, Crop.PALM))
                .hardPreplant(slot(PatchType.CELASTRUS, Crop.CELASTRUS))
                .hardPreplant(slot(PatchType.TREE, Crop.MAPLE))
                // One herb patch accepts either herb.
                .hardPreplant(slot(PatchType.HERB, Crop.LANTADYME, Crop.CADANTINE))
                .hardPreplant(slot(PatchType.BUSH, Crop.POISON_IVY))
                .hardPreplant(slot(PatchType.FLOWER, Crop.WHITE_LILY))
                .hardPreplant(slot(PatchType.ALLOTMENT, Crop.WATERMELON))
                .hardPreplant(slot(PatchType.ALLOTMENT, Crop.SNAPE_GRASS))
                .hardPreplant(slot(PatchType.CACTUS, Crop.POTATO_CACTUS))
                .build();
    }

    private static ContractStrategy.PreplantSlot slot(PatchType type, Crop... acceptable) {
        return new ContractStrategy.PreplantSlot(List.of(acceptable), type);
    }

    /** The herbs ticked: the cutoff and every herb ranked above it but those kept for contracts. */
    public Set<Crop> herbsOn() {
        EnumSet<Crop> on = EnumSet.copyOf(herbRanking.subList(0,
                herbRanking.indexOf(herbCutoff) + 1));
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
        return new FarmingPreferences.FarmingPreferencesBuilder()
                .enabled(on)
                .picksFruitInPassing(picksFruitInPassing)
                .worksCompostBins(worksCompostBins)
                .travelOff(travelOff)
                .sitesOff(sitesOff)
                .houseDeclared(houseDeclared)
                .favouriteHerb(favouriteHerb)
                .plantsBestSeedsEverywhere(goodSeedsInEveryPatch)
                .herbRunFirst(herbRunFirst)
                .waitsForEveryPatch(waitsForEveryPatch)
                .fruitTreesWaitForSaplings(fruitTreesWaitForSaplings)
                .replantsGrownTrees(replantsGrownTrees)
                .herbRunsWaitForLevel(herbRunsWaitForLevel)
                .coralWaitsForAttas(coralWaitsForAttas)
                .compostsWithFertileSoil(compostsWithFertileSoil)
                .makeCompostBelow(makeCompostBelow)
                .stopCompostAt(stopCompostAt)
                .build();
    }

    /** Whether every setting this preset sets still has the preset's value. */
    public boolean matches(FarmingPreferences settings) {
        return preferences().equals(settings);
    }
}
