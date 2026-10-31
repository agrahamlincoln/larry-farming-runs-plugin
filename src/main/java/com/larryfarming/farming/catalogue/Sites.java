package com.larryfarming.farming.catalogue;

import static com.larryfarming.farming.model.Capability.ARDOUGNE_CLOAK_1;
import static com.larryfarming.farming.model.Capability.ATES_STATUE_NEMUS_RETREAT;
import static com.larryfarming.farming.model.Capability.BALLOON_STATION_CASTLE_WARS;
import static com.larryfarming.farming.model.Capability.BALLOON_STATION_GRAND_TREE;
import static com.larryfarming.farming.model.Capability.BALLOON_STATION_TAVERLEY;
import static com.larryfarming.farming.model.Capability.BALLOON_STATION_VARROCK;
import static com.larryfarming.farming.model.Capability.BRIMHAVEN_TABLET;
import static com.larryfarming.farming.model.Capability.CANAL_BARGE;
import static com.larryfarming.farming.model.Capability.CHARTER_SHIP;
import static com.larryfarming.farming.model.Capability.CHRONICLE;
import static com.larryfarming.farming.model.Capability.COMBAT_BRACELET;
import static com.larryfarming.farming.model.Capability.CONSTRUCTION_CAPE;
import static com.larryfarming.farming.model.Capability.CORAL_NURSERY_ACCESS;
import static com.larryfarming.farming.model.Capability.DIARY_CAPE;
import static com.larryfarming.farming.model.Capability.DIGSITE_PENDANT;
import static com.larryfarming.farming.model.Capability.ECTOPHIAL;
import static com.larryfarming.farming.model.Capability.EXPLORERS_RING_2;
import static com.larryfarming.farming.model.Capability.EXPLORERS_RING_3;
import static com.larryfarming.farming.model.Capability.EXPLORERS_RING_4;
import static com.larryfarming.farming.model.Capability.FAIRY_RINGS;
import static com.larryfarming.farming.model.Capability.FOSSIL_ISLAND_ZIPLINE;
import static com.larryfarming.farming.model.Capability.GAMES_NECKLACE;
import static com.larryfarming.farming.model.Capability.GNOME_GLIDER;
import static com.larryfarming.farming.model.Capability.GREAT_CONCH_ACCESS;
import static com.larryfarming.farming.model.Capability.HARMONY_ISLAND_ACCESS;
import static com.larryfarming.farming.model.Capability.HUNTER_CAPE;
import static com.larryfarming.farming.model.Capability.ICY_BASALT;
import static com.larryfarming.farming.model.Capability.LLETYA_ACCESS;
import static com.larryfarming.farming.model.Capability.LOCUS_OASIS_PATCH;
import static com.larryfarming.farming.model.Capability.MORYTANIA_ACCESS;
import static com.larryfarming.farming.model.Capability.MOUNTAIN_GUIDE;
import static com.larryfarming.farming.model.Capability.NECKLACE_OF_PASSAGE;
import static com.larryfarming.farming.model.Capability.PENDANT_OF_ATES;
import static com.larryfarming.farming.model.Capability.POH_IN_BRIMHAVEN;
import static com.larryfarming.farming.model.Capability.POH_IN_TAVERLEY;
import static com.larryfarming.farming.model.Capability.POH_NEXUS_HARMONY;
import static com.larryfarming.farming.model.Capability.POH_NEXUS_TROLL_STRONGHOLD;
import static com.larryfarming.farming.model.Capability.POH_NEXUS_WEISS;
import static com.larryfarming.farming.model.Capability.POH_PORTAL_FENKENSTRAIN;
import static com.larryfarming.farming.model.Capability.POH_PORTAL_HARMONY;
import static com.larryfarming.farming.model.Capability.POH_PORTAL_TROLL_STRONGHOLD;
import static com.larryfarming.farming.model.Capability.POH_PORTAL_WEISS;
import static com.larryfarming.farming.model.Capability.POH_SPIRIT_TREE;
import static com.larryfarming.farming.model.Capability.PRIFDDINAS_ACCESS;
import static com.larryfarming.farming.model.Capability.QUETZAL_WHISTLE;
import static com.larryfarming.farming.model.Capability.RENU;
import static com.larryfarming.farming.model.Capability.RESPAWN_AVAILABLE_FALADOR;
import static com.larryfarming.farming.model.Capability.RESPAWN_TELEPORT;
import static com.larryfarming.farming.model.Capability.RING_OF_DUELING;
import static com.larryfarming.farming.model.Capability.RING_OF_THE_ELEMENTS;
import static com.larryfarming.farming.model.Capability.RING_OF_WEALTH;
import static com.larryfarming.farming.model.Capability.ROYAL_SEED_POD;
import static com.larryfarming.farming.model.Capability.SAILORS_AMULET;
import static com.larryfarming.farming.model.Capability.SKILLS_NECKLACE;
import static com.larryfarming.farming.model.Capability.SLAYER_RING;
import static com.larryfarming.farming.model.Capability.SPIRIT_TREE_GROWN_BRIMHAVEN;
import static com.larryfarming.farming.model.Capability.SPIRIT_TREE_GROWN_PORT_SARIM;
import static com.larryfarming.farming.model.Capability.SPIRIT_TREE_NETWORK;
import static com.larryfarming.farming.model.Capability.SPIRIT_TREE_STRONGHOLD;
import static com.larryfarming.farming.model.Capability.STONY_BASALT;
import static com.larryfarming.farming.model.Capability.TAVERLEY_CLIMBING_ROCKS;
import static com.larryfarming.farming.model.Capability.TELEPORT_CRYSTAL;
import static com.larryfarming.farming.model.Capability.TROLL_STRONGHOLD_HERB_PATCH;
import static com.larryfarming.farming.model.Capability.VARLAMORE_ACCESS;
import static com.larryfarming.farming.model.Capability.VARROCK_DIARY_MEDIUM;
import static com.larryfarming.farming.model.Capability.WEISS_HERB_PATCH;
import static com.larryfarming.farming.model.Requirement.all;
import static com.larryfarming.farming.model.Requirement.cap;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Bank;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.Preset;
import com.larryfarming.farming.model.Requirement;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.SeedPolicy;
import com.larryfarming.farming.model.TravelOption;
import com.larryfarming.farming.model.TravelOption.Cost;
import com.larryfarming.farming.model.UnlockGate;
import com.larryfarming.farming.model.Zone;
import com.larryfarming.model.Skill;
import java.util.List;

/**
 * Every place a run visits, one method per site. A site is one or more patches of one type,
 * worked on one visit by one travel ladder. Where a run writes its ladder per spellbook,
 * each book's version is its own method.
 *
 * <p>A site may also carry what makes its patch a better home for a seed
 * ({@link PatchAttributes}) and a diary's claim on one crop for it, each written beside the
 * site's stop.
 *
 * <p>{@link Site} lists these; what a run adds on top (its gate, pacing crop, respawn town
 * and seeds) lives in {@link Families}.
 *
 * <p>Each rung carries its walk ({@code walks}): tiles over the whole route, from the
 * landing to the patch, measured on the game's collision map. {@link Site} ranks every
 * ladder by them, so a ladder is written in any order and reads in walk order. A rung with
 * no figure keeps its written place among the rest.
 */
final class Sites {


    private Sites() {
    }

    // === Herb runs ==============================================================

    // --- Weiss and Trollheim -----------------------------------------------------

    static RunStop weissArceuus() {
        return RunStop.builder(Patches.WEISS_HERB)
                .at(Arrival.WEISS)
                .accessGate(WEISS_HERB_PATCH)
                .chain(weissArceuusChain())
                .skipIfUnreachable()
                .build();
    }

    static RunStop trollheimArceuus() {
        return RunStop.builder(Patches.TROLLHEIM_HERB)
                .at(Arrival.TROLL_STRONGHOLD)
                .accessGate(TROLL_STRONGHOLD_HERB_PATCH)
                .chain(trollheimArceuusChain())
                .skipIfUnreachable()
                .build();
    }

    // Immunity granted by the quest that unlocks the patch at all.

    static PatchAttributes.Entry weissAttributes() {
        return diseaseFreeByQuest("Making Friends with My Arm");
    }

    static PatchAttributes.Entry trollheimAttributes() {
        return diseaseFreeByQuest("My Arm's Big Adventure");
    }

    private static PatchAttributes.Entry diseaseFreeByQuest(String quest) {
        return new PatchAttributes.Entry(
                false, null, null, quest, List.of(), List.of(), false, false);
    }

    // The Arceuus chain for these two ends in a skip; the Lunar one gives no fallback on
    // purpose, since a Lunar player is expected to have the house portals.

    static RunStop weissLunar() {
        return RunStop.builder(Patches.WEISS_HERB)
                .at(Arrival.WEISS)
                .accessGate(WEISS_HERB_PATCH)
                .chain(weissLunarChain())
                .noFallbackGiven()
                .build();
    }

    static RunStop trollheimLunar() {
        return RunStop.builder(Patches.TROLLHEIM_HERB)
                .at(Arrival.TROLL_STRONGHOLD)
                .accessGate(TROLL_STRONGHOLD_HERB_PATCH)
                .chain(trollheimLunarChain())
                .noFallbackGiven()
                .build();
    }

    // Weiss and Trollheim share one written chain, but the item differs by destination
    // (icy basalt to Weiss, stony basalt to the Troll Stronghold), so they share a shape, not
    // an object.

    /**
     * Weiss on the Arceuus run: basalt first, house second. Every rung lands at
     * {@code 2846,3938}, four walked tiles from the patch, and each spends a basalt or a house
     * teleport, so they tie and keep this order.
     *
     * <p>The Lunar run reverses these two on purpose: a player on Lunar has usually built
     * the portals, and the Construction cape spends nothing.
     */
    private static List<TravelOption> weissArceuusChain() {
        return List.of(
                TravelOption.via(ICY_BASALT, "Icy basalt to Weiss")
                        .withWalk(4, Cost.CONSUMABLE),
                TravelOption.to(Destination.HOUSE, cap(POH_PORTAL_WEISS),
                        "then the Weiss portal")
                        .withWalk(4, Cost.CONSUMABLE),
                TravelOption.to(Destination.HOUSE, cap(POH_NEXUS_WEISS),
                        "then Weiss from the Portal Nexus")
                        .withWalk(4, Cost.CONSUMABLE));
    }

