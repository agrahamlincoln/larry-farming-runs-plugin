package com.larryfarming.farming;

import com.larryfarming.LarryConfig;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.detection.HouseFeature;
import com.larryfarming.farming.detection.HouseSight;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.persistence.ConfigStore;

import java.util.*;

/**
 * What the farm-run planner saves: per RS profile, the farming contract, the day's spent
 * teleports, what the player's house was seen to hold; in regular config, the side
 * panel's display toggles. The crop settings live in their own config keys, read through
 * {@code ConfiguredCrops}.
 *
 * <p>Keys sit in the plugin's config group beside the account state and the settings.
 */
public class FarmingPlannerPersistence {

    private static final String CONFIG_GROUP = LarryConfig.GROUP;

    private static final String KEY_FARMING_CONTRACT = "farmingContract";
    private static final String KEY_DAILY_TELEPORT_PREFIX = "farming.dailyTeleport.";
    private static final String KEY_DAILY_TELEPORT_DAY = "farming.dailyTeleportDay";
    private static final String KEY_HOUSE = "farming.house";
    private static final String KEY_HOUSE_NEXUS = "farming.houseNexus";

    // Regular (non-RS-profile) config keys, readable before login.
    private static final String KEY_FARMING_SHOW_SKIPPED = "farmingShowSkipped";
    private static final String KEY_FARMING_SHOW_BANKS = "farmingShowBanks";
    private static final String KEY_FARMING_TRAVEL_OPEN = "farmingTravelOpen";
    private static final String KEY_FARMING_SETTINGS_OPEN = "farmingSettingsOpen";
    private static final String KEY_FARMING_HOUSE_OPEN = "farmingHouseOpen";

    private final ConfigStore store;

    public FarmingPlannerPersistence(ConfigStore store) {
        this.store = store;
    }

    /** This RS profile's value for {@code key}, or null when unset. */
    private String get(String key) {
        return store.getRSProfileConfiguration(CONFIG_GROUP, key);
    }

    /** Saves {@code value} under {@code key} for this RS profile; null clears the key. */
    private void put(String key, Object value) {
        if (value == null) {
            store.unsetRSProfileConfiguration(CONFIG_GROUP, key);
        } else {
            store.setRSProfileConfiguration(CONFIG_GROUP, key, value.toString());
        }
    }

