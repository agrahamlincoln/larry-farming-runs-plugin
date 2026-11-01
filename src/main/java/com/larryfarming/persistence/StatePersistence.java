package com.larryfarming.persistence;

import com.google.gson.*;
import com.larryfarming.LarryConfig;
import com.larryfarming.engine.farming.FarmingTracker;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

/**
 * What the plugin saves per RS profile about the account: its {@link PlayerState}, the last
 * seen contents of every farming patch, and the learned farm-tick offset.
 *
 * <p>The player state is written on a debounce, because it changes with every inventory
 * move. Patch observations are written as they change, so offline growth survives a crash.
 */
@Slf4j
public class StatePersistence {

    private static final String CONFIG_GROUP = LarryConfig.GROUP;

    private static final String KEY_PLAYER_STATE = "playerState";
    /** {@code PlayerState}'s own field, which a save replaces with {@link #KEY_BANK_HELD}. */
    private static final String KEY_BANK = "bank";
    private static final String KEY_BANK_HELD = "bankHeld";
    private static final String KEY_FARMING_PREFIX = "farming.";
    private static final String KEY_FARMING_TICK_OFFSET = "farmingTickOffset";

    private static final long SAVE_DEBOUNCE_MS = 5000;

    private final ConfigStore store;
    private final Gson gson;

    private long lastSaveTime;
    private boolean dirty;

    public StatePersistence(ConfigStore store, Gson base) {
        this.store = store;
        this.gson = buildGson(base);
    }

    /**
     * Build the Gson used for the saved player state.
     *
     * <p>The {@link Instant} adapter is required. {@link PlayerState} holds {@code Instant}
     * fields, and Gson's reflective fallback calls {@code setAccessible} on {@code Instant}'s
     * private fields, which JDK 17+ module encapsulation refuses. Instants are stored as epoch
     * seconds.
     *
     * <p>{@code base} is the client's injected Gson. The adapter registered here is added
     * later and wins.
     */
    static Gson buildGson(Gson base) {
        return base.newBuilder().registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, type, ctx) ->
                                new JsonPrimitive(src.getEpochSecond())).registerTypeAdapter(Instant.class,
                        (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.ofEpochSecond(json.getAsLong())).create();
    }

    /** The current RS profile's saved player state, or null when it has none or it is unreadable. */
    public PlayerState loadPlayerState() {
        String json = store.getRSProfileConfiguration(CONFIG_GROUP, KEY_PLAYER_STATE);
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            JsonObject tree = gson.fromJson(json, JsonObject.class);
            JsonElement held = tree.remove(KEY_BANK_HELD);
            if (held != null && held.isJsonArray()) {
                tree.add(KEY_BANK, expandBank(held.getAsJsonArray()));
            }
            return gson.fromJson(tree, PlayerState.class);
        } catch (RuntimeException e) {
            log.warn("Failed to read the saved account state, starting fresh", e);
            return null;
        }
    }

    /**
     * The bank as flat id, quantity pairs: {@code [952,1,5343,1]}.
     *
     * <p>A bank holds up to 1,250 stacks, and their names alone would run a save to 60 KB of
     * config that RuneLite syncs on every write. Names come back the next time the bank
     * opens. Placeholders hold nothing, so they are left out.
     */
    static JsonArray compactBank(List<ItemStack> bank) {
        JsonArray pairs = new JsonArray();
        for (ItemStack stack : bank) {
            if (stack.getQuantity() > 0) {
                pairs.add(stack.getItemId());
                pairs.add(stack.getQuantity());
            }
        }
        return pairs;
    }

    /** {@link #compactBank}'s pairs as the stacks {@code PlayerState} reads, unnamed. */
    private static JsonArray expandBank(JsonArray pairs) {
        JsonArray stacks = new JsonArray();
        for (int i = 0; i + 1 < pairs.size(); i += 2) {
            JsonObject stack = new JsonObject();
            stack.addProperty("itemId", pairs.get(i).getAsInt());
            stack.addProperty("quantity", pairs.get(i + 1).getAsInt());
            stacks.add(stack);
        }
        return stacks;
    }

    /** Mark state dirty; the write happens on the next debounced save or {@link #forceSave}. */
    public void markDirty() {
        dirty = true;
    }

    /** Save if dirty and the debounce period has elapsed. Call once per game tick. */
    public void saveIfNeeded(PlayerState playerState) {
        if (!dirty) {
            return;
        }
        if (System.currentTimeMillis() - lastSaveTime < SAVE_DEBOUNCE_MS) {
            return;
        }
        forceSave(playerState);
    }

    /** Save immediately, ignoring the debounce. Call at logout and shutdown. */
    public void forceSave(PlayerState playerState) {
        if (playerState == null) {
            return;
        }
        try {
            JsonObject tree = gson.toJsonTree(playerState).getAsJsonObject();
            tree.remove(KEY_BANK);
            tree.add(KEY_BANK_HELD, compactBank(playerState.getBank()));
            store.setRSProfileConfiguration(CONFIG_GROUP, KEY_PLAYER_STATE, gson.toJson(tree));
            lastSaveTime = System.currentTimeMillis();
            dirty = false;
        } catch (RuntimeException e) {
            log.warn("Failed to save the account state", e);
        }
    }

    /**
     * Persist observed farming patch states, one key per patch, as
     * "varbitValue:observedAtEpochSec" under "farming.&lt;regionId&gt;.&lt;varbitId&gt;".
     * No unset pass: a patch is never un-observed.
     */
    public void saveFarmingPatches(Collection<FarmingTracker.PersistedEntry> entries) {
        if (entries == null) {
            return;
        }
        for (FarmingTracker.PersistedEntry e : entries) {
            store.setRSProfileConfiguration(CONFIG_GROUP, KEY_FARMING_PREFIX + e.getConfigKey(),
                    e.getVarbitValue() + ":" + e.getObservedAtEpochSec());
        }
    }

    /** Load the persisted farming patch observations for the current RS profile. */
    public Collection<FarmingTracker.PersistedEntry> loadFarmingPatches() {
        List<FarmingTracker.PersistedEntry> out = new ArrayList<>();
        for (FarmingWorldMap.PatchDef patch : FarmingWorldMap.allPatches()) {
            String stored = store.getRSProfileConfiguration(CONFIG_GROUP, KEY_FARMING_PREFIX + patch.configKey());
            if (stored == null || stored.isEmpty()) {
                continue;
            }
            String[] parts = stored.split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                int value = Integer.parseInt(parts[0]);
                long ts = Long.parseLong(parts[1]);
                out.add(new FarmingTracker.PersistedEntry(patch.configKey(), value, ts));
            } catch (NumberFormatException ex) {
                // ignore malformed entry
            }
        }
        return out;
    }

    /**
     * Persist the learned farming tick offset as
     * "offsetSeconds:precisionMins:resolutionSeconds". The offset aligns growth predictions
     * to the player's farm-tick grid. Losing it only widens estimates until the next
     * observed growth tick.
     */
    public void saveFarmingTickOffset(int offsetSeconds, int precisionMins, int resolutionSeconds) {
        store.setRSProfileConfiguration(CONFIG_GROUP, KEY_FARMING_TICK_OFFSET,
                offsetSeconds + ":" + precisionMins + ":" + resolutionSeconds);
    }

    /**
     * Load the learned farming tick offset as {offsetSeconds, precisionMins,
     * resolutionSeconds}, or null if none was ever observed or the value is malformed.
     */
    public int[] loadFarmingTickOffset() {
        String stored = store.getRSProfileConfiguration(CONFIG_GROUP, KEY_FARMING_TICK_OFFSET);
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        String[] parts = stored.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