    /**
     * The Troll Stronghold patch on the stronghold roof. Every rung here lands where stony
     * basalt does: the entrance at {@code 2845,3694}, or with the hard Fremennik diary and 73
     * Agility the roof at {@code 2837,3695}, nine tiles from the patch. From the entrance the
     * way up is about 81 tiles with a door and a ladder, and the climb is unmeasured. Either way
     * every rung walks the same, so the roof's nine stands for all of them.
     *
     * <p>The house's Trollheim Portal is never a rung. It lands on the mountain at
     * {@code 2890,3679}, 163 walked tiles short of the stronghold entrance.
     */
    private static List<TravelOption> trollheimArceuusChain() {
        return List.of(
                TravelOption.via(STONY_BASALT, "Stony basalt to the Troll Stronghold")
                        .withWalk(9, Cost.CONSUMABLE),
                TravelOption.to(Destination.HOUSE, cap(POH_PORTAL_TROLL_STRONGHOLD),
                        "then the Troll Stronghold portal")
                        .withWalk(9, Cost.CONSUMABLE),
                TravelOption.to(Destination.HOUSE, cap(POH_NEXUS_TROLL_STRONGHOLD),
                        "then the Troll Stronghold from the Portal Nexus")
                        .withWalk(9, Cost.CONSUMABLE));
    }

    /** Weiss on the Lunar run, where the free house portal outranks the basalt. */
    private static List<TravelOption> weissLunarChain() {
        return List.of(
                TravelOption.via(all(cap(CONSTRUCTION_CAPE), cap(POH_PORTAL_WEISS)),
                        "Construction cape to your house, then the Weiss portal")
                        .withWalk(4, Cost.FREE),
                TravelOption.via(ICY_BASALT, "Icy basalt to Weiss")
                        .withWalk(4, Cost.CONSUMABLE));
    }

    /** The Troll Stronghold on the Lunar run. Both rungs land as on the Arceuus chain. */
    private static List<TravelOption> trollheimLunarChain() {
        return List.of(
                TravelOption.via(all(cap(CONSTRUCTION_CAPE), cap(POH_PORTAL_TROLL_STRONGHOLD)),
                        "Construction cape to your house, then the Troll Stronghold portal")
                        .withWalk(9, Cost.FREE),
                TravelOption.via(STONY_BASALT, "Stony basalt to the Troll Stronghold")
                        .withWalk(9, Cost.CONSUMABLE));
    }

    // --- Morytania -----------------------------------------------------------------

    /**
     * Morytania on Arceuus. The main rung is Fenkenstrain's Castle Teleport, one click from
     * the open book, which lands 56 walked tiles from the patch. Fairy ring ALQ lands 40
     * away and needs no spellbook, so it ranks above the spell for a player who reaches a
     * ring by a house ring (5 tiles to it), a quest cape (12) or a slayer ring (16).
     *
     * <p>The ectophial lands at the Ectofuntus, outside Port Phasmatys's barrier, 60 walked
     * tiles east of the patch. It costs nothing, but it walks four tiles more than the spell,
     * outside the tie band, so it ranks below the spell.
     */
    static RunStop morytaniaArceuus() {
        return RunStop.builder(Patches.MORYTANIA_HERB)
                .at(Arrival.CANIFIS)
                .accessGate(MORYTANIA_ACCESS)
                // No rung banks well: the patch is a hundred tiles from Canifis bank.
                .to(Destination.FENKENSTRAINS_CASTLE, "")
                .banks(157, Bank.CANIFIS)
                .walks(56, Cost.CONSUMABLE)
                .via(ECTOPHIAL, "Ectophial to the Ectofuntus, then run west to the patch")
                .banks(272, Bank.CANIFIS)
                .walks(60, Cost.FREE)
                .via(Networks.onTheFairyRingNetwork(),
                        "Fairy ring ALQ, then run north to the patch")
                .banks(216, Bank.CANIFIS)
                .walks(40, Cost.FREE)
                .noAlternative()
                .build();
    }

    /**
     * Morytania on Lunar. The house portal, the tablet and the swap all land where the
     * spell does, 56 walked tiles from the patch, so they share its walk and bank figure and
     * rank by what each spends: the house costs nothing, the tablet is consumed, and
     * Spellbook Swap spends its own runes and a second cast.
     *
     * <p>Fairy ring ALQ lands 40 away, and how far the player walks to a ring decides where
     * it ranks. The chain carries the ring twice. Once marked for a player the last stop left
     * beside a ring, who walks from there: two tiles from the mushrooms, which puts the ring
     * first, or 43 from the Monastery bushes, which puts it last. And once plainly, for
     * everyone else, who adds the walk from their own way to a ring.
     *
     * <p>A lunar staff opens a fairy ring as a dramen staff does.
     *
     * <p>The ectophial walks 60, four more than the castle, so it follows every rung that
     * lands there.
     */
    static RunStop morytaniaLunar() {
        return RunStop.builder(Patches.MORYTANIA_HERB)
                .at(Arrival.CANIFIS)
                .accessGate(MORYTANIA_ACCESS)
                .via(all(cap(CONSTRUCTION_CAPE), cap(POH_PORTAL_FENKENSTRAIN)),
                        "Construction cape to your house, then the Fenkenstrain's Castle "
                                + "portal")
                .banks(157, Bank.CANIFIS)
                .walks(56, Cost.FREE)
                .via(Networks.onTheFairyRingNetwork(),
                        "Fairy ring ALQ, then run north to the patch")
                .onlyFromAstart()
                .banks(216, Bank.CANIFIS)
                .walks(40, Cost.FREE)
                .to(Destination.FENKENSTRAINS_CASTLE, "")
                .banks(157, Bank.CANIFIS)
                .walks(56, Cost.CONSUMABLE)
                .via(Networks.onTheFairyRingNetwork(),
                        "Fairy ring ALQ, then run north to the patch")
                .banks(216, Bank.CANIFIS)
                .walks(40, Cost.FREE)
                .swapTo(Destination.FENKENSTRAINS_CASTLE, "")
                .banks(157, Bank.CANIFIS)
                .walks(56, Cost.CONSUMABLE)
                .via(ECTOPHIAL, "Ectophial to the Ectofuntus, then run west to the patch")
                .banks(272, Bank.CANIFIS)
                .walks(60, Cost.FREE)
                .noAlternative()
                .build();
    }

    // --- Catherby ------------------------------------------------------------------

    /**
     * Shared by both runs. The Catherby chain is written for Arceuus, so a Lunar
     * player is also offered the Arceuus Respawn Teleport rung and falls through to the
     * tablet rungs.
     */
    static RunStop catherbyHerb() {
        return RunStop.builder(Patches.CATHERBY_HERB)
                .at(Arrival.CATHERBY)
                .chain(Networks.catherby(20, 58))
                // Forty here against eleven for the fruit tree: the herbs lie north-east of
                // the booths, so a bank stop is an out-and-back. A trip working both patches
                // pays the smaller figure.
                .banksAll(40, Bank.CATHERBY)
                .noFallbackGiven()
                .build();
    }

    /** Catherby never becomes disease-free. */
    static PatchAttributes.Entry catherbyHerbAttributes() {
        return new PatchAttributes.Entry(
                false, null, null, null,
                List.of(
                        new PatchAttributes.BonusTier("Kandarin", "Medium", 5),
                        new PatchAttributes.BonusTier("Kandarin", "Hard", 10),
                        new PatchAttributes.BonusTier("Kandarin", "Elite", 15)),
                List.of(), false, false);
    }

    // --- Falador ---------------------------------------------------------------
    // The rings land at the cabbage patch (3052, 3291), 20 walked tiles north of the farm.
    // Port Sarim's spirit tree (3058, 3257) is 55, the Falador teleport (2964, 3378) 129.

    static RunStop faladorHerbArceuus() {
        return RunStop.builder(Patches.FALADOR_HERB)
                .at(Arrival.FALADOR_FARM)
                .via(EXPLORERS_RING_4, "Explorer's ring 4 to the cabbage patch")
                .walks(20, Cost.CHARGE)
                .via(EXPLORERS_RING_3, "Explorer's ring 3 to the cabbage patch")
                .walks(20, Cost.CHARGE)
                .via(EXPLORERS_RING_2, "Explorer's ring 2 to the cabbage patch")
                .walks(20, Cost.CHARGE)
                // Port Sarim's spirit tree is player-planted, so this asks for that tree as
                // well as the network. The walk to the Stronghold's tree depends on the way
                // into the gnome lands.
                .via(all(Networks.STRONGHOLD_TREE_BY_POD_OR_RING.getWay(),
                                cap(SPIRIT_TREE_NETWORK), cap(SPIRIT_TREE_GROWN_PORT_SARIM)),
                        "Spirit tree to the Grand Tree, then on to Port Sarim")
                .walks(55, Cost.FREE)
                // Last, and the only rung that needs no item. Also
                // the only rung that banks: the east bank sits on the south-east walk.
                .to(Destination.FALADOR, "then run south-east to the farm")
                .banks(22, Bank.FALADOR_EAST)
                .walks(129, Cost.CONSUMABLE)
                .skipIfUnreachable()
                .build();
    }

