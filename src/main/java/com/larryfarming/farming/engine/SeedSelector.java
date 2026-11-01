package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.Skill;
import java.util.*;

/**
 * Picks the seeds a herb run plants, from the tier list and what the player owns.
 *
 * <p>The rotation is a pair of pointers walking inward from the ends of the tier
 * list's ticked herbs. Each run takes two seeds off the good end for the run's best patches
 * and five off the bad end for the rest. When either stack runs out the pointer steps
 * inward, and when the two meet the whole thing restarts from the outside.
 *
 * <p>This class says only how many patches take each end. {@code PatchPriority} scores
 * which patches earn the better seed per player at planning time, so both ends come back
 * best seed first.
 *
 * <p>Ticks are hard. An unticked herb is never allocated, even when the result is an empty
 * allocation.
 *
 * <p>What is allocated is <em>seeds</em> (see {@link SeedAllocation}). Both ends draw from
 * the same stock, so a seed spent on a good patch is not also offered to a filler patch.
 *
 * <p>Nothing is remembered between runs. The best seed the player still holds marks where
 * the good pointer got to, so reading the pointers off current stock gives the same answer
 * as tracking them, and cannot drift.
 *
 * <p>Torstol tops the list but starts unticked, saved for hard contracts, so both ends
 * skip it unless the player ticks it.
 */
public final class SeedSelector {

    /** The Farming level that opens the Farming Guild, and the seed vault inside it. */
    static final int FARMING_GUILD_LEVEL = 45;

    private SeedSelector() {
    }

    /**
     * The rotation's seeds, counted once so several runs can draw on them in turn.
     *
     * <p>A trip can walk several families that draw on the herb rotation. Passing this one
     * mutable stock through each stops the second family spending the first family's seeds.
     * Built from the preferences rather than the run, because the window is the player's.
     *
     * <p>Package-private with its overload of {@code select}: the shared stock belongs to one
     * trip's planning.
     */
    static Stock rotationStock(SeedPolicy policy, FarmingPreferences prefs, PlayerState state) {
        return stockOf(state, herbWindow(policy, prefs));
    }

    /**
     * Both ends of the rotation for one run, drawn against a stock the caller owns.
     *
     * <p>Together because the ends compete for the same seeds: allocating them apart would
     * hand the same stack to both.
     *
     * <p><b>Carried seeds are the player's choice only when they cover the run.</b> A player
     * who brings enough herb seeds for every patch to fill has chosen what to plant, and
     * being told to fetch others ignores that choice. Then only the carried seeds are
     * allocated, the best of them to the good end.
     *
     * <p>Short of that, the bag says nothing about intent. Seeds are withdrawn one click at a
     * time and each click is a new reading, so a player halfway through taking out what the
     * plan asked for carries a partial handful, and so does one with leftovers in a seed box.
     * Letting that handful lead the good end would flip the plan under them mid-withdrawal.
     * So the good end ranks over all the stock as it would with nothing carried, and only the
     * filler end draws the carried seeds first.
     *
     * <p>Ticks stay a hard rule: an unticked seed is outside the window however it got into
     * the bag.
     *
     * @param stock <b>mutated</b> as seeds are taken so several runs share one seed vault.
     *     Must come from {@link #rotationStock} on the same policy and preferences; a stock
     *     built from another pair reads as empty
     */
    static SeedPlan select(SeedPolicy policy, FarmingPreferences prefs, PlayerState state, int goodPatches, int badPatches, Stock stock) {
        List<Crop> window = herbWindow(policy, prefs);
        boolean confident = seedsSeen(state);
        List<Map<Crop, Integer>> sources = stock.carriedCovers(goodPatches + badPatches) ? stock.carriedOnly() : stock.carriedFirst();

        // The good end draws first, so the best seeds go where the yield bonus applies. It
        // walks crop by crop over every source, so a carried seed never outranks a better
        // one in the bank.
        List<Crop> good = new ArrayList<>();
        for (int i = 0; i < window.size() && good.size() < goodPatches; i++) {
            for (Map<Crop, Integer> source : sources) {
                take(window.get(i), source, good, goodPatches);
            }
        }

        // The filler end works up from the floor toward the good end, on what is left, and
        // empties one source before the next so the carried seeds go in first.
        List<Crop> bad = new ArrayList<>();
        for (Map<Crop, Integer> source : sources) {
            for (int i = window.size() - 1; i >= 0 && bad.size() < badPatches; i--) {
                take(window.get(i), source, bad, badPatches);
            }
        }

        // Handed over best-first: patches are dealt seeds best patch first, so the best
        // seed of each end must lead. A carried filler seed drawn ahead of a better banked
        // one would otherwise lead out of rank.
        Comparator<Crop> byRank = Comparator.comparingInt(window::indexOf);
        good.sort(byRank);
        bad.sort(byRank);

        return new SeedPlan(new SeedAllocation(List.copyOf(good), goodPatches, confident),
                new SeedAllocation(List.copyOf(bad), badPatches, confident));
    }

