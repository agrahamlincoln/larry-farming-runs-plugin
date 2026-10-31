package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.Crop;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a crop's produce does in a compost bin.
 *
 * <p>A bin yields supercompost only if every one of its fifteen items was supercompostable,
 * so a part-filled supercompost bin and a part-filled ordinary one want different items.
 *
 * <p>Derived rather than listed, as the game derives it: produce is supercompostable if and
 * only if growing it needs 47 Farming or more, with the exceptions named below. A newly
 * added crop therefore gets a sensible answer. Every crop's answer has been checked against
 * the wiki's table, not just this rule.
 */
public final class Compostables {

    private Compostables() {
    }

    /** What fifteen of this produce would make. */
    public enum Compostability {
        /** Not accepted by a bin at all. */
        NONE,
        /** Makes ordinary compost, and drags a supercompostable bin down to ordinary. */
        COMPOST,
        /** Makes supercompost, so long as nothing ordinary went in with it. */
        SUPERCOMPOST
    }

    /**
     * Whether this produce is worth putting in a bin, as opposed to merely accepted by one.
     *
     * <p>Separate from {@link Compostability}: a bin accepts grimy avantoe, but nobody who
     * grew avantoe wants it turned into compost.
     */
    enum FillValue {
        /** Grown for something better than compost. Never suggested as a fill item. */
        KEEP,
        /** Bulk produce with no use worth more than the bucket it becomes. */
        FILLER
    }

    private static final int SUPERCOMPOSTABLE_FROM = 47;

    /**
     * Produce a bin may be offered.
     *
     * <p>An allowlist, so an unlisted crop defaults to {@link FillValue#KEEP}. A missing entry
     * only skips a workable suggestion; a wrong entry tells a player to destroy something
     * they grew on purpose.
     *
     * <p>Allotment produce comes first because harvestfilling (harvesting from the
     * neighbouring allotment straight into the bin) is the preferred method, and
     * every bin sits at an allotment farm. The rest is bulk fruit carried from the bank; its
     * grade is filtered by {@link #keepsAbinSuper}, not here.
     *
     * <p>Herbs, Herblore secondaries and other produce grown for its own use are absent.
     * Tomatoes are absent because fifteen of them make rotten tomatoes, not compost.
     */
    private static final Set<Crop> GOOD_FILL = EnumSet.of(
            // Allotments — harvestfilling's own material.
            Crop.WATERMELON, Crop.STRAWBERRY,
            // Bulk fruit, carried from the bank.
            Crop.PINEAPPLE, Crop.PALM, Crop.PAPAYA,
            Crop.APPLE, Crop.BANANA, Crop.ORANGE, Crop.CURRY,
            // Bush produce grown for the experience, the wiki's stock bin filler.
            Crop.JANGERBERRIES, Crop.POISON_IVY);

    /**
     * Crops whose produce a bin will not take.
     *
     * <p>Bins refuse logs of every kind, which covers every timber tree here, including the
     * redwood and mahogany whose levels would otherwise read as supercompostable. The wiki
     * names cactus spines and nightshade as uncompostable. An anima plant yields nothing.
     *
     * <p>Hespori seeds and coral count as uncompostable. The wiki's Compost Bin table does not
     * list them, and offering a bin something it refuses wastes a trip.
     */
    private static final Set<Crop> NOT_ACCEPTED = EnumSet.of(
            Crop.OAK, Crop.WILLOW, Crop.MAPLE, Crop.YEW, Crop.MAGIC,
            Crop.TEAK, Crop.MAHOGANY, Crop.REDWOOD,
            Crop.CACTUS, Crop.BELLADONNA,
            Crop.ATTAS, Crop.IASOR, Crop.KRONOS,
            Crop.ELKHORN_CORAL, Crop.PILLAR_CORAL, Crop.UMBRAL_CORAL,
            Crop.HESPORI);

    /**
     * Produce that needs 47 Farming or more and still only makes ordinary compost.
     *
     * <p>The wiki also names the cotton boll, which the catalogue does not grow.
     */
    private static final Set<Crop> ORDINARY_DESPITE_THE_LEVEL = EnumSet.of(Crop.POTATO_CACTUS);

    /**
     * Produce that is supercompostable below 47 Farming.
     *
     * <p>The wiki also names tree roots and white tree fruit, which are not crops here.
     */
    private static final Set<Crop> SUPER_DESPITE_THE_LEVEL = EnumSet.of(Crop.TOADFLAX);

    /**
     * What this crop's produce makes in a bin.
     *
     * @param crop the crop grown, not a derived product (watermelon slices are compostable,
     *             pineapple rings are not)
     * @return {@link Compostability#NONE} for null or anything the catalogue does not grow
     */
    public static Compostability of(Crop crop) {
        if (crop == null || NOT_ACCEPTED.contains(crop)) {
            return Compostability.NONE;
        }
        if (SUPER_DESPITE_THE_LEVEL.contains(crop)) {
            return Compostability.SUPERCOMPOST;
        }
        if (ORDINARY_DESPITE_THE_LEVEL.contains(crop)) {
            return Compostability.COMPOST;
        }
        CropData data = CropData.forCrop(crop);
        if (data == null) {
            return Compostability.NONE;
        }
        return data.getFarmingLevel() >= SUPERCOMPOSTABLE_FROM
                ? Compostability.SUPERCOMPOST
                : Compostability.COMPOST;
    }

    /**
     * Whether this produce would finish a supercompost bin without spoiling it.
     */
    public static boolean keepsAbinSuper(Crop crop) {
        return of(crop) == Compostability.SUPERCOMPOST;
    }

    /**
     * Which side of the keep/fill line this produce falls on. Package-private;
     * outside callers use {@link #worthBinning}.
     *
     * @param crop the crop grown, or null
     * @return {@link FillValue#KEEP} for anything not on {@link #GOOD_FILL}, including null
     */
    static FillValue fillValueOf(Crop crop) {
        return crop != null && GOOD_FILL.contains(crop) ? FillValue.FILLER : FillValue.KEEP;
    }

    /**
     * Whether a bin should be offered this produce at all: accepted <em>and</em> not worth
     * more than the bucket.
     *
     * <p>The caller still applies the bin's target grade with {@link #keepsAbinSuper}.
     */
    public static boolean worthBinning(Crop crop) {
        return of(crop) != Compostability.NONE && fillValueOf(crop) == FillValue.FILLER;
    }
}
