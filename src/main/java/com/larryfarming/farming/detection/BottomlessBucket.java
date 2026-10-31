package com.larryfarming.farming.detection;

import com.larryfarming.model.PlayerState;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.VarbitID;

/**
 * How many uses of ultracompost the bottomless compost bucket holds, from the three places
 * the game says so.
 *
 * <p>Stored at the tool leprechaun, two varbits give the bucket exactly. Carried or banked,
 * the game state holds nothing: the uses show only in the dialogue box the bucket opens when
 * the player checks or fills it. Each patch treated from the bucket spends one use, and the
 * game says in chat how many are left. The treatment line after it matches a loose bucket's,
 * so a treatment with no count is put down to the bucket only when the player used the bucket
 * on the patch. A compost bin emptied into the bucket changes its uses without a word,
 * so its figure is no longer known.
 *
 * <p>The chat wordings beyond the wiki's transcript are the Item Charges Improved plugin's
 * (tictac7x), which tracks the bucket from them.
 *
 * <p>No RuneLite client types.
 */
public final class BottomlessBucket {

    /** The bucket with nothing in it. */
    public static final int EMPTY_ITEM_ID = 22994;

    /** The bucket holding compost of any kind. */
    public static final int FILLED_ITEM_ID = 22997;

    /**
     * What the leprechaun is holding in the bucket's slot. The game's own
     * {@code farming_tools_getstored} script draws no bucket for 0, an empty one for 1 and a
     * filled one above 1. Above 1 the value names the compost: 2 compost, 3 supercompost and
     * 4 ultracompost, as the Dude, Where's My Stuff? plugin decodes it.
     */
    private static final int STORED_TYPE_VARBIT = VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE;

    /** The uses in a bucket stored at the leprechaun. */
    private static final int STORED_USES_VARBIT =
            VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY;

    private static final int STORED_EMPTY = 1;
    private static final int STORED_ULTRACOMPOST = 4;

    /** Both of the leprechaun's bucket varbits, read at login with the rest of its store. */
    public static final Set<Integer> STORED_VARBITS =
            Set.of(STORED_TYPE_VARBIT, STORED_USES_VARBIT);

    /** The "Check" answer for a bucket with something in it, one use worded "one use". */
    private static final Pattern CHECKED = Pattern.compile(
            "^Your bottomless compost bucket is currently holding (one|\\d[\\d,]{0,11}) uses? "
                    + "of (ultracompost|supercompost|compost)\\.$");

    /** The line after a patch is treated from the bucket: the uses it has left. */
    private static final Pattern REMAINING = Pattern.compile(
            "^Your bottomless compost bucket has (a single|\\d[\\d,]{0,11}) uses? of "
                    + "(ultracompost|supercompost|compost) remaining\\.$");

    /**
     * Lines that leave the bucket empty: two wordings of the "Check" answer (the wiki's
     * transcript leaves out "bottomless"), the last use spent, and "Discard".
     */
    private static final Pattern EMPTIED = Pattern.compile("^(?:"
            + "Your (?:bottomless )?compost bucket is currently empty\\."
            + "|Your bottomless compost bucket doesn't currently have any compost in it!"
            + "|Your bottomless compost bucket has run out of compost!"
            + "|You discard the contents of your bottomless compost bucket\\.)$");

    /** A compost bin's menu target, as the game names the farm bins and the guild's. */
    private static final Pattern COMPOST_BIN = Pattern.compile("^(?:Big )?Compost Bin$");

    /** The "Fill" answer: the compost added, then the bucket's new total. */
    private static final Pattern FILLED = Pattern.compile(
            "^You fill your bottomless compost bucket with (?:a single bucket|\\d[\\d,]{0,11} "
                    + "buckets) of (ultracompost|supercompost|compost)\\. Your bottomless "
                    + "compost bucket now contains a total of (\\d[\\d,]{0,11}) uses?\\.$");

    /**
     * A patch treated with compost of any grade, from a bucket of either kind. RuneLite's
     * own {@code CompostTracker} reads the same game message.
     */
    private static final Pattern TREATED =
            Pattern.compile("^You treat the .+ with (?:ultra|super|)compost\\.$");

