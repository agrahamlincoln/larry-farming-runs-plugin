package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import java.util.Collection;
import lombok.Getter;

/**
 * Something a player can or cannot do, referenced by the ordered travel chains in
 * {@link RunStop}. Every route is a ranked list of alternatives
 * ("Ardougne cloak 4, else cloak 3, else the Fishing Guild teleport"). Downgrading walks
 * such a list and stops at the first capability the player has.
 *
 * <p>This enum carries no item IDs, varbit IDs or skill thresholds. It names what a
 * route requires, and the detection layer answers it against game state, so improving
 * detection never forces an edit to the catalogue. {@link Detectability} is the
 * exception, because whether a capability is observable at all is a property of the thing.
 * Each constant's attributes come from the capabilities table.
 *
 * <p>Some capabilities are rationed per day: an Explorer's ring 2 teleports to the Falador
 * cabbage patch three times a day, an Ardougne cloak 2 three times and a cloak 3 five, and
 * the higher tiers without limit. {@link #getDailyUses()} holds each allowance. Which
 * allowances the game reports lives with the detection code.
 *
 * <p>Charge counts on rechargeable items are not modelled: item IDs distinguish "holds a
 * charged one" but never "has two left", and no daily reset gives a count to start from.
 */
@Getter
public enum Capability {

    // --- Achievement diary reward items -------------------------------------
    // Each diary tier replaces the cloak or ring the player holds, so asking for a low tier
    // accepts any higher one (see isSatisfiedBy).
    // Answered from the diary flag, not the item: the item may sit in a bank the plugin has not
    // seen, while the diary flag is readable from login.
    // Ardougne cloak 1 matters only for its Monastery teleport, which lands beside a fairy
    // ring.
    ARDOUGNE_CLOAK_1,
    ARDOUGNE_CLOAK_2,
    ARDOUGNE_CLOAK_3,
    ARDOUGNE_CLOAK_4,
    EXPLORERS_RING_2,
    EXPLORERS_RING_3,
    EXPLORERS_RING_4,
    DIARY_CAPE,
    /**
     * The medium Varrock diary. Its reward adds the Grand Exchange as a second landing for
     * the Varrock Teleport spell and tablet, which the player picks per cast. Read from the
     * diary flag, since no item carries it.
     */
    VARROCK_DIARY_MEDIUM,

    // --- Skill capes --------------------------------------------------------
    FARMING_CAPE,
    CONSTRUCTION_CAPE,
    HUNTER_CAPE,
    FISHING_CAPE,
    QUEST_CAPE,

    // --- Jewellery and charged teleport items -------------------------------
    SKILLS_NECKLACE,
    GAMES_NECKLACE,
    COMBAT_BRACELET,
    RING_OF_DUELING,
    RING_OF_WEALTH,
    RING_OF_THE_ELEMENTS,
    SLAYER_RING,
    ROYAL_SEED_POD,
    XERICS_TALISMAN,
    /**
     * Kharedst's memoirs with its Lunch by the Lancalliums page added and a charge left.
     * The page and the charges are the player's varbits, not the book's.
     */
    KHAREDSTS_MEMOIRS,
    /** The memoirs' upgrade from A Kingdom Divided, with the same page and charges. */
    BOOK_OF_THE_DEAD,
    DIGSITE_PENDANT,
    TELEPORT_CRYSTAL,
    CHRONICLE,
    /**
     * Ghosts Ahoy's reward, which teleports to the Ectofuntus and refills there on landing,
     * so it never runs out. The empty one is not a route: it must be refilled at the
     * Ectofuntus first.
     */
    ECTOPHIAL,

    // --- Consumable teleports ------------------------------------------------
    ICY_BASALT,
    STONY_BASALT,
    MOKHAIOTL_WAYSTONE,

    /**
     * A redirected house tab: a teleport-to-house tablet with a scroll of
     * redirection used on it, open to a player housed anywhere. It lands where
     * {@link #POH_IN_BRIMHAVEN} does, with different unlocks and costs.
     */
    BRIMHAVEN_TABLET,

    // --- Spells --------------------------------------------------------------
    // Teleports to a fixed place are not capabilities: a tablet and a spell to the same
    // spot are one requirement, expressed as a Destination. Respawn Teleport stays here
    // because it lands at a setting rather than a place.
    RESPAWN_TELEPORT,
    FERTILE_SOIL,

