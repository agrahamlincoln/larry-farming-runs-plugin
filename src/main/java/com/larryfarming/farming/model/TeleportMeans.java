package com.larryfarming.farming.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One way of reaching a {@link Destination}: a spell on a particular spellbook, or a
 * tablet that works from any of them.
 *
 * <p>A spell costs only runes but needs the right book open. A tablet is a consumable but
 * works on any book. The planner chooses between them against live state.
 *
 * <p>A third kind is a spell reached through Spellbook Swap: two casts, both paid in runes,
 * which a rung asks for by name ({@link #afterSwap}).
 */
public final class TeleportMeans {

    public enum Kind {
        SPELL,
        TABLET
    }

    private final Kind kind;
    private final String displayName;
    private final Spellbook spellbook;
    private final int magicLevel;
    private final String questName;
    private final Map<Rune, Integer> runes;
    private final int itemId;
    private final int spriteId;
    private final TeleportMeans swappedSpell;

    private TeleportMeans(Kind kind, String displayName, Spellbook spellbook, int magicLevel,
            String questName, Map<Rune, Integer> runes, int itemId, int spriteId,
            TeleportMeans swappedSpell) {
        this.kind = kind;
        this.displayName = displayName;
        this.spellbook = spellbook;
        this.magicLevel = magicLevel;
        this.questName = questName;
        this.runes = Collections.unmodifiableMap(runes);
        this.itemId = itemId;
        this.spriteId = spriteId;
        this.swappedSpell = swappedSpell;
    }

    /**
     * A teleport spell.
     *
     * @param questName the quest it is locked behind, or null when only the level gates it
     * @param spriteId  the spell's icon in the game's spellbook
     * @param runes     what one cast costs, in pairs of rune and count
     */
    public static TeleportMeans spell(String displayName, Spellbook spellbook, int magicLevel,
            String questName, int spriteId, Object... runes) {
        Map<Rune, Integer> costs = new EnumMap<>(Rune.class);
        if (runes.length % 2 != 0) {
            throw new IllegalArgumentException(displayName + ": runes come in rune-count pairs");
        }
        for (int i = 0; i < runes.length; i += 2) {
            costs.put((Rune) runes[i], (Integer) runes[i + 1]);
        }
        if (costs.isEmpty()) {
            throw new IllegalArgumentException(displayName + ": a teleport spell costs runes");
        }
        return new TeleportMeans(Kind.SPELL, displayName, spellbook, magicLevel, questName,
                costs, 0, spriteId, null);
    }

    /** A teleport tablet, usable on any spellbook with no quest behind it. */
    public static TeleportMeans tablet(String displayName, int itemId) {
        return tablet(displayName, itemId, null);
    }

    /**
     * A teleport tablet, usable on any spellbook once a quest is finished. Breaking one asks
     * for no Magic level.
     *
     * @param questName the quest the tablet refuses to work without, or null for none
     */
    public static TeleportMeans tablet(String displayName, int itemId, String questName) {
        return new TeleportMeans(Kind.TABLET, displayName, Spellbook.ANY, 0, questName,
                new EnumMap<>(Rune.class), itemId, 0, null);
    }

    /**
     * Two casts as one way there: {@code swap}, which opens another book for a single
     * spell, then {@code spell} from that book.
     *
     * <p>Reads as the swap for its book, level, quest and icon, since the player casts it
     * first and from the book they are on. The runes are both casts' together, so the bag
     * packs them as one line each. {@link #getSwappedSpell()} keeps the second spell's own
     * level and quest.
     */
    public static TeleportMeans afterSwap(TeleportMeans swap, TeleportMeans spell) {
        if (!swap.isSpell() || !spell.isSpell() || swap.isSwap() || spell.isSwap()) {
            throw new IllegalArgumentException("a swap joins two plain spells");
        }
        if (swap.getSpellbook() == spell.getSpellbook()) {
            throw new IllegalArgumentException(
                    spell.getDisplayName() + " is on the book the swap is cast from");
        }
        Map<Rune, Integer> both = new EnumMap<>(Rune.class);
        both.putAll(swap.getRunes());
        spell.getRunes().forEach((rune, count) -> both.merge(rune, count, Integer::sum));
        return new TeleportMeans(Kind.SPELL,
                swap.getDisplayName() + ", then " + spell.getDisplayName(),
                swap.getSpellbook(), swap.getMagicLevel(), swap.getQuestName(), both, 0,
                swap.getSpriteId(), spell);
    }

    public boolean isSpell() {
        return kind == Kind.SPELL;
    }

    public boolean isTablet() {
        return kind == Kind.TABLET;
    }

    public Kind getKind() {
        return kind;
    }

    /** What to call this in an instruction: "Catherby Teleport", "Camelot teleport tablet". */
    public String getDisplayName() {
        return displayName;
    }

    /** The book this spell lives on, or {@link Spellbook#ANY} for a tablet. */
    public Spellbook getSpellbook() {
        return spellbook;
    }

    public int getMagicLevel() {
        return magicLevel;
    }

    /** The quest behind the spell or tablet, or null when there is none. */
    public String getQuestName() {
        return questName;
    }

    /** What one cast costs. Empty for a tablet. */
    public Map<Rune, Integer> getRunes() {
        return runes;
    }

    /** The tablet's item ID, or 0 for a spell. */
    public int getItemId() {
        return itemId;
    }

    /** The spell's icon in the game's spellbook, or 0 for a tablet. */
    public int getSpriteId() {
        return spriteId;
    }

    /** Whether this is a swap followed by a spell from another book; see {@link #afterSwap}. */
    public boolean isSwap() {
        return swappedSpell != null;
    }

    /** The spell cast after the swap, or null when this is not a swap. */
    public TeleportMeans getSwappedSpell() {
        return swappedSpell;
    }
}
