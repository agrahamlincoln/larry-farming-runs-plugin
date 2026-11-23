package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.CropData;
import java.util.*;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * What the player is willing to plant, and what they would rather leave alone.
 *
 * <p>Every crop the player has a say in carries one tick. Herbs and the three tree families
 * are ticked crop by crop: a patch takes the best ticked crop the player has the seed for,
 * and a family with nothing ticked is switched off. Every other crop the catalogue grows
 * takes the same tick as a plain on/off switch.
 *
 * <p>The defaults are {@link Preset#DEFAULT}'s, so an untouched install plants what the
 * runs are written for. Each default must agree with the catalogue, so retune a run there
 * too.
 *
 * <p>Ticks are hard: an unticked crop is never suggested, even when that leaves a patch
 * empty and a run unproposed.
 *
 * <p>Some settings plant nothing themselves but change how the planner ranks and paces:
 * the favourite herb, where the best herb seeds go, which run goes first, and whether a
 * run waits for every patch.
 */
@EqualsAndHashCode
@Getter
@Accessors(fluent = true)
public final class FarmingPreferences {

    /**
     * Every herb the player ticks, best first by the preset's ranking.
     *
     * <p>The herb ranking is by experience and disagrees with level order at three places,
     * so the order here is not the Farming level's.
     *
     * <p>Torstol is here though the preset leaves it unticked, kept for hard contracts.
     */
    public static final List<Crop> HERB_CHOICES = Preset.DEFAULT.getHerbRanking();

    /**
     * Every crop of each tree family, best first, which for trees is level order: each is
     * every crop its patch takes, so no tree is missing a tick.
     */
    public static final Map<PatchType, List<Crop>> TREE_CHOICES = treeChoices();

    private static Map<PatchType, List<Crop>> treeChoices() {
        Map<PatchType, List<Crop>> families = new EnumMap<>(PatchType.class);
        for (PatchType family : EnumSet.of(PatchType.TREE, PatchType.FRUIT_TREE, PatchType.HARDWOOD_TREE)) {
            families.put(family, CropData.plantableIn(family));
        }
        return Collections.unmodifiableMap(families);
    }

    private final Set<Crop> enabled;

    /**
     * Whether a fruit tree is worth stopping at purely to pick it. Off by default: a living
     * fruit tree keeps its fruit until the next visit, and the fruit alone does not repay
     * the trip. Replanting is offered regardless of this setting.
     */
    private final boolean picksFruitInPassing;

    /**
     * The travel methods the player has switched off. Each refusal is hard and covers every
     * use: a stop that only a refused method reaches loses that rung. See
     * {@link TravelMethod}.
     */
    private final Set<TravelMethod> travelOff;

    /**
     * The sites the player has dropped from their runs, by {@link FarmingKeys#SITES} key. A
     * dropped site is silenced as a switched-off crop is: gone from the walkthrough, the runs
     * list and the timers.
     */
    private final Set<String> sitesOff;

    /**
     * The sites this account is seen unable to reach yet, by key: a fact about the account,
     * not a setting, so it is never saved. Only {@link #dropsEverySiteOf} reads it, so a run
     * whose every reachable site is dropped switches off; a shut site still keeps its stop.
     * Empty until the engine reads the account.
     */
    private final Set<String> sitesShut;

    /**
     * What the player says their house holds, as the {@link HouseItem} capabilities. A
     * declaration counts only until the item has been seen; what was seen wins, and the
     * declaration stays saved.
     */
    private final Set<Capability> houseDeclared;

    /**
     * The herb the rotation ranks above every other, or null for the preset's ranking.
     * Always a ticked herb: a favourite the player cannot plant would rank nothing.
     */
    private final Crop favouriteHerb;

    /** Whether every herb patch takes the best seeds, not only the yield-bonus ones. */
    private final boolean plantsBestSeedsEverywhere;

    /** Whether the herb run goes first among runs due together. */
    private final boolean herbRunFirst;

    /** Whether a run waits until every patch on it needs a visit before it is due. */
    private final boolean waitsForEveryPatch;

    /** Whether the fruit tree run waits until the player owns a sapling for every patch. */
    private final boolean fruitTreesWaitForSaplings;

    /** Whether a grown fruit tree is felled for a sapling no worse than itself. */
    private final boolean replantsGrownTrees;

    /** Whether the herb run waits for {@link Preset#HERB_RUN_LEVEL} Farming. */
    private final boolean herbRunsWaitForLevel;

    /**
     * Whether patches are treated with Fertile Soil cast with volcanic ash, which needs the
     * Ash covered tome read, instead of carried ultracompost.
     */
    private final boolean compostsWithFertileSoil;

    /**
     * The compost stock, in patches it can treat, below which the bins are worked again.
     * Always a whole step, from {@link #COMPOST_STEP} up to one step below {@link #COMPOST_MOST}.
     */
    private final int makeCompostBelow;

    /**
     * The compost stock at which the bins are left alone. Always a step above
     * {@link #makeCompostBelow}, and at most {@link #COMPOST_MOST}.
     */
    private final int stopCompostAt;

    /** The compost thresholds move in steps of this many patches, starting at one step. */
    public static final int COMPOST_STEP = 100;

    /**
     * The longest run after a teleport a route may ask for, in whole seconds, from
     * {@link #RUN_LEAST_SECONDS} up to {@link #RUN_MOST_SECONDS}.
     */
    private final int longestRunSeconds;

    /** The shortest limit offered: 33 tiles, a short run from the landing. */
    public static final int RUN_LEAST_SECONDS = 10;
    /** The longest limit offered: 200 tiles, a minute's run. */
    public static final int RUN_MOST_SECONDS = 60;

    /** The highest compost threshold: the tool leprechaun holds 1,000 of each compost. */
    public static final int COMPOST_MOST = 1000;

    /** Each picked patch's crop, by site ({@link FarmingKeys#PICKS}); a patch missing plants nothing. */
    private final Map<String, Crop> picks;

    /** The picked allotments that plant for compost, by site. */
    private final Set<String> forCompost;

    /**
     * The picked patches whose crop the player chose, by site, as against the preset's. A
     * crop the player chose says why it isn't planting; the preset's stays quiet.
     */
    private final Set<String> picksChosen;

    /**
     * The picked patches that plant nothing and say nothing for now, by site, and the
     * settings still show their pick. A pick is quiet while the account can't plant its
     * preset crop yet. It is also quiet while its farm's compost thermostat holds it: the
     * thermostat is off, the bank is unseen, or Larry leaves the bin beside it this run with
     * a crop standing in the patch (the bin rotting, full, short of potion, ash or buckets,
     * or at Ortus Farm with no bottomless bucket).
     */
    private final Set<String> picksQuiet;

    /**
     * @param enabled every crop the player has ticked; a crop missing from it is unticked
     * @param travelOff every travel method switched off
     * @param sitesOff every site dropped, by key; a key naming no site is ignored
     * @param sitesShut every site the account is seen unable to reach, by key
     * @param houseDeclared every house item declared built; any other capability is ignored
     * @param favouriteHerb read as none unless it is a ticked herb
     * @param makeCompostBelow rounded down to a step and kept below {@link #COMPOST_MOST}
     * @param stopCompostAt rounded down to a step, and raised a step above
     *     {@code makeCompostBelow} if it is not already above it
     */
    // Copied into fresh EnumSets because EnumSet.copyOf throws on an empty plain
    // collection, which "everything switched off" produces.
    //
    // No public builder(): a fresh builder defaults every switch to false, which is not the
    // default route. Callers start from defaults().toBuilder() and change only what they
    // mean to.
    @Builder(toBuilder = true, builderMethodName = "")
    private FarmingPreferences(Set<Crop> enabled, boolean picksFruitInPassing, Set<TravelMethod> travelOff, Set<String> sitesOff,
            Set<String> sitesShut, Set<Capability> houseDeclared, Crop favouriteHerb,
            boolean plantsBestSeedsEverywhere, boolean herbRunFirst, boolean waitsForEveryPatch, boolean fruitTreesWaitForSaplings,
            boolean replantsGrownTrees, boolean herbRunsWaitForLevel, boolean compostsWithFertileSoil, Map<String, Crop> picks,
            Set<String> forCompost, Set<String> picksChosen, Set<String> picksQuiet, int makeCompostBelow, int stopCompostAt,
            int longestRunSeconds) {
        this.longestRunSeconds = Math.max(RUN_LEAST_SECONDS, Math.min(RUN_MOST_SECONDS, longestRunSeconds));
        this.enabled = copy(Crop.class, enabled);
        this.picksFruitInPassing = picksFruitInPassing;
        this.travelOff = copy(TravelMethod.class, travelOff);
        // Sorted, so equal preferences print alike; filtered, so a stray key cannot make two
        // identical sets of choices unequal.
        this.sitesOff = knownSites(sitesOff);
        this.sitesShut = knownSites(sitesShut);
        EnumSet<Capability> declaredCopy = EnumSet.noneOf(Capability.class);
        if (houseDeclared != null) {
            for (Capability capability : houseDeclared) {
                if (HouseItem.of(capability) != null) {
                    declaredCopy.add(capability);
                }
            }
        }
        this.houseDeclared = Collections.unmodifiableSet(declaredCopy);
        this.favouriteHerb = favouriteHerb != null && HERB_CHOICES.contains(favouriteHerb)
                && this.enabled.contains(favouriteHerb) ? favouriteHerb : null;
        this.plantsBestSeedsEverywhere = plantsBestSeedsEverywhere;
        this.herbRunFirst = herbRunFirst;
        this.waitsForEveryPatch = waitsForEveryPatch;
        this.fruitTreesWaitForSaplings = fruitTreesWaitForSaplings;
        this.replantsGrownTrees = replantsGrownTrees;
        this.herbRunsWaitForLevel = herbRunsWaitForLevel;
        this.compostsWithFertileSoil = compostsWithFertileSoil;
        // The pair stays valid whatever was saved: the low threshold leaves room for one
        // step above it, and the high one is always above the low one.
        this.makeCompostBelow = toStep(makeCompostBelow, COMPOST_STEP, COMPOST_MOST - COMPOST_STEP);
        this.stopCompostAt = toStep(stopCompostAt, this.makeCompostBelow + COMPOST_STEP, COMPOST_MOST);
        // Sorted, less any site the settings don't know, any patch picked for nothing and any
        // crop its patch can't grow.
        Map<String, Crop> picked = new TreeMap<>(picks != null ? picks : Map.of());
        picked.keySet().retainAll(FarmingKeys.PICKS.keySet());
        picked.values().removeIf(Objects::isNull);
        picked.entrySet().removeIf(pick -> !CropData.plantableIn(FarmingKeys.PICKS.get(pick.getKey()).getPatchType())
                .contains(pick.getValue()));
        this.picks = Collections.unmodifiableMap(picked);
        this.forCompost = knownPicks(forCompost);
        this.picksChosen = knownPicks(picksChosen);
        this.picksQuiet = knownPicks(picksQuiet);
    }

    /** A threshold rounded down to a whole step and held between the two bounds. */
    private static int toStep(int value, int least, int most) {
        int stepped = value / COMPOST_STEP * COMPOST_STEP;
        return Math.max(least, Math.min(most, stepped));
    }

    private static Set<String> knownPicks(Set<String> sites) {
        return known(sites, FarmingKeys.PICKS.keySet());
    }

    private static <E extends Enum<E>> Set<E> copy(Class<E> type, Set<E> from) {
        EnumSet<E> copy = EnumSet.noneOf(type);
        if (from != null) {
            copy.addAll(from);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Set<String> knownSites(Set<String> keys) {
        return known(keys, FarmingKeys.SITES);
    }

    /** These keys less any the settings don't know, sorted. */
    private static Set<String> known(Set<String> keys, Collection<String> known) {
        Set<String> copy = new TreeSet<>();
        if (keys != null) {
            for (String site : keys) {
                if (known.contains(site)) {
                    copy.add(site);
                }
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    /** The default route's settings, which an untouched install runs on. */
    public static FarmingPreferences defaults() {
        return Preset.DEFAULT.preferences();
    }

    /** {@link #longestRunSeconds} as walked tiles, at a run's two tiles a 0.6-second tick. */
    public int longestWalkTiles() {
        return longestRunSeconds * 10 / 3;
    }

    /** Whether this travel method may be used. Every method is on by default. */
    public boolean uses(TravelMethod method) {
        return !travelOff.contains(method);
    }

    /** Whether the runs visit the site with this key. Every site is visited by default. */
    public boolean visits(String siteKey) {
        return !sitesOff.contains(siteKey);
    }

    /**
     * Whether the player has dropped every one of these sites they can reach, and at least
     * one: a site the account is seen unable to reach counts as dropped, so it cannot hold a
     * run open on its own. False for no sites, and while none is dropped.
     */
    public boolean dropsEverySiteOf(List<String> sites) {
        if (sites.isEmpty() || sitesOff.isEmpty()) {
            return false;
        }
        boolean anyDropped = false;
        for (String site : sites) {
            if (sitesOff.contains(site)) {
                anyDropped = true;
            } else if (!sitesShut.contains(site)) {
                return false;
            }
        }
        return anyDropped;
    }

    /**
     * The same preferences, with these sites known to be out of the account's reach. See
     * {@link #sitesShut}.
     */
    public FarmingPreferences withSitesShut(Set<String> shut) {
        return knownSites(shut).equals(sitesShut) ? this : toBuilder().sitesShut(shut).build();
    }

    /** The same preferences with these picked patches quiet. See {@link #picksQuiet}. */
    public FarmingPreferences withPicksQuiet(Set<String> quiet) {
        return knownPicks(quiet).equals(picksQuiet) ? this : toBuilder().picksQuiet(quiet).build();
    }

    /** The crop this picked patch plants, or null for none or for a quiet preset crop. */
    public Crop pickAt(String site) {
        return picksQuiet.contains(site) ? null : picks.get(site);
    }

    /** Whether this picked allotment plants for compost. */
    public boolean plantsForCompost(String site) {
        return forCompost.contains(site);
    }

    /** Whether the player says their house holds this. Nothing is declared by default. */
    public boolean declares(HouseItem item) {
        return houseDeclared.contains(item.getCapability());
    }

    /**
     * A herb ranking with the favourite moved to the top and the rest left in order. The
     * ranking itself when there is no favourite.
     */
    public List<Crop> favouriteFirst(List<Crop> ranking) {
        if (favouriteHerb == null || !ranking.contains(favouriteHerb)) {
            return ranking;
        }
        List<Crop> ranked = new ArrayList<>(ranking);
        ranked.removeIf(herb -> herb == favouriteHerb);
        ranked.add(0, favouriteHerb);
        return List.copyOf(ranked);
    }

    /** Where this run sorts among runs due together; lower goes first, starting at 1. */
    public int priorityOf(RunId run) {
        return Preset.DEFAULT.priorityOf(run, herbRunFirst);
    }

    /**
     * The crops ticked one by one for this family, best first: the herbs, or a tree
     * family's crops. Empty for a family whose crops are plain switches or have no tick.
     */
    public static List<Crop> familyChoices(PatchType type) {
        if (type == PatchType.HERB) {
            return HERB_CHOICES;
        }
        return TREE_CHOICES.getOrDefault(type, List.of());
    }

    /**
     * Whether this family is one the player has switched off entirely: a family ticked crop
     * by crop with nothing ticked. Other patch types (bushes, flowers) answer
     * false: {@link #allows} covers them.
     */
    public boolean isFamilyOff(PatchType type) {
        List<Crop> choices = familyChoices(type);
        return !choices.isEmpty() && Collections.disjoint(enabled, choices);
    }

    /**
     * Whether this crop is one the player still wants planted. A crop with no tick answers
     * true: the player has no say in it.
     */
    public boolean allows(Crop crop) {
        return !hasAswitch(crop) || enabled.contains(crop);
    }

    /** Whether the player is offered a say in this crop at all. */
    public static boolean hasAswitch(Crop crop) {
        return FarmingKeys.CROPS.containsKey(crop);
    }

    /**
     * The ticked crops of a ranking, in the ranking's order. A crop with no tick is never in
     * the answer, so a ranking that names one cannot plant it.
     *
     * @param ranking every crop a patch could take, best first
     */
    public List<Crop> ticked(List<Crop> ranking) {
        List<Crop> wanted = new ArrayList<>(ranking);
        wanted.retainAll(enabled);
        return List.copyOf(wanted);
    }
}