    /**
     * Where the player respawns, which decides where Respawn Teleport lands. The default
     * route uses Camelot for the Catherby patches and Falador for the tree patch.
     *
     * <p>The game keeps one flag per town, with all zero meaning Lumbridge. The client names
     * the flags by town ({@code CAMELOT_SPAWN}, {@code FALADOR_SPAWN}), so search for
     * {@code _SPAWN}, not {@code RESPAWN}.
     */
    RESPAWN_AVAILABLE_CAMELOT,
    RESPAWN_AVAILABLE_FALADOR,

    // --- Transport networks --------------------------------------------------
    // Fairy-ring codes and glider stations are instruction text, not capabilities: the
    // player has or lacks access to the network.
    FAIRY_RINGS,
    /**
     * Using a fairy ring without a staff. Fairytale I and the Fairy Godfather's permission
     * in Fairytale II open the rings, but until the elite Lumbridge &amp; Draynor diary is
     * finished every trip needs a dramen or lunar staff wielded.
     */
    STAFFLESS_FAIRY_RINGS,
    /**
     * The three Varlamore ring codes, which the rest of the network does not open. Each
     * requires having travelled to Civitas illa Fortis via Regulus Cento after Children of
     * the Sun.
     */
    VARLAMORE_FAIRY_RINGS,
    /**
     * Reaching Varlamore at all. Every route in (charter ships, fairy rings, spells,
     * quetzals) works only once Children of the Sun is done and Regulus Cento has carried
     * the player over once.
     *
     * <p>The bottom of every Varlamore chain: without it the run drops the patch rather
     * than inventing a route.
     */
    VARLAMORE_ACCESS,
    GNOME_GLIDER,
    /**
     * One balloon station. Flights are gated per station: Enlightened Journey opens only
     * Entrana-to-Taverley, and each other station opens by flying to it from Entrana once.
     * Opened stations then connect to each other, so a rung names both ends.
     *
     * <p>Each station's varbit is set only after its Firemaking level is met (20 Taverley,
     * 40 Varrock, 50 Castle Wars, 60 Grand Tree, all boostable) and the opening trip made,
     * so the flag alone is the test.
     *
     * <p>Every flight burns a log, which is not modelled. Balloon rungs rank below free ones
     * for that reason.
     *
     * <p>Only stations the runs use are named. Entrana and the Crafting Guild have varbits
     * too ({@code ZEP_MULTI_BASKET}, {@code ZEP_MULTI_CRAFT}).
     */
    BALLOON_STATION_TAVERLEY,
    BALLOON_STATION_CASTLE_WARS,
    BALLOON_STATION_GRAND_TREE,
    BALLOON_STATION_VARROCK,
    LOVAKENGJ_MINECART,
    /**
     * The charter ships, which sail for anyone who pays. The fare is halved by Cabin Fever
     * or a ring of charos(a).
     */
    CHARTER_SHIP,
    QUETZAL_WHISTLE,
    /**
     * Renu, the quetzal Twilight's Promise gifts, ridden from a landing site.
     *
     * <p>Not interchangeable with {@link #QUETZAL_WHISTLE}: the whistle calls a quetzal from
     * anywhere, but Renu has to be walked to. Chains carry the leg that reaches a site.
     */
    RENU,
    /**
     * The Kastori landing site, which the player builds (ten quetzal feed, three limestone
     * bricks, four soft clay, no Construction level). Without it a quetzal flies to Tal
     * Teklan instead of the patch.
     */
    QUETZAL_SITE_KASTORI,
    MOUNTAIN_GUIDE,
    CANAL_BARGE,
    /**
     * Being allowed onto Harmony Island at all, which starting The Great Brain Robbery
     * opens: Brother Tranquility ferries anyone who has begun it. The Harmony stop's access
     * gate. The teleports, house portals included, also need the quest finished, which
     * each one checks for itself.
     */
    HARMONY_ISLAND_ACCESS,
    FOSSIL_ISLAND_ZIPLINE,
    /** The climbing rocks on Taverley's east wall, crossed east to west only. */
    TAVERLEY_CLIMBING_ROCKS,

    // --- Sailing travel ------------------------------------------------------
    // Holding the item is readable, and so is statue activation for the two pendant
    // destinations the runs use. Each statue is its own capability because a chain asks
    // for one destination on the pendant.
    //
    // The pendant's other four destinations and the Sailors' amulet's docks have no known
    // flag, so they count as available. Unlocking one is a one-off trip, and the
    // instruction tells the player to activate it.
    PENDANT_OF_ATES,
    ATES_STATUE_KASTORI,
    ATES_STATUE_NEMUS_RETREAT,
    SAILORS_AMULET,