    static RunStop faladorHerbLunar() {
        return RunStop.builder(Patches.FALADOR_HERB)
                .at(Arrival.FALADOR_FARM)
                .via(EXPLORERS_RING_4, "Explorer's ring 4 to the cabbage patch")
                .walks(20, Cost.CHARGE)
                .via(EXPLORERS_RING_3, "Explorer's ring 3 to the cabbage patch")
                .walks(20, Cost.CHARGE)
                .via(EXPLORERS_RING_2, "Explorer's ring 2 to the cabbage patch")
                .walks(20, Cost.CHARGE)
                .via(all(cap(CONSTRUCTION_CAPE), cap(POH_SPIRIT_TREE), cap(SPIRIT_TREE_NETWORK),
                                cap(SPIRIT_TREE_GROWN_PORT_SARIM)),
                        "Construction cape to your house spirit tree, then Port Sarim")
                .walks(55, Cost.FREE)
                // As on the Arceuus chain: last, needs no item, and the only rung that banks.
                .to(Destination.FALADOR, "then run south-east to the farm")
                .banks(22, Bank.FALADOR_EAST)
                .walks(129, Cost.CONSUMABLE)
                .skipIfUnreachable()
                .build();
    }

    /** Falador Medium grants +10% experience here, not extra yield. */
    static PatchAttributes.Entry faladorHerbAttributes() {
        return new PatchAttributes.Entry(
                false, null, null, null,
                List.of(), List.of(new PatchAttributes.BonusTier("Falador", "Medium", 10)),
                false, false);
    }

    // --- Ardougne -------------------------------------------------------------

    // Shared by both runs. A skip rather than an unstated fallback: nothing gates the farm,
    // so the stop may only say nothing quick reaches it (see Networks.ardougne()).
    static RunStop ardougneHerb() {
        return RunStop.builder(Patches.ARDOUGNE_HERB)
                .at(Arrival.ARDOUGNE_FARM)
                .chain(Networks.ardougne())
                .skipIfUnreachable()
                .build();
    }

    // --- Hosidius -------------------------------------------------------------

    // Skipped when no rung holds: every other way in takes longer than the patch is worth.

    static RunStop hosidiusHerbArceuus() {
        return RunStop.builder(Patches.HOSIDIUS_HERB)
                .at(Arrival.HOSIDIUS)
                .chain(Networks.hosidiusArceuus(23, 33, 74))
                .skipIfUnreachable()
                .build();
    }

    static RunStop hosidiusHerbLunar() {
        return RunStop.builder(Patches.HOSIDIUS_HERB)
                .at(Arrival.HOSIDIUS)
                .chain(Networks.hosidiusLunar(23, 185, 33, 74))
                .skipIfUnreachable()
                .build();
    }

    /**
     * Kourend Easy makes the patches south of the church disease-free; Kourend Hard adds
     * the yield bonus.
     */
    static PatchAttributes.Entry hosidiusHerbAttributes() {
        return new PatchAttributes.Entry(
                false, "Kourend", "Easy", null,
                List.of(new PatchAttributes.BonusTier("Kourend", "Hard", 5)), List.of(),
                false, false);
    }

    // --- Harmony Island -------------------------------------------------------

    /**
     * Harmony Island, the Lunar run's eighth stop. Gated on the Elite Morytania diary,
     * which the patch requires.
     *
     * <p>Harmony Island Teleport is the only direct way here; every other route ends in
     * Brother Tranquility's boat from Mos Le'Harmless, which is slow and left out. The spell,
     * the portal and the Nexus land 27 walked tiles north of the patch, the tablet a tile
     * further, 28. They tie, so they rank by what each spends: the house is free to use, a
     * tablet is consumed, and Spellbook Swap costs runes and a second cast.
     *
     * <p>The tablet rung is written as the place. On Arceuus that place resolves to the
     * spell, which is the cheapest way of all for a player on that book.
     */
    static RunStop harmonyHerb() {
        return RunStop.builder(Patches.HARMONY_HERB)
                .at(Arrival.HARMONY_ISLAND)
                .accessGate(HARMONY_ISLAND_ACCESS)
                .via(all(cap(CONSTRUCTION_CAPE), cap(POH_PORTAL_HARMONY)),
                        "Construction cape to your house, then the Harmony Island portal")
                .walks(27, Cost.FREE)
                .via(all(cap(CONSTRUCTION_CAPE), cap(POH_NEXUS_HARMONY)),
                        "Construction cape to your house, then Harmony Island from the "
                                + "Portal Nexus")
                .walks(27, Cost.FREE)
                .to(Destination.HARMONY_ISLAND, "")
                .walks(28, Cost.CONSUMABLE)
                .swapTo(Destination.HARMONY_ISLAND, "")
                .walks(27, Cost.CONSUMABLE)
                .skipIfUnreachable()
                .gate(UnlockGate.diary("Morytania", "Elite"))
                .build();
    }

    static PatchAttributes.Entry harmonyHerbAttributes() {
        return new PatchAttributes.Entry(
                false, "Morytania", "Elite", null, List.of(), List.of(), false, false);
    }

    // --- Woven-in extras ------------------------------------------------------
    // Additive stops the default route folds in. They are not part of either
    // run's stated patch order and must not count toward its stop total.

    static RunStop snapeGrass() {
        return RunStop.builder(Patches.CATHERBY_NORTH_ALLOTMENT)
                .at(Arrival.CATHERBY)
                .chain(Networks.catherby(17, 49))
                // The herb cluster's figure: this patch is in it.
                .banksAll(40, Bank.CATHERBY)
                .noFallbackGiven()
                .plants(Crop.SNAPE_GRASS)
                .optional("Harvested over several runs alongside the Catherby herb patch")
                .build();
    }

    /**
     * The "north patch only" beside the white lily belongs to the snape grass allotment: a
     * flower patch has no north and south. The lily goes in the flower patch and protects the
     * allotments beside it.
     */
    static RunStop whiteLily() {
        return RunStop.builder(Patches.CATHERBY_FLOWER)
                .at(Arrival.CATHERBY)
                .chain(Networks.catherby(18, 54))
                // The herb cluster's figure: this patch is in it.
                .banksAll(40, Bank.CATHERBY)
                .noFallbackGiven()
                .plants(Crop.WHITE_LILY)
                // Planted to protect, not to harvest. A healthy lily keeps protecting, so a
                // run that finds one has nothing to do here.
                .standingCrop()
                .optional("Protects the Catherby allotments from disease")
                .build();
    }

    static RunStop limpwurtArceuus() {
        return limpwurt(Networks.hosidiusArceuus(26, 39, 68));
    }

    static RunStop limpwurtLunar() {
        return limpwurt(Networks.hosidiusLunar(26, 179, 39, 68));
    }

    /**
     * The limpwurt flowers, worked on the same visit as the Hosidius herbs and so taking
     * that run's Hosidius chain.
     */
    private static RunStop limpwurt(List<TravelOption> hosidius) {
        return RunStop.builder(Patches.HOSIDIUS_FLOWER)
                .at(Arrival.HOSIDIUS)
                .chain(hosidius)
                .skipIfUnreachable()
                // This one needs no compost.
                .actions(RunStop.StopAction.HARVEST, RunStop.StopAction.PLANT)
                .plants(Crop.LIMPWURT)
                .optional("One seed a run, no compost needed")
                .build();
    }

    /**
     * Potato cactus at Al Kharid. The technique: stop harvesting with one
     * slot free, dig the plant up with a spade rather than filling the inventory,
     * then teleport to the guild and claim the contract straight away.
     */
    static RunStop potatoCactus() {
        return RunStop.builder(Patches.AL_KHARID_CACTUS)
                .at(Arrival.AL_KHARID)
                // The arena's bank is a real detour from the cactus patch at the city's far
                // south.
                .via(RING_OF_DUELING, "Ring of dueling to Emir's Arena")
                .banks(105, Bank.AL_KHARID)
                // From the arena landing (3315, 3235).
                .walks(34, Cost.CHARGE)
                .noFallbackGiven()
                .plants(Crop.POTATO_CACTUS)
                .optional("Replanted each visit for its health check")
                .everyNthRun(Families.herbRunsToGrow(Crop.POTATO_CACTUS))
                .build();
    }

    /**
     * Whiteberries at the Monastery bush patch. The cloak's Monastery teleport is unlimited
     * from cloak 1 up; the daily limit belongs to the separate farm-patch teleport, which
     * needs cloak 2. So this rung asks for cloak 1 and spends no allowance.
     *
     * <p>Fairy ring DJP (2658, 3230), east past the Tower of Life and 39 walked tiles from the
     * patch, serves an account with no Ardougne diary. It ranks below the cloak, which lands
     * nine tiles from the patch, whatever way the player has to a ring.
     *
     * <p>The bushes lie on the walk from the cloak's landing to ring DJP. A run lists them at
     * their seat beside the Ardougne farm; a trip whose player enters the rings by cloak
     * moves them ahead of its first fairy ring leg, which then starts at DJP.
     */
    static RunStop whiteberries() {
        return RunStop.builder(Patches.ARDOUGNE_MONASTERY_BUSH)
                .at(Arrival.ARDOUGNE_MONASTERY)
                .via(ARDOUGNE_CLOAK_1, "Ardougne cloak to the Monastery")
                .banks(142, Bank.ARDOUGNE_SOUTH)
                .walks(9, Cost.FREE)
                .via(Networks.onTheFairyRingNetwork(),
                        "Fairy ring DJP, then run west past the Tower of Life")
                .banks(98, Bank.ARDOUGNE_SOUTH)
                .walks(39, Cost.FREE)
                .noFallbackGiven()
                .plants(Crop.WHITEBERRIES)
                .optional("Grows more slowly than a herb run comes round")
                .everyNthRun(Families.herbRunsToGrow(Crop.WHITEBERRIES))
                .build();
    }