    /** A saved whole number, or null when unset or malformed. */
    private static Integer parseInt(String stored) {
        try {
            return Integer.parseInt(stored);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Persist the crop named by the current farming contract, by enum name.
     *
     * <p>Stored so the panel can name the contract before login, when the varbit is not yet
     * readable. A null crop clears the key.
     */
    public void saveFarmingContract(Crop crop) {
        put(KEY_FARMING_CONTRACT, crop != null ? crop.name() : null);
    }

    /** Load the persisted contract crop for the current RS profile, or null if unknown. */
    public Crop loadFarmingContract() {
        String stored = get(KEY_FARMING_CONTRACT);
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return Crop.valueOf(stored);
        } catch (IllegalArgumentException ex) {
            // A name no crop carries reads as unknown until the contract varbit is read again.
            return null;
        }
    }

    /**
     * Persist how many of today's rationed teleports have been spent, per capability,
     * plus the day the counts belong to. Each capability has its own key, so a key no
     * capability names goes unread and leaves the other counts intact.
     *
     * <p>The Ardougne cloak has no usage varbit, so its observed count survives a client
     * restart only through this store. The Explorer's ring is stored the same way, though
     * its varbit corrects the count at login anyway.
     */
    public void saveDailyTeleportLedger(long day, Map<Capability, Integer> usedToday) {
        put(KEY_DAILY_TELEPORT_DAY, day);
        for (Capability capability : Capability.values()) {
            if (capability.isRationedDaily()) {
                Integer spent = usedToday != null ? usedToday.get(capability) : null;
                put(KEY_DAILY_TELEPORT_PREFIX + capability.name(), spent == null || spent == 0 ? null : spent);
            }
        }
    }

    /**
     * Load the persisted daily-teleport ledger. A profile that never saved one returns an
     * empty count map and {@code Long.MIN_VALUE} as the day, which
     * {@link com.larryfarming.farming.engine.DailyTeleportLedger#loadPersisted} reads as "nothing
     * saved yet" and rolls over to today's allowance.
     */
    public DailyTeleportLedgerState loadDailyTeleportLedger() {
        String dayStored = get(KEY_DAILY_TELEPORT_DAY);
        long day;
        try {
            day = dayStored != null ? Long.parseLong(dayStored) : Long.MIN_VALUE;
        } catch (NumberFormatException ex) {
            day = Long.MIN_VALUE;
        }
        Map<Capability, Integer> used = new EnumMap<>(Capability.class);
        for (Capability capability : Capability.values()) {
            // A malformed entry is skipped.
            Integer spent = capability.isRationedDaily() ? parseInt(get(KEY_DAILY_TELEPORT_PREFIX + capability.name())) : null;
            if (spent != null) {
                used.put(capability, spent);
            }
        }
        return new DailyTeleportLedgerState(day, used);
    }

    /**
     * Persist what the player's own house was last seen to hold, as two keys: the last scan
     * as "scannedAtEpochSec:FEATURE,FEATURE" and the Nexus attunement as
     * "readAtEpochSec:key,key", where a read stamp of 0 means only passive reads. A house
     * never seen leaves both keys unset.
     *
     * <p>Features are stored by name. Load skips a name no feature carries and keeps the
     * rest.
     */
    public void saveHouse(HouseSight house) {
        if (house == null || !house.isScanned()) {
            put(KEY_HOUSE, null);
        } else {
            List<String> names = new ArrayList<>();
            for (HouseFeature feature : house.getBuilt()) {
                names.add(feature.name());
            }
            put(KEY_HOUSE, house.getScannedAtEpochSec() + ":" + String.join(",", names));
        }
        if (house == null || (!house.isNexusRead() && house.getAttuned().isEmpty())) {
            put(KEY_HOUSE_NEXUS, null);
        } else {
            List<String> keys = new ArrayList<>();
            for (Integer key : house.getAttuned()) {
                keys.add(key.toString());
            }
            put(KEY_HOUSE_NEXUS, house.getNexusReadAtEpochSec() + ":" + String.join(",", keys));
        }
    }

    /**
     * Load what the current RS profile's house was last seen to hold. A profile with
     * nothing saved, or with a malformed value, reads as a house never seen.
     */
    public HouseSight loadHouse() {
        long scannedAt = 0L;
        Set<HouseFeature> built = EnumSet.noneOf(HouseFeature.class);
        String[] scan = stampAndList(get(KEY_HOUSE));
        // A scan without a real stamp is no scan, so none of its furniture is credited.
        long scanStamp = scan != null ? parseStamp(scan[0]) : -1L;
        if (scanStamp > 0L) {
            scannedAt = scanStamp;
            for (String name : scan[1].split(",")) {
                try {
                    built.add(HouseFeature.valueOf(name));
                } catch (IllegalArgumentException ex) {
                    // A feature dropped since the write, or an empty list.
                }
            }
        }
        long readAt = 0L;
        Set<Integer> attuned = new TreeSet<>();
        String[] nexus = stampAndList(get(KEY_HOUSE_NEXUS));
        long nexusStamp = nexus != null ? parseStamp(nexus[0]) : -1L;
        if (nexusStamp >= 0L) {
            readAt = nexusStamp;
            for (String key : nexus[1].split(",")) {
                // A malformed entry is skipped.
                Integer parsed = parseInt(key);
                if (parsed != null) {
                    attuned.add(parsed);
                }
            }
        }
        return HouseSight.of(built, scannedAt, attuned, readAt);
    }

    /** A saved "stamp:list" split in two, or null when unset or malformed. */
    private static String[] stampAndList(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        String[] parts = stored.split(":", 2);
        return parts.length == 2 ? parts : null;
    }

    /** A saved epoch-second stamp, or -1 when it is not one. */
    private static long parseStamp(String stamp) {
        try {
            long parsed = Long.parseLong(stamp);
            return parsed >= 0L ? parsed : -1L;
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    /**
     * What was saved of the daily-teleport ledger: the day the counts belong to, and how
     * many of each rationed capability's allowance were spent that day.
     */
    @lombok.Value
    public static class DailyTeleportLedgerState {
        long day;
        Map<Capability, Integer> used;
    }

    // The panel's display toggles. Regular config, not RS-profile config: each is a display
    // preference, not a fact about an account, so it should not be per-account. The panel is
    // also first drawn in startUp(), before any RS profile exists. None is a @ConfigItem: the
    // controls live on the side panel itself.

    private void saveFlag(String key, boolean on) {
        store.setConfiguration(CONFIG_GROUP, key, Boolean.toString(on));
    }

    /** A saved toggle, false when unset. */
    private boolean flag(String key) {
        return Boolean.parseBoolean(store.getConfiguration(CONFIG_GROUP, key));
    }

    /** Remember whether the side panel is showing the stops the trip is not making. */
    public void saveFarmingShowSkipped(boolean show) {
        saveFlag(KEY_FARMING_SHOW_SKIPPED, show);
    }

    /** Whether to show skipped stops. Defaults to false when unset. */
    public boolean loadFarmingShowSkipped() {
        return flag(KEY_FARMING_SHOW_SKIPPED);
    }

    public void saveFarmingShowBanks(boolean show) {
        saveFlag(KEY_FARMING_SHOW_BANKS, show);
    }

    /**
     * Whether to draw the banks the route passes, defaulting to <em>yes</em>.
     *
     * <p>An unset key must read as true, so this checks for the literal "false" instead of
     * using {@code Boolean.parseBoolean}, which reads an unset key as false.
     */
    public boolean loadFarmingShowBanks() {
        String saved = store.getConfiguration(CONFIG_GROUP, KEY_FARMING_SHOW_BANKS);
        return !"false".equalsIgnoreCase(saved);
    }

    /** Remember whether the side panel's travel fold is open. */
    public void saveFarmingTravelOpen(boolean open) {
        saveFlag(KEY_FARMING_TRAVEL_OPEN, open);
    }

    /** Whether the travel fold is open. Shut when unset. */
    public boolean loadFarmingTravelOpen() {
        return flag(KEY_FARMING_TRAVEL_OPEN);
    }

    /** Remember whether the side panel's house fold is open. */
    public void saveFarmingHouseOpen(boolean open) {
        saveFlag(KEY_FARMING_HOUSE_OPEN, open);
    }

    /** Whether the house fold is open. Shut when unset. */
    public boolean loadFarmingHouseOpen() {
        return flag(KEY_FARMING_HOUSE_OPEN);
    }

    /** Remember whether the side panel's settings fold is open. */
    public void saveFarmingSettingsOpen(boolean open) {
        saveFlag(KEY_FARMING_SETTINGS_OPEN, open);
    }

    /** Whether the settings fold is open. Shut when unset. */
    public boolean loadFarmingSettingsOpen() {
        return flag(KEY_FARMING_SETTINGS_OPEN);
    }
}
