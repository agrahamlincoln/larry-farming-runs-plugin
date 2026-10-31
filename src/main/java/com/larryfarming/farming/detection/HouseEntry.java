package com.larryfarming.farming.detection;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How a click leads into a house: the player's own, someone else's, or a way in whose
 * house cannot be told.
 *
 * <p>No variable names a house's owner, so a house is known to be the player's own only from
 * how they got in. The game's own wording decides it, matched loosely: case is ignored, and
 * colour tags and spacing are stripped from the target.
 *
 * <ul>
 *   <li>A house portal ({@code Portal}, any object op): "Home" and "Build mode" enter the
 *       player's own house, "Friend's house" someone else's. Any other op, "Enter" among
 *       them, opens a dialog or leaves, so its house is unknown until the dialog answers.
 *   <li>The House Advertisement board, any op: someone else's house.
 *   <li>A dialog option: "a friend's house" is someone else's, "your house" the player's.
 *   <li>Teleport to House, the spell or the tablet: "Cast", "Break" and "Inside" go to the
 *       player's own house, "Group" to a group member's, and any other op is unknown.
 *   <li>"Tele to POH", the Construction and Max capes' house teleport, and the Construction
 *       cape's "Home" teleport: the player's own house.
 * </ul>
 *
 * <p>An op renamed in a game update falls to unknown, or to no match, and an unknown entry
 * learns nothing. So a rename can stop the scan but never credit a friend's house.
 */
public final class HouseEntry {

    /** Whose house the click leads into. */
    public enum Owner {
        OWN,
        GUEST,
        UNKNOWN
    }

    /** What kind of menu entry was clicked, from its menu action. */
    public enum Click {
        /** An op on a game object in the scene. */
        OBJECT,
        /** A dialog option, which carries its line as the clicked widget's text. */
        DIALOG,
        /** Anything else: a spell, an item in the inventory or worn, a widget. */
        OTHER
    }

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACING = Pattern.compile("\\s+");

    private static final String PORTAL = "portal";
    private static final String ADVERTISEMENT = "house advertisement";
    private static final String HOUSE_TELEPORT = "teleport to house";

    private final Owner owner;
    private final String signal;

    private HouseEntry(Owner owner, String signal) {
        this.owner = owner;
        this.signal = signal;
    }

    public Owner getOwner() {
        return owner;
    }

    /**
     * Which rule matched, in fixed words. Never a target's own text, so logging it can
     * never write a player's name.
     */
    public String getSignal() {
        return signal;
    }

    /**
     * The house a click leads into, or null when it is no way into a house at all.
     *
     * @param widgetText the clicked widget's text, read only for a dialog option; may be null
     */
    public static HouseEntry of(Click click, String option, String target, String widgetText) {
        String op = plain(option);
        String on = plain(target);
        switch (click) {
            case OBJECT:
                if (on.equals(PORTAL)) {
                    if (op.equals("home") || op.equals("build mode")) {
                        return new HouseEntry(Owner.OWN, "house portal \"" + option(op) + "\"");
                    }
                    if (op.equals("friend's house")) {
                        return new HouseEntry(Owner.GUEST, "house portal \"Friend's house\"");
                    }
                    return new HouseEntry(Owner.UNKNOWN, "a portal op that names no house");
                }
                if (on.equals(ADVERTISEMENT)) {
                    return new HouseEntry(Owner.GUEST, "the House Advertisement board");
                }
                return null;
            case DIALOG: {
                String line = plain(widgetText);
                if (line.contains("friend's house")) {
                    return new HouseEntry(Owner.GUEST, "a dialog naming a friend's house");
                }
                if (line.contains("your house")) {
                    return new HouseEntry(Owner.OWN, "a dialog naming your house");
                }
                return null;
            }
            default:
                if (on.equals(HOUSE_TELEPORT)) {
                    if (op.equals("cast") || op.equals("break") || op.equals("inside")) {
                        return new HouseEntry(Owner.OWN, "Teleport to House");
                    }
                    if (op.equals("group")) {
                        return new HouseEntry(Owner.GUEST, "Teleport to House \"Group\"");
                    }
                    return new HouseEntry(Owner.UNKNOWN, "a house teleport op that lands outside");
                }
                if (op.equals("tele to poh")) {
                    return new HouseEntry(Owner.OWN, "\"Tele to POH\"");
                }
                if (op.equals("home")) {
                    return new HouseEntry(Owner.OWN, "a \"Home\" teleport");
                }
                return null;
        }
    }

    private static String option(String op) {
        return op.equals("home") ? "Home" : "Build mode";
    }

    /**
     * Lower case, without colour tags, with runs of spacing folded to one space. The game
     * writes some menu text with non-breaking spaces, which {@code \s} does not match.
     */
    private static String plain(String text) {
        if (text == null) {
            return "";
        }
        String bare = text.indexOf('<') < 0 ? text : TAG.matcher(text).replaceAll("");
        String spaced = bare.replace(' ', ' ').trim();
        return SPACING.matcher(spaced).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