    /**
     * Bittercaps west of Canifis, which the player opts into. Mushrooms earn little
     * experience; they are farmed for the bittercaps.
     *
     * <p>The Salve graveyard teleport lands twenty-two tiles from fairy ring CKS, with the
     * patch five tiles from the ring, so a herb runner entering Morytania that way passes
     * it.
     *
     * <p>Walked to the patch: the Salve graveyard 19 tiles, CKS 2. So the ring leads for a
     * player whose own way to a ring walks less than the graveyard's: a house ring (5 tiles
     * to it), a quest cape (12), a slayer ring (16) or a diary cape (20, a tie the free cape
     * wins). A player who reaches rings by the Salve graveyard walks straight to the patch.
     *
     * <p>Every third run: bittercaps grow in six ticks of forty minutes, four hours, and a
     * herb comes round in eighty minutes, so a spore planted on one herb run is ready on the
     * third after it.
     *
     * <p>The wiki's other routes (diary cape, Kharyrll, slayer ring) land east of Canifis
     * and walk back, so they are left out.
     */
    static RunStop mushrooms() {
        return RunStop.builder(Patches.MORT_MYRE_MUSHROOM)
                .at(Arrival.MORT_MYRE)
                .accessGate(MORYTANIA_ACCESS)
                .to(Destination.SALVE_GRAVEYARD, "then run north-east to the patch")
                .walks(19, Cost.CONSUMABLE)
                .via(Networks.onTheFairyRingNetwork(), "Fairy ring CKS, then run east")
                .walks(2, Cost.FREE)
                .skipIfUnreachable()
                .plants(Crop.MUSHROOM)
                .optional("Grows more slowly than a herb run comes round")
                .everyNthRun(Families.herbRunsToGrow(Crop.MUSHROOM))
                .build();
    }

    /**
     * Belladonna in Draynor Manor's grounds, which the player opts into for the Tangleroot
     * pet: its roll, on the last picking, is far better than a herb's.
     *
     * <p>Walked to the patch: the manor teleport's landing at the front doors 49 tiles, the
     * Explorer's ring's at the Falador cabbage patch 115. No fairy ring walks in.
     *
     * <p>Every fourth run: belladonna grows in four ticks of eighty minutes, and a herb comes
     * round in eighty.
     */
    static RunStop belladonna() {
        return RunStop.builder(Patches.DRAYNOR_MANOR_BELLADONNA)
                .at(Arrival.DRAYNOR_MANOR)
                .to(Destination.DRAYNOR_MANOR, "then run west to the patch")
                .walks(49, Cost.CONSUMABLE)
                .via(EXPLORERS_RING_4, "Explorer's ring 4 to the cabbage patch, then run east")
                .walks(115, Cost.CHARGE)
                .via(EXPLORERS_RING_3, "Explorer's ring 3 to the cabbage patch, then run east")
                .walks(115, Cost.CHARGE)
                .via(EXPLORERS_RING_2, "Explorer's ring 2 to the cabbage patch, then run east")
                .walks(115, Cost.CHARGE)
                .skipIfUnreachable()
                .plants(Crop.BELLADONNA)
                .optional("Grows more slowly than a herb run comes round")
                .everyNthRun(Families.herbRunsToGrow(Crop.BELLADONNA))
                .build();
    }

    /**
     * Belladonna at the east end of Auburnvale, on the same switch as Draynor Manor's, so a
     * player out for the Tangleroot pet plants both.
     *
     * <p>Walked to the patch's west face (1447, 3354): the quetzal landing (1411, 3361) 36
     * tiles, fairy ring AIS 42 to 44, the Pendant of Ates statue 121 and the Nemus mountain
     * guide's landing 125. A tool leprechaun stands beside the patch.
     */
    static RunStop auburnvaleBelladonna() {
        return RunStop.builder(Patches.AUBURNVALE_BELLADONNA)
                .at(Arrival.AUBURNVALE)
                .accessGate(VARLAMORE_ACCESS)
                // Auburnvale is a default landing site, so neither rung needs a site built.
                .via(QUETZAL_WHISTLE, "Quetzal whistle to Auburnvale, then run east")
                .walks(36, Cost.CHARGE)
                .to(Destination.CIVITAS_ILLA_FORTIS, cap(RENU),
                        "then run east to the quetzal landing site, ride Renu to Auburnvale, "
                                + "then run east")
                .walks(Networks.RENU_LEG + 36, Cost.CONSUMABLE)
                .via(Networks.onTheVarlamoreFairyRings(),
                        "Fairy ring AIS to Auburn Valley, then run north-east")
                .walks(44, Cost.FREE)
                .via(all(cap(PENDANT_OF_ATES), cap(ATES_STATUE_NEMUS_RETREAT)),
                        "Pendant of Ates to Nemus Retreat, then run north-east")
                .walks(121, Cost.CHARGE)
                .skipIfUnreachable()
                .plants(Crop.BELLADONNA)
                .optional("Grows more slowly than a herb run comes round")
                .everyNthRun(Families.herbRunsToGrow(Crop.BELLADONNA))
                .build();
    }

    /**
     * Ortus Farm, woven in as it suits rather than given a slot.
     *
     * <p>Walked to the nearest face of the patch, which spans (1581-1582, 3094-3095): the
     * whistle 47 tiles, the Civitas teleport and Renu 62, the Hunter cape 74, AJP 91 and the
     * Civitas teleport alone 114. Two rungs bank in Civitas illa Fortis and three at the
     * Hunter Guild.
     */
    static RunStop ortusFarm() {
        // Each rung lands somewhere different, so each names its own direction to the
        // patches.
        return RunStop.builder(Patches.ORTUS_HERB)
                .at(Arrival.ORTUS_FARM)
                // The whistle lands at the Hunter Guild landing site (1585, 3053), the closest
                // arrival. The site is a default one, so neither this rung nor Renu's needs a
                // landing-site capability. The guild's bank chest is south-west of the landing
                // and the patches north, so a bank stop doubles back: 87 tiles.
                .via(QUETZAL_WHISTLE,
                        "Quetzal whistle to the Hunter Guild landing site, then run north")
                .banks(87, Bank.HUNTER_GUILD)
                .walks(47, Cost.CHARGE)
                // Renu flies from the city's landing site, 15 walked tiles from the
                // teleport's landing, to the same Hunter Guild site as the whistle.
                .to(Destination.CIVITAS_ILLA_FORTIS, cap(RENU),
                        "then run east to the quetzal landing site, ride Renu to the Hunter "
                                + "Guild, then run north")
                .banks(87, Bank.HUNTER_GUILD)
                .walks(Networks.RENU_LEG + 47, Cost.CONSUMABLE)
                // The cape lands at (1558, 3046) inside the guild, once Guildmaster Apatura
                // has unlocked the teleport. The chest stands 16 tiles west of the landing,
                // away from the patches: 36 tiles of detour.
                .via(HUNTER_CAPE, "Hunter cape to the Hunter Guild, then run north")
                .banks(36, Bank.HUNTER_GUILD)
                .walks(74, Cost.FREE)
                // AJP (1651, 3011) sits east of the patches as well as south, hence
                // north-west. The city's west bank costs 106 tiles of detour from here and
                // the guild's chest 108.
                .via(Networks.onTheVarlamoreFairyRings(),
                        "Fairy ring AJP to the Avium Savannah, then run north-west")
                .banks(106, Bank.CIVITAS_ILLA_FORTIS_WEST)
                .walks(91, Cost.FREE)
                // The teleport lands at (1682, 3136), by Sunrise Palace, north-east of the
                // farm. Written as a "then" continuation because a player already at the
                // palace is shown only this tail.
                .to(Destination.CIVITAS_ILLA_FORTIS,
                        "then run south-west from Sunrise Palace")
                .banks(15, Bank.CIVITAS_ILLA_FORTIS_WEST)
                .walks(114, Cost.CONSUMABLE)
                .noFallbackGiven()
                .optional("No fixed place in the run; weave it in")
                .build();
    }

    // === Tree runs ==============================================================

    // --- Hardwood -------------------------------------------------------------

    /**
     * Three patches in the Mushroom Forest, reached by one journey, so they are one stop.
     * No other route gets here.
     */
    static RunStop fossilIsland() {
        return RunStop.builder(Patches.FOSSIL_ISLAND_HARDWOOD_EAST,
                        Patches.FOSSIL_ISLAND_HARDWOOD_MIDDLE,
                        Patches.FOSSIL_ISLAND_HARDWOOD_WEST)
                .at(Arrival.FOSSIL_ISLAND_HARDWOOD)
                .accessGate(CANAL_BARGE)
                .via(all(cap(DIGSITE_PENDANT), cap(CANAL_BARGE)),
                        "Digsite pendant to the Digsite, then the barge and the shortcut")
                // The Museum Camp chest: sixteen tiles off the barge landing, then fifty-two
                // to (3710, 3836) against a direct fifty-eight. It needs a one-off
                // Construction 21 repair with no varbit to read.
                .banks(10, Bank.MUSEUM_CAMP)
                // 21 tiles from the pendant's Digsite landing to the barge (3362, 3445), then 51
                // from the barge's Fossil Island landing (3724, 3807) to the nearest patch, the
                // west one; the middle is 54 and the east 60.
                .walks(72, Cost.CHARGE)
                .noAlternative()
                .build();
    }

    static RunStop locusOasis() {
        return RunStop.builder(Patches.LOCUS_OASIS_HARDWOOD)
                .at(Arrival.LOCUS_OASIS)
                .accessGate(LOCUS_OASIS_PATCH)
                .via(Networks.onTheVarlamoreFairyRings(),
                        "Fairy ring AJP to the Avium Savannah, then run south-east")
                .walks(42, Cost.FREE)
                .noFallbackGiven()
                .build();
    }

    // --- Calquat --------------------------------------------------------------

    /**
     * The first two rungs are the main routes. The third uses the shared network helper because
     * CKR is dialable from any ring, whatever the approach to the network.
     *
     * <p>Walked to the patch: the Brimhaven house portal 74 tiles, CKR (2801, 3003) 107. The
     * Salve rung adds the graveyard's 22 to CKS, 129 in all. The plain ring rung adds the
     * player's own way to a ring, so it outranks the Salve rung for anyone with a nearer
     * one. For a player whose way is the Salve graveyard the two tie at one cost, and the
     * Salve rung stays first: it is narrower, and its sentence can name the teleport the
     * player spends.
     */
    static RunStop taiBwoWannaiCalquat() {
        return RunStop.builder(Patches.TAI_BWO_WANNAI_CALQUAT)
                .at(Arrival.TAI_BWO_WANNAI)
                .to(Destination.HOUSE, cap(POH_IN_BRIMHAVEN), "with your house in Brimhaven")
                .onlyWhenCertain()
                .walks(74, Cost.CONSUMABLE)
                .to(Destination.SALVE_GRAVEYARD, cap(FAIRY_RINGS), "then fairy ring CKR")
                .walks(22 + 107, Cost.CONSUMABLE)
                .via(Networks.onTheFairyRingNetwork(),
                        "Fairy ring CKR, south of Tai Bwo Wannai, then run north")
                .walks(107, Cost.FREE)
                .noFallbackGiven()
                .build();
    }

