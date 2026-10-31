package com.larryfarming.engine;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.runelite.api.ChatMessageType;

/**
 * Reads the seed box's chat lines: seeds stored, emptied or planted, and the box's own
 * listing of what it holds.
 *
 * <p>All methods are static and side-effect-free. They parse a line into a result and leave
 * dispatch to the caller.
 */
public final class SeedBoxChat
{
	/**
	 * The seed box announcing that its contents moved, in each of the four wordings it uses.
	 *
	 * <p>The chat text is the only source for these counts: the game never sends the
	 * {@code SEED_BOX} item container, so {@code Client#getItemContainer} always answers null.
	 *
	 * <p>Filling and emptying give an exact item name and a count. Planting straight from the
	 * box gives a lower-case name, singular or plural depending on how many the patch takes.
	 * The emptying line never names the box ("Emptied 1 x Asgarnian seed to your inventory"),
	 * so a match on the box's name would miss it.
	 *
	 * <p>Digits are bounded to nine so a malformed line cannot overflow the parse.
	 */
	private static final Pattern SEED_BOX_STORED =
		Pattern.compile("^Stored (\\d{1,9}) x (.+?) in your seed box\\.?$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern SEED_BOX_EMPTIED =
		Pattern.compile("^Emptied (\\d{1,9}) x (.+?) to your inventory\\.?$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern SEED_BOX_TAKEN_ONE =
		Pattern.compile("^An? (.+?) was taken from your seed box\\.?$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern SEED_BOX_TAKEN_MANY =
		Pattern.compile("^(\\d{1,9}) (.+?) were taken from your seed box\\.?$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The box answering Check with nothing in it.
	 *
	 * <p>The four patterns above are movements, and a sum of movements cannot correct an
	 * over-count. This line is the box describing itself, so it replaces the tally rather
	 * than adjusting it.
	 *
	 * <p>Checking a box prints its contents to chat. This is one of that listing's two shapes; {@link #SEED_BOX_CONTENTS_HEADER} is
	 * the other.
	 *
	 * <p>The pattern accepts both "the" and "your"; nothing else in the game says "seed box
	 * is empty".
	 */
	private static final Pattern SEED_BOX_EMPTY =
		Pattern.compile("^(?:the|your) seed box is empty\\.?$", Pattern.CASE_INSENSITIVE);

	/**
	 * The box answering Check with something in it: a header, then one line per seed.
	 *
	 * <pre>
	 * The seed box contains:
	 * 1 x Harralander seed.
	 * 2 x Guam seed.
	 * 1 x Potato cactus seed.
	 * </pre>
	 *
	 * <p>Each line arrives as its own message, so reading the listing needs state across
	 * messages. {@code SeedBoxListing} holds that state.
	 */
	private static final Pattern SEED_BOX_CONTENTS_HEADER =
		Pattern.compile("^(?:the|your) seed box contains:?$", Pattern.CASE_INSENSITIVE);

	/**
	 * One line of a listing: a count, the separator, and the seed's name.
	 *
	 * <p>Only valid in the lines following a header. The game prints "1 x Harralander seed."
	 * in many places unrelated to a seed box. See {@link #parseSeedBoxContentsEntry}.
	 */
	private static final Pattern SEED_BOX_CONTENTS_ENTRY =
		Pattern.compile("^(\\d{1,9}) x (.+?)\\.?$", Pattern.CASE_INSENSITIVE);

	private SeedBoxChat()
	{
	}

	/**
	 * Seeds moving into or out of the box, by the game's name for them.
	 *
	 * <p>The name is the game's text, unresolved. Planting from the box reports a lower-case
	 * name that may be plural. The caller resolves it against the candidates it holds, which
	 * for a seed leaving the box are the box's own contents.
	 */
	public static final class SeedBoxMove
	{
		private final String itemName;
		private final int quantity;

		public SeedBoxMove(String itemName, int quantity)
		{
			this.itemName = itemName;
			this.quantity = quantity;
		}

		/** The item as the game named it, which may be lower-case or plural. */
		public String getItemName()
		{
			return itemName;
		}

		/** How many moved: positive into the box, negative out of it. Never zero. */
		public int getQuantity()
		{
			return quantity;
		}
	}

	/**
	 * Whether the game's own announcements arrive under this chat type.
	 *
	 * <p>{@code SPAM} is easy to leave out. Jagex files repetitive item handling under it
	 * ("You catch a shrimp"), and every seed box line is {@code SPAM}, so a listener on
	 * {@code GAMEMESSAGE} alone never hears about the box.
	 *
	 * <p>The client raises these events even when the player filters game chat.
	 */
	public static boolean isReadableGameChat(ChatMessageType type)
	{
		return type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM;
	}

	/**
	 * Seeds moving into or out of a seed box, or empty when the line is about something else.
	 *
	 * <p>Anchored at both ends, so a line that merely mentions a seed box is not mistaken for
	 * an amount. This feeds a running tally with nothing to correct it.
	 *
	 * <p>"Emptied ... to your inventory" does not name the box, so another container's
	 * identically-worded line also matches. The caller narrows it by applying a take only
	 * against seeds the box is known to hold.
	 */
	public static Optional<SeedBoxMove> parseSeedBoxMove(String message)
	{
		if (message == null)
		{
			return Optional.empty();
		}

		Matcher stored = SEED_BOX_STORED.matcher(message);
		if (stored.matches())
		{
			return moveOf(stored.group(2), Integer.parseInt(stored.group(1)));
		}

		Matcher emptied = SEED_BOX_EMPTIED.matcher(message);
		if (emptied.matches())
		{
			return moveOf(emptied.group(2), -Integer.parseInt(emptied.group(1)));
		}

		Matcher takenOne = SEED_BOX_TAKEN_ONE.matcher(message);
		if (takenOne.matches())
		{
			return moveOf(takenOne.group(1), -1);
		}

		Matcher takenMany = SEED_BOX_TAKEN_MANY.matcher(message);
		if (takenMany.matches())
		{
			return moveOf(takenMany.group(2), -Integer.parseInt(takenMany.group(1)));
		}

		return Optional.empty();
	}

	/**
	 * True when the box has just told us it holds nothing.
	 *
	 * <p>Not a move: the caller replaces the tally with zero rather than subtracting, which
	 * corrects an over-count.
	 */
	public static boolean isSeedBoxReportedEmpty(String message)
	{
		return message != null && SEED_BOX_EMPTY.matcher(message).matches();
	}

	/** True when the box has begun listing what it holds; its seeds are the lines after. */
	public static boolean isSeedBoxContentsHeader(String message)
	{
		return message != null && SEED_BOX_CONTENTS_HEADER.matcher(message).matches();
	}

	/**
	 * One seed of a listing, read as a positive amount.
	 *
	 * <p><b>Only meaningful directly after {@link #isSeedBoxContentsHeader}</b> or another
	 * entry. On its own this matches any "2 x Thing." the game prints. The result reuses
	 * {@link SeedBoxMove}, but its quantity is an amount held, not an amount moved.
	 */
	public static Optional<SeedBoxMove> parseSeedBoxContentsEntry(String message)
	{
		if (message == null)
		{
			return Optional.empty();
		}
		Matcher entry = SEED_BOX_CONTENTS_ENTRY.matcher(message);
		return entry.matches()
			? moveOf(entry.group(2), Integer.parseInt(entry.group(1)))
			: Optional.empty();
	}

	/** Empty for a zero quantity, which is not a change. */
	private static Optional<SeedBoxMove> moveOf(String itemName, int quantity)
	{
		return quantity == 0 ? Optional.empty()
			: Optional.of(new SeedBoxMove(itemName, quantity));
	}
}
