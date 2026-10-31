package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Crops named for the player: as the game names what stands in the patch, and as a word
 * mid-sentence. One place, so the runs list, the rows and the skip reasons spell a crop the
 * same way.
 */
final class CropNames {

    private CropNames() {
    }

    /**
     * Every crop of a bush patch, those Larry never plants included: the game can show any
     * of them standing in a patch whose crop the player has since switched.
     */
    private static final Set<Crop> BUSHES = EnumSet.of(Crop.REDBERRIES, Crop.CADAVABERRIES,
            Crop.DWELLBERRIES, Crop.JANGERBERRIES, Crop.WHITEBERRIES, Crop.POISON_IVY);

    /**
     * The crop as the game names what stands in the patch: "Dwarf weed", "Whiteberry bush".
     * A bush crop's own name ("Whiteberry") reads as one berry, so the bush is named.
     */
    static String displayNameOf(Crop crop) {
        return BUSHES.contains(crop) ? crop.getDisplayName() + " bush" : crop.getDisplayName();
    }

    /** A crop name for mid-sentence: lower case, except Hespori, which is a name. */
    static String inSentence(Crop crop) {
        return inSentence(displayNameOf(crop));
    }

    /**
     * A crop or seed name for mid-sentence: lower case, except a name led by Hespori, which
     * is a proper noun ("no Hespori seed").
     */
    static String inSentence(String name) {
        return name.startsWith(Crop.HESPORI.getDisplayName()) ? name
                : name.toLowerCase(Locale.ROOT);
    }
}