    static RunStop kastoriCalquat() {
        return RunStop.builder(Patches.KASTORI_CALQUAT)
                .at(Arrival.KASTORI)
                .accessGate(VARLAMORE_ACCESS)
                .chain(Networks.kastori(56, 20, 56, 90))
                .skipIfUnreachable()
                .build();
    }

    /**
     * CJQ (3178, 2447) lands 104 walked tiles from the patch. The sailors' amulet and charter
     * land about 70 away, but the walk from the Deepfin Point dock is not measured, so that rung
     * counts as unmeasured and keeps its place below the ring.
     */
    static RunStop greatConchCalquat() {
        return RunStop.builder(Patches.GREAT_CONCH_CALQUAT)
                .at(Arrival.GREAT_CONCH)
                .accessGate(GREAT_CONCH_ACCESS)
                .via(Networks.onTheFairyRingNetwork(), "Fairy ring CJQ to the Great Conch")
                .walks(104, Cost.FREE)
                .via(all(cap(SAILORS_AMULET), cap(CHARTER_SHIP)),
                        "Sailors' amulet to Deepfin Point, then charter to the Summer Shore. "
                                + "On your first visit, inspect the dock marker at Deepfin "
                                + "Point.")
                // The base fare both ways. Cabin Fever and a worn ring of charos(a) each
                // halve it; the packing list applies those.
                .fare(1800)
                .skipIfUnreachable()
                .build();
    }

    // --- Regular trees --------------------------------------------------------

    static RunStop varrockTree() {
        return RunStop.builder(Patches.VARROCK_TREE)
                .at(Arrival.VARROCK_PARK)
                .to(Destination.VARROCK, "")
                // Priced from Varrock square. The medium diary's Grand Exchange landing banks
                // for eleven but lands farther west of the patch, so it gets no rung here: a
                // worse rung is never taken to catch a bank.
                .banks(62, Bank.VARROCK_EAST)
                // 37 walked tiles from Varrock square (3213, 3424).
                .walks(37, Cost.CONSUMABLE)
                .to(Destination.HOUSE, all(cap(POH_SPIRIT_TREE), cap(SPIRIT_TREE_NETWORK)),
                        "then the spirit tree to Varrock")
                // 81 from the Grand Exchange tree (3185, 3508).
                .walks(81, Cost.CONSUMABLE)
                // A single fixed destination outside the Champions' Guild, (3200, 3355): 110
                // walked tiles from the patch, the furthest of the three.
                .via(CHRONICLE, "Chronicle to the Champions' Guild")
                .banks(42, Bank.VARROCK_EAST)
                .walks(110, Cost.CHARGE)
                .skipIfUnreachable()
                .build();
    }

    static RunStop lumbridgeTree() {
        return RunStop.builder(Patches.LUMBRIDGE_TREE)
                .at(Arrival.LUMBRIDGE)
                .to(Destination.LUMBRIDGE, "")
                // Five tiles on the ground plus two flights of stairs, which are uncounted.
                .banksUpstairs(5, Bank.LUMBRIDGE_CASTLE)
                // 46 walked tiles from (3221, 3218).
                .walks(46, Cost.CONSUMABLE)
                .via(RING_OF_THE_ELEMENTS, "Ring of the elements to the water altar")
                .banksUpstairs(47, Bank.LUMBRIDGE_CASTLE)
                // 109 from the water altar (3170, 3155).
                .walks(109, Cost.CHARGE)
                .skipIfUnreachable()
                .build();
    }

    /**
     * Not the herb chain: this is the tree patch north of the city. Walks end at
     * (3002, 3373), beside the patch.
     */
    static RunStop faladorTree() {
        return RunStop.builder(Patches.FALADOR_TREE)
                .at(Arrival.FALADOR_PARK)
                // 6 tiles from (2996, 3375), and the worst bank: the east bank nearly
                // quadruples the leg. Bank detours vary by rung here (one to fifty tiles),
                // so each rung carries its own.
                .via(RING_OF_WEALTH, "Ring of wealth to Falador Park")
                .banks(49, Bank.FALADOR_EAST)
                .walks(6, Cost.CHARGE)
                // 39 tiles. East, not north-west: the spell lands west of the city at
                // (2964, 3378).
                .to(Destination.FALADOR, "then run east into the park")
                .banks(41, Bank.FALADOR_EAST)
                .walks(39, Cost.CONSUMABLE)
                // 61 tiles from the respawn point in the White Knights' Castle grounds,
                // (2970, 3342) on the wiki's respawn map.
                .via(all(cap(RESPAWN_TELEPORT), cap(RESPAWN_AVAILABLE_FALADOR)),
                        "Respawn Teleport, with Falador set as your respawn point")
                .banks(41, Bank.FALADOR_EAST)
                .walks(61, Cost.CONSUMABLE)
                // 104 tiles: 35 underground from the landing at (3049, 9763) to the ladder,
                // then 69 from its top at (3021, 3339). The one rung that passes the east
                // bank on its way out of the guild.
                .via(SKILLS_NECKLACE, "Skills necklace to the Mining Guild")
                .banks(1, Bank.FALADOR_EAST)
                .walks(104, Cost.CHARGE)
                // 114 tiles, free for a player already at the Taverley tree patch, which
                // the circuit walks first.
                .via(Requirement.reachedPatch(Patches.TAVERLEY_TREE),
                        "Run south-east from the Taverley tree patch")
                .banks(50, Bank.FALADOR_EAST)
                .walks(114, Cost.FREE)
                // 129 tiles from the cabbage patch at (3052, 3291). Encoded as the lowest
                // tier that reaches the cabbage patch, which any higher one satisfies.
                .via(EXPLORERS_RING_2, "Explorer's ring to the cabbage patch, then run north")
                .banks(14, Bank.FALADOR_EAST)
                .walks(129, Cost.CHARGE)
                .skipIfUnreachable()
                .build();
    }

    /**
     * The elite Falador diary makes this patch disease-free, so it takes the magic saplings
     * every other tree patch gives up.
     */
    static SeedPolicy.DiaryOverride faladorTreeDiaryClaim() {
        return new SeedPolicy.DiaryOverride("Falador", "Elite", Patches.FALADOR_TREE, Crop.MAGIC);
    }

    /**
     * The walk ends at the patch's north tile (2936, 3440), except the balloon's, which ends
     * at the south tile (2936, 3436) because it arrives from the south.
     */
    static RunStop taverleyTree() {
        return RunStop.builder(Patches.TAVERLEY_TREE)
                .at(Arrival.TAVERLEY)
                // 48 tiles: 34 from the Castle Wars landing (2440, 3090) to the basket, then
                // 14 from the Taverley station at (2937, 3422). The flight burns a log. Ring of
                // dueling only: the ring of the elements and the digsite pendant do not reach
                // Castle Wars.
                .via(all(cap(RING_OF_DUELING), cap(BALLOON_STATION_CASTLE_WARS),
                                cap(BALLOON_STATION_TAVERLEY)),
                        "Ring of dueling to Castle Wars, then the balloon to Taverley")
                .walks(34 + 14, Cost.CONSUMABLE)
                // 49 tiles from the house portal at (2893, 3465). The spell and the house
                // tablet both land there. A tie with the balloon, kept in this order.
                .to(Destination.HOUSE, cap(POH_IN_TAVERLEY), "with your house in Taverley")
                // Never a guess: the sentence tells the player where their house is.
                .onlyWhenCertain()
                .walks(49, Cost.CONSUMABLE)
                // 91 tiles from (2964, 3378). The wiki lists the Falador teleport among
                // Taverley's transport options, "well east of the village".
                .to(Destination.FALADOR, "then run north-west to Taverley")
                .walks(91, Cost.CONSUMABLE)
                // With 66 Agility, the climbing rocks on Taverley's east wall cut the walk:
                // 61 tiles to their east side at (2946, 3439), then 7 from (2943, 3439). They
                // cross east to west only, so they serve the way in and never the way out.
                .to(Destination.FALADOR, cap(TAVERLEY_CLIMBING_ROCKS),
                        "then run north and climb the rocks into Taverley")
                .walks(61 + 7, Cost.CONSUMABLE)
                // 78 tiles to the rocks from (2996, 3375), then 7.
                .via(all(cap(RING_OF_WEALTH), cap(TAVERLEY_CLIMBING_ROCKS)),
                        "Ring of wealth to Falador Park, then run north and climb the rocks "
                                + "into Taverley")
                .walks(78 + 7, Cost.CHARGE)
                // 108 tiles from (2996, 3375). The ring recharges only at the Fountain of
                // Rune, in the Wilderness.
                .via(RING_OF_WEALTH, "Ring of wealth to Falador Park, then run north-west "
                        + "to Taverley")
                .walks(108, Cost.CHARGE)
                // 114 tiles, free for a player already at the Falador tree patch. The
                // circuit walks Taverley before Falador Park, so on a trip this is a no.
                .via(Requirement.reachedPatch(Patches.FALADOR_TREE),
                        "Run north-west from the Falador tree patch")
                .walks(114, Cost.FREE)
                // 117 tiles from the Warriors' Guild at (2882, 3547).
                .via(COMBAT_BRACELET, "Combat bracelet to the Warriors' Guild, then run south "
                        + "to Taverley")
                .walks(117, Cost.CHARGE)
                // 119 tiles from Burthorpe at (2899, 3553).
                .via(GAMES_NECKLACE, "Games necklace to Burthorpe, then run south to Taverley")
                .walks(119, Cost.CHARGE)
                .skipIfUnreachable()
                .build();
    }

