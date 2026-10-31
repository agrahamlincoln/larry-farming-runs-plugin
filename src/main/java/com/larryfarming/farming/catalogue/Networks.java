package com.larryfarming.farming.catalogue;

import static com.larryfarming.farming.model.Capability.ARDOUGNE_CLOAK_1;
import static com.larryfarming.farming.model.Capability.ARDOUGNE_CLOAK_2;
import static com.larryfarming.farming.model.Capability.ARDOUGNE_CLOAK_3;
import static com.larryfarming.farming.model.Capability.ARDOUGNE_CLOAK_4;
import static com.larryfarming.farming.model.Capability.ATES_STATUE_KASTORI;
import static com.larryfarming.farming.model.Capability.BOOK_OF_THE_DEAD;
import static com.larryfarming.farming.model.Capability.CONSTRUCTION_CAPE;
import static com.larryfarming.farming.model.Capability.DIARY_CAPE;
import static com.larryfarming.farming.model.Capability.FAIRY_RINGS;
import static com.larryfarming.farming.model.Capability.FARMING_CAPE;
import static com.larryfarming.farming.model.Capability.FISHING_CAPE;
import static com.larryfarming.farming.model.Capability.GAMES_NECKLACE;
import static com.larryfarming.farming.model.Capability.KHAREDSTS_MEMOIRS;
import static com.larryfarming.farming.model.Capability.LOVAKENGJ_MINECART;
import static com.larryfarming.farming.model.Capability.MOKHAIOTL_WAYSTONE;
import static com.larryfarming.farming.model.Capability.NECKLACE_OF_PASSAGE;
import static com.larryfarming.farming.model.Capability.PENDANT_OF_ATES;
import static com.larryfarming.farming.model.Capability.POH_FAIRY_RING;
import static com.larryfarming.farming.model.Capability.POH_IN_HOSIDIUS;
import static com.larryfarming.farming.model.Capability.POH_PORTAL_KOUREND;
import static com.larryfarming.farming.model.Capability.POH_SPIRIT_TREE;
import static com.larryfarming.farming.model.Capability.QUEST_CAPE;
import static com.larryfarming.farming.model.Capability.QUETZAL_SITE_KASTORI;
import static com.larryfarming.farming.model.Capability.QUETZAL_WHISTLE;
import static com.larryfarming.farming.model.Capability.RENU;
import static com.larryfarming.farming.model.Capability.RESPAWN_AVAILABLE_CAMELOT;
import static com.larryfarming.farming.model.Capability.RESPAWN_TELEPORT;
import static com.larryfarming.farming.model.Capability.ROYAL_SEED_POD;
import static com.larryfarming.farming.model.Capability.SKILLS_NECKLACE;
import static com.larryfarming.farming.model.Capability.SLAYER_RING;
import static com.larryfarming.farming.model.Capability.SPIRIT_TREE_GROWN_FARMING_GUILD;
import static com.larryfarming.farming.model.Capability.SPIRIT_TREE_NETWORK;
import static com.larryfarming.farming.model.Capability.VARLAMORE_ACCESS;
import static com.larryfarming.farming.model.Capability.VARLAMORE_FAIRY_RINGS;
import static com.larryfarming.farming.model.Capability.VARROCK_DIARY_MEDIUM;
import static com.larryfarming.farming.model.Capability.XERICS_TALISMAN;
import static com.larryfarming.farming.model.Requirement.all;
import static com.larryfarming.farming.model.Requirement.cap;

import com.larryfarming.farming.model.Approach;
import com.larryfarming.farming.model.Bank;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.Requirement;
import com.larryfarming.farming.model.TravelOption;
import com.larryfarming.farming.model.TravelOption.Cost;
import java.util.List;

/**
 * Downgrade chains used more than once, and the travel networks many rungs ride, held in
 * one place so the copies cannot drift apart.
 */
final class Networks {

    private Networks() {
    }

