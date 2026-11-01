package com.larryfarming.farming.catalogue;

import com.larryfarming.data.Tables;
import com.larryfarming.farming.model.*;
import java.util.*;

/**
 * Every way the runs know of reaching each place: the spell, and the tablet that lands in
 * the same spot, read from the teleports table.
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

    private static final Table TABLE = new Table("/com/larryfarming/teleports.tsv");

    private static final Map<Destination, List<TeleportMeans>> MEANS = TABLE.means;
    private static final Map<Capability, Map<Rune, Integer>> SPELL_COSTS = TABLE.costs;

    /**
     * Spellbook Swap: Lunar, 96 Magic, Dream Mentor. It opens another book the player has
     * unlocked for one cast within two minutes, then returns them to Lunar.
     */
    public static final TeleportMeans SPELLBOOK_SWAP = TABLE.swap;

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

    /** The teleports table, read once. */
    private static final class Table {

        final Map<Destination, List<TeleportMeans>> means;
        final Map<Capability, Map<Rune, Integer>> costs;
        final TeleportMeans swap;

        Table(String resource) {
            Map<Destination, List<TeleportMeans>> byPlace = new EnumMap<>(Destination.class);
            Map<Capability, Map<Rune, Integer>> byCapability = new EnumMap<>(Capability.class);
            TeleportMeans swapRow = null;
            for (Tables.Row row : Tables.read(resource)) {
                switch (row.text(0)) {
                    case "spell":
                    case "swap":
                        TeleportMeans spell = TeleportMeans.spell(row.requireWidth(9).text(2),
                                row.constant(Spellbook.class, 3), row.integer(4), nullable(row, 5), row.integer(6), runes(row, 8));
                        if (row.text(0).equals("swap")) {
                            if (swapRow != null) {
                                throw row.error("a second swap row");
                            }
                            swapRow = spell;
                        } else {
                            byPlace.computeIfAbsent(row.constant(Destination.class, 1), place -> new ArrayList<>()).add(spell);
                        }
                        break;
                    case "tablet":
                        byPlace.computeIfAbsent(row.requireWidth(6).constant(Destination.class, 1),
                                place -> new ArrayList<>()).add(TeleportMeans.tablet(row.text(2), row.integer(3), nullable(row, 5)));
                        break;
                    case "same":
                        List<TeleportMeans> shared = byPlace.get(row.requireWidth(3).constant(Destination.class, 2));
                        if (shared == null) {
                            throw row.error("no means above this line to share");
                        }
                        byPlace.put(row.constant(Destination.class, 1), shared);
                        break;
                    case "runes":
                        Object[] pairs = runes(row.requireWidth(3), 2);
                        Map<Rune, Integer> cost = new EnumMap<>(Rune.class);
                        for (int i = 0; i < pairs.length; i += 2) {
                            cost.put((Rune) pairs[i], (Integer) pairs[i + 1]);
                        }
                        byCapability.put(row.constant(Capability.class, 1), Collections.unmodifiableMap(cost));
                        break;
                    default:
                        throw row.error("no kind " + row.text(0));
                }
            }
            if (swapRow == null) {
                throw new IllegalStateException(resource + ": no swap row");
            }
            // Places that share means share one list, as the table says.
            Map<List<TeleportMeans>, List<TeleportMeans>> frozen = new IdentityHashMap<>();
            byPlace.replaceAll((place, list) -> frozen.computeIfAbsent(list, List::copyOf));
            means = Collections.unmodifiableMap(byPlace);
            costs = Collections.unmodifiableMap(byCapability);
            swap = swapRow;
        }

        private static String nullable(Tables.Row row, int column) {
            String text = row.text(column);
            return text.isEmpty() ? null : text;
        }

        /** The cell's rune-count pairs, as {@link TeleportMeans#spell} takes them. */
        private static Object[] runes(Tables.Row row, int column) {
            String[] words = row.text(column).split(" ");
            if (words.length % 2 != 0) {
                throw row.error("runes come in rune-count pairs: " + row.text(column));
            }
            Object[] pairs = new Object[words.length];
            for (int i = 0; i < words.length; i += 2) {
                try {
                    pairs[i] = Rune.valueOf(words[i]);
                    pairs[i + 1] = Integer.parseInt(words[i + 1]);
                } catch (IllegalArgumentException e) {
                    throw row.error("not a rune and a count: " + words[i] + " " + words[i + 1]);
                }
            }
            return pairs;
        }
    }
}