    /**
     * The slayer ring has no Grand Tree teleport by name, but its Stronghold Slayer Cave
     * entrance (beside Nieve) is inside the Tree Gnome Stronghold, a walk from this patch.
     * It asks for nothing else: a "slayer ring to Grand Tree spirit tree" route has
     * already arrived once the ring lands.
     *
     * <p>Not ordered like {@link #grandTreeFruit()}. The two patches are 44 walked tiles
     * apart with the arrivals between them: the slayer ring is seven tiles from this patch
     * and 46 from the fruit tree, and the diary cape is the other way round.
     */
    static RunStop grandTreeRegular() {
        return RunStop.builder(Patches.GRAND_TREE_TREE)
                .at(Arrival.GNOME_STRONGHOLD)
                // South, not south-west: the cave entrance is at (2431, 3422) and the
                // patch at (2436, 3415).
                .via(SLAYER_RING, "Slayer ring to the Stronghold Slayer Cave, "
                        + "then run south")
                .banks(18, Bank.GNOME_STRONGHOLD_SOUTH)
                .walks(7, Cost.CHARGE)
                // The south bank is on the route from the spirit tree to this patch, but a
                // forty-seven-tile detour on the way to the fruit tree. The slayer ring is
                // the reverse, so these figures live per rung per stop.
                .via(all(cap(POH_SPIRIT_TREE), cap(SPIRIT_TREE_NETWORK)),
                        "House spirit tree to the Grand Tree, then run south-west")
                .banks(0, Bank.GNOME_STRONGHOLD_SOUTH)
                // 36 from the Stronghold tree (2461, 3444).
                .walks(36, Cost.FREE)
                .via(DIARY_CAPE, "Achievement diary cape to the Elder Gnome child, "
                        + "then run south-west")
                .banks(0, Bank.GNOME_STRONGHOLD_SOUTH)
                // 48 from the Elder Gnome child (2467, 3459).
                .walks(48, Cost.FREE)
                .to(Destination.GRAND_EXCHANGE,
                        all(cap(VARROCK_DIARY_MEDIUM), cap(SPIRIT_TREE_NETWORK)),
                        "choosing the Grand Exchange, then run north-east to its spirit tree "
                                + "and ride it to the Grand Tree")
                .banks(0, Bank.GNOME_STRONGHOLD_SOUTH)
                // 30 from the Exchange landing to the Exchange tree, then the tree's 36.
                .walks(30 + 36, Cost.CONSUMABLE)
                .to(Destination.VARROCK, cap(SPIRIT_TREE_NETWORK),
                        "then run north to the Grand Exchange spirit tree and ride it "
                                + "to the Grand Tree")
                .banks(0, Bank.GNOME_STRONGHOLD_SOUTH)
                // 87 from Varrock square to the Exchange tree, then the tree's 36.
                .walks(87 + 36, Cost.CONSUMABLE)
                // The pod lands inside the tree, so the walk starts at a door. The north bank
                // is on the level above the landing and counts 3 tiles. The walk is 79 in all:
                // 74 from (2465, 3490), plus about five inside the tree.
                .via(ROYAL_SEED_POD, "Royal seed pod to the Grand Tree, then out "
                        + "of the tree and run south-west")
                .banks(3, Bank.GNOME_STRONGHOLD_NORTH)
                .walks(79, Cost.FREE)
                // The glider route changes floor three times and is unmeasured, so its bank
                // is left to the arrival's own grade, and it keeps its place, last.
                .via(all(cap(RING_OF_DUELING), cap(GNOME_GLIDER)),
                        "Ring of dueling to Al Kharid, then the glider")
                .skipIfUnreachable()
                .build();
    }

    static RunStop nemusRetreatTree() {
        return RunStop.builder(Patches.NEMUS_RETREAT_TREE)
                .at(Arrival.NEMUS_RETREAT)
                .accessGate(VARLAMORE_ACCESS)
                // The pendant needs the statue activated; the whistle rung makes that first
                // trip. The statue (1364, 3277) is 57 walked tiles from the patch.
                .via(all(cap(PENDANT_OF_ATES), cap(ATES_STATUE_NEMUS_RETREAT)),
                        "Pendant of Ates to Nemus Retreat")
                .walks(57, Cost.CHARGE)
                // 28 from the Quetzacalli Gorge landing site to the mountain guide, then 16
                // from where he lands, (1361, 3309): nearer than the statue, so it leads.
                .via(all(cap(QUETZAL_WHISTLE), cap(MOUNTAIN_GUIDE)),
                        "Quetzal whistle to Quetzacalli Gorge, then the mountain guide. "
                                + "On your first visit, activate the statue.")
                .walks(28 + 16, Cost.CHARGE)
                .skipIfUnreachable()
                .build();
    }

    // --- Fruit trees ----------------------------------------------------------

    /**
     * The house portal, the tablet, the house teleport and the spirit tree from a house
     * land 32 to 35 walked tiles from the patch, a tie the two free rungs win. The diary
     * cape and Barnaby's ferry walk about twice as far.
     *
     * <p>A redirected house tab is the Brimhaven teleport tablet: a scroll of redirection
     * makes one wherever the house sits. Each scroll is consumed, at 775 Nightmare Zone
     * points a trip.
     */
    static RunStop brimhavenFruit() {
        return RunStop.builder(Patches.BRIMHAVEN_FRUIT_TREE)
                .at(Arrival.BRIMHAVEN)
                // The town portal, which the cape reaches wherever the house is.
                .via(CONSTRUCTION_CAPE, "Construction cape to the Brimhaven house portal, "
                        + "then run north")
                .walks(34, Cost.FREE)
                // 63 from Pirate Jackie (2808, 3192).
                .via(DIARY_CAPE, "Achievement diary cape to Pirate Jackie the Fruit, "
                        + "then run west")
                .walks(63, Cost.FREE)
                .to(Destination.HOUSE, cap(POH_IN_BRIMHAVEN), "with your house in Brimhaven, "
                        + "then run north from the portal")
                .onlyWhenCertain()
                .walks(33, Cost.CONSUMABLE)
                .via(BRIMHAVEN_TABLET, "Brimhaven teleport tablet, then run north")
                .walks(32, Cost.CONSUMABLE)
                // 35 from the Brimhaven tree (2800, 3203), plus the walk to whichever tree
                // the player boards: none in the house, 30 to the Grand Exchange's from the
                // Exchange landing, 87 from Varrock square.
                .via(all(Networks.onTheSpiritTreeNetwork(), cap(SPIRIT_TREE_GROWN_BRIMHAVEN)),
                        "Ride a spirit tree to Brimhaven from your house or the Grand "
                                + "Exchange, then run west")
                .walks(35, Cost.FREE)
                // It asks only for a way to Ardougne and thirty coins, and lands closest to
                // the patch: 46 from the Ardougne teleport to Barnaby's dock (2683, 3271),
                // then 19 from his Brimhaven landing (2772, 3234).
                .to(Destination.ARDOUGNE, "then Captain Barnaby's ferry to Brimhaven, "
                        + "and run north-west")
                .fare(30)
                .walks(46 + 19, Cost.CONSUMABLE)
                .skipIfUnreachable()
                .build();
    }

    static RunStop catherbyFruit() {
        return RunStop.builder(Patches.CATHERBY_FRUIT_TREE)
                .at(Arrival.CATHERBY)
                .chain(Networks.catherby(63, 112))
                // The bank is eleven tiles off the way to this patch and forty off the way to
                // the herbs, so it is set here rather than on the shared chain.
                .banksAll(11, Bank.CATHERBY)
                .noFallbackGiven()
                .build();
    }

    /**
     * The diary cape and the house spirit tree land 13 walked tiles from the patch. The
     * balloon lands nine from it but starts with 16 from the earth altar to the Varrock
     * basket, 25 in all, and burns a magic log. The royal seed pod and the slayer ring walk
     * about 47, a tie the unlimited pod wins.
     *
     * <p>The Grand Exchange rung ranks low because the ride is free but not near: 87 walked
     * tiles from Varrock square to the Exchange tree, then the Stronghold tree's 13.
     *
     * <p>The medium Varrock diary's Exchange landing gets no rung here. It would walk 30 + 13,
     * and 43 opens a tie band holding the slayer ring's 46 but not the pod's 48. Every
     * account would then rank the ring above the pod, whether it held the diary or not, for
     * a five-tile saving over the pod that only diary holders get.
     *
     * <p>A balloon flight needs both the departure and the arrival station.
     */
    static RunStop grandTreeFruit() {
        return RunStop.builder(Patches.GRAND_TREE_FRUIT_TREE)
                .at(Arrival.GNOME_STRONGHOLD)
                .via(DIARY_CAPE, "Achievement diary cape to the Elder Gnome child, "
                        + "then run south-east")
                .walks(13, Cost.FREE)
                // About 48, counting about five tiles inside the tree.
                .via(ROYAL_SEED_POD, "Royal seed pod to the Grand Tree")
                .walks(48, Cost.FREE)
                // The ring alone: it lands in the Stronghold. Of all the rungs, only this one
                // passes the south bank on the way here; see grandTreeRegular().
                .via(SLAYER_RING, "Slayer ring to the Stronghold Slayer Cave, then run "
                        + "north-east")
                .banks(3, Bank.GNOME_STRONGHOLD_SOUTH)
                .walks(46, Cost.CHARGE)
                .via(all(cap(POH_SPIRIT_TREE), cap(SPIRIT_TREE_NETWORK)),
                        "House spirit tree to the Grand Tree")
                .walks(13, Cost.FREE)
                .to(Destination.VARROCK, cap(SPIRIT_TREE_NETWORK),
                        "then run north to the Grand Exchange spirit tree and ride it "
                                + "to the Grand Tree")
                .walks(87 + 13, Cost.CONSUMABLE)
                .via(all(cap(RING_OF_THE_ELEMENTS), cap(BALLOON_STATION_VARROCK),
                                cap(BALLOON_STATION_GRAND_TREE)),
                        "Ring of the elements to the earth altar, then the Varrock balloon "
                                + "to the Grand Tree. The flight costs one magic log.")
                .walks(16 + 9, Cost.CONSUMABLE)
                // Unmeasured: the glider lands three floors up the Grand Tree. It keeps its
                // place, last.
                .via(all(cap(RING_OF_DUELING), cap(GNOME_GLIDER)),
                        "Ring of dueling to Al Kharid, then the glider")
                // The other rungs measured 47 to 65 tiles, which all grade the same; the top
                // of the band is used.
                .banksAll(65, Bank.GNOME_STRONGHOLD_SOUTH)
                .noFallbackGiven()
                .build();
    }

