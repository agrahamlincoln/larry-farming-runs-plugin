package com.larryfarming.farming.catalogue;

import static com.larryfarming.farming.model.Capability.BALLOON_STATION_GRAND_TREE;
import static com.larryfarming.farming.model.Capability.BALLOON_STATION_VARROCK;
import static com.larryfarming.farming.model.Capability.DIARY_CAPE;
import static com.larryfarming.farming.model.Capability.GNOME_GLIDER;
import static com.larryfarming.farming.model.Capability.POH_SPIRIT_TREE;
import static com.larryfarming.farming.model.Capability.RING_OF_DUELING;
import static com.larryfarming.farming.model.Capability.RING_OF_THE_ELEMENTS;
import static com.larryfarming.farming.model.Capability.ROYAL_SEED_POD;
import static com.larryfarming.farming.model.Capability.SLAYER_RING;
import static com.larryfarming.farming.model.Capability.SPIRIT_TREE_NETWORK;
import static com.larryfarming.farming.model.Capability.VARROCK_DIARY_MEDIUM;
import static com.larryfarming.farming.model.Requirement.all;
import static com.larryfarming.farming.model.Requirement.cap;

import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Arrival.BankProximity;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.farming.model.TravelOption;
import com.larryfarming.farming.model.TravelOption.Cost;
import com.larryfarming.farming.model.Zone;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The arrival table: each arrival's seat on the circuit, what a bank costs there, the zone a
 * player there stands in, and how to reach it when more than one patch there is being worked.
 *
 * <p><b>The seats are the order a farm trip visits places in.</b> Only the herb runs have a
 * fixed order, so a trip that interleaves every crop family needs an order of its own. This
 * one is authored once over every arrival rather than derived per trip, so a player walks the
 * same learnable circuit each time. The seats are kept per spellbook ({@link Seats}), and
 * every book walks this circuit unless {@link Seats#withOrder} gives it an order of its own.
 *
 * <p>The order follows the transport networks so consecutive arrivals are one cheap hop
 * apart: the quetzal mesh across Varlamore, the crystal between Lletya and Prifddinas, the
 * spirit tree out of the gnome lands, and Fenkenstrain's Castle, where the Canifis stop's
 * first rung lands. Harmony Island has no leg from there: every way in is its own teleport,
 * so it sits with the rest of Morytania only by geography. Elsewhere the order falls back
 * to geography too. The isolated arrivals trail because each costs a teleport in and out
 * wherever it sits.
 *
 * <p>Read backwards, this is the Arceuus herb run's order (Weiss, Trollheim, Morytania,
 * Falador, Catherby, Ardougne, Hosidius), apart from Weiss and the Troll Stronghold, whose
 * order is arbitrary, and the Ardougne Monastery bushes. The bushes sit beside the Ardougne
 * farm, which gives up the Lunar run's DJP-then-ALQ hop into Morytania. That hop pays only
 * on Lunar and only on the runs the bushes are due. A player who may enter the fairy rings
 * by cloak gets it back in general form: {@code CloakToTheRings} moves the bushes ahead of
 * the trip's first ring leg, which then starts at DJP.
 *
 * <p>A trip is this order with the arrivals it has no work at removed. {@code Itinerary}
 * also permutes the seed rotation's herb seats into {@code PatchPriority} order, because a
 * seed box plants its first stored seed the patch will take and so the visiting order
 * decides where each seed lands. The Farming Guild herb patch is reserved for contracts and
 * is never a rotation seat, so the guild keeps its lead.
 *
 * <p>The order is fixed; the travel is not. Each arrival still resolves its own downgrade
 * chain against the player.
 *
 * <p>Not {@code Arrival.values()}: the enum is grouped by region for readability, and
 * regrouping it must not reorder every player's run.
 *
 * <p>Most arrivals need no combined chain: their patches sit close enough that each stop's
 * own ranking serves both. The exception is an arrival whose landing spots fall
 * <em>between</em> its patches, so the rung closest to one is furthest from the other.
 * A visit working both patches then needs its own ranking, or the merged row would borrow
 * whichever stop happened to lead it.
 *
 * <p><b>Combined chains do not replace the stops' own chains.</b> A trip working one patch
 * still uses that patch's ranking. The two Gnome Stronghold stops do not share one
 * ordering, so do not align them.
 */
