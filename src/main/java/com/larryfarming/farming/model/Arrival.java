package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import lombok.Getter;

/**
 * One place on the trip: somewhere the player travels to, and every patch they work
 * before travelling again.
 *
 * <p>A trip is a sequence of arrivals, and everything the player does at one costs a single
 * journey however many patches, runs or crop families it spans. The catalogue decides the
 * grouping patch by patch; distance does not. Falador's farm and park tree are two arrivals,
 * because eighty-five tiles through the city is a journey.
 *
 * <p><b>Not a {@link Destination}.</b> A destination is where a teleport puts you; an
 * arrival is a stop on the itinerary. Falador's park tree is reached by a ring of wealth, a
 * respawn teleport or the Falador spell, and all three are the same stop.
 *
 * <p><b>Not a map region either.</b> Catherby's herb cluster and fruit tree are two regions
 * and one arrival: no teleport lands nearer the fruit tree than Catherby town. Ardougne's
 * farm and Monastery bushes share one cloak but sit 150 tiles apart, so they are two
 * arrivals.
 *
 * <p>Each arrival's name and bank grade come from the arrivals table.
 */
@Getter
public enum Arrival {

    // --- Kourend --------------------------------------------------------------
    /** Contract, hespori and every guild patch: one walled garden. */
    FARMING_GUILD,
    HOSIDIUS,

    // --- Varlamore ------------------------------------------------------------
    /**
     * The patches are a run north of the Hunter Guild. The Civitas illa Fortis teleport
     * banks on the way, fifteen tiles of detour; the Hunter cape detours thirty-six tiles
     * to the guild's chest, and a quetzal to the guild eighty-seven. Graded SHORT_WALK, on
     * the teleport's fifteen.
     */
    ORTUS_FARM,
    LOCUS_OASIS,
    KASTORI,
    NEMUS_RETREAT,
    /** The belladonna patch at the east end of town. Graded NONE. */
    AUBURNVALE,

    // --- Tirannwn -------------------------------------------------------------
    /**
     * The bank is five tiles from the patch in a straight line and thirty-one walked, because
     * a building stands between them. It is not on the walk in either: the crystal lands
     * seventeen tiles from the patch and twenty-seven from the bank. Graded FAR.
     */
    LLETYA,

    // --- Kandarin and the gnome lands ----------------------------------------
    /** Tree and fruit tree both: every arrival here falls between the two patches. */
    GNOME_STRONGHOLD,
    TREE_GNOME_VILLAGE,
    /**
     * Seventy tiles to the nearest bank: ninety-eight tiles of detour by fairy ring, a
     * hundred and forty-two by the cloak.
     */
    ARDOUGNE_MONASTERY,
    ARDOUGNE_FARM,
    /** Herb cluster, two allotments, flowers, and the fruit tree east of them. */
    CATHERBY,

    // --- Karamja --------------------------------------------------------------
    BRIMHAVEN,
    TAI_BWO_WANNAI,

    // --- Asgarnia -------------------------------------------------------------
    TAVERLEY,
    /**
     * Thirty-seven tiles to Falador east bank walked, not the sixteen of the straight line:
     * the park is fenced and the bank sits behind the south-east wall. The rungs differ
     * widely: a skills necklace passes the bank on the way out of the Mining Guild, while
     * the ring of wealth makes it a fifty-tile round trip.
     */
    FALADOR_PARK,
    /** Eighty-five tiles from the park through the city, and never the same stop. */
    FALADOR_FARM,

    // --- Misthalin ------------------------------------------------------------
    /**
     * The belladonna patch inside the manor's fence. The nearest bank, Draynor Village's, is
     * about 110 tiles from the teleport landing, past the ninety that still grades, so it is
     * graded NONE.
     */
    DRAYNOR_MANOR,
    LUMBRIDGE,
    VARROCK_PARK,
    AL_KHARID,

    // --- Morytania ------------------------------------------------------------
    /**
     * The bittercap patch west of Canifis, which the default route leaves out and a player can
     * switch on. Five tiles from fairy ring CKS and about twenty-five from the Salve
     * graveyard teleport landing, so it is passed on the way into the region.
     *
     * <p>Graded FAR from the Canifis bank, so the leg claims nothing about the route.
     */
    MORT_MYRE,
    CANIFIS,
    HARMONY_ISLAND,