    /**
     * The patch sits outside the hedge maze. The spirit tree lands <em>inside</em>, at
     * (2542, 3170): 30 walked tiles to Elkoy, whose escort ends 18 from the patch, 48 in
     * all. Fairy ring CIQ lands 81 away.
     *
     * <p>The fairy ring rung asks for the network rather than one approach to it, and the
     * bare spirit tree rung for a house tree or the Grand Exchange's, so where each ranks
     * depends on the account: a house tree puts the ride first, and the Exchange tree adds
     * the walk from the Varrock teleport's landing, 30 walked tiles from the Grand Exchange
     * landing the medium Varrock diary adds and 87 from Varrock square.
     *
     * <p><b>The top rungs are the same tree from a known position.</b> The fruit run works
     * the Grand Tree fruit patch (2476, 3446) just before this stop, and the Stronghold
     * spirit tree stands 13 walked tiles west of it, and 36 from the tree patch. From there
     * the ride needs no teleport in front of it.
     *
     * <p>Both Stronghold patches count: on a combined trip the regular tree run's visit to
     * the Grand Tree also leaves the player there.
     *
     * <p>Departing the Stronghold tree also needs The Grand Tree quest, which arriving at it
     * does not.
     */
    static RunStop treeGnomeVillageFruit() {
        return RunStop.builder(Patches.TREE_GNOME_VILLAGE_FRUIT_TREE)
                .at(Arrival.TREE_GNOME_VILLAGE)
                .via(all(
                                Requirement.reachedPatch(Patches.GRAND_TREE_FRUIT_TREE),
                                cap(SPIRIT_TREE_NETWORK),
                                cap(SPIRIT_TREE_STRONGHOLD)),
                        "Run west to the Stronghold spirit tree and ride it to the Tree Gnome "
                                + "Village, then ask Elkoy to lead you out of the maze and run "
                                + "north-west")
                .walks(13 + 48, Cost.FREE)
                // The same tree from the regular tree patch. A separate rung so each can say
                // which way to walk; the two patches lie on opposite sides of the tree.
                .via(all(
                                Requirement.reachedPatch(Patches.GRAND_TREE_TREE),
                                cap(SPIRIT_TREE_NETWORK),
                                cap(SPIRIT_TREE_STRONGHOLD)),
                        "Run north-east to the Stronghold spirit tree and ride it to the Tree "
                                + "Gnome Village, then ask Elkoy to lead you out of the maze "
                                + "and run north-west")
                .walks(36 + 48, Cost.FREE)
                // The same tree for a player the game reports standing in the Stronghold,
                // however they got there. Unmeasured, since the zone does not say where in
                // it they stand, so it keeps its place below the two rungs above.
                .via(all(
                                Requirement.standingIn(Zone.GNOME_STRONGHOLD),
                                cap(SPIRIT_TREE_NETWORK),
                                cap(SPIRIT_TREE_STRONGHOLD)),
                        "Run to the Stronghold spirit tree and ride it to the Tree Gnome "
                                + "Village, then ask Elkoy to lead you out of the maze and "
                                + "run north-west")
                .via(Networks.onTheFairyRingNetwork(),
                        "Fairy ring CIQ, north-west of Yanille, then run north-west")
                .walks(81, Cost.FREE)
                // 155 from the Castle Wars landing (2440, 3090).
                .via(RING_OF_DUELING, "Ring of dueling to Castle Wars, then run north")
                .walks(155, Cost.CHARGE)
                .via(Networks.onTheSpiritTreeNetwork(),
                        "Ride a spirit tree to the Tree Gnome Village from your house or the "
                                + "Grand Exchange, then ask Elkoy to lead you out of the maze "
                                + "and run north-west")
                .walks(48, Cost.FREE)
                // 300 from the Outpost (2430, 3349).
                .via(NECKLACE_OF_PASSAGE, "Necklace of passage to the Outpost, then run south")
                .walks(300, Cost.CHARGE)
                .noFallbackGiven()
                .build();
    }

    /**
     * One rung. Only the crystal teleports into Lletya (about twelve seconds); every other
     * way in walks 75 tiles of maze through the forest door at (2304, 3194), about eighty
     * seconds. The walk is not a rung because anyone who can walk in gets a crystal at the
     * same quest step, and Eluned recharges it.
     *
     * <p>A skip rather than no-alternative: the walk exists, so the run trims the patch.
     *
     * <p>Lletya access is Mourning's End Part I <em>started</em>, not finished: the city
     * opens partway through.
     */
    static RunStop lletyaFruit() {
        return RunStop.builder(Patches.LLETYA_FRUIT_TREE)
                .at(Arrival.LLETYA)
                // The access gate separates "cannot enter Lletya yet" from the skip below,
                // which is for a player who can enter but has no crystal.
                .accessGate(LLETYA_ACCESS)
                // Not the five tiles a straight line gives: a building stands between the
                // patch and the bank, and the way round is south.
                .via(all(cap(LLETYA_ACCESS), cap(TELEPORT_CRYSTAL)),
                        "Teleport crystal to Lletya, then run east")
                .banks(41, Bank.LLETYA)
                // From the crystal's landing (2330, 3171).
                .walks(16, Cost.CHARGE)
                .skipIfUnreachable()
                .build();
    }

    static RunStop kastoriFruit() {
        return RunStop.builder(Patches.KASTORI_FRUIT_TREE)
                .at(Arrival.KASTORI)
                .accessGate(VARLAMORE_ACCESS)
                .chain(Networks.kastori(34, 33, 41, 63))
                .skipIfUnreachable()
                .build();
    }

    // === Special runs ===========================================================

    static RunStop hespori() {
        // Walked to the cave entrance (1231, 3728); the leg inside is the same for every rung.
        return RunStop.builder(Patches.HESPORI)
                .at(Arrival.FARMING_GUILD)
                .chain(Networks.farmingGuild(19, 35, 93, 64))
                .noFallbackGiven()
                .plants(Crop.HESPORI)
                .build();
    }

    /**
     * The anima patch in the guild's west wing. Walked to its east side (1234, 3723), a tile
     * nearer the guild's landings than the cave entrance, so each rung walks a tile less than
     * Hespori's.
     *
     * <p>Planted with whichever plant the player has ticked that {@code AnimaChoice} ranks
     * first. Attas stands as the default crop only because the stop must name one.
     */
    static RunStop anima() {
        return RunStop.builder(Patches.ANIMA)
                .at(Arrival.FARMING_GUILD)
                .chain(Networks.farmingGuild(18, 34, 92, 63))
                .noFallbackGiven()
                .plants(Crop.ATTAS)
                .build();
    }

    /**
     * The two coral nurseries, underwater off the Great Conch's east coast. The player walks
     * down the steps at the end of the east dock (3272, 2463), and the patches lie four or
     * five tiles in, with a tool leprechaun just north of them.
     *
     * <p>Fairy ring CJQ walks 90 tiles to the steps. The Sailors' amulet and charter land at
     * the Summer Shore, whose rowboat puts in at the east dock five or six tiles from the
     * steps. Teleport to Boat also ends at that rowboat, but only with a boat moored at the
     * Summer Shore, which the game does not show, so it has no rung.
     *
     * <p>Planted with the best coral the player has ticked and holds a frag for. Umbral
     * stands as the default crop only because the stop must name one.
     */
    static RunStop coral() {
        return RunStop.builder(Patches.CORAL_EAST, Patches.CORAL_WEST)
                .at(Arrival.CORAL_NURSERIES)
                .accessGate(CORAL_NURSERY_ACCESS)
                .via(Networks.onTheFairyRingNetwork(),
                        "Fairy ring CJQ to the Great Conch, then run east to the dock steps")
                .walks(90, Cost.FREE)
                .via(all(cap(SAILORS_AMULET), cap(CHARTER_SHIP)),
                        "Sailors' amulet to Deepfin Point, charter to the Summer Shore, then "
                                + "the rowboat to the east dock. On your first visit, inspect "
                                + "the dock marker at Deepfin Point.")
                .fare(1800)
                .skipIfUnreachable()
                .plants(Crop.UMBRAL_CORAL)
                .build();
    }

    /**
     * Both rungs end in the same dive, and the walks before it are unmeasured, so the ladder
     * keeps its written order. Underwater, the anchor rope is six to ten tiles from the
     * patches.
     */
    static RunStop seaweed() {
        return RunStop.builder(Patches.SEAWEED_NORTH, Patches.SEAWEED_SOUTH)
                .at(Arrival.FOSSIL_ISLAND_SEAWEED)
                .via(all(cap(DIGSITE_PENDANT), cap(FOSSIL_ISLAND_ZIPLINE)),
                        "Digsite pendant to the House on the Hill, then the zipline down")
                // One row, not two: the Museum Camp rowboat goes straight to the
                // island north of the House on the Hill once the player has been
                // there. The once-visited unlock is not modelled; a
                // player who has never been rows two legs and still arrives.
                .via(all(cap(DIGSITE_PENDANT), cap(CANAL_BARGE)),
                        "Digsite pendant to the Digsite, barge to the Museum Camp, "
                                + "then row out and dive")
                .noFallbackGiven()
                // Protection is worth paying for only with numulite to spare.
                .actions(RunStop.StopAction.HARVEST, RunStop.StopAction.COMPOST,
                        RunStop.StopAction.PLANT, RunStop.StopAction.PAY_PROTECTION)
                .plants(Crop.SEAWEED)
                .build();
    }