    /**
     * Kastori, for both its calquat and its fruit tree.
     *
     * <p>The fairy-ring rung uses AIS, not CKQ: AIS lands on the river bank near the boat,
     * which is shorter than CKQ's walk to the Aldarin landing site.
     *
     * <p>The whistle and Renu stay separate rungs. A whistle is blown from anywhere, but Renu
     * has to be walked to, so every Renu rung carries the leg to the Civitas illa Fortis
     * landing site, 15 walked tiles from where the Civitas teleport lands. That leg keeps
     * each Renu rung beneath its whistle twin, which matters because the four whistle ids are
     * tiers, not charge levels: nothing reports whether a held whistle will fire.
     *
     * <p>Nothing walks into the Tlati Rainforest. The River Varla has no bridge or ford, so
     * every route ends in a teleport inside it or in Achilka's rowboat. The rowboat is free
     * and open to anyone once Varlamore is open, so the chain always ends in a real route.
     *
     * <p>The two patches sit either side of the landings, so the chain ranks differently for
     * each: the pendant's statue is nearer the fruit tree, the quetzal site the calquat.
     *
     * @param toPendant walked tiles from the pendant's statue landing (1366, 3086)
     * @param toSite from the Kastori quetzal landing site (1344, 3022)
     * @param toJetty from Achilka's Kastori jetty (1390, 3074)
     * @param toWaystoneExit from the Mokhaiotl waystone's surface exit (1309, 3104)
     */
    static List<TravelOption> kastori(int toPendant, int toSite, int toJetty,
            int toWaystoneExit) {
        return List.of(
                TravelOption.via(all(cap(PENDANT_OF_ATES), cap(ATES_STATUE_KASTORI)),
                        "Pendant of Ates to the statue north of Kastori, then run south-west")
                        .withWalk(toPendant, Cost.CHARGE),
                // The statue reminder lives here, where a player with the pendant but no
                // statue lands. One whose statue is active reads it only where the site is
                // nearer than the statue, which costs a sentence.
                TravelOption.via(all(cap(QUETZAL_WHISTLE), cap(QUETZAL_SITE_KASTORI)),
                        "Quetzal whistle to the Kastori landing site, then run north-east. "
                                + "If you have a pendant of Ates, activate the statue north "
                                + "of the patch.")
                        .withWalk(toSite, Cost.CHARGE),
                TravelOption.to(Destination.CIVITAS_ILLA_FORTIS,
                        all(cap(RENU), cap(QUETZAL_SITE_KASTORI)),
                        "then run east to the quetzal landing site and ride Renu to "
                                + "Kastori, then run north-east")
                        .withWalk(RENU_LEG + toSite, Cost.CONSUMABLE),
                // Tal Teklan is a default landing site, so no capability is needed. The
                // quetzal lands 48 walked tiles short of the jetty, so the instruction says so.
                TravelOption.via(QUETZAL_WHISTLE,
                        "Quetzal whistle to Tal Teklan, walk north-east to the jetty, then "
                                + "Achilka's boat to Kastori and run south-west")
                        .withWalk(TAL_TEKLAN_TO_JETTY + toJetty, Cost.CHARGE),
                TravelOption.to(Destination.CIVITAS_ILLA_FORTIS, cap(RENU),
                        "then run east to the quetzal landing site, ride Renu to Tal Teklan, "
                                + "walk north-east to the jetty, then Achilka's boat to "
                                + "Kastori and run south-west")
                        .withWalk(RENU_LEG + TAL_TEKLAN_TO_JETTY + toJetty, Cost.CONSUMABLE),
                // Its own jetty on the Gloomthorn Trail, which runs straight to Kastori
                // without touching Tal Teklan: 106 walked tiles from AIS to that jetty.
                TravelOption.via(onTheVarlamoreFairyRings(),
                        "Fairy ring AIS to Auburn Valley, then run south along the River Varla "
                                + "to the Gloomthorn Trail jetty and Achilka's boat")
                        .withWalk(106 + toJetty, Cost.FREE),
                // Spends a Doom of Mokhaiotl drop. The underground leg from the landing to the
                // surface exit is unmeasured, so the figure is a floor on the walk.
                TravelOption.via(MOKHAIOTL_WAYSTONE,
                        "Mokhaiotl waystone to the Ruins of Mokhaiotl, then climb out and run "
                                + "south-east")
                        .withWalk(toWaystoneExit, Cost.CONSUMABLE),
                // The floor. Primio flies from the Varrock east gate to Civitas illa Fortis
                // for free after Children of the Sun, which VARLAMORE_ACCESS already requires.
                // The mountain guide and Achilka ferry anyone, so nothing else is needed.
                TravelOption.via(VARLAMORE_ACCESS,
                        "Take Primio from the Varrock east gate to Civitas illa Fortis. Run "
                                + "north-west to Quetzacalli Gorge and take the mountain guide "
                                + "to Auburn Valley, then run south to Achilka's boat.")
                        .asFloor());
    }

