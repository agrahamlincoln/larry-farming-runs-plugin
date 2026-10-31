package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.Builder;
import lombok.EqualsAndHashCode;

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
 * run waits for every patch. Others set the compost stock the bins are worked between.
 */
@EqualsAndHashCode
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
     * Every crop of each tree family, best first, which for trees is level order. Each list
     * must match {@code CropData.plantableIn}.
     */
    public static final Map<PatchType, List<Crop>> TREE_CHOICES = treeChoices();

    private static Map<PatchType, List<Crop>> treeChoices() {
        Map<PatchType, List<Crop>> families = new EnumMap<>(PatchType.class);
        families.put(PatchType.TREE, List.of(
                Crop.MAGIC, Crop.YEW, Crop.MAPLE, Crop.WILLOW, Crop.OAK));
        families.put(PatchType.FRUIT_TREE, List.of(
                Crop.DRAGONFRUIT, Crop.PALM, Crop.PAPAYA, Crop.PINEAPPLE, Crop.CURRY,
                Crop.ORANGE, Crop.BANANA, Crop.APPLE));
        families.put(PatchType.HARDWOOD_TREE, List.of(Crop.MAHOGANY, Crop.TEAK));
        return Collections.unmodifiableMap(families);
    }

    /**
     * Every crop the player switches on or off outright: each crop a named patch may grow
     * (the default crop and the alternatives {@code FarmingCatalogue.cropChoices} offers
     * there), and the crops that are a whole run on their own.
     *
     * <p>Herbs and trees are ticked the same way but are not in this set: each grows in
     * every patch of its family, picked from a ranking.
     */
    private static final Set<Crop> TOGGLEABLE = Collections.unmodifiableSet(EnumSet.of(
            Crop.SNAPE_GRASS, Crop.WATERMELON, Crop.WHITE_LILY, Crop.LIMPWURT,
            Crop.POTATO_CACTUS, Crop.CACTUS, Crop.WHITEBERRIES, Crop.POISON_IVY,
            Crop.CALQUAT, Crop.SEAWEED, Crop.HESPORI, Crop.MUSHROOM, Crop.BELLADONNA,
            Crop.ATTAS, Crop.IASOR, Crop.KRONOS,
            Crop.ELKHORN_CORAL, Crop.PILLAR_CORAL, Crop.UMBRAL_CORAL));

    /** The switchable crops that start off, because the preset does not switch them on. */
    private static final Set<Crop> OFF_BY_DEFAULT = offByDefault(Preset.DEFAULT);

    private static Set<Crop> offByDefault(Preset preset) {
        EnumSet<Crop> off = EnumSet.copyOf(TOGGLEABLE);
        off.removeAll(preset.getSwitchedOn());
        return Collections.unmodifiableSet(off);
    }

    private final Set<Crop> enabled;

    /**
     * Whether a fruit tree is worth stopping at purely to pick it. Off by default: a living
     * fruit tree keeps its fruit until the next visit, and the fruit alone does not repay
     * the trip. Replanting is offered regardless of this setting.
     */
    private final boolean picksFruitInPassing;

    /**
     * Whether compost bins are part of the route. Off removes the whole errand: the stops,
     * the fill items, the ash and the dose.
     *
     * <p>A field rather than a tick because a bin grows no crop, so {@link #allows(Crop)}
     * has nothing to ask about.
     */
    private final boolean worksCompostBins;

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
     * Whether the coral run waits for {@link Preset#CORAL_LEVEL} Farming and a living Attas
     * plant.
     */
    private final boolean coralWaitsForAttas;

    /**
     * Whether patches are treated with Fertile Soil cast with volcanic ash, which needs the
     * Ash covered tome read, instead of carried ultracompost.
     */
    private final boolean compostsWithFertileSoil;

    /**
     * The compost stock, in patches it can treat, below which the bins are worked again.
     * Always a step between {@link #COMPOST_STEP} and {@link #COMPOST_MOST} less one step.
     */
    private final int makeCompostBelow;

    /**
     * The compost stock at which the bins are left alone. Always a step above
     * {@link #makeCompostBelow}, and at most {@link #COMPOST_MOST}.
     */
    private final int stopCompostAt;

    /** The compost thresholds move in steps of this many patches, starting at one step. */
    public static final int COMPOST_STEP = 100;

    /** The highest compost threshold: the tool leprechaun holds 1,000 of each compost. */
    public static final int COMPOST_MOST = 1000;

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
    private FarmingPreferences(Set<Crop> enabled, boolean picksFruitInPassing,
            boolean worksCompostBins, Set<TravelMethod> travelOff, Set<String> sitesOff,
            Set<String> sitesShut, Set<Capability> houseDeclared, Crop favouriteHerb,
            boolean plantsBestSeedsEverywhere, boolean herbRunFirst,
            boolean waitsForEveryPatch, boolean fruitTreesWaitForSaplings,
            boolean replantsGrownTrees, boolean herbRunsWaitForLevel, boolean coralWaitsForAttas,
            boolean compostsWithFertileSoil, int makeCompostBelow, int stopCompostAt) {
        EnumSet<Crop> enabledCopy = EnumSet.noneOf(Crop.class);
        if (enabled != null) {
            enabledCopy.addAll(enabled);
        }
        this.enabled = Collections.unmodifiableSet(enabledCopy);
        this.picksFruitInPassing = picksFruitInPassing;
        this.worksCompostBins = worksCompostBins;
        EnumSet<TravelMethod> offCopy = EnumSet.noneOf(TravelMethod.class);
        if (travelOff != null) {
            offCopy.addAll(travelOff);
        }
        this.travelOff = Collections.unmodifiableSet(offCopy);
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
                && enabledCopy.contains(favouriteHerb) ? favouriteHerb : null;
        this.plantsBestSeedsEverywhere = plantsBestSeedsEverywhere;
        this.herbRunFirst = herbRunFirst;
        this.waitsForEveryPatch = waitsForEveryPatch;
        this.fruitTreesWaitForSaplings = fruitTreesWaitForSaplings;
        this.replantsGrownTrees = replantsGrownTrees;
        this.herbRunsWaitForLevel = herbRunsWaitForLevel;
        this.coralWaitsForAttas = coralWaitsForAttas;
        this.compostsWithFertileSoil = compostsWithFertileSoil;
        // The pair stays valid whatever was saved: the low threshold leaves room for one
        // step above it, and the high one is always above the low one.
        this.makeCompostBelow = toStep(makeCompostBelow, COMPOST_STEP,
                COMPOST_MOST - COMPOST_STEP);
        this.stopCompostAt = toStep(stopCompostAt, this.makeCompostBelow + COMPOST_STEP,
                COMPOST_MOST);
    }

    /** A threshold rounded down to a whole step and held between the two bounds. */
    private static int toStep(int value, int least, int most) {
        int stepped = value / COMPOST_STEP * COMPOST_STEP;
        return Math.max(least, Math.min(most, stepped));
    }

    private static Set<String> knownSites(Set<String> keys) {
        Set<String> copy = new TreeSet<>();
        if (keys != null) {
            for (String site : keys) {
                if (FarmingKeys.SITES.contains(site)) {
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

    /** Whether picking a fruit tree is on its own a reason to stop there. False by default. */
    public boolean picksFruitInPassing() {
        return picksFruitInPassing;
    }

    /** Whether compost bins are worked at all. True by default. */
    public boolean worksCompostBins() {
        return worksCompostBins;
    }

    /** Whether this travel method may be used. Every method is on by default. */
    public boolean uses(TravelMethod method) {
        return !travelOff.contains(method);
    }

    /** The travel methods switched off, never null. */
    public Set<TravelMethod> travelOff() {
        return travelOff;
    }

    /** Whether the runs visit the site with this key. Every site is visited by default. */
    public boolean visits(String siteKey) {
        return !sitesOff.contains(siteKey);
    }

    /** The keys of the sites dropped, never null. */
    public Set<String> sitesOff() {
        return sitesOff;
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
        return knownSites(shut).equals(sitesShut) ? this
                : toBuilder().sitesShut(shut).build();
    }

    /** The same preferences with one more site dropped. */
    public FarmingPreferences withoutSite(String siteKey) {
        Set<String> off = new TreeSet<>(sitesOff);
        off.add(siteKey);
        return toBuilder().sitesOff(off).build();
    }

    /** Whether the player says their house holds this. Nothing is declared by default. */
    public boolean declares(HouseItem item) {
        return houseDeclared.contains(item.getCapability());
    }

    /** The capabilities of every house item declared built, never null. */
    public Set<Capability> houseDeclared() {
        return houseDeclared;
    }

    /** The favourite herb, or null for none, as by default. */
    public Crop favouriteHerb() {
        return favouriteHerb;
    }

    /**
     * A herb ranking with the favourite moved to the top and the rest left in order. The
     * ranking itself when there is no favourite.
     */
    public List<Crop> favouriteFirst(List<Crop> ranking) {
        if (favouriteHerb == null || !ranking.contains(favouriteHerb)) {
            return ranking;
        }
        List<Crop> ranked = new ArrayList<>(ranking.size());
        ranked.add(favouriteHerb);
        for (Crop herb : ranking) {
            if (herb != favouriteHerb) {
                ranked.add(herb);
            }
        }
        return List.copyOf(ranked);
    }

    /**
     * Whether every herb patch takes the best seeds the player has, falling to the next
     * best as each runs out. False by default: only the yield-bonus patches do.
     */
    public boolean plantsBestSeedsEverywhere() {
        return plantsBestSeedsEverywhere;
    }

    /** Whether the herb run is lifted to the top of the run priority. False by default. */
    public boolean herbRunFirst() {
        return herbRunFirst;
    }

    /** Where this run sorts among runs due together; lower goes first, starting at 1. */
    public int priorityOf(RunId run) {
        return Preset.DEFAULT.priorityOf(run, herbRunFirst);
    }

    /**
     * Whether a run is due only once every patch on it needs a visit. False by default:
     * one patch that does is enough.
     */
    public boolean waitsForEveryPatch() {
        return waitsForEveryPatch;
    }

    /**
     * Whether the fruit tree run is due only once the player owns a sapling, or a seed to
     * make one, for every patch the run would plant. On by default.
     */
    public boolean fruitTreesWaitForSaplings() {
        return fruitTreesWaitForSaplings;
    }

    /**
     * Whether a grown, checked fruit tree is chopped down for a sapling at least as good,
     * its own kind included, so the new tree's health check pays again. Off, a grown tree
     * stays to bear fruit and is felled only for a better one. On by default.
     */
    public boolean replantsGrownTrees() {
        return replantsGrownTrees;
    }

    /**
     * Whether the herb run waits for {@link Preset#HERB_RUN_LEVEL} Farming, the level from
     * which the default route counts herb runs as worth doing. Off by default: below it they
     * are optional, not ruled out.
     */
    public boolean herbRunsWaitForLevel() {
        return herbRunsWaitForLevel;
    }

    /**
     * Whether the coral run waits for {@link Preset#CORAL_LEVEL} Farming and an Attas plant
     * growing in the anima patch, which the default route asks for before coral is worth
     * farming. On by default.
     */
    public boolean coralWaitsForAttas() {
        return coralWaitsForAttas;
    }

    /**
     * Whether Larry packs Fertile Soil's runes and two volcanic ash a patch in place of
     * ultracompost, on the Lunar book. Off by default: the player says they have read the
     * Ash covered tome by switching it on, since the game does not show it.
     */
    public boolean compostsWithFertileSoil() {
        return compostsWithFertileSoil;
    }

    /**
     * The compost stock, in patches it can treat, below which Larry works the bins again.
     * 300 by default.
     */
    public int makeCompostBelow() {
        return makeCompostBelow;
    }

    /** The compost stock at which Larry leaves the bins alone. 800 by default. */
    public int stopCompostAt() {
        return stopCompostAt;
    }

    /** The same preferences with one more travel method switched off. */
    public FarmingPreferences without(TravelMethod method) {
        EnumSet<TravelMethod> off = EnumSet.of(method);
        off.addAll(travelOff);
        return toBuilder().travelOff(off).build();
    }

    /** Every crop offered as a plain on/off, in no particular order. */
    public static Set<Crop> toggleableCrops() {
        return TOGGLEABLE;
    }

    /** The herbs an untouched install plants — irit and everything ranked above it. */
    public static Set<Crop> defaultHerbs() {
        return Preset.DEFAULT.herbsOn();
    }

    /** The switchable crops an untouched install leaves alone. */
    public static Set<Crop> offByDefault() {
        return OFF_BY_DEFAULT;
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
     * by crop with nothing ticked. Other patch types (bushes, flowers, compost bins) answer
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
        List<Crop> wanted = new ArrayList<>(ranking.size());
        for (Crop crop : ranking) {
            if (enabled.contains(crop)) {
                wanted.add(crop);
            }
        }
        return List.copyOf(wanted);
    }
}