public final class Arrivals {

    private Arrivals() {
    }

    /**
     * One row per arrival, in circuit order. Give a row a combined chain only when its stops'
     * rankings genuinely disagree, and a zone only when the zone covers where a player there
     * stands.
     *
     * <p>The Farming Guild leads by constraint, not geography: the contract must be done
     * before the seeds are spent, and the guild's bank stands inside the walls with its
     * patches, so a trip kits out there.
     *
     * <p>Kourend is then left once. The Lovakengj minecart links the guild gate to
     * Hosidius West for 20 gp (free after The Forsaken Tower) and is open to everyone.
     * That leg runs between two stops, so it is not a rung on the Hosidius chain: a rung
     * would make Hosidius reachable by every account, since {@code LOVAKENGJ_MINECART}
     * resolves true for everyone.
     */
    private static final List<Row> TABLE = List.of(
            // Kourend, entered at the guild and left by the minecart.
            row(Arrival.FARMING_GUILD),
            row(Arrival.HOSIDIUS),
            // Varlamore, which Renu crosses end to end once any two sites are open. Fairy
            // ring AJP serves the oasis hardwood and Ortus Farm from opposite sides, and the
            // player prefers the hardwood first, before the run up to the farm.
            row(Arrival.LOCUS_OASIS),
            row(Arrival.ORTUS_FARM),
            row(Arrival.KASTORI, null, kastori()),
            row(Arrival.NEMUS_RETREAT),
            row(Arrival.AUBURNVALE),
            // Tirannwn: the teleport crystal's second dial lands at Prifddinas, a permanent
            // spirit tree destination, which leads on to the gnome lands.
            row(Arrival.LLETYA),
            row(Arrival.PRIFDDINAS),
            // The gnome lands and Kandarin, strung on the spirit tree and the cloak.
            row(Arrival.GNOME_STRONGHOLD, Zone.GNOME_STRONGHOLD, gnomeStronghold()),
            row(Arrival.TREE_GNOME_VILLAGE),
            row(Arrival.ARDOUGNE_MONASTERY),
            row(Arrival.ARDOUGNE_FARM),
            row(Arrival.CATHERBY),
            // Karamja, walked north to south and entered from Kandarin. The cheap link is
            // Captain Barnaby from East Ardougne (30 gp), not the 480 gp Catherby charter.
            row(Arrival.BRIMHAVEN),
            row(Arrival.TAI_BWO_WANNAI),
            // Asgarnia, walked west to east. Taverley leads on walked tiles from the Falador
            // teleport (2964, 3378), for an account with no way into Taverley:
            //
            //   Taverley (2936, 3438) first  91 + 114 + ~100 = ~305 tiles
            //   Falador Park (3004, 3373) first  ~40 + 114 + ~220 = ~375 tiles
            //
            // Park-first opens cheaper but loses it on the closing leg: Taverley to the
            // farm (3055, 3308) is ~220 tiles, the park ~100.
            row(Arrival.TAVERLEY),
            row(Arrival.FALADOR_PARK),
            row(Arrival.FALADOR_FARM),
            // Misthalin. Draynor Manor leads: the Explorer's ring lands at the Falador farm,
            // the cheapest walk in for a player with no way to the manor's doors.
            row(Arrival.DRAYNOR_MANOR),
            row(Arrival.LUMBRIDGE),
            row(Arrival.VARROCK_PARK),
            row(Arrival.AL_KHARID),
            // Morytania, entered across the Salve. The mushroom patch comes first because it
            // lies five tiles from fairy ring CKS, on the way from the Salve graveyard
            // landing. An Arceuus runner arriving at Fenkenstrain's pays a detour, but the
            // mushroom patch is opt-in.
            row(Arrival.MORT_MYRE),
            row(Arrival.CANIFIS),
            row(Arrival.HARMONY_ISLAND),
            // Fossil Island: the hardwoods are on the surface and the seaweed under it.
            row(Arrival.FOSSIL_ISLAND_HARDWOOD),
            row(Arrival.FOSSIL_ISLAND_SEAWEED),
            // Isolated: a teleport in and a teleport out, wherever they are put.
            row(Arrival.WEISS),
            row(Arrival.TROLL_STRONGHOLD),
            row(Arrival.GREAT_CONCH),
            row(Arrival.CORAL_NURSERIES));