    static RunStop guildContract() {
        // Walked to the herb patch (1238, 3726), in the west wing. Jane is nearer every
        // landing, by ten tiles or by four from the spirit tree, which ranks the rungs the
        // same way.
        return RunStop.builder(Patches.FARMING_GUILD_HERB)
                .at(Arrival.FARMING_GUILD)
                .chain(Networks.farmingGuild(11, 27, 85, 56))
                // Jane decides whether to go, not the herb patch: a herb growing in
                // the patch must not read as "nothing to do" while a contract is
                // outstanding.
                .errand("The contract is Guildmaster Jane's errand, not the herb "
                        + "patch's crop")
                .noFallbackGiven()
                .build();
    }

    /** A real yield bonus, but the preset may reserve this patch for contracts. */
    static PatchAttributes.Entry guildHerbAttributes() {
        return new PatchAttributes.Entry(
                false, null, null, null,
                List.of(new PatchAttributes.BonusTier("Kourend", "Hard", 5)), List.of(),
                false, Preset.DEFAULT.isGuildHerbPatchReserved());
    }

    static RunStop guildCompostBin() {
        return RunStop.builder(Patches.FARMING_GUILD_BIG_COMPOST)
                .at(Arrival.FARMING_GUILD)
                .chain(Networks.farmingGuild(24, 40, 98, 69))
                // The guild door needs 45 Farming, boostable from 42 with a garden
                // pie. The gate sits on this stop because the other bins are public.
                .gate(UnlockGate.boostableSkill(Skill.FARMING, 45, 42))
                .noFallbackGiven()
                .actions(RunStop.StopAction.FILL_COMPOST_BIN,
                        RunStop.StopAction.EMPTY_COMPOST_BIN)
                .build();
    }

    static RunStop catherbyCompostBin() {
        return compostBin(Patches.CATHERBY_COMPOST, Arrival.CATHERBY, 7);
    }

    static RunStop ardougneCompostBin() {
        return compostBin(Patches.ARDOUGNE_COMPOST, Arrival.ARDOUGNE_FARM, 7);
    }

    // One tile.
    static RunStop faladorCompostBin() {
        return compostBin(Patches.FALADOR_COMPOST, Arrival.FALADOR_FARM, 1);
    }

    static RunStop hosidiusCompostBin() {
        return compostBin(Patches.HOSIDIUS_COMPOST, Arrival.HOSIDIUS, 10);
    }

    static RunStop morytaniaCompostBin() {
        return compostBin(Patches.MORYTANIA_COMPOST, Arrival.CANIFIS, 7);
    }

    /**
     * West of Civitas illa Fortis, twelve tiles from the Ortus Farm herb patch. It rides
     * along on the herb visit as the five farm bins do. Its gate is Varlamore access: every
     * way to Ortus Farm needs Children of the Sun.
     */
    static RunStop civitasCompostBin() {
        return RunStop.builder(Patches.CIVITAS_COMPOST)
                .at(Arrival.ORTUS_FARM)
                .accessGate(VARLAMORE_ACCESS)
                .viaAlways("Fill the bin while passing the neighbouring allotment")
                .walks(12, Cost.FREE)
                .noFallbackGiven()
                .actions(RunStop.StopAction.FILL_COMPOST_BIN, RunStop.StopAction.EMPTY_COMPOST_BIN)
                .optional("Folded into a herb run rather than visited for its own sake")
                .build();
    }

    /**
     * North-east Prifddinas. No herb patch stands there, so this bin is a journey of its own
     * and carries a chain, as the guild's does. The city's spirit tree lands at (3274, 6123),
     * 41 tiles from the bin; the teleport crystal's Prifddinas dial at (3264, 6066), 64.
     * Both need Song of the Elves, the stop's access gate, and the crystal's dial needs it
     * even where the crystal is held from Lletya.
     */
    static RunStop prifddinasCompostBin() {
        return RunStop.builder(Patches.PRIFDDINAS_COMPOST)
                .at(Arrival.PRIFDDINAS)
                .accessGate(PRIFDDINAS_ACCESS)
                .via(all(Networks.onTheSpiritTreeNetwork(), cap(PRIFDDINAS_ACCESS)),
                        "Spirit tree to Prifddinas, then run south-east")
                .banks(27, Bank.PRIFDDINAS_NORTH)
                .walks(41, Cost.FREE)
                .via(all(cap(PRIFDDINAS_ACCESS), cap(TELEPORT_CRYSTAL)),
                        "Teleport crystal to Prifddinas, then run north-east")
                .banks(22, Bank.PRIFDDINAS_NORTH)
                .walks(64, Cost.CHARGE)
                .skipIfUnreachable()
                .actions(RunStop.StopAction.FILL_COMPOST_BIN, RunStop.StopAction.EMPTY_COMPOST_BIN)
                .build();
    }

    /**
     * An allotment that grows watermelons for the bin beside it.
     *
     * <p>It rides along on the bin's arrival, and its harvest goes into the bin rather than
     * the bag. It is no reason to go, except beside a bin with its own route, as at
     * Prifddinas, where no herb visit passes. It is switched with the bin's site, not with the
     * watermelon switch, which belongs to the Catherby north allotment's snape grass.
     *
     * @param gate the bin's access gate, or null where the bin has none
     */
    private static RunStop feedingAllotment(PatchRef allotment, PatchRef bin, Arrival at,
            Capability gate) {
        return RunStop.builder(allotment)
                .at(at)
                .accessGate(gate)
                .viaAlways("Grow watermelons for the neighbouring compost bin")
                .noFallbackGiven()
                .plants(Crop.WATERMELON)
                .feeds(bin)
                .optional("Grows the fill for the compost bin beside it")
                .build();
    }

    static RunStop faladorNorthWestAllotment() {
        return feedingAllotment(Patches.FALADOR_NORTH_WEST_ALLOTMENT, Patches.FALADOR_COMPOST,
                Arrival.FALADOR_FARM, null);
    }

    static RunStop faladorSouthEastAllotment() {
        return feedingAllotment(Patches.FALADOR_SOUTH_EAST_ALLOTMENT, Patches.FALADOR_COMPOST,
                Arrival.FALADOR_FARM, null);
    }

    /**
     * Catherby north feeds the bin only while its snape grass stop is off: the same patch
     * cannot grow both. {@code PlantingGate} reads that off the snape grass stop's switches.
     */
    static RunStop catherbyNorthAllotment() {
        return feedingAllotment(Patches.CATHERBY_NORTH_ALLOTMENT, Patches.CATHERBY_COMPOST,
                Arrival.CATHERBY, null);
    }

    static RunStop catherbySouthAllotment() {
        return feedingAllotment(Patches.CATHERBY_SOUTH_ALLOTMENT, Patches.CATHERBY_COMPOST,
                Arrival.CATHERBY, null);
    }

    static RunStop ardougneNorthAllotment() {
        return feedingAllotment(Patches.ARDOUGNE_NORTH_ALLOTMENT, Patches.ARDOUGNE_COMPOST,
                Arrival.ARDOUGNE_FARM, null);
    }

    static RunStop ardougneSouthAllotment() {
        return feedingAllotment(Patches.ARDOUGNE_SOUTH_ALLOTMENT, Patches.ARDOUGNE_COMPOST,
                Arrival.ARDOUGNE_FARM, null);
    }

    static RunStop hosidiusNorthEastAllotment() {
        return feedingAllotment(Patches.HOSIDIUS_NORTH_EAST_ALLOTMENT,
                Patches.HOSIDIUS_COMPOST, Arrival.HOSIDIUS, null);
    }

    static RunStop hosidiusSouthWestAllotment() {
        return feedingAllotment(Patches.HOSIDIUS_SOUTH_WEST_ALLOTMENT,
                Patches.HOSIDIUS_COMPOST, Arrival.HOSIDIUS, null);
    }

    static RunStop morytaniaNorthWestAllotment() {
        return feedingAllotment(Patches.MORYTANIA_NORTH_WEST_ALLOTMENT,
                Patches.MORYTANIA_COMPOST, Arrival.CANIFIS, null);
    }

    static RunStop morytaniaSouthEastAllotment() {
        return feedingAllotment(Patches.MORYTANIA_SOUTH_EAST_ALLOTMENT,
                Patches.MORYTANIA_COMPOST, Arrival.CANIFIS, null);
    }

    static RunStop civitasNorthAllotment() {
        return feedingAllotment(Patches.CIVITAS_NORTH_ALLOTMENT, Patches.CIVITAS_COMPOST,
                Arrival.ORTUS_FARM, VARLAMORE_ACCESS);
    }

    static RunStop civitasSouthAllotment() {
        return feedingAllotment(Patches.CIVITAS_SOUTH_ALLOTMENT, Patches.CIVITAS_COMPOST,
                Arrival.ORTUS_FARM, VARLAMORE_ACCESS);
    }

    static RunStop prifddinasNorthAllotment() {
        return feedingAllotment(Patches.PRIFDDINAS_NORTH_ALLOTMENT, Patches.PRIFDDINAS_COMPOST,
                Arrival.PRIFDDINAS, PRIFDDINAS_ACCESS);
    }

    static RunStop prifddinasSouthAllotment() {
        return feedingAllotment(Patches.PRIFDDINAS_SOUTH_ALLOTMENT, Patches.PRIFDDINAS_COMPOST,
                Arrival.PRIFDDINAS, PRIFDDINAS_ACCESS);
    }

    /**
     * A herb-run compost bin, filled straight from the neighbouring allotment.
     *
     * <p>The bin has no travel chain of its own. It takes its farm's arrival, so it
     * always lands on the same visit as the allotment that feeds it.
     *
     * @param fromHerbPatch walked tiles from the farm's herb patch to the bin
     */
    private static RunStop compostBin(PatchRef bin, Arrival at, int fromHerbPatch) {
        return RunStop.builder(bin)
                .at(at)
                .viaAlways("Fill the bin while passing the neighbouring allotment")
                .walks(fromHerbPatch, Cost.FREE)
                .noFallbackGiven()
                .actions(RunStop.StopAction.FILL_COMPOST_BIN, RunStop.StopAction.EMPTY_COMPOST_BIN)
                .optional("Folded into a herb run rather than visited for its own sake")
                .build();
    }
}
