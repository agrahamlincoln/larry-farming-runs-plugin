package com.larryfarming.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.larryfarming.engine.StateEngine;
import com.larryfarming.engine.farming.FarmingTracker;
import com.larryfarming.farming.FarmingPlanner;
import com.larryfarming.farming.detection.TravelTaken;
import com.larryfarming.farming.engine.DepositPlanner;
import com.larryfarming.model.PlayerState;
import com.larryfarming.persistence.ConfigStore;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Everything the planner reads at one moment, as one JSON object, so the plan can be rebuilt
 * from it exactly:
 *
 * <ul>
 * <li>{@code profile} and {@code config}: Larry's saved keys, per account and global, the
 * player state taken out of {@code profile} into {@code state} as JSON so a checkpoint can be
 * compared field by field with the last;</li>
 * <li>{@code live}: the player state's readings that are never saved, such as the seed box's
 * trust stamps;</li>
 * <li>{@code tracker}: each patch with the stamps only this session holds;</li>
 * <li>{@code planner}: the trip record, the previewed book and the banks switch;</li>
 * <li>{@code now}: the second the plan was made at.</li>
 * </ul>
 *
 * <p>Keys are read without the RS profile prefix, so the account's profile key is not in it.
 */
public final class Checkpoints {

    /** The key the player state is saved under. */
    public static final String PLAYER_STATE_KEY = "playerState";

    private Checkpoints() {
    }

    /**
     * The checkpoint now. Saves the player state first, so {@code profile} holds it as it
     * stands rather than as the last debounced save left it.
     */
    public static JsonObject capture(ConfigStore store, String group, StateEngine engine,
            FarmingPlanner planner, long nowEpochSec) {
        engine.forceSave();
        JsonObject checkpoint = new JsonObject();
        checkpoint.addProperty("now", nowEpochSec);

        JsonObject profile = new JsonObject();
        JsonElement state = null;
        for (Map.Entry<String, String> entry : store.profileEntries(group).entrySet()) {
            if (PLAYER_STATE_KEY.equals(entry.getKey())) {
                state = parse(entry.getValue());
            } else {
                profile.addProperty(entry.getKey(), entry.getValue());
            }
        }
        checkpoint.add("profile", profile);
        if (state != null) {
            checkpoint.add("state", state);
        }
        JsonObject config = new JsonObject();
        for (Map.Entry<String, String> entry : store.configEntries(group).entrySet()) {
            config.addProperty(entry.getKey(), entry.getValue());
        }
        checkpoint.add("config", config);

        PlayerState player = engine.getPlayerState();
        if (player != null) {
            checkpoint.add("live", live(player));
        }
        checkpoint.add("tracker", tracker(engine.getFarmingTracker()));
        checkpoint.add("planner", planner(planner));
        return checkpoint;
    }

    private static JsonObject live(PlayerState player) {
        JsonObject live = new JsonObject();
        live.addProperty("runEnergy", player.getRunEnergy());
        live.addProperty("runEnergyLow", player.isRunEnergyLow());
        live.addProperty("regionId", player.getRegionId());
        live.addProperty("seedBoxVerifiedAt", millis(player.getSeedBoxVerifiedAt()));
        live.addProperty("seedBoxWatchedAt", millis(player.getSeedBoxWatchedAt()));
        JsonObject watched = new JsonObject();
        for (Map.Entry<Integer, Integer> seed
                : new TreeMap<>(player.getSeedBoxWatchedNet()).entrySet()) {
            watched.addProperty(String.valueOf(seed.getKey()), seed.getValue());
        }
        live.add("seedBoxWatchedNet", watched);
        live.addProperty("contractsPerDay", player.getContractsPerDay());
        return live;
    }

    private static JsonArray tracker(FarmingTracker tracker) {
        JsonArray patches = new JsonArray();
        for (FarmingTracker.LiveEntry entry : tracker.exportLive()) {
            JsonObject patch = new JsonObject();
            patch.addProperty("key", entry.getConfigKey());
            patch.addProperty("value", entry.getVarbitValue());
            patch.addProperty("observedAt", entry.getObservedAtEpochSec());
            patch.addProperty("firstSeenAt", entry.getFirstSeenAtEpochSec());
            patch.addProperty("lastSeen", entry.getLastSeenEpochSec());
            if (entry.getPlantedAtEpochSec() > 0) {
                patch.addProperty("plantedAt", entry.getPlantedAtEpochSec());
            }
            patches.add(patch);
        }
        return patches;
    }

    private static JsonObject planner(FarmingPlanner planner) {
        JsonObject out = new JsonObject();
        DepositPlanner.TripRecord trip = planner.getTripRecord();
        JsonArray packed = new JsonArray();
        for (int id : new TreeSet<>(trip.getPacked())) {
            packed.add(id);
        }
        JsonObject lowest = new JsonObject();
        for (Map.Entry<Integer, Integer> low : new TreeMap<>(trip.getLowest()).entrySet()) {
            lowest.addProperty(String.valueOf(low.getKey()), low.getValue());
        }
        JsonObject tripJson = new JsonObject();
        tripJson.add("packed", packed);
        tripJson.add("lowest", lowest);
        tripJson.addProperty("underway", trip.isUnderway());
        out.add("trip", tripJson);
        out.addProperty("spellbookPreview", planner.getSpellbookPreview() != null
                ? planner.getSpellbookPreview().name() : null);
        out.addProperty("offeredBanksHidden", planner.isOfferedBanksHidden());
        TravelTaken taken = planner.getTravelTaken();
        if (!taken.isNone()) {
            JsonObject travel = new JsonObject();
            JsonArray places = new JsonArray();
            taken.getPlaces().forEach(place -> places.add(place.name()));
            JsonArray items = new JsonArray();
            taken.getItems().forEach(item -> items.add(item.name()));
            travel.add("places", places);
            travel.add("items", items);
            travel.addProperty("for", planner.getTravelTakenFor());
            out.add("travelTaken", travel);
        }
        return out;
    }

    private static Long millis(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }

    private static JsonElement parse(String json) {
        try {
            return new JsonParser().parse(json);
        } catch (RuntimeException e) {
            // Kept as text: a state that does not parse is itself worth seeing.
            return new com.google.gson.JsonPrimitive(json);
        }
    }
}
