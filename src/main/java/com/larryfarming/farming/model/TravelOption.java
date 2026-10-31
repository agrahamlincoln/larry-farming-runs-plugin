package com.larryfarming.farming.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * One rung of a {@link RunStop}'s downgrade chain: a way of reaching the patch,
 * together with what it asks of the player. Chains are ordered best-first, so the
 * first rung whose requirement holds is the one to show. {@link RungOrder} ranks them.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TravelOption {

    /**
     * What {@link #getBankDetour()} and {@link #getWalk()} read when nobody has measured
     * this rung.
     */
    public static final int UNMEASURED = -1;

    /**
     * What {@link #getWalk()} reads on a floor: a route that starts from wherever the player
     * is, so no walk can be measured for it. A floor ranks last.
     */
    public static final int FLOOR = Integer.MAX_VALUE;

    /**
     * What a rung spends, which breaks a tie between rungs that walk about as far. Declared
     * cheapest first.
     */
    public enum Cost {
        /** Unlimited: a cape, a house amenity, a walk. */
        FREE,
        /** A daily allowance or charges that recharge: a ring, a necklace, a diary cloak. */
        CHARGE,
        /** Spent for good: a tablet, a spell's runes, a basalt, a log. */
        CONSUMABLE
    }

    Requirement requires;

    /**
     * What to tell the player, imperative and in game terms: "Icy basalt to Weiss".
     * Never contains item IDs. Daily limits live on the {@link Capability}, not here.
     */
    String instruction;

    /**
     * The place this rung arrives at, when the rung is written as a place rather than as
     * a particular item. Null for every other rung.
     *
     * <p>When set, the instruction is only the tail of the sentence ("then run east"). The
     * head is whichever teleport the planner picks for this account.
     */
    Destination destination;

    /** Coins this rung costs to travel, or zero when it is free. */
    int fareCoins;

    /**
     * Extra tiles walked to touch a bank between landing here and reaching the patch, or
     * {@link #UNMEASURED} when the rung has no figure.
     *
     * <p>{@code landing→bank + bank→patch − landing→patch}, so zero means the bank is on the
     * route. Walked over the game's collision map, not straight-line distance: walls can
     * double the real walk.
     *
     * <p>Held on the rung, not the {@link Arrival}, because the figure depends on both the
     * landing and the patch. Rungs to one arrival land far apart, and one landing can bank
     * free on the way to one patch but not another.
     *
     * <p>{@code RunStop.Builder.banks} replaces the entry in the stop's own list rather than
     * mutating the rung, so a shared chain can carry different figures per stop.
     */
    int bankDetour;

    /**
     * Whether reaching that bank changes floor. The tile figure counts the ground floor
     * only and omits the stairs: Lumbridge measures five tiles but also climbs
     * two flights.
     */
    boolean bankUpstairs;

    /**
     * Which bank those tiles were walked to, or null when this rung has no figure.
     *
     * <p>Always set together with the figure, since a detour only means something for one
     * particular bank.
     */
    Bank bank;

    /**
     * True when the planner may take this rung only if it certainly holds, never as a guess.
     *
     * <p>For a rung whose sentence asserts something about the account. "With your house in
     * Hosidius" is wrong advice for a player housed anywhere else, so it waits for the
     * house's town to be read.
     */
    boolean certainOnly;

    /**
     * True when the planner may take this rung only where the previous stop left the player
     * at its approach, as {@link #startingFrom} grants it. Anywhere else the rung is not on
     * the ladder.
     *
     * <p>For a chain whose ranking turns on where the player stands. Lunar's Morytania
     * chain ranks fairy ring ALQ above the Fenkenstrain's tablet only for a player already
     * beside a ring, so it carries the ring twice: once marked this way above the tablet,
     * and once plainly below it.
     */
    boolean startOnly;

    /**
     * Tiles walked over the whole route, as the player takes it, from the first landing to
     * the patch. Teleports and rides count nothing; the walks to a fairy ring, a spirit tree,
     * a boat or a station count, and so does the walk from the last landing to the patch.
     * {@link #UNMEASURED} when the rung has no figure, {@link #FLOOR} on a floor.
     *
     * <p>A rung that asks for a way onto a network by an {@link Approach} leaves that
     * approach's walk out: it depends on which way in the player has, so the planner adds it
     * per account. A rung that leans on a patch is measured from that patch.
     */
    int walk;

    /** What this rung spends, or null when it has no walk figure. */
    Cost cost;

    private TravelOption(Requirement requires, String instruction, Destination destination,
            int fareCoins, int bankDetour, boolean bankUpstairs, Bank bank,
            boolean certainOnly) {
        this(requires, instruction, destination, fareCoins, bankDetour, bankUpstairs, bank,
                certainOnly, false, UNMEASURED, null);
    }

    public static TravelOption via(Capability requires, String instruction) {
        return new TravelOption(Requirement.cap(requires), instruction, null, 0, UNMEASURED,
                false, null, false);
    }

    public static TravelOption via(Requirement requires, String instruction) {
        return new TravelOption(requires, instruction, null, 0, UNMEASURED, false, null,
                false);
    }

    /**
     * A rung that gets the player to a place, however they get there.
     *
     * @param rest what to do after arriving, or "" when arriving is the whole of it
     */
    public static TravelOption to(Destination destination, String rest) {
        return new TravelOption(Requirement.reach(destination), rest, destination, 0,
                UNMEASURED, false, null, false);
    }

    /** Arriving somewhere, plus something else that also has to hold. */
    public static TravelOption to(Destination destination, Requirement alsoNeeds, String rest) {
        return new TravelOption(Requirement.all(Requirement.reach(destination), alsoNeeds),
                rest, destination, 0, UNMEASURED, false, null, false);
    }

    /**
     * A rung that gets the player to a place by Spellbook Swap and the place's spell from
     * another book. The planner opens the sentence with both casts: "Spellbook Swap, then
     * Harmony Island Teleport".
     *
     * <p>Rank it below the place's tablet: the swap spends its own runes and a second cast,
     * where a tablet is one click.
     *
     * @param rest what to do after arriving, or "" when arriving is the whole of it
     */
    public static TravelOption swapTo(Destination destination, String rest) {
        return new TravelOption(Requirement.reachBySwap(destination), rest, destination, 0,
                UNMEASURED, false, null, false);
    }

    /** The same rung, offered only when it certainly holds. See {@link #isCertainOnly()}. */
    public TravelOption onlyWhenCertain() {
        return new TravelOption(requires, instruction, destination, fareCoins, bankDetour,
                bankUpstairs, bank, true, startOnly, walk, cost);
    }

    /**
     * The same rung, offered only where the previous stop left the player at its approach.
     * See {@link #isStartOnly()}.
     */
    public TravelOption onlyFromAstart() {
        return new TravelOption(requires, instruction, destination, fareCoins, bankDetour,
                bankUpstairs, bank, certainOnly, true, walk, cost);
    }

    /**
     * The same rung, with a fare to pay on the way.
     *
     * <p>The packing list carries the coins. The fare lives on the rung rather than the
     * capability because the same charter costs a different fare from each port.
     */
    public TravelOption withFare(int coins) {
        if (coins <= 0) {
            throw new IllegalArgumentException("a fare has to cost something");
        }
        return new TravelOption(requires, instruction, destination, coins, bankDetour,
                bankUpstairs, bank, certainOnly, startOnly, walk, cost);
    }

    /**
     * The same rung, with the walk to a bank measured.
     *
     * <p>The bank is required so every figure can be re-walked.
     *
     * @param tiles    extra tiles the bank stop costs, zero when it is on the route
     * @param upstairs true when the bank is on another floor, which the tiles do not cover
     * @param bank     the bank they were walked to
     */
    public TravelOption withBank(int tiles, boolean upstairs, Bank bank) {
        if (tiles < 0) {
            throw new IllegalArgumentException("a bank detour cannot be negative");
        }
        if (bank == null) {
            throw new IllegalArgumentException("a bank detour has to name its bank");
        }
        return new TravelOption(requires, instruction, destination, fareCoins, tiles, upstairs,
                bank, certainOnly, startOnly, walk, cost);
    }

    /**
     * The same rung, with its walk measured.
     *
     * @param tiles walked over the whole route; see {@link #getWalk()}
     * @param spends what the rung spends, which breaks ties
     */
    public TravelOption withWalk(int tiles, Cost spends) {
        if (tiles < 0) {
            throw new IllegalArgumentException("a walk cannot be negative");
        }
        if (spends == null) {
            throw new IllegalArgumentException("a walk figure has to say what the rung spends");
        }
        return new TravelOption(requires, instruction, destination, fareCoins, bankDetour,
                bankUpstairs, bank, certainOnly, startOnly, tiles, spends);
    }

    /** The same rung, as a floor: it starts wherever the player is, so it ranks last. */
    public TravelOption asFloor() {
        return new TravelOption(requires, instruction, destination, fareCoins, bankDetour,
                bankUpstairs, bank, certainOnly, startOnly, FLOOR, null);
    }

    /** Whether anybody has walked this rung. A floor has no figure. */
    public boolean hasWalkFigure() {
        return walk != UNMEASURED && walk != FLOOR;
    }

    /** Whether this rung starts wherever the player is. See {@link #FLOOR}. */
    public boolean isFloor() {
        return walk == FLOOR;
    }

    /**
     * The same rung for a player the previous stop left a short walk from where
     * {@code granted} would take them: that part reads as held, so it packs nothing, and
     * the walk opens the sentence.
     *
     * <p>Returns this same rung when it does not ask for {@code granted}, or when it is
     * written as a place, whose sentence the planner opens with the teleport instead. The
     * rung it returns is never {@link #isStartOnly()}, since the start it waited for has
     * come.
     *
     * <p>The walk's tiles replace whatever the granted part would have cost to reach, so a
     * fairy ring rung started beside a ring counts the walk to it rather than an approach.
     *
     * @param walk a whole sentence, ending in ". ", that the rung's own sentence follows
     * @param tiles the walk's length, added to this rung's figure
     */
    public TravelOption startingFrom(Requirement granted, String walk, int tiles) {
        if (destination != null || !requires.contains(granted)) {
            return this;
        }
        return new TravelOption(requires.granting(granted), walk + instruction, null,
                fareCoins, bankDetour, bankUpstairs, bank, certainOnly, false,
                hasWalkFigure() ? this.walk + tiles : this.walk, cost);
    }

    /** Whether anybody has walked the bank from this rung. */
    public boolean hasBankFigure() {
        return bankDetour != UNMEASURED;
    }

    /** A route needing nothing — a valid floor for a chain that has no skip. */
    public static TravelOption always(String instruction) {
        return new TravelOption(Requirement.none(), instruction, null, 0, UNMEASURED, false,
                null, false);
    }

    /**
     * The whole instruction, given the way this player is actually travelling: the
     * teleport's own name, then whatever the rung says to do on arrival.
     */
    public String instructionFor(TeleportMeans means) {
        return instructionFor(means, false);
    }

    /**
     * The same, for a player who turns out to be standing at the place already.
     *
     * <p>The teleport is dropped and the tail promoted to a sentence: "then run east to the
     * quetzal landing site" becomes "Run east to the quetzal landing site". An empty tail
     * says the player is already there.
     *
     * <p>Takes an explicit flag rather than reading a null {@code means} as "already there":
     * {@code TeleportPlanner} also returns no means when the player's bank is unread.
     *
     * @param alreadyThere true only when the player's position is known to be the place
     */
    public String instructionFor(TeleportMeans means, boolean alreadyThere) {
        if (destination != null && alreadyThere) {
            return standingThere();
        }
        if (destination == null || means == null) {
            return instruction;
        }
        return instruction == null || instruction.isEmpty()
                ? means.getDisplayName()
                : means.getDisplayName() + ", " + instruction;
    }

    /**
     * The tail of a destination rung, read as a sentence by a player already at it.
     *
     * <p>The tail from its {@link #THEN} on is promoted. Whatever comes before describes the
     * arrival itself ("choosing the Grand Exchange, then run north-east"), which a player
     * standing there has already made. A tail with no {@link #THEN} names only the place.
     */
    private String standingThere() {
        if (instruction == null) {
            return alreadyAt();
        }
        int then = instruction.startsWith(THEN) ? 0 : instruction.indexOf(", " + THEN);
        if (then < 0) {
            return alreadyAt();
        }
        String rest = instruction.substring(then == 0 ? THEN.length() : then + 2
                + THEN.length());
        return rest.isEmpty() ? alreadyAt()
                : Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
    }

    /**
     * Names the zone, matching {@code TeleportPlanner}'s reason text. Falls back to the
     * destination when it has no zone.
     */
    private String alreadyAt() {
        return "You are already at "
                + (destination.getZone() != null
                        ? destination.getZone().getDisplayName()
                        : destination.getDisplayName());
    }

    /**
     * How a destination rung's tail joins the teleport in front of it. Every catalogue rung
     * with a zoned destination leaves its tail empty or opens it with this word.
     */
    public static final String THEN = "then ";
}