    /**
     * A patch that refuses compost because it already has some, so the click spent nothing.
     * The wording is RuneLite {@code CompostTracker}'s, which also covers the Fertile Soil
     * spell's version of the line.
     */
    private static final Pattern ALREADY_TREATED = Pattern.compile(
            "^This .+ has already been (?:treated|fertilised) with (?:ultra|super|)compost"
                    + "(?: - the spell can't make it any more fertile)?\\.$");

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern LINE_BREAK = Pattern.compile("<br>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final String ULTRACOMPOST = "ultracompost";

    /**
     * How long a "use the bucket on a patch" click waits for its treatment line: the player
     * may have to walk to the patch first. RuneLite's {@code CompostTracker} waits 30
     * seconds, 50 ticks; this allows a longer walk.
     */
    private static final int WINDOW_TICKS = 100;

    private static final int NOT_ARMED = Integer.MIN_VALUE;

    private int armedAtTick = NOT_ARMED;

    /** Whether the armed click was the filled bucket, the only one that can treat a patch. */
    private boolean armedFilled;

    /**
     * The uses of ultracompost a dialogue box or chat line says the bucket holds, or null
     * when the text is not one of the bucket's. A bucket holding another compost, or none,
     * holds 0 uses of ultracompost.
     *
     * @param text as the game draws it, line breaks and colour tags included
     */
    public static Integer ultracompostUses(String text) {
        if (text == null || text.indexOf("compost bucket") < 0) {
            return null;
        }
        String plain = plain(text);
        Matcher checked = CHECKED.matcher(plain);
        if (checked.matches()) {
            return ULTRACOMPOST.equals(checked.group(2)) ? number(checked.group(1)) : 0;
        }
        Matcher remaining = REMAINING.matcher(plain);
        if (remaining.matches()) {
            return ULTRACOMPOST.equals(remaining.group(2)) ? number(remaining.group(1)) : 0;
        }
        if (EMPTIED.matcher(plain).matches()) {
            return 0;
        }
        Matcher filled = FILLED.matcher(plain);
        if (filled.matches()) {
            return ULTRACOMPOST.equals(filled.group(1)) ? number(filled.group(2)) : 0;
        }
        return null;
    }

    /** Whether this chat line is a patch being treated with compost of any grade. */
    public static boolean isTreatment(String message) {
        return message != null && message.startsWith("You treat the ")
                && TREATED.matcher(message).matches();
    }

    /**
     * Whether this chat line is a patch refusing compost because it is already treated. It
     * answers a bucket's click without spending a use.
     */
    public static boolean isRefusedTreatment(String message) {
        return message != null && message.startsWith("This ")
                && ALREADY_TREATED.matcher(message).matches();
    }

    /** Whether a treatment line is one with ultracompost. */
    public static boolean isUltracompostTreatment(String message) {
        return isTreatment(message) && message.endsWith(" with ultracompost.");
    }

    /** Whether the bucket is at the tool leprechaun, empty or not. */
    public static boolean isStored(PlayerState state) {
        return storedType(state) >= STORED_EMPTY;
    }

    /** The uses of ultracompost in a bucket stored at the leprechaun; 0 for any other. */
    public static int storedUltracompostUses(PlayerState state) {
        if (storedType(state) != STORED_ULTRACOMPOST) {
            return 0;
        }
        Integer uses = varbits(state).get(STORED_USES_VARBIT);
        return uses == null || uses < 0 ? 0 : uses;
    }

    /**
     * Whether a bucket stored at the leprechaun holds another compost than ultracompost, so
     * emptying a bin of ultracompost into it is impossible until it is emptied.
     */
    public static boolean storedWithOtherCompost(PlayerState state) {
        int type = storedType(state);
        return type > STORED_EMPTY && type != STORED_ULTRACOMPOST;
    }

    private static int storedType(PlayerState state) {
        Integer type = varbits(state).get(STORED_TYPE_VARBIT);
        return type == null ? 0 : type;
    }

    private static Map<Integer, Integer> varbits(PlayerState state) {
        return state == null ? Map.of() : state.getVarbits();
    }

    /**
     * The player used an item on an object: either bucket arms the watch, anything else
     * disarms it, since the next line is that item's.
     */
    public void used(int tick, int itemId) {
        armedAtTick = isBucket(itemId) ? tick : NOT_ARMED;
        armedFilled = itemId == FILLED_ITEM_ID;
    }

    /** Whether this item is the bottomless bucket, empty or filled. */
    public static boolean isBucket(int itemId) {
        return itemId == FILLED_ITEM_ID || itemId == EMPTY_ITEM_ID;
    }

    /**
     * Offers a treatment line, and answers whether it spent a use of the bucket. True once
     * per click, because the watch disarms as it answers.
     */
    public boolean treated(int tick) {
        boolean fromTheBucket = armedFilled && armedAtTick != NOT_ARMED
                && tick >= armedAtTick && tick - armedAtTick <= WINDOW_TICKS;
        armedAtTick = NOT_ARMED;
        return fromTheBucket;
    }

    /**
     * Whether a bucket used on an object has gone the whole window with no treatment line or
     * refusal, so it went somewhere that changes its uses unseen: emptying a compost bin into it, most
     * likely. True once per click, because the watch disarms as it answers.
     */
    public boolean usedElsewhere(int tick) {
        if (armedAtTick == NOT_ARMED || tick - armedAtTick <= WINDOW_TICKS) {
            return false;
        }
        armedAtTick = NOT_ARMED;
        return true;
    }

    /** The bucket said what it holds, so a click before it has nothing left to explain. */
    public void read() {
        armedAtTick = NOT_ARMED;
    }

    /** The patch was already treated, so the click left the bucket as it was. */
    public void refused() {
        armedAtTick = NOT_ARMED;
    }

    /** Forgets a pending click: on a logout or an account switch, or once it is answered. */
    public void clear() {
        armedAtTick = NOT_ARMED;
    }

    /** The text with its line breaks as spaces and its tags gone. */
    private static String plain(String text) {
        String spaced = text.indexOf('<') < 0 ? text
                : TAG.matcher(LINE_BREAK.matcher(text).replaceAll(" ")).replaceAll("");
        return SPACES.matcher(spaced).replaceAll(" ").trim();
    }

    /**
     * Whether this click takes compost out of a bin. With the bottomless bucket carried, the
     * compost can go into it, and the game says nothing of how much.
     *
     * @param target the menu target, colour tags removed
     */
    public static boolean isTakeFromAbin(String option, String target) {
        return "Take".equals(option) && target != null && COMPOST_BIN.matcher(target).matches();
    }

    /**
     * Whether an item used on this target goes into a compost bin. The target names the item
     * first: "Bottomless compost bucket -> Compost Bin".
     *
     * @param target the menu target, colour tags removed
     */
    public static boolean isUseOnAbin(String target) {
        if (target == null) {
            return false;
        }
        int arrow = target.lastIndexOf(" -> ");
        return arrow >= 0 && COMPOST_BIN.matcher(target.substring(arrow + 4)).matches();
    }

    /** A count as the game prints it, thousands separated by commas, or a worded one. */
    private static int number(String digits) {
        if (digits.equals("one") || digits.equals("a single")) {
            return 1;
        }
        long value = Long.parseLong(digits.replace(",", ""));
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
