package com.larryfarming.engine;

import net.runelite.api.gameval.VarbitID;

import java.util.*;
import java.util.function.IntUnaryOperator;

/**
 * Reads the contents of a rune pouch (or divine rune pouch) from RuneLite varbits.
 *
 * <p>Runes in a pouch leave the inventory item array. Each of the six slots has a TYPE varbit
 * (an index into the {@code RUNEPOUCH_RUNE} enum, {@code 0} = empty) and an aligned QUANTITY
 * varbit, read the same way as RuneLite's {@code RunepouchOverlay}.
 *
 * <p>The caller supplies the index→item-id translation
 * ({@code client.getEnum(EnumID.RUNEPOUCH_RUNE)}), so this class needs no client and runs
 * on a plain lambda.
 */
public final class RunePouch {

    private RunePouch() {
    }

    /** Item IDs that count as a rune pouch occupying an inventory or bank slot. */
    public static final Set<Integer> POUCH_ITEM_IDS = Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(
            12791,  // Rune pouch
            24416,  // Rune pouch (Trouver-locked variant)
            27281,  // Divine rune pouch
            27509   // Divine rune pouch (Trouver-locked variant)
    )));

    /** Per-slot rune TYPE varbits (index into the RUNEPOUCH_RUNE enum; 0 = empty slot). */
    private static final int[] TYPE_VARBITS = {
            VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
            VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
    };

    /** Per-slot rune QUANTITY varbits, index-aligned with {@link #TYPE_VARBITS}. */
    private static final int[] AMOUNT_VARBITS = {
            VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
            VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
    };

    /** Varbit IDs whose change means the pouch contents must be re-read. */
    public static final Set<Integer> CONTENT_VARBITS;

    static {
        Set<Integer> v = new HashSet<>();
        for (int id : TYPE_VARBITS) {
            v.add(id);
        }
        for (int id : AMOUNT_VARBITS) {
            v.add(id);
        }
        CONTENT_VARBITS = Collections.unmodifiableSet(v);
    }

    public static boolean isContentVarbit(int varbitId) {
        return CONTENT_VARBITS.contains(varbitId);
    }

    /**
     * Reads the current rune pouch contents into a map of rune item ID → total quantity,
     * summing any rune that appears in more than one slot.
     *
     * @param varbitReader      reads a varbit value by ID (e.g. {@code client::getVarbitValue})
     * @param runeIndexToItemId maps a TYPE varbit value (rune index) to its rune item ID, e.g.
     *                          {@code client.getEnum(EnumID.RUNEPOUCH_RUNE)::getIntValue}; may
     *                          return a non-positive value for an unknown index
     * @return empty when the pouch is empty
     */
    public static Map<Integer, Integer> read(IntUnaryOperator varbitReader, IntUnaryOperator runeIndexToItemId) {
        Map<Integer, Integer> contents = new HashMap<>();
        for (int slot = 0; slot < TYPE_VARBITS.length; slot++) {
            int runeIndex = varbitReader.applyAsInt(TYPE_VARBITS[slot]);
            int amount = varbitReader.applyAsInt(AMOUNT_VARBITS[slot]);
            if (runeIndex <= 0 || amount <= 0) {
                continue;
            }
            int itemId = runeIndexToItemId.applyAsInt(runeIndex);
            if (itemId <= 0) {
                continue;
            }
            contents.merge(itemId, amount, Integer::sum);
        }
        return contents;
    }
}
