package com.larryfarming.farming;

import com.larryfarming.LarryConfig;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.detection.HouseFeature;
import com.larryfarming.farming.detection.HouseSight;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.persistence.ConfigStore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the farm-run planner saves: per RS profile, the farming contract, the day's spent
 * teleports, what the player's house was seen to hold, the bottomless bucket's last reading
 * and whether compost upkeep is on; in regular config, the side
 * panel's display toggles. The crop settings live in their own config items, read through
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
    private static final String KEY_BOTTOMLESS_BUCKET = "farming.bottomlessBucketUses";
    private static final String KEY_COMPOST_UPKEEP = "farming.compostUpkeep";

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

    /**
     * Persist the crop named by the current farming contract, by enum name.
     *
     * <p>Stored so the panel can name the contract before login, when the varbit is not yet
     * readable. A null crop clears the key.
     */
    public void saveFarmingContract(Crop crop) {
        if (crop == null) {
            store.unsetRSProfileConfiguration(CONFIG_GROUP, KEY_FARMING_CONTRACT);
            return;
        }
        store.setRSProfileConfiguration(CONFIG_GROUP, KEY_FARMING_CONTRACT, crop.name());
    }

    /** Load the persisted contract crop for the current RS profile, or null if unknown. */
    public Crop loadFarmingContract() {
        String stored = store.getRSProfileConfiguration(CONFIG_GROUP, KEY_FARMING_CONTRACT);
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return Crop.valueOf(stored);
        } catch (IllegalArgumentException ex) {
            // A crop renamed or dropped since the write: treat as unknown until the contract
            // varbit is read again.
            return null;
        }
    }

    /**
     * Persist how many of today's rationed teleports have been spent, per capability,
     * plus the day the counts belong to. One key per capability, so a retired or renamed
     * capability stops being read without corrupting the other counts.
     *
     * <p>The Ardougne cloak has no usage varbit, so its observed count survives a client
     * restart only through this store. The Explorer's ring is stored the same way, though
     * its varbit corrects the count at login anyway.
     */
    public void saveDailyTeleportLedger(long day, Map<Capability, Integer> usedToday) {
        store.setRSProfileConfiguration(CONFIG_GROUP, KEY_DAILY_TELEPORT_DAY,
                Long.toString(day));
        for (Capability capability : Capability.values()) {
            if (!capability.isRationedDaily()) {
                continue;
            }
            String key = KEY_DAILY_TELEPORT_PREFIX + capability.name();
            Integer spent = usedToday != null ? usedToday.get(capability) : null;
            if (spent == null || spent == 0) {
                store.unsetRSProfileConfiguration(CONFIG_GROUP, key);
            } else {
                store.setRSProfileConfiguration(CONFIG_GROUP, key, spent.toString());
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
        String dayStored = store.getRSProfileConfiguration(CONFIG_GROUP,
                KEY_DAILY_TELEPORT_DAY);
        long day;
        try {
            day = dayStored != null ? Long.parseLong(dayStored) : Long.MIN_VALUE;
        } catch (NumberFormatException ex) {
            day = Long.MIN_VALUE;
        }
        Map<Capability, Integer> used = new EnumMap<>(Capability.class);
        for (Capability capability : Capability.values()) {
            if (!capability.isRationedDaily()) {
                continue;
            }
            String stored = store.getRSProfileConfiguration(CONFIG_GROUP,
                    KEY_DAILY_TELEPORT_PREFIX + capability.name());
            if (stored == null || stored.isEmpty()) {
                continue;
            }
            try {
                used.put(capability, Integer.parseInt(stored));
            } catch (NumberFormatException ex) {
                // ignore malformed entry
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
     * <p>Features are stored by name, so a feature renamed or dropped is skipped on load
     * without losing the rest.
     */
    public void saveHouse(HouseSight house) {
        if (house == null || !house.isScanned()) {
            store.unsetRSProfileConfiguration(CONFIG_GROUP, KEY_HOUSE);
        } else {
            List<String> names = new ArrayList<>();
            for (HouseFeature feature : house.getBuilt()) {
                names.add(feature.name());
            }
            store.setRSProfileConfiguration(CONFIG_GROUP, KEY_HOUSE,
                    house.getScannedAtEpochSec() + ":" + String.join(",", names));
        }
        if (house == null || (!house.isNexusRead() && house.getAttuned().isEmpty())) {
            store.unsetRSProfileConfiguration(CONFIG_GROUP, KEY_HOUSE_NEXUS);
        } else {
            List<String> keys = new ArrayList<>();
            for (Integer key : house.getAttuned()) {
                keys.add(key.toString());
            }
            store.setRSProfileConfiguration(CONFIG_GROUP, KEY_HOUSE_NEXUS,
                    house.getNexusReadAtEpochSec() + ":" + String.join(",", keys));
        }
    }

    /**
     * Load what the current RS profile's house was last seen to hold. A profile with
     * nothing saved, or with a malformed value, reads as a house never seen.
     */
    public HouseSight loadHouse() {
        long scannedAt = 0L;
        Set<HouseFeature> built = EnumSet.noneOf(HouseFeature.class);
        String[] scan = stampAndList(store.getRSProfileConfiguration(CONFIG_GROUP, KEY_HOUSE));
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
        String[] nexus = stampAndList(
                store.getRSProfileConfiguration(CONFIG_GROUP, KEY_HOUSE_NEXUS));
        long nexusStamp = nexus != null ? parseStamp(nexus[0]) : -1L;
        if (nexusStamp >= 0L) {
            readAt = nexusStamp;
            for (String key : nexus[1].split(",")) {
                try {
                    attuned.add(Integer.parseInt(key));
                } catch (NumberFormatException ex) {
                    // ignore malformed entry
                }
            }
        }
        return HouseSight.of(built, scannedAt, attuned, readAt);
    }

    /**
     * Persist the uses of ultracompost the bottomless bucket was last read to hold. Null
     * clears the key, for a bucket never read.
     */
    public void saveBottomlessBucket(Integer uses) {
        if (uses == null) {
            store.unsetRSProfileConfiguration(CONFIG_GROUP, KEY_BOTTOMLESS_BUCKET);
            return;
        }
        store.setRSProfileConfiguration(CONFIG_GROUP, KEY_BOTTOMLESS_BUCKET, uses.toString());
    }

    /** The bucket's last reading for the current RS profile, or null when never read. */
    public Integer loadBottomlessBucket() {
        String stored = store.getRSProfileConfiguration(CONFIG_GROUP, KEY_BOTTOMLESS_BUCKET);
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            int uses = Integer.parseInt(stored);
            return uses >= 0 ? uses : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Persist whether compost upkeep was last on, so the gap between the two thresholds keeps
     * its state across a logout. Null clears the key.
     */
    public void saveCompostUpkeep(Boolean on) {
        if (on == null) {
            store.unsetRSProfileConfiguration(CONFIG_GROUP, KEY_COMPOST_UPKEEP);
            return;
        }
        store.setRSProfileConfiguration(CONFIG_GROUP, KEY_COMPOST_UPKEEP, on.toString());
    }

    /** Whether compost upkeep was last on, or null when it has never been decided. */
    public Boolean loadCompostUpkeep() {
        String stored = store.getRSProfileConfiguration(CONFIG_GROUP, KEY_COMPOST_UPKEEP);
        if ("true".equals(stored)) {
            return Boolean.TRUE;
        }
        return "false".equals(stored) ? Boolean.FALSE : null;
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

    /**
     * Remember whether the side panel is showing the stops the trip is not making.
     *
     * <p>Regular config, not RS-profile config: this is a display preference, not a fact
     * about an account, so it should not be per-account. The panel is also first drawn in
     * {@code startUp()}, before any RS profile exists.
     *
     * <p>Not a {@code @ConfigItem}: the control lives on the side panel itself.
     */
    public void saveFarmingShowSkipped(boolean show) {
        store.setConfiguration(CONFIG_GROUP, KEY_FARMING_SHOW_SKIPPED,
                Boolean.toString(show));
    }

    /** Whether to show skipped stops. Defaults to false when unset. */
    public boolean loadFarmingShowSkipped() {
        return Boolean.parseBoolean(
                store.getConfiguration(CONFIG_GROUP, KEY_FARMING_SHOW_SKIPPED));
    }

    public void saveFarmingShowBanks(boolean show) {
        store.setConfiguration(CONFIG_GROUP, KEY_FARMING_SHOW_BANKS,
                Boolean.toString(show));
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
        store.setConfiguration(CONFIG_GROUP, KEY_FARMING_TRAVEL_OPEN,
                Boolean.toString(open));
    }

    /** Whether the travel fold is open. Shut when unset. */
    public boolean loadFarmingTravelOpen() {
        return Boolean.parseBoolean(
                store.getConfiguration(CONFIG_GROUP, KEY_FARMING_TRAVEL_OPEN));
    }

    /** Remember whether the side panel's house fold is open. */
    public void saveFarmingHouseOpen(boolean open) {
        store.setConfiguration(CONFIG_GROUP, KEY_FARMING_HOUSE_OPEN, Boolean.toString(open));
    }

    /** Whether the house fold is open. Shut when unset. */
    public boolean loadFarmingHouseOpen() {
        return Boolean.parseBoolean(
                store.getConfiguration(CONFIG_GROUP, KEY_FARMING_HOUSE_OPEN));
    }

    /** Remember whether the side panel's settings fold is open. */
    public void saveFarmingSettingsOpen(boolean open) {
        store.setConfiguration(CONFIG_GROUP, KEY_FARMING_SETTINGS_OPEN, Boolean.toString(open));
    }

    /** Whether the settings fold is open. Shut when unset. */
    public boolean loadFarmingSettingsOpen() {
        return Boolean.parseBoolean(
                store.getConfiguration(CONFIG_GROUP, KEY_FARMING_SETTINGS_OPEN));
    }
}
