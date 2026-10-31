package com.larryfarming.farming.model;

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
 */
public enum Arrival {

    // --- Kourend --------------------------------------------------------------
    /** Contract, hespori, the big bin and every guild patch: one walled garden. */
    FARMING_GUILD("the Farming Guild", BankProximity.AT_ARRIVAL),
    HOSIDIUS("Hosidius", BankProximity.FAR),

    // --- Varlamore ------------------------------------------------------------
    /**
     * The patches are a run north of the Hunter Guild. The Civitas illa Fortis teleport
     * banks on the way, fifteen tiles of detour; the Hunter cape detours thirty-six tiles
     * to the guild's chest, and a quetzal to the guild eighty-seven.
     */
    ORTUS_FARM("Ortus Farm", BankProximity.SHORT_WALK),
    LOCUS_OASIS("the Locus Oasis", BankProximity.NONE),
    KASTORI("Kastori", BankProximity.NONE),
    NEMUS_RETREAT("Nemus Retreat", BankProximity.NONE),
    /** The belladonna patch at the east end of town. The town's bank is unmeasured. */
    AUBURNVALE("Auburnvale", BankProximity.NONE),

    // --- Tirannwn -------------------------------------------------------------
    /**
     * The bank is five tiles from the patch in a straight line and thirty-one walked, because
     * a building stands between them. It is not on the walk in either: the crystal lands
     * seventeen tiles from the patch and twenty-seven from the bank.
     */
    LLETYA("Lletya", BankProximity.FAR),
    /**
     * The bin and the allotments in the north-east of the city. Its north bank costs
     * twenty-two tiles of detour from the teleport crystal and twenty-seven from the spirit
     * tree.
     */
    PRIFDDINAS("Prifddinas", BankProximity.SHORT_WALK),

    // --- Kandarin and the gnome lands ----------------------------------------
    /** Tree and fruit tree both: every arrival here falls between the two patches. */
    GNOME_STRONGHOLD("the Tree Gnome Stronghold", BankProximity.SHORT_WALK),
    TREE_GNOME_VILLAGE("Tree Gnome Village", BankProximity.NONE),
    /**
     * Seventy tiles to the nearest bank: ninety-eight tiles of detour by fairy ring, a
     * hundred and forty-two by the cloak. Graded on the better of the two, as
     * {@link BankProximity} asks.
     */
    ARDOUGNE_MONASTERY("Ardougne Monastery", BankProximity.FAR),
    ARDOUGNE_FARM("Ardougne", BankProximity.FAR),
    /** Herb cluster, allotments, flowers, the bin, and the fruit tree east of them. */
    CATHERBY("Catherby", BankProximity.SHORT_WALK),

    // --- Karamja --------------------------------------------------------------
    BRIMHAVEN("Brimhaven", BankProximity.NONE),
    TAI_BWO_WANNAI("Tai Bwo Wannai", BankProximity.NONE),

    // --- Asgarnia -------------------------------------------------------------
    TAVERLEY("Taverley", BankProximity.NONE),
    /**
     * Thirty-seven tiles to Falador east bank walked, not the sixteen of the straight line:
     * the park is fenced and the bank sits behind the south-east wall. The rungs differ
     * widely: a skills necklace passes the bank on the way out of the Mining Guild, while
     * the ring of wealth makes it a fifty-tile round trip.
     */
    FALADOR_PARK("Falador Park", BankProximity.SHORT_WALK),
    /** Eighty-five tiles from the park through the city, and never the same stop. */
    FALADOR_FARM("Falador", BankProximity.FAR),

    // --- Misthalin ------------------------------------------------------------
    /**
     * The belladonna patch inside the manor's fence. The nearest bank, Draynor Village's, is
     * about 110 tiles from the teleport landing.
     */
    DRAYNOR_MANOR("Draynor Manor", BankProximity.NONE),
    LUMBRIDGE("Lumbridge", BankProximity.SHORT_WALK),
    VARROCK_PARK("Varrock", BankProximity.FAR),
    AL_KHARID("Al Kharid", BankProximity.FAR),

    // --- Morytania ------------------------------------------------------------
    /**
     * The bittercap patch west of Canifis, which the default route leaves out and a player can
     * switch on. Five tiles from fairy ring CKS and about twenty-five from the Salve
     * graveyard teleport landing, so it is passed on the way into the region.
     *
     * <p>Graded from the Canifis bank without a measured rung, so the leg claims nothing
     * about the route.
     */
    MORT_MYRE("the Mort Myre mushrooms", BankProximity.FAR),
    CANIFIS("Canifis", BankProximity.FAR),
    HARMONY_ISLAND("Harmony Island", BankProximity.NONE),

    // --- Fossil Island --------------------------------------------------------
    /**
     * Three hardwood patches inside an eighteen-by-nine box: one journey, one stop. The
     * Museum Camp bank chest is ten tiles off the route in from the barge. The chest is a
     * one-off Construction 21 repair and no varbit reports it, so it is assumed built.
     */
    FOSSIL_ISLAND_HARDWOOD("Fossil Island", BankProximity.SHORT_WALK),
    /** Underwater, and reached from the surface separately. Never the hardwood stop. */
    FOSSIL_ISLAND_SEAWEED("the Fossil Island seaweed", BankProximity.FAR),

    // --- The far north --------------------------------------------------------
    WEISS("Weiss", BankProximity.NONE),
    TROLL_STRONGHOLD("the Troll Stronghold", BankProximity.NONE),

    // --- The Great Conch ------------------------------------------------------
    GREAT_CONCH("the Great Conch", BankProximity.NONE),
    /**
     * Underwater off the Great Conch's east coast, down the steps at the end of the east
     * dock. Never the calquat stop: the two are reached by different walks.
     */
    CORAL_NURSERIES("the Coral Nurseries", BankProximity.NONE);

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
            int cost = upstairs ? tiles + FLOOR_CHANGE_TILES : tiles;
            if (cost <= 5) {
                return AT_ARRIVAL;
            }
            if (cost <= 25) {
                return SHORT_WALK;
            }
            return cost <= 90 ? FAR : NONE;
        }
    }

    private final String displayName;
    private final BankProximity bank;

    Arrival(String displayName, BankProximity bank) {
        this.displayName = displayName;
        this.bank = bank;
    }

    /**
     * Player-facing, written to sit mid-sentence: "travel to the Farming Guild". A caller
     * wanting a heading capitalises and strips the article.
     */
    public String getDisplayName() {
        return displayName;
    }

    public BankProximity getBank() {
        return bank;
    }
}
