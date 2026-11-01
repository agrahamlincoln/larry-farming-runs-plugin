package com.larryfarming.farming.detection;

import com.larryfarming.model.PlayerState;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;

/**
 * Whether the bottomless compost bucket is stored at the tool leprechaun, which a varbit
 * says exactly.
 *
 * <p>No RuneLite client types.
 */
public final class BottomlessBucket {

    /**
     * What the leprechaun is holding in the bucket's slot. The game's own
     * {@code farming_tools_getstored} script draws no bucket for 0, an empty one for 1 and a
     * filled one above 1.
     */
    private static final int STORED_TYPE_VARBIT = VarbitID.FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE;

    private static final int STORED_EMPTY = 1;

    /** The leprechaun's bucket varbit, read at login with the rest of its store. */
    public static final Set<Integer> STORED_VARBITS = Set.of(STORED_TYPE_VARBIT);

    private BottomlessBucket() {
    }

    /** Whether the bucket is at the tool leprechaun, empty or not. */
    public static boolean isStored(PlayerState state) {
        Integer type = state == null ? null : state.getVarbits().get(STORED_TYPE_VARBIT);
        return type != null && type >= STORED_EMPTY;
    }
}
