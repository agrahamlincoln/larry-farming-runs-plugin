package com.larryfarming.farming.catalogue;

import static com.larryfarming.farming.model.Rune.AIR;
import static com.larryfarming.farming.model.Rune.ASTRAL;
import static com.larryfarming.farming.model.Rune.BLOOD;
import static com.larryfarming.farming.model.Rune.COSMIC;
import static com.larryfarming.farming.model.Rune.EARTH;
import static com.larryfarming.farming.model.Rune.FIRE;
import static com.larryfarming.farming.model.Rune.LAW;
import static com.larryfarming.farming.model.Rune.NATURE;
import static com.larryfarming.farming.model.Rune.SOUL;
import static com.larryfarming.farming.model.Rune.WATER;

import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.Rune;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.farming.model.TeleportMeans;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every way the runs know of reaching each place: the spell, and the tablet that lands in
 * the same spot.
 *
 * <p>Each spell's icon number is a RuneLite sprite ID ({@code SpriteID.Magicon},
 * {@code Magicon2}, {@code LunarMagicOn}, {@code MagicNecroOn}), written as a literal
 * because those constants hide behind indexed names like {@code _22}.
 *
 * <p>Levels and rune costs come from the OSRS wiki spellbook pages
 * (<a href="https://oldschool.runescape.wiki/w/Standard_spellbook">standard</a>,
 * <a href="https://oldschool.runescape.wiki/w/Lunar_spellbook">Lunar</a>,
 * <a href="https://oldschool.runescape.wiki/w/Arceuus_spellbook">Arceuus</a>).
 *
 * <p>Teleport to House is a standard-book spell, so a player on Arceuus or Lunar reaches
 * their house only by tablet. The runs therefore ask for a place rather than an item.
 *
 * <p>Spellbook Swap's level, quest and runes come from the wiki's
 * <a href="https://oldschool.runescape.wiki/w/Spellbook_Swap">Spellbook Swap</a> page.
 */
public final class Teleports {

    private Teleports() {
    }

    private static final Map<Destination, List<TeleportMeans>> MEANS = build();
    private static final Map<Capability, Map<Rune, Integer>> SPELL_COSTS = spellCosts();

    /**
     * Spellbook Swap: Lunar, 96 Magic, Dream Mentor. It opens another book the player has
     * unlocked for one cast within two minutes, then returns them to Lunar.
     */
    public static final TeleportMeans SPELLBOOK_SWAP = TeleportMeans.spell("Spellbook Swap",
            Spellbook.LUNAR, 96, "Dream Mentor", 582, ASTRAL, 3, COSMIC, 2, LAW, 1);

    private static final Map<Destination, TeleportMeans> BY_SWAP = bySwap();

    /** Every way of reaching this place, spells before tablets. Never null. */
    public static List<TeleportMeans> to(Destination destination) {
        List<TeleportMeans> means = MEANS.get(destination);
        return means != null ? means : Collections.emptyList();
    }

    /**
     * Spellbook Swap then this place's spell from another book, or null when no spell off
     * Lunar reaches it. Not among {@link #to}: only a rung that names the swap offers it.
     */
    public static TeleportMeans bySwap(Destination destination) {
        return BY_SWAP.get(destination);
    }

    private static Map<Destination, TeleportMeans> bySwap() {
        Map<Destination, TeleportMeans> swaps = new EnumMap<>(Destination.class);
        for (Map.Entry<Destination, List<TeleportMeans>> place : MEANS.entrySet()) {
            for (TeleportMeans means : place.getValue()) {
                if (means.isSpell() && means.getSpellbook() != SPELLBOOK_SWAP.getSpellbook()) {
                    swaps.put(place.getKey(), TeleportMeans.afterSwap(SPELLBOOK_SWAP, means));
                    break;
                }
            }
        }
        return Collections.unmodifiableMap(swaps);
    }

    /**
     * What one cast of a capability spell costs, or empty for anything that is not cast.
     *
     * <p>Covers the spells with no destination: three cast at a patch, and the respawn
     * teleport, which lands wherever the player set it.
     */
    public static Map<Rune, Integer> runesFor(Capability capability) {
        Map<Rune, Integer> cost = SPELL_COSTS.get(capability);
        return cost != null ? cost : Collections.emptyMap();
    }

    private static Map<Capability, Map<Rune, Integer>> spellCosts() {
        Map<Capability, Map<Rune, Integer>> costs = new EnumMap<>(Capability.class);
        costs.put(Capability.RESPAWN_TELEPORT, runes(LAW, 1, SOUL, 1));
        costs.put(Capability.RESURRECT_CROP,
                runes(EARTH, 25, BLOOD, 8, NATURE, 12, SOUL, 8));
        costs.put(Capability.FERTILE_SOIL, runes(EARTH, 15, ASTRAL, 3, NATURE, 2));
        costs.put(Capability.HUMIDIFY, runes(WATER, 3, ASTRAL, 1, FIRE, 1));
        return Collections.unmodifiableMap(costs);
    }

    private static Map<Rune, Integer> runes(Object... pairs) {
        Map<Rune, Integer> cost = new EnumMap<>(Rune.class);
        for (int i = 0; i < pairs.length; i += 2) {
            cost.put((Rune) pairs[i], (Integer) pairs[i + 1]);
        }
        return Collections.unmodifiableMap(cost);
    }