    /** Walked from the Civitas teleport's landing to the city's quetzal landing site. */
    static final int RENU_LEG = 15;

    /** Walked from the Tal Teklan quetzal landing site to Achilka's jetty. */
    private static final int TAL_TEKLAN_TO_JETTY = 48;

    /**
     * Catherby, for both the herb patch and the fruit tree.
     *
     * <p>The first and last rungs are places, not tablets, so a
     * player whose spellbook has the Catherby or Camelot teleport casts it instead.
     *
     * <p>The two patches are 60 tiles apart, but the ordering is the same for both. The
     * respawn point and the Camelot teleport land a tile apart, (2757, 3479) and (2757, 3478),
     * so they share one figure.
     *
     * @param fromCatherby walked tiles from the Catherby landing (2801, 3449)
     * @param fromCamelot from Camelot (2757, 3479)
     */
    static List<TravelOption> catherby(int fromCatherby, int fromCamelot) {
        return List.of(
                TravelOption.to(Destination.CATHERBY, "")
                        .withWalk(fromCatherby, Cost.CONSUMABLE),
                TravelOption.via(all(cap(RESPAWN_TELEPORT), cap(RESPAWN_AVAILABLE_CAMELOT)),
                        "Respawn Teleport to Camelot, then run south-east")
                        .withWalk(fromCamelot, Cost.CONSUMABLE),
                TravelOption.to(Destination.CAMELOT, "then run south-east")
                        .withWalk(fromCamelot, Cost.CONSUMABLE));
    }