    /**
     * The herb seeds the player carries but has not ticked, best first, so the advice can
     * say why they are not being planted.
     *
     * <p>Only crops the player could otherwise plant: one above their Farming level is not a
     * missing tick. Nor is a herb the preset keeps for contracts (torstol): carried, it is
     * most likely bound for one, and the note would push against the preset's own advice.
     */
    public static List<Crop> carriedButUnticked(SeedPolicy policy, FarmingPreferences prefs, PlayerState state) {
        List<Crop> unticked = new ArrayList<>();
        for (Crop crop : policy.getHerbTierList()) {
            if (!FarmingPreferences.HERB_CHOICES.contains(crop) || prefs.allows(crop)
                    || Preset.DEFAULT.getHerbsKeptForContracts().contains(crop)) {
                continue;
            }
            CropData data = CropData.forCrop(crop);
            // plantableCount reads zero for a crop above the player's level.
            if (data != null && plantableCount(state, crop) > 0 && formsHeld(state, data, true) > 0) {
                unticked.add(crop);
            }
        }
        return List.copyOf(unticked);
    }

    /**
     * The seeds a run plants when it names its own seed patch type rather than drawing on
     * the herb rotation — every tree run.
     *
     * <p>The ladder ranks the type's ticked crops. A player out of mahogany plants teak, so the
     * walk continues down the {@linkplain #plantingLadder ladder} before giving up. Stock
     * is drawn down as it goes, because two patches cannot both have the last sapling.
     *
     * <p>Everything goes on the filler end: tree runs have no yield-bonus patches to hold
     * a better seed back for.
     *
     * <p>Some crops are kept off the ladder because a diary keeps them for one patch (see
     * {@link DiaryPlantings}). That patch takes its crop outside this plan.
     *
     * @param patches how many patches draw on the ladder, so without the claimed patch
     */
    static SeedPlan forPreference(FarmingPreferences prefs, PatchType seedPatchType, PlayerState state, int patches, Set<Crop> withheld) {
        List<Crop> ladder = new ArrayList<>(plantingLadder(prefs, seedPatchType));
        ladder.removeAll(withheld);
        Map<Crop, Integer> stock = new EnumMap<>(Crop.class);
        List<Crop> chosen = new ArrayList<>();
        for (Crop crop : ladder) {
            stock.put(crop, plantableCount(state, crop));
            take(crop, stock, chosen, patches);
        }
        boolean seen = seedsSeen(state);
        return new SeedPlan(new SeedAllocation(List.of(), 0, seen), new SeedAllocation(List.copyOf(chosen), patches, seen));
    }

    /**
     * Everything a run will actually accept: the ticked crops of the patch type it names,
     * best first, or nothing when it names none.
     *
     * <p>The ticks decide the crops, so a player out of mahogany plants teak when teak is
     * ticked. A single-crop run (calquat) has one crop in its type and follows that crop's
     * tick. A diary can keep one crop for one patch; {@link DiaryPlantings#ladderAt} narrows
     * this list per stop.
     */
    public static List<Crop> plantingLadder(FarmingPreferences prefs, PatchType seedPatchType) {
        if (seedPatchType == null) {
            return List.of();
        }
        return prefs.ticked(CropData.plantableIn(seedPatchType));
    }