    private static Map<Destination, List<TeleportMeans>> build() {
        Map<Destination, List<TeleportMeans>> means = new EnumMap<>(Destination.class);

        means.put(Destination.CATHERBY, List.of(
                TeleportMeans.spell("Catherby Teleport", Spellbook.LUNAR, 87,
                        "Lunar Diplomacy", 556, WATER, 10, ASTRAL, 3, LAW, 3),
                TeleportMeans.tablet("Catherby teleport tablet", 24961)));

        means.put(Destination.CAMELOT, List.of(
                TeleportMeans.spell("Camelot Teleport", Spellbook.STANDARD, 45, null, 37,
                        AIR, 5, LAW, 1),
                TeleportMeans.tablet("Camelot teleport tablet", 8010)));

        List<TeleportMeans> varrock = List.of(
                TeleportMeans.spell("Varrock Teleport", Spellbook.STANDARD, 25, null, 27,
                        AIR, 3, FIRE, 1, LAW, 1),
                TeleportMeans.tablet("Varrock teleport tablet", 8007));
        means.put(Destination.VARROCK, varrock);
        // The medium Varrock diary adds the Grand Exchange to the same spell and tablet: the
        // spell's destination is switched by right-click, and the tablet gains a Grand
        // Exchange option. The diary gate sits on the rungs, since neither means names it.
        means.put(Destination.GRAND_EXCHANGE, varrock);

        means.put(Destination.LUMBRIDGE, List.of(
                TeleportMeans.spell("Lumbridge Teleport", Spellbook.STANDARD, 31, null, 30,
                        AIR, 3, EARTH, 1, LAW, 1),
                TeleportMeans.tablet("Lumbridge teleport tablet", 8008)));

        // The Falador chains are all jewellery; this is the fallback for a player holding
        // none of it.
        means.put(Destination.FALADOR, List.of(
                TeleportMeans.spell("Falador Teleport", Spellbook.STANDARD, 37, null, 33,
                        AIR, 3, WATER, 1, LAW, 1),
                TeleportMeans.tablet("Falador teleport tablet", 8009)));

        // The fallback for an account with no diary cloak. Plague
        // City gates the spell, the tablet and the house portal alike.
        means.put(Destination.ARDOUGNE, List.of(
                TeleportMeans.spell("Ardougne Teleport", Spellbook.STANDARD, 51,
                        "Plague City", 54, WATER, 2, LAW, 2),
                TeleportMeans.tablet("Ardougne teleport tablet", 8011, "Plague City")));

        means.put(Destination.HOUSE, List.of(
                TeleportMeans.spell("Teleport to House", Spellbook.STANDARD, 40, null, 355,
                        AIR, 1, EARTH, 1, LAW, 1),
                TeleportMeans.tablet("Teleport to house tablet", 8013)));

        means.put(Destination.FISHING_GUILD, List.of(
                TeleportMeans.spell("Fishing Guild Teleport", Spellbook.LUNAR, 85,
                        "Lunar Diplomacy", 555, WATER, 10, ASTRAL, 3, LAW, 3),
                TeleportMeans.tablet("Fishing Guild teleport tablet", 24959)));

        // Arceuus arrivals. Each tablet here is made at the Arceuus lectern and works on any
        // book, but breaks only once the spell's quest is finished. Fenkenstrain's tablet
        // lands where the spell does (3548,3529).
        means.put(Destination.FENKENSTRAINS_CASTLE, List.of(
                TeleportMeans.spell("Fenkenstrain's Castle Teleport", Spellbook.ARCEUUS, 48,
                        "Priest in Peril", 1259, EARTH, 1, LAW, 1, SOUL, 1),
                TeleportMeans.tablet("Fenkenstrain's castle teleport", 19621,
                        "Priest in Peril")));

        // The tablet lets non-Arceuus players reach fairy ring CKS this way (see
        // Networks.onTheFairyRingNetwork()).
        means.put(Destination.SALVE_GRAVEYARD, List.of(
                TeleportMeans.spell("Salve Graveyard Teleport", Spellbook.ARCEUUS, 40,
                        "Priest in Peril", 1254, LAW, 1, SOUL, 2),
                TeleportMeans.tablet("Salve graveyard teleport", 19619, "Priest in Peril")));

        // Both land at the manor's front doors. Neither asks for a quest.
        means.put(Destination.DRAYNOR_MANOR, List.of(
                TeleportMeans.spell("Draynor Manor Teleport", Spellbook.ARCEUUS, 17, null,
                        1253, LAW, 1, EARTH, 1, WATER, 1),
                TeleportMeans.tablet("Draynor manor teleport", 19615)));

        // The only direct way to the island: every other route ends in Brother Tranquility's
        // boat from Mos Le'Harmless. Both need The Great Brain Robbery finished. The spell
        // lands at 3797,2866 and the tablet one tile north.
        means.put(Destination.HARMONY_ISLAND, List.of(
                TeleportMeans.spell("Harmony Island Teleport", Spellbook.ARCEUUS, 65,
                        "The Great Brain Robbery", 1261, LAW, 1, SOUL, 1, NATURE, 1),
                TeleportMeans.tablet("Harmony island teleport", 19625,
                        "The Great Brain Robbery")));

        means.put(Destination.ARCEUUS_LIBRARY, List.of(
                TeleportMeans.spell("Arceuus Library Teleport", Spellbook.ARCEUUS, 6, null,
                        1252, EARTH, 2, LAW, 1)));

        means.put(Destination.CIVITAS_ILLA_FORTIS, List.of(
                TeleportMeans.spell("Civitas illa Fortis Teleport", Spellbook.STANDARD, 54,
                        "Twilight's Promise", 367, EARTH, 1, FIRE, 1, LAW, 2)));

        return Collections.unmodifiableMap(means);
    }
}