    // --- Player-owned house --------------------------------------------------
    // The house's town is a varbit (2187), readable anywhere. The portals, the spirit tree
    // and the fairy ring are SEEN: their objects load only inside the house, so they are
    // read on a visit to the player's own house and absent until one. The Nexus's attuned
    // destinations are varbits, but proven absent only from a full read on its teleport
    // menu, so they are SEEN too.
    //
    // A "redirected" tab's landing spot belongs to the tab, not the house: see
    // BRIMHAVEN_TABLET.
    POH_IN_HOSIDIUS,
    POH_IN_BRIMHAVEN,
    POH_IN_TAVERLEY,
    POH_PORTAL_WEISS,
    /**
     * The Troll Stronghold Portal, directed with stony basalt's salts, which lands where the
     * basalt does: the stronghold entrance, or its roof after the hard Fremennik diary with
     * 73 Agility. Not the Trollheim Portal, which lands on the mountain far from the patch.
     */
    POH_PORTAL_TROLL_STRONGHOLD,
    POH_PORTAL_KOUREND,
    /** Lands where Fenkenstrain's Castle Teleport does, west of the Morytania herb patch. */
    POH_PORTAL_FENKENSTRAIN,
    /** Lands where Harmony Island Teleport does, 27 walked tiles north of the herb patch. */
    POH_PORTAL_HARMONY,
    // A Portal Nexus destination is a separate build from a portal room portal, and
    // the chains rank them as separate rungs, so they cannot share a constant.
    POH_NEXUS_WEISS,
    POH_NEXUS_HARMONY,
    /** The Nexus's stony basalt slot, which lands where the Troll Stronghold Portal does. */
    POH_NEXUS_TROLL_STRONGHOLD,
    POH_SPIRIT_TREE,
    POH_FAIRY_RING,
    /** Teleports beside the Wizards' Tower ring, for accounts with no Ardougne diary. */
    NECKLACE_OF_PASSAGE,

    /**
     * Permission to ride the spirit tree network, which Tree Gnome Village grants. Eight
     * destinations are permanent: Tree Gnome Village, the Gnome Stronghold, the Battlefield
     * of Khazard, the Grand Exchange, Feldip Hills, the Poison Waste, Prifddinas and Laguna
     * Aurorae. The rest are trees the player plants in farming patches, so a rung landing at
     * one also asks for that patch's tree ({@link #SPIRIT_TREE_GROWN_BRIMHAVEN}).
     *
     * <p>Riding still means walking to a tree, so a rung asking for this alone belongs below
     * any rung that names a way to one.
     *
     * <p>Departing the Gnome Stronghold tree also needs {@link #SPIRIT_TREE_STRONGHOLD}.
     */
    SPIRIT_TREE_NETWORK,

    /**
     * Boarding the Gnome Stronghold's own spirit tree at (2461, 3444), which The Grand Tree
     * opens. Arriving there needs only {@link #SPIRIT_TREE_NETWORK}.
     *
     * <p>Kept out of {@link #SPIRIT_TREE_NETWORK} so that the second quest gates only rungs
     * departing from this tree. Kept apart from {@link #GNOME_GLIDER}, which shares the
     * unlock, because the packing list names what the player is about to click.
     */
    SPIRIT_TREE_STRONGHOLD,

    /**
     * A spirit tree grown in one patch, which adds that location as a destination.
     *
     * <p>Answered from the patch as last seen (see {@code CapabilitySources}). An unseen
     * patch counts as empty.
     */
    SPIRIT_TREE_GROWN_BRIMHAVEN,
    SPIRIT_TREE_GROWN_FARMING_GUILD,
    SPIRIT_TREE_GROWN_PORT_SARIM,

    /**
     * Being allowed into Lletya, which Mourning's End Part I opens once <em>started</em>.
     * It gates the patch rather than any one rung: without it no route in exists.
     */
    LLETYA_ACCESS,

    // --- Whether a patch is the player's to work at all -----------------------
    // Each of these is a stop's accessGate: without it the patch cannot be worked by any
    // means, because the place is shut or the patch is not built. Access gates are checked
    // before any travel chain is walked.

    MORYTANIA_ACCESS,

    /**
     * The Troll Stronghold herb patch, which sits on the stronghold roof and is a reward of
     * My Arm's Big Adventure, not of Eadgar's Ruse (which only opens a teleport). That quest
     * requires Death Plateau and Troll Stronghold, so anyone who can work the patch can walk
     * to it.
     */
    TROLL_STRONGHOLD_HERB_PATCH,

