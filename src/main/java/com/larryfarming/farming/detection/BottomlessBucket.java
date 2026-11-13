package com.larryfarming.farming.detection;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.model.PlayerState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * The bottomless compost bucket: what it holds, read from the chat and dialogue lines that
 * state its uses, and whether it is stored at the tool leprechaun, which a varbit says
 * exactly.
 *
 * <p>No RuneLite client types.
 */
public final class BottomlessBucket {

    /** The compost kinds the bucket holds, weakest first. */
    public static final List<Crop> KINDS = List.of(Crop.COMPOST, Crop.SUPERCOMPOST, Crop.ULTRACOMPOST);

    /**
     * What the leprechaun is holding in the bucket's slot. The game's own
     * {@code farming_tools_getstored} script draws no bucket for 0, an empty one for 1 and a
     * filled one above 1.
     */
    private static final int STORED_TYPE_VARBIT = VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE;

    /** The uses in a bucket stored at the leprechaun. */
    private static final int STORED_USES_VARBIT = VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY;

    private static final int STORED_EMPTY = 1;

    /** The leprechaun's bucket varbits, read at login with the rest of its store. */
    public static final Set<Integer> STORED_VARBITS = Set.of(STORED_TYPE_VARBIT, STORED_USES_VARBIT);

    /** The three chat shapes that state the bucket's uses: checked, treated from, filled. */
    private static final Pattern USES = Pattern.compile(
            "compost bucket (?:is currently holding|has|now contains a total of) (one|a single|\\d[\\d,]{0,8}) uses?");

    /** The kind of compost a line names: the bucket's contents, or what was poured in. */
    private static final Pattern KIND = Pattern.compile("of (ultra|super|)compost");

    /** The lines that leave the bucket empty. */
    private static final Pattern EMPTIED = Pattern.compile(
            "compost bucket (?:is currently empty|doesn't currently have|has run out)|discard the contents");

    /**
     * The bucket empty and filled: one item in two states, either of which counts as owning
     * it. The leprechaun holds one, so a second is never taken.
     */
    public static final Set<Integer> ITEM_IDS = Set.of(ItemID.BOTTOMLESS_COMPOST_BUCKET, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);

    /** What the bucket was last read holding: the compost kind and the uses of it. */
    @Value
    public static class Reading {
        Crop kind;
        int uses;
    }

    private BottomlessBucket() {
    }

    /**
     * What a chat line or dialogue box says the bucket holds, or null when it is not a line
     * about the bucket's uses. A line that leaves the bucket empty reads as 0 uses.
     */
    public static Reading read(String text) {
        if (text == null || !text.contains("compost bucket")) {
            return null;
        }
        String plain = text.replace("<br>", " ").replaceAll("<[^>]*>", "");
        Matcher uses = USES.matcher(plain);
        Matcher kind = KIND.matcher(plain);
        if (!uses.find() || !kind.find()) {
            return EMPTIED.matcher(plain).find() ? new Reading(Crop.COMPOST, 0) : null;
        }
        String count = uses.group(1);
        int n = Character.isDigit(count.charAt(0)) ? Integer.parseInt(count.replace(",", "")) : 1;
        return new Reading(Crop.valueOf(kind.group(1).toUpperCase() + "COMPOST"), n);
    }

    /**
     * What the leprechaun's bucket holds, or null when it keeps none. An empty one reads as 0
     * uses of compost, so the bucket withdrawn and filled is not counted at an older reading.
     */
    public static Reading stored(PlayerState state) {
        Map<Integer, Integer> varbits = state == null ? Map.of() : state.getVarbits();
        int type = varbits.getOrDefault(STORED_TYPE_VARBIT, 0);
        if (type == STORED_EMPTY) {
            return new Reading(Crop.COMPOST, 0);
        }
        // Types 2, 3 and 4 are compost, supercompost and ultracompost.
        return type < 2 || type > 4 ? null
                : new Reading(KINDS.get(type - 2), varbits.getOrDefault(STORED_USES_VARBIT, 0));
    }

    /** Whether the player owns the bucket, carried, banked or at the tool leprechaun. */
    public static boolean owned(PlayerState state) {
        return toHand(state) || isStored(state);
    }

    /**
     * Whether the player owns the bucket as an item, carried or banked, so the bag can pack
     * it. One stored at the tool leprechaun is not to hand.
     */
    public static boolean toHand(PlayerState state) {
        return ItemCounts.ownsAny(state, ITEM_IDS);
    }

    /** Whether the bucket is at the tool leprechaun, empty or not. */
    public static boolean isStored(PlayerState state) {
        Integer type = state == null ? null : state.getVarbits().get(STORED_TYPE_VARBIT);
        return type != null && type >= STORED_EMPTY;
    }
}