    /**
     * Every book's seats. Each book walks the table's order, the circuit, unless
     * {@link Seats#withOrder} gives it its own. Lunar keeps the circuit: every Lunar herb
     * stop is a teleport that works from anywhere, and the circuit keeps walked neighbours
     * from other families together.
     */
    private static final Seats SEATS = Seats.circuit(arrivalsOf(TABLE));

    private static final Map<Arrival, Row> ROWS = rowsByArrival(TABLE);

    private static List<Arrival> arrivalsOf(List<Row> table) {
        List<Arrival> order = new ArrayList<>(table.size());
        for (Row row : table) {
            order.add(row.arrival);
        }
        return List.copyOf(order);
    }

    private static Map<Arrival, Row> rowsByArrival(List<Row> table) {
        Map<Arrival, Row> rows = new EnumMap<>(Arrival.class);
        for (Row row : table) {
            rows.put(row.arrival, row);
        }
        return Collections.unmodifiableMap(rows);
    }

    /** Every book's seats, for a caller that walks one order against another. */
    public static Seats seats() {
        return SEATS;
    }

    /** Every arrival, in the order a trip on this book visits them. */
    public static List<Arrival> inSeatOrder(Spellbook book) {
        return SEATS.inSeatOrder(book);
    }

    /**
     * Where this arrival sits in this book's order. Lower comes first. Seats run 0..n-1 with
     * no gaps, and the table refuses to load if an {@link Arrival} has none.
     */
    public static int seat(Spellbook book, Arrival arrival) {
        return SEATS.seat(book, arrival);
    }

    /** What a bank costs once the player stands at this arrival. */
    static BankProximity bank(Arrival arrival) {
        return arrival.getBank();
    }

    /** The zone a player at this arrival stands in, or null when no zone covers it. */
    static Zone zone(Arrival arrival) {
        return ROWS.get(arrival).zone;
    }