    // --- Fossil Island --------------------------------------------------------
    /**
     * Three hardwood patches inside an eighteen-by-nine box: one journey, one stop. The
     * Museum Camp bank chest is ten tiles off the route in from the barge, which grades
     * SHORT_WALK. The chest is a one-off Construction 21 repair and no varbit reports it, so it
     * is assumed built.
     */
    FOSSIL_ISLAND_HARDWOOD,
    /** Underwater, and reached from the surface separately. Never the hardwood stop. */
    FOSSIL_ISLAND_SEAWEED,

    // --- The far north --------------------------------------------------------
    WEISS,
    TROLL_STRONGHOLD,

    // --- The Great Conch ------------------------------------------------------
    GREAT_CONCH;

    static {
        Rows.TABLE.requireAll(values());
    }

    /**
     * How much a bank costs once the player is standing here.
     *
     * <p><b>Stated per arrival, which is coarser than the truth.</b> The real cost depends
     * on the leg, landing to patch: Ortus Farm banks fifteen tiles off the Civitas illa
     * Fortis teleport and over a hundred out on fairy ring AJP, and at the Tree Gnome
     * Stronghold the spirit tree and the slayer ring favour opposite patches. This records
     * the best a player at the arrival can do. Deciding where a trip breaks must ask the
     * leg, not read this as final.
     */
    public enum BankProximity {
        /** On the walk in, or close enough that stopping costs nothing measurable. */
        AT_ARRIVAL,
        /** A short detour — worth it to avoid a dedicated trip, not worth it otherwise. */
        SHORT_WALK,
        /** A real detour. Bank here only when there is nowhere better in reach. */
        FAR,
        /**
         * Nowhere a trip can break: either there is no bank, or reaching one costs more
         * than the trip it would save.
         */
        NONE;

        /**
         * What changing floor costs, in tiles, on top of the walk.
         *
         * <p>Unmeasured: walks count the ground floor only, so a bank up a staircase reads
         * as a ground-level walk. Lumbridge measures five tiles of detour and is two
         * flights and about twelve tiles each way in game. Twenty puts Lumbridge at the far
         * end of {@link #SHORT_WALK}.
         */
        public static final int FLOOR_CHANGE_TILES = 20;

        /**
         * Whether a bank at this grade is close enough that a trip passing here walks by it.
         *
         * <p>For a bank with no figure, where the grade is all there is. {@link #FAR} spans
         * twenty-five to ninety tiles, so it could mean either end. A measured route is
         * judged on its tiles instead; see {@code InventorySim.offeredHere}.
         */
        public boolean onTheWay() {
            return this == AT_ARRIVAL || this == SHORT_WALK;
        }

        /**
         * The grade a measured leg earns.
         *
         * <p>The lower cutoffs read as time at about 3.3 tiles a second: five tiles is
         * beneath noticing, twenty-five is eight seconds and worth it against a dedicated
         * trip. Both sit in gaps in the measured detours.
         *
         * <p>The top cutoff of ninety is a player's judgement, not a reading of the data:
         * Al Kharid's bank, 105 tiles from the cactus patch, is not a detour anyone wants
         * offered.
         *
         * @param tiles    extra tiles walked, as {@code TravelOption.getBankDetour()} gives
         * @param upstairs whether the bank is on another floor
         */
        public static BankProximity of(int tiles, boolean upstairs) {
            int cost = costInTiles(tiles, upstairs);
            if (cost <= 5) {
                return AT_ARRIVAL;
            }
            if (cost <= 25) {
                return SHORT_WALK;
            }
            return cost <= 90 ? FAR : NONE;
        }

        /** A bank detour's walked tiles with {@link #FLOOR_CHANGE_TILES} added when it changes floor. */
        public static int costInTiles(int tiles, boolean upstairs) {
            return upstairs ? tiles + FLOOR_CHANGE_TILES : tiles;
        }
    }

    /**
     * Player-facing, written to sit mid-sentence: "travel to the Farming Guild". A caller
     * wanting a heading capitalises and strips the article.
     */
    private final String displayName;
    private final BankProximity bank;

    Arrival() {
        Tables.Row row = Rows.TABLE.of(name());
        this.displayName = row.text(1);
        this.bank = row.constant(BankProximity.class, 2);
    }

    /**
     * The arrivals table, held apart so the constructors can read it; see
     * {@link Tables#byName}.
     */
    private static final class Rows {
        static final Tables.Named TABLE = Tables.byName("/com/larryfarming/arrivals.tsv", 3);
    }
}
