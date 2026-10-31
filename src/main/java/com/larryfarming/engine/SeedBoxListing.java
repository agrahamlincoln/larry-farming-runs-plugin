package com.larryfarming.engine;

import com.larryfarming.model.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A Check listing part way through arriving.
 *
 * <p>Checking the seed box prints a header line, then one chat message per seed, and no
 * closing line. {@link SeedBoxChat} stays stateless; the caller owns one of these while a
 * listing is open and drops it when a message arrives that is not an entry.
 *
 * <p><b>The entry wording is not self-identifying.</b> The game prints "2 x Guam seed." in
 * places unrelated to farming, so an entry counts only after the header has been seen.
 */
public final class SeedBoxListing {

    private final List<ItemStack> seeds = new ArrayList<>();
    private int lines;

    /**
     * How many entry lines this listing has printed, named or not.
     *
     * <p>Can exceed the size of {@link #getSeeds} when a name is unknown. A listing whose
     * names we could not place is not an empty box and must not be committed as one.
     */
    public int getLines() {
        return lines;
    }

    /** The seeds read so far, in the order the box listed them. */
    public List<ItemStack> getSeeds() {
        return seeds;
    }

    /**
     * Folds one message into this listing.
     *
     * @param known lower-cased name → item id, as the plugin learns names. A name absent from
     *              it is counted in {@link #getLines} and left out of {@link #getSeeds}:
     *              a missing seed costs a needless withdrawal, an invented one a planting the
     *              player cannot make
     * @return false when the message is not a listing entry, which means the listing has ended
     */
    public static boolean read(String message, SeedBoxListing into, Map<String, Integer> known) {
        Optional<SeedBoxChat.SeedBoxMove> entry =
                SeedBoxChat.parseSeedBoxContentsEntry(message);
        if (!entry.isPresent()) {
            return false;
        }
        into.lines++;
        String name = entry.get().getItemName();
        Integer itemId = known.get(name.toLowerCase(Locale.ROOT));
        if (itemId != null) {
            into.seeds.add(new ItemStack(itemId, entry.get().getQuantity(), name));
        }
        return true;
    }
}