    /**
     * Everything a stop on this run could actually be filled from, best first.
     *
     * <p>The one answer to both "what will go in this patch" and "could anything go in it",
     * so the scheduler never counts seeds the walkthrough would not allocate.
     *
     * <p>Herbs use the rotation's ticked herbs in the tier list's order, not
     * {@link CropData#plantableIn}: the rotation ranks by experience and {@code plantableIn}
     * by level.
     */
    public static List<Crop> plantableFrom(SeedPolicy policy, FarmingPreferences prefs, PatchType seedPatchType, PatchType type) {
        if (seedPatchType != null) {
            return plantingLadder(prefs, seedPatchType);
        }
        if (type == PatchType.HERB) {
            return herbWindow(policy, prefs);
        }
        // Everything else the rotation touches — allotments, flowers — takes no tick, so
        // the patch itself decides what could go in it.
        return CropData.plantableIn(type);
    }

    /**
     * The ticked herbs of the tier list, best first, with the player's favourite herb at the
     * top. The good end therefore plants the favourite first, and the filler end reaches it
     * last.
     *
     * <p>{@link #rotationStock}, {@link #select} and {@link #plantableFrom} must all read
     * this same list, or the allocation draws seeds the stock never counted.
     */
    private static List<Crop> herbWindow(SeedPolicy policy, FarmingPreferences prefs) {
        return prefs.ticked(prefs.favouriteFirst(policy.getHerbTierList()));
    }

