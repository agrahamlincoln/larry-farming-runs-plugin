package com.larryfarming.engine.farming;

/**
 * Decodes the farming contract varbit (7963) to the {@link Crop} the contract asks for.
 *
 * <p>The varbit values match RuneLite's Time Tracking {@code Produce} table, which decodes the
 * same varbit. The varbit arrives at login, so the crop is known without talking to
 * Guildmaster Jane.
 */
public final class ContractProduce {

    /** Indexed by varbit value; 0 is the empty contract slot. */
    private static final Crop[] BY_VARBIT_VALUE = {
        null,
        // 1-6: bushes
        Crop.REDBERRIES, Crop.CADAVABERRIES, Crop.DWELLBERRIES, Crop.JANGERBERRIES,
        Crop.WHITEBERRIES, Crop.POISON_IVY,
        // 7-11: trees
        Crop.OAK, Crop.WILLOW, Crop.MAPLE, Crop.YEW, Crop.MAGIC,
        // 12-19: fruit trees
        Crop.APPLE, Crop.BANANA, Crop.ORANGE, Crop.CURRY, Crop.PINEAPPLE, Crop.PAPAYA,
        Crop.PALM, Crop.DRAGONFRUIT,
        // 20-33: herbs
        Crop.GUAM, Crop.MARRENTILL, Crop.TARROMIN, Crop.HARRALANDER, Crop.RANARR,
        Crop.TOADFLAX, Crop.IRIT, Crop.AVANTOE, Crop.KWUARM, Crop.SNAPDRAGON,
        Crop.CADANTINE, Crop.LANTADYME, Crop.DWARF_WEED, Crop.TORSTOL,
        // 34-39: flowers
        Crop.MARIGOLD, Crop.ROSEMARY, Crop.NASTURTIUM, Crop.WOAD, Crop.LIMPWURT,
        Crop.WHITE_LILY,
        // 40-47: allotments
        Crop.POTATO, Crop.ONION, Crop.CABBAGE, Crop.TOMATO, Crop.SWEETCORN,
        Crop.STRAWBERRY, Crop.WATERMELON, Crop.SNAPE_GRASS,
        // 48-51: cacti and the special trees
        Crop.CACTUS, Crop.POTATO_CACTUS, Crop.REDWOOD, Crop.CELASTRUS,
    };

    private ContractProduce() {
    }

    /**
     * The crop a contract varbit value names.
     *
     * @return the crop, or null for the empty slot (0) and for a value the table does not
     *     know, so a new contract tier is never read as the wrong crop
     */
    public static Crop byVarbitValue(int value) {
        return value > 0 && value < BY_VARBIT_VALUE.length ? BY_VARBIT_VALUE[value] : null;
    }
}