    /**
     * The Tree Gnome Stronghold, working the tree patch and the fruit tree on one visit.
     *
     * <p>Ranked on the walk a visit makes: land once, cross to the nearer patch, then the
     * 44 walked tiles between the two. Summing both patches' own walks would charge a leg
     * nobody walks and sink the royal seed pod. Walks for the pair: slayer ring 7 + 44,
     * diary cape 13 + 44, either spirit tree 13 + 44 (the Grand Exchange's after 30 from the
     * Varrock teleport's Exchange landing, or 87 from Varrock square), balloon 25 + 44 (16 of
     * them to the Varrock basket), royal seed pod about 48 + 44.
     *
     * <p>The glider lands three floors up the Grand Tree. Its walk down changes floor, so
     * it is unmeasured and stays last.
     */
    private static List<TravelOption> gnomeStronghold() {
        // Every rung names both patches, nearer first. The patches lie on opposite sides
        // of most landings, so the walking direction differs per rung.
        return List.of(
                TravelOption.via(DIARY_CAPE,
                        "Achievement diary cape to the Elder Gnome child, then run south-east "
                                + "to the fruit tree and south-west across the Stronghold to "
                                + "the tree patch")
                        .withWalk(13 + BETWEEN_THE_PATCHES, Cost.FREE),
                TravelOption.via(SLAYER_RING,
                        "Slayer ring to the Stronghold Slayer Cave, then run south to the tree "
                                + "patch and north-east across the Stronghold to the fruit tree")
                        .withWalk(7 + BETWEEN_THE_PATCHES, Cost.CHARGE),
                TravelOption.via(ROYAL_SEED_POD,
                        "Royal seed pod to the Grand Tree, then run south to the fruit tree and "
                                + "south-west across the Stronghold to the tree patch")
                        .withWalk(48 + BETWEEN_THE_PATCHES, Cost.FREE),
                TravelOption.via(all(cap(POH_SPIRIT_TREE), cap(SPIRIT_TREE_NETWORK)),
                        "House spirit tree to the Grand Tree. The fruit tree is just east and "
                                + "the tree patch is south-west.")
                        .withWalk(13 + BETWEEN_THE_PATCHES, Cost.FREE),
                TravelOption.to(Destination.GRAND_EXCHANGE,
                        all(cap(VARROCK_DIARY_MEDIUM), cap(SPIRIT_TREE_NETWORK)),
                        "choosing the Grand Exchange, then run north-east to its spirit tree "
                                + "and ride it to the Grand Tree. The fruit tree is just east "
                                + "and the tree patch is south-west.")
                        .withWalk(30 + 13 + BETWEEN_THE_PATCHES, Cost.CONSUMABLE),
                TravelOption.to(Destination.VARROCK, cap(SPIRIT_TREE_NETWORK),
                        "then run north to the Grand Exchange spirit tree and ride it to the "
                                + "Grand Tree. The fruit tree is just east and the tree patch "
                                + "is south-west.")
                        .withWalk(87 + 13 + BETWEEN_THE_PATCHES, Cost.CONSUMABLE),
                TravelOption.via(all(cap(RING_OF_THE_ELEMENTS), cap(BALLOON_STATION_VARROCK),
                                cap(BALLOON_STATION_GRAND_TREE)),
                        "Ring of the elements to the earth altar, then the Varrock balloon to "
                                + "the Grand Tree. The flight costs one magic log. The fruit "
                                + "tree is south and the tree patch is south-west.")
                        .withWalk(16 + 9 + BETWEEN_THE_PATCHES, Cost.CONSUMABLE),
                TravelOption.via(all(cap(RING_OF_DUELING), cap(GNOME_GLIDER)),
                        "Ring of dueling to Al Kharid, then the glider. Climb down from the "
                                + "top of the Grand Tree to the fruit tree, then run south-west "
                                + "to the tree patch."));
    }

    /** Walked between the nearest faces of the two Stronghold patches. */
    private static final int BETWEEN_THE_PATCHES = 44;

    /**
     * Kastori, working the fruit tree and the calquat on one visit.
     *
     * <p>The landings fall either side of the two patches: the pendant's statue and
     * Achilka's jetty are nearer the fruit tree, the quetzal site and the waystone's exit
     * nearer the calquat, so the two stops rank the shared chain differently. A visit
     * working both lands once, walks to the nearer patch, then the 26 walked tiles between
     * them, so each rung here counts its nearer patch's walk.
     */
    private static List<TravelOption> kastori() {
        return Networks.kastori(34 + KASTORI_BETWEEN, 20 + KASTORI_BETWEEN,
                41 + KASTORI_BETWEEN, 63 + KASTORI_BETWEEN);
    }

    /** Walked between the fruit tree's and the calquat's nearest faces at Kastori. */
    private static final int KASTORI_BETWEEN = 26;

    /**
     * How to reach this arrival when several of its patches are being worked, or empty
     * when its stops' own chains already agree.
     */
    static List<TravelOption> combinedChain(Arrival arrival) {
        return ROWS.get(arrival).combined;
    }

    private static Row row(Arrival arrival) {
        return new Row(arrival, null, List.of());
    }

    /** A row whose combined chain is ranked like a stop's ({@link Ladders}). */
    private static Row row(Arrival arrival, Zone zone, List<TravelOption> combined) {
        return new Row(arrival, zone, Ladders.ranked(combined));
    }

    private static final class Row {
        final Arrival arrival;
        final Zone zone;
        final List<TravelOption> combined;

        Row(Arrival arrival, Zone zone, List<TravelOption> combined) {
            this.arrival = arrival;
            this.zone = zone;
            this.combined = combined;
        }
    }
}