    /**
     * Whether the player is known to hold nothing any of these crops could be planted from.
     *
     * <p>False before any seed container has been seen, as for {@link #definitelyOutOf}.
     *
     * @param needed how many one patch swallows, so an allotment's three is not read as
     *               three separate plantings' worth
     */
    public static boolean definitelyOutOfAll(List<Crop> crops, PlayerState state, int needed) {
        if (!seedsSeen(state)) {
            return false;
        }
        for (Crop crop : crops) {
            if (plantableCount(state, crop) >= needed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the player is known to have nothing to plant here.
     *
     * <p>False before any seed container has been seen: a stack we have never been shown is
     * not an empty one, and dropping a stop on a guess is the expensive mistake.
     *
     * @param needed how many the patch swallows, since an allotment wants three
     */
    public static boolean definitelyOutOf(Crop crop, PlayerState state, int needed) {
        return definitelyOutOfAll(List.of(crop), state, needed);
    }

    /**
     * How many patches of this tree family the player can fill: every form of every ticked
     * crop on the family's ladder they own and have the level for. -1 until every store
     * that could hold saplings has been seen, so a short count is never a guess: the bank,
     * and the seed vault once the player can enter the Farming Guild at
     * {@value #FARMING_GUILD_LEVEL}.
     */
    static int plantableAcross(FarmingPreferences prefs, PatchType seedPatchType, PlayerState state) {
        if (state == null || state.getBankLastSeen() == null || state.getSeedVaultLastSeen() == null
                        && state.getSkillLevel(Skill.FARMING) >= FARMING_GUILD_LEVEL) {
            return -1;
        }
        int owned = 0;
        for (Crop crop : plantingLadder(prefs, seedPatchType)) {
            owned += plantableCount(state, crop);
        }
        return owned;
    }

    /** Whether the player owns at least one of this crop's planting forms and can plant it. */
    static boolean holdsAny(Crop crop, PlayerState state) {
        return knownToHold(crop, state, 1);
    }

    /**
     * Whether the stores Larry has seen hold enough of this crop to fill one patch, at a
     * Farming level that plants it. Counted as the allocator counts, so the scheduler never
     * calls a patch plantable that the walkthrough would leave empty.
     *
     * @param needed the seeds one patch takes
     */
    static boolean knownToHold(Crop crop, PlayerState state, int needed) {
        return plantableCount(state, crop) >= needed;
    }

    /** How many of this crop the player could put in the ground right now. */
    private static int plantableCount(PlayerState state, Crop crop) {
        CropData data = CropData.forCrop(crop);
        if (data == null || state == null || state.getSkillLevel(Skill.FARMING) < data.getFarmingLevel()) {
            return 0;
        }
        return formsHeld(state, data, false);
    }

    /**
     * The planting stock for one crop, owned or only carried, counting every form it comes
     * in.
     *
     * <p>A tree patch takes a sapling, which players usually prepare at the bank from a
     * seed, through seedling stages in between. Every form fills a patch, so the forms are
     * summed.
     */
    private static int formsHeld(PlayerState state, CropData data, boolean carriedOnly) {
        int stock = 0;
        for (int itemId : data.getPlantingForms()) {
            stock += carriedOnly ? ItemCounts.countCarried(state, itemId, null) : ItemCounts.countOwned(state, itemId, null);
        }
        return stock;
    }

    /** Moves as many seeds of this crop into the allocation as it can still use. */
    private static void take(Crop crop, Map<Crop, Integer> stock, List<Crop> into, int wanted) {
        Integer available = stock.get(crop);
        if (available == null || available <= 0) {
            return;
        }
        int left = available;
        while (left > 0 && into.size() < wanted) {
            into.add(crop);
            left--;
        }
        stock.put(crop, left);
    }

    /** How many of each tier the player could plant right now (see {@link Stock}). */
    private static Stock stockOf(PlayerState state, List<Crop> tiers) {
        Stock stock = new Stock();
        for (Crop crop : tiers) {
            int held = plantableCount(state, crop);
            CropData data = CropData.forCrop(crop);
            // Capped at what is owned: the two counts read different containers, and a
            // carried seed is never one the player does not own.
            int carried = held == 0 || data == null ? 0 : Math.min(held, formsHeld(state, data, true));
            stock.carried.put(crop, carried);
            stock.elsewhere.put(crop, held - carried);
        }
        return stock;
    }

    /**
     * How many of each tier the player could plant right now, split into the seeds they
     * carry and the rest they own.
     *
     * <p>Zero covers above their Farming level and absent alike: the allocation walks past
     * both the same way.
     *
     * <p>Mutable, and shared by every family one trip plans, so a seed one family takes is
     * gone for the next whichever side of the split it came from.
     */
    static final class Stock {
        private final Map<Crop, Integer> carried = new EnumMap<>(Crop.class);
        private final Map<Crop, Integer> elsewhere = new EnumMap<>(Crop.class);

        private Stock() {
        }

        /** The two halves in the order the rotation draws from them. */
        private List<Map<Crop, Integer>> carriedFirst() {
            return List.of(carried, elsewhere);
        }

        /** The carried half alone, for a bag that covers the run. */
        private List<Map<Crop, Integer>> carriedOnly() {
            return List.of(carried);
        }

        /**
         * Whether the carried seeds left could fill this many patches on their own. Zero
         * patches is covered by anything, which changes nothing: there is nothing to fill.
         */
        private boolean carriedCovers(int patches) {
            int total = 0;
            for (int held : carried.values()) {
                total += held;
            }
            return total >= patches;
        }

        /**
         * Takes one seed of this crop out of the stock, a carried one first, or nothing
         * when none is left. A seed planted outside the rotation is most likely the one in
         * the bag.
         */
        void spendOne(Crop crop) {
            for (Map<Crop, Integer> source : carriedFirst()) {
                Integer held = source.get(crop);
                if (held != null && held > 0) {
                    source.put(crop, held - 1);
                    return;
                }
            }
        }
    }

    /**
     * Whether we have been shown where seeds live. An account keeps its seeds in one of
     * these three, so having heard from none of them means every "you have none" would be
     * guesswork.
     *
     * <p>The seed box is the weak source. The vault and the bank are read while open, so
     * their stamps mean the contents were counted. The box's stamp comes from a chat line
     * about seeds going in or out, because the game never reports the box itself, so it is
     * a running tally rather than a snapshot. It still counts, so a player who keeps seeds
     * only in the box is not told to open a vault.
     */
    private static boolean seedsSeen(PlayerState state) {
        return state != null && (state.getSeedVaultLastSeen() != null || state.getBankLastSeen() != null
                        || state.getSeedBoxLastSeen() != null);
    }
}