    /**
     * The fire of nourishment, without which the Weiss patch cannot be farmed.
     *
     * <p>No varbit names it: the five {@code MY2ARM_FIRE_*} flags are other fires. It needs
     * Construction 35, Firemaking 66 and three hundred salt, so many players past the quest
     * lack it. Detection reads it from whether the Weiss patch has been seen (see
     * {@code CapabilitySources}).
     */
    WEISS_HERB_PATCH,

    /**
     * The Locus Oasis hardwood patches, behind a quest of their own on top of Varlamore.
     */
    LOCUS_OASIS_PATCH,

    /**
     * The Great Conch, which Troubled Tortugans opens partway through, when the player hands
     * Floopa the bandage. Neither Varlamore nor the Sailing level for its dock is needed: a
     * fairy ring and a charter both reach it.
     */
    GREAT_CONCH_ACCESS,

    // --- Farming supplies ----------------------------------------------------
    // Inventory-space downgrades, ranked the same way as travel ones.
    BOTTOMLESS_COMPOST_BUCKET,
    ULTRACOMPOST,
    MAGIC_SECATEURS,
    SEED_DIBBER,
    SPADE,

    /**
     * Planting seeds by hand, which the Farming part of Barbarian Training teaches and
     * which removes the need for a seed dibber.
     */
    BARE_HAND_PLANTING;

    static {
        Rows.TABLE.requireAll(values());
    }

    /**
     * A set of items that are upgrades of one another, where owning a higher one means
     * owning everything the lower ones could do.
     */
    public enum TierFamily {
        /** Stands alone; nothing else substitutes for it. */
        NONE,
        EXPLORERS_RING,
        ARDOUGNE_CLOAK
    }

    /** How, if at all, this capability can be answered from observable game state. */
    public enum Detectability {
        /** Answerable from what the player is carrying, wearing, or banking. */
        ITEM,
        /** Answerable from a quest, diary, spellbook, or other game flag. */
        VARBIT,
        /** Answerable from a base skill level. Boosts are never read. */
        SKILL,
        /**
         * Answerable only from what loads around the player while they stand in it: the
         * furniture of their own house, read on a visit. Until then it is taken to be
         * absent, and the player is never asked. Assuming absence costs at worst a slower
         * route, never a wrong instruction.
         */
        SEEN
    }

    /** A capability with no per-day cap, which is most of them. */
    public static final int UNLIMITED = 0;

    /** Player-facing name, phrased as the game phrases it. */
    private final String displayName;
    private final Detectability detectability;
    /** How many times a day this can be used, or {@link #UNLIMITED} for no cap. */
    private final int dailyUses;
    private final TierFamily tierFamily;
    /** Position within {@link #tierFamily}, higher being better. Zero if untiered. */
    private final int tier;

    Capability() {
        Tables.Row row = Rows.TABLE.of(name());
        this.displayName = row.text(1);
        this.detectability = row.constant(Detectability.class, 2);
        this.dailyUses = row.integer(3);
        this.tierFamily = row.constant(TierFamily.class, 4);
        this.tier = row.integer(5);
    }

    /**
     * The capabilities table, held apart so the constructors can read it; see
     * {@link Tables#byName}.
     */
    private static final class Rows {
        static final Tables.Named TABLE = Tables.byName("/com/larryfarming/capabilities.tsv", 6);
    }

    public boolean isRationedDaily() {
        return dailyUses != UNLIMITED;
    }

    /**
     * Whether holding {@code held} is enough to satisfy a requirement for this one.
     *
     * <p>A higher tier of the same family satisfies a lower one: a player past the elite
     * diary holds a ring 4 and no ring 2.
     */
    public boolean isSatisfiedBy(Capability held) {
        if (held == this) {
            return true;
        }
        return tierFamily != TierFamily.NONE && held.tierFamily == tierFamily && held.tier >= tier;
    }

    /**
     * The tier of this family the player actually holds, given everything they have, or
     * null if they hold none of it.
     *
     * <p>Use this rather than the tier a chain names when checking daily allowances: a
     * player asked for a ring 2 while holding a ring 3 has no daily limit.
     */
    public static Capability bestHeldTier(TierFamily family, Collection<Capability> held) {
        if (family == TierFamily.NONE) {
            return null;
        }
        Capability best = null;
        for (Capability capability : held) {
            if (capability.tierFamily == family && (best == null || capability.tier > best.tier)) {
                best = capability;
            }
        }
        return best;
    }

}
