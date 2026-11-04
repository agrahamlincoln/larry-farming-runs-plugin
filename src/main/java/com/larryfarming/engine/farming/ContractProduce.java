package com.larryfarming.engine.farming;

import com.larryfarming.data.Tables;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the farming contract varbit (7963) to the {@link Crop} the contract asks for.
 *
 * <p>The varbit values match RuneLite's Time Tracking {@code Produce} table, which decodes the
 * same varbit. The varbit arrives at login, so the crop is known without talking to
 * Guildmaster Jane.
 */
public final class ContractProduce {

    /** Indexed by varbit value; 0 is the empty contract slot. */
    private static final List<Crop> BY_VARBIT_VALUE = load();

    /** The table lists every value from 1 in order, so a missing or out-of-order value fails at load. */
    private static List<Crop> load() {
        List<Crop> crops = new ArrayList<>();
        crops.add(null);
        for (Tables.Row row : Tables.read("/com/larryfarming/contract-produce.tsv", 2)) {
            if (row.integer(0) != crops.size()) {
                throw row.error("expected value " + crops.size());
            }
            crops.add(row.constant(Crop.class, 1));
        }
        return crops;
    }

    private ContractProduce() {
    }

    /**
     * The crop a contract varbit value names.
     *
     * @return the crop, or null for the empty slot (0) and for a value the table does not
     *     know, so a new contract tier is never read as the wrong crop
     */
    public static Crop byVarbitValue(int value) {
        return value > 0 && value < BY_VARBIT_VALUE.size() ? BY_VARBIT_VALUE.get(value) : null;
    }
}