    /**
     * The Ardougne herb patch, for both spellbooks.
     *
     * <p>One chain serves both books: the only book-dependent part, which teleport reaches
     * the Fishing Guild, belongs to the destination rung.
     *
     * <p>The chain stops at these rungs on purpose. Past them the stop reports that nothing
     * quick reaches the patch, not that nothing does: anyone can walk to the farm.
     *
     * <p>The combat bracelet, Kandarin headgear and a house route to the Fishing Guild are
     * left out. The Ardougne teleport rung dominates each of them by at most four seconds.
     *
     * <p>Walked tiles to the patch: the cloaks land on it (2665, 3374), 5; the Fishing Guild
     * teleport (2613, 3391) 59, the skills necklace (2611, 3390) 61 and the quest cape's
     * Legends' Guild (2729, 3348) 62, a tie the free cape wins; the Fishing cape (2604, 3401)
     * 74; fairy ring BLR (2740, 3351) 72; the Ardougne teleport (2662, 3305) 97.
     */
    static List<TravelOption> ardougne() {
        // Bank figures sit on the chain because it serves a single patch. The best rungs land
        // on the patch with the bank behind them, so the top rung has the longest detour.
        return List.of(
                // Cloak 4's farm teleport is unlimited; cloaks 2 and 3 have a daily allowance.
                TravelOption.via(ARDOUGNE_CLOAK_4, "Ardougne cloak 4 to the farm patch")
                        .withBank(175, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(5, Cost.FREE),
                TravelOption.via(ARDOUGNE_CLOAK_3, "Ardougne cloak 3 to the farm patch")
                        .withBank(175, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(5, Cost.CHARGE),
                TravelOption.via(ARDOUGNE_CLOAK_2, "Ardougne cloak 2 to the farm patch")
                        .withBank(173, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(5, Cost.CHARGE),
                TravelOption.via(SKILLS_NECKLACE,
                                "Skills necklace to the Fishing Guild, then run east")
                        .withBank(63, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(61, Cost.CHARGE),
                TravelOption.to(Destination.FISHING_GUILD, "then run east")
                        .withBank(64, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(59, Cost.CONSUMABLE),
                TravelOption.via(FISHING_CAPE,
                                "Fishing cape to the Fishing Guild, then run east")
                        .withBank(35, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(74, Cost.FREE),
                TravelOption.via(QUEST_CAPE,
                                "Quest point cape to the Legends' Guild, then run west")
                        .withBank(160, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(62, Cost.FREE),
                // Most accounts without the diary use it: 51 Magic
                // after Plague City, or the tablet on any book.
                TravelOption.to(Destination.ARDOUGNE, "then run north")
                        .withBank(52, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(97, Cost.CONSUMABLE),
                TravelOption.via(onTheFairyRingNetwork(),
                                "Fairy ring BLR by the Legends' Guild, then run west")
                        .withBank(161, false, Bank.ARDOUGNE_NORTH)
                        .withWalk(72, Cost.FREE));
    }

    /**
     * The Hosidius farm on the Arceuus run, for the herb patch and for the limpwurt
     * flowers beside it.
     *
     * <p>One chain serves both patches because they share one farm. Walked over the
     * game's collision map to the herb patch: Xeric's Glade ({@code 1752,3566}) 23 tiles,
     * the Hosidius house portal ({@code 1743,3517}) 33, and the Lunch by the Lancalliums
     * landing ({@code 1714,3611}; the wiki pins {@code 1713,3612}) 74.
     *
     * <p>Every other way in is slower than the patch is worth, so the stops using this
     * chain skip when none of these holds.
     *
     * @param fromGlade walked tiles from Xeric's Glade: 23 to the herb patch, 26 to the flowers
     * @param fromPortal from the house portal: 33 and 39
     * @param fromLancalliums from the Lancalliums landing: 74 and 68
     */
    static List<TravelOption> hosidiusArceuus(int fromGlade, int fromPortal,
            int fromLancalliums) {
        return List.of(
                TravelOption.via(XERICS_TALISMAN, "Xeric's talisman to Xeric's Glade")
                        .withBank(91, false, Bank.HOSIDIUS)
                        .withWalk(fromGlade, Cost.CHARGE),
                hosidiusHouse(fromPortal),
                lancalliums(BOOK_OF_THE_DEAD, "Book of the Dead", fromLancalliums),
                lancalliums(KHAREDSTS_MEMOIRS, "Kharedst's memoirs", fromLancalliums));
    }

    /**
     * The same farm on the Lunar run, which reaches the house by Construction cape.
     *
     * <p>The cape's Kourend portal lands at Kourend Castle ({@code 1641,3673}), so it walks
     * furthest and ranks last, free as it is.
     *
     * @param fromGlade as on {@link #hosidiusArceuus}
     * @param fromCastle walked tiles from Kourend Castle: 185 to the herb patch, 179 to the
     *     flowers
     * @param fromPortal as on {@link #hosidiusArceuus}
     * @param fromLancalliums as on {@link #hosidiusArceuus}
     */
    static List<TravelOption> hosidiusLunar(int fromGlade, int fromCastle, int fromPortal,
            int fromLancalliums) {
        return List.of(
                TravelOption.via(XERICS_TALISMAN, "Xeric's talisman to Xeric's Glade")
                        .withBank(91, false, Bank.HOSIDIUS)
                        .withWalk(fromGlade, Cost.CHARGE),
                TravelOption.via(all(cap(CONSTRUCTION_CAPE), cap(POH_PORTAL_KOUREND)),
                        "Construction cape to your house, then the Kourend portal")
                        .withWalk(fromCastle, Cost.FREE),
                hosidiusHouse(fromPortal),
                lancalliums(BOOK_OF_THE_DEAD, "Book of the Dead", fromLancalliums),
                lancalliums(KHAREDSTS_MEMOIRS, "Kharedst's memoirs", fromLancalliums));
    }

    /**
     * A house teleport, for a player whose house is in Hosidius.
     *
     * <p>Never offered as a guess: the sentence tells the player where their house is, so
     * it waits for varbit 2187 to be read. The rung carries no bank figure because the
     * teleport lands inside the house, not at the portal. Its walk is from the portal.
     */
    private static TravelOption hosidiusHouse(int fromPortal) {
        return TravelOption.to(Destination.HOUSE, cap(POH_IN_HOSIDIUS),
                "with your house in Hosidius").onlyWhenCertain()
                .withWalk(fromPortal, Cost.CONSUMABLE);
    }

    /**
     * Lunch by the Lancalliums, which both books cast once its page is added. Stopping at
     * Hosidius bank on the way costs 55 extra tiles, walked over the game's collision map
     * from this landing ({@code 1714,3611}) through the bank ({@code 1748,3599}) to the herb
     * patch ({@code 1738,3552}). Each cast spends one of the book's charges.
     */
    private static TravelOption lancalliums(Capability book, String name, int fromLanding) {
        return TravelOption.via(book, name + ": Lunch by the Lancalliums")
                .withBank(55, false, Bank.HOSIDIUS)
                .withWalk(fromLanding, Cost.CHARGE);
    }

    /**
     * Being able to use the fairy rings: the network unlocked, and a way to reach a ring
     * to enter it by.
     *
     * <p>Both parts are required. A rung requiring only Fairytale I assumes the player is
     * already standing at a ring and offers a route they cannot start. The destination code
     * stays in each rung's instruction, since any ring reaches every other.
     *
     * <p>Ordered by the wiki's access table of walked tiles: house 5, quest cape 12, slayer
     * ring 16, diary cape 20, Salve graveyard 22, Arceuus Library 34, necklace of passage
     * 39, Ardougne cloak 51. The order matters because {@code any} resolves first-match: it
     * decides which approach the bag packs a teleport for, and which landing
     * {@code reliedOn} reports. Each ring rung's walk leaves this approach out, and the
     * planner adds the walk of the approach the player takes.
     *
     * <p>The table's royal seed pod (46) and ring of wealth (48) rows are left out. The pod's
     * route sits behind One Small Favour and three transports nothing here models. The ring
     * of wealth recharges only at the Fountain of Rune, so it buys an ironman nothing.
     *
     * <p>The Arceuus Library has no tablet in {@link Teleports}, so it only answers for a
     * player on the Arceuus spellbook.
     *
     * <p>The player's travel switches reach these arms as they reach every rung: a
     * switched-off item fails its capability, and switched-off tablets fail the Salve
     * graveyard arm while the Arceuus spell still counts.
     */
    static Requirement onTheFairyRingNetwork() {
        return all(TO_A_FAIRY_RING, cap(FAIRY_RINGS));
    }

    /**
     * Some way to stand at a fairy ring, each with its walk from the landing to the ring:
     * the wiki's access table, ranked by walked tiles.
     *
     * <p>The house ring and the cloak's Monastery teleport spend nothing, the quest and
     * diary capes are unlimited, the slayer ring and the necklace spend a charge, and the
     * Salve graveyard and the library spend a tablet or a spell's runes.
     */
    static final Approach RING_APPROACH = Approach.of(
            Approach.leg(cap(POH_FAIRY_RING), 5, Cost.FREE),
            Approach.leg(cap(QUEST_CAPE), 12, Cost.FREE),
            Approach.leg(cap(SLAYER_RING), 16, Cost.CHARGE),
            Approach.leg(cap(DIARY_CAPE), 20, Cost.FREE),
            // The Salve graveyard lands 22 tiles from CKS, the ring by the mushroom patch
            // (BKR is three times as far). A destination, not a capability, because a spell
            // or a tablet reaches it.
            Approach.leg(Requirement.reach(Destination.SALVE_GRAVEYARD), 22, Cost.CONSUMABLE),
            Approach.leg(Requirement.reach(Destination.ARCEUUS_LIBRARY), 34, Cost.CONSUMABLE),
            Approach.leg(cap(NECKLACE_OF_PASSAGE), 39, Cost.CHARGE),
            // 51 (49 to 54) from the cloak's Monastery landing, east past the Tower of Life.
            Approach.leg(cap(ARDOUGNE_CLOAK_1), 51, Cost.FREE));

    /**
     * The approach half of {@link #onTheFairyRingNetwork()}: some way to stand at a ring.
     * {@link FarmingCatalogue#toAfairyRing()} hands it to the planner, which tells a stop
     * lost to having no way to a ring from one lost to the network itself.
     */
    static final Requirement TO_A_FAIRY_RING = RING_APPROACH.getWay();

    /**
     * Landing at the Grand Exchange by the Varrock Teleport spell or tablet. The medium
     * Varrock diary adds that landing, and the means carry no diary gate, so every way to
     * {@link Destination#GRAND_EXCHANGE} asks for the diary as this does.
     */
    static Requirement atTheExchange() {
        return all(cap(VARROCK_DIARY_MEDIUM), Requirement.reach(Destination.GRAND_EXCHANGE));
    }

    /**
     * A spirit tree to board at: a house tree, or the permanent Grand Exchange tree, which
     * makes reaching Varrock enough. The Exchange landing (3165, 3479) is 30 walked tiles
     * from the Exchange tree (3185, 3508), and Varrock square (3213, 3424) 87. The walk
     * inside the house is unmeasured and counted as none.
     *
     * <p>The Exchange leg comes before the square's: the first way in held is the one taken,
     * and both spend the same teleport.
     */
    static final Approach SPIRIT_TREE_DEPARTURE = Approach.of(
            Approach.leg(cap(POH_SPIRIT_TREE), 0, Cost.FREE),
            Approach.leg(atTheExchange(), 30, Cost.CONSUMABLE),
            Approach.leg(Requirement.reach(Destination.VARROCK), 87, Cost.CONSUMABLE));

    /**
     * The Gnome Stronghold spirit tree (2461, 3444), reached by a teleport into the gnome
     * lands. Walked from the diary cape's landing (2467, 3459) 17 tiles, from the slayer
     * ring's (2431, 3422) 38. The royal seed pod's 48 start at (2465, 3490), outside the
     * Grand Tree's door. About five tiles inside the tree are uncounted there; 53 counts them.
     */
    static final Approach STRONGHOLD_TREE = Approach.of(
            Approach.leg(cap(DIARY_CAPE), 17, Cost.FREE),
            Approach.leg(cap(ROYAL_SEED_POD), 53, Cost.FREE),
            Approach.leg(cap(SLAYER_RING), 38, Cost.CHARGE));

    /** The same tree by the two of those that do not need the diary cape. */
    static final Approach STRONGHOLD_TREE_BY_POD_OR_RING = Approach.of(
            Approach.leg(cap(ROYAL_SEED_POD), 53, Cost.FREE),
            Approach.leg(cap(SLAYER_RING), 38, Cost.CHARGE));

    /** Every approach a rung's walk leaves out, which the planner adds per account. */
    static final List<Approach> APPROACHES = List.of(RING_APPROACH, SPIRIT_TREE_DEPARTURE,
            STRONGHOLD_TREE, STRONGHOLD_TREE_BY_POD_OR_RING);

    /**
     * The same two questions for the spirit trees: the network unlocked, and a tree to
     * board at. A rung asking only for the network assumes the player is standing at one.
     *
     * <p>Two trees count, the house's and the Exchange's, and the Exchange's by either
     * Varrock landing ({@link #SPIRIT_TREE_DEPARTURE}). Chains that word each departure
     * their own way name them as separate rungs instead of using this.
     */
    static Requirement onTheSpiritTreeNetwork() {
        return all(SPIRIT_TREE_DEPARTURE.getWay(), cap(SPIRIT_TREE_NETWORK));
    }

    /**
     * The fairy ring network <em>and</em> Varlamore's own gate on it.
     *
     * <p>The three Varlamore codes (AIS, AJP, CKQ) also need Children of the Sun, so
     * {@link #onTheFairyRingNetwork()} alone is not enough for them.
     */
    static Requirement onTheVarlamoreFairyRings() {
        return all(onTheFairyRingNetwork(), cap(VARLAMORE_FAIRY_RINGS));
    }

    /**
     * Reaching the Farming Guild, for contracts, hespori and the compost bin.
     *
     * <p>A speed ranking of independent methods, not a tier ladder, but it is walked the
     * same way. It also serves hespori.
     *
     * <p>The games necklace rung carries no "if not on the Arceuus book" qualifier. An
     * Arceuus player holding a necklace takes it either way: it walks two tiles less to its
     * minecart stop than the library does.
     *
     * <p>Each stop's figures are walked from the landing at the guild; the walks before a
     * ride are this chain's own: 30 from the Varrock teleport's Grand Exchange landing to the
     * Exchange tree and 87 from Varrock square, 38 from the Arceuus Library to its minecart
     * stop, 36 from the Wintertodt landing to the Northern Tundras stop. At all three guild
     * stops the minecart stop is 29 tiles farther than the guild tree, so over the tree's
     * own walk the games necklace adds 65, the Exchange landing 30 and Varrock square 87.
     * The medium Varrock diary therefore puts the Exchange tree above the necklace.
     *
     * @param fromCape walked tiles from the cape's and the necklace's landing (1248, 3725)
     * @param fromTree from the guild spirit tree (1251, 3750)
     * @param fromRing from fairy ring CIR (1302, 3762)
     * @param fromMinecart from the guild minecart stop (1218, 3737)
     */
    static List<TravelOption> farmingGuild(int fromCape, int fromTree, int fromRing,
            int fromMinecart) {
        // The spirit-tree rungs land inside the walls beside the bank chest (4 tiles); every
        // other rung arrives at the south gate (25 tiles). All three guild stops share these
        // figures, so they sit on the chain.
        return List.of(
                TravelOption.via(FARMING_CAPE, "Farming cape to the Farming Guild")
                        .withBank(25, false, Bank.FARMING_GUILD)
                        .withWalk(fromCape, Cost.FREE),
                TravelOption.via(SKILLS_NECKLACE, "Skills necklace to the Farming Guild")
                        .withBank(25, false, Bank.FARMING_GUILD)
                        .withWalk(fromCape, Cost.CHARGE),
                // The spirit-tree rungs also require the guild's own tree: the player plants
                // it, so network access alone does not guarantee it exists.
                TravelOption.via(all(cap(POH_SPIRIT_TREE), cap(SPIRIT_TREE_NETWORK),
                                cap(SPIRIT_TREE_GROWN_FARMING_GUILD)),
                        "House spirit tree, then the Farming Guild tree")
                        .withBank(4, false, Bank.FARMING_GUILD)
                        .withWalk(fromTree, Cost.FREE),
                // The walk to the Stronghold's tree depends on the way into the gnome lands.
                TravelOption.via(all(STRONGHOLD_TREE.getWay(),
                                cap(SPIRIT_TREE_NETWORK), cap(SPIRIT_TREE_GROWN_FARMING_GUILD)),
                        "Reach the Gnome Stronghold, then its spirit tree to the "
                                + "Farming Guild")
                        .withBank(4, false, Bank.FARMING_GUILD)
                        .withWalk(fromTree, Cost.FREE),
                // The Grand Exchange tree is permanent and reached by a teleport the planner
                // resolves: the Exchange landing for a player with the medium Varrock diary,
                // else Varrock square. The spell and the tablet still offer the square, so the
                // instruction names the Exchange.
                TravelOption.to(Destination.GRAND_EXCHANGE,
                        all(cap(VARROCK_DIARY_MEDIUM), cap(SPIRIT_TREE_NETWORK),
                                cap(SPIRIT_TREE_GROWN_FARMING_GUILD)),
                        "choosing the Grand Exchange, then run north-east to its spirit tree "
                                + "and ride it to the Farming Guild")
                        .withBank(4, false, Bank.FARMING_GUILD)
                        .withWalk(30 + fromTree, Cost.CONSUMABLE),
                TravelOption.to(Destination.VARROCK,
                        all(cap(SPIRIT_TREE_NETWORK), cap(SPIRIT_TREE_GROWN_FARMING_GUILD)),
                        "then run north to the Grand Exchange spirit tree and ride it "
                                + "to the Farming Guild")
                        .withBank(4, false, Bank.FARMING_GUILD)
                        .withWalk(87 + fromTree, Cost.CONSUMABLE),
                // CIR sits south of Mount Karuulm and north-east of the guild.
                TravelOption.via(onTheFairyRingNetwork(),
                        "Fairy ring CIR, south of Mount Karuulm, then run south-west")
                        .withBank(25, false, Bank.FARMING_GUILD)
                        .withWalk(fromRing, Cost.FREE),
                TravelOption.to(Destination.ARCEUUS_LIBRARY, cap(LOVAKENGJ_MINECART),
                        "then the minecart")
                        .withBank(25, false, Bank.FARMING_GUILD)
                        .withWalk(38 + fromMinecart, Cost.CONSUMABLE),
                TravelOption.via(all(cap(GAMES_NECKLACE), cap(LOVAKENGJ_MINECART)),
                        "Games necklace to Wintertodt, then the minecart")
                        .withBank(25, false, Bank.FARMING_GUILD)
                        .withWalk(36 + fromMinecart, Cost.CHARGE),
                // The floor. The minecart network has a stop at the guild gate and needs no
                // Kourend favour, so any player already in Kourend can finish the journey.
                TravelOption.via(LOVAKENGJ_MINECART,
                                "Minecart to the Farming Guild stop, beside the gate")
                        .withBank(25, false, Bank.FARMING_GUILD)
                        .asFloor());
    }
}
