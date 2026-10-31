package com.larryfarming.persistence;

import java.util.Map;
import java.util.TreeMap;
import net.runelite.client.config.ConfigManager;

/**
 * The slice of {@link ConfigManager} that the plugin's persistence reads and writes.
 *
 * <p>The plugin persists only pre-encoded strings, so this needs none of ConfigManager's
 * typed overloads. The interface keeps the persistence independent of an injected client.
 */
public interface ConfigStore {

    String getRSProfileConfiguration(String group, String key);

    void setRSProfileConfiguration(String group, String key, String value);

    void unsetRSProfileConfiguration(String group, String key);

    String getConfiguration(String group, String key);

    void setConfiguration(String group, String key, String value);

    void unsetConfiguration(String group, String key);

    /**
     * The key of the RS profile that the RS-profile methods read and write, or null when
     * there is none. RuneLite has no RS profile until an account logs in.
     */
    String rsProfileKey();

    /**
     * Every key the current RS profile holds in {@code group}, without the group or profile
     * prefix, with its value. Empty with no RS profile, and by default.
     */
    default Map<String, String> profileEntries(String group) {
        return Map.of();
    }

    /** Every global key in {@code group}, without the group prefix, with its value. */
    default Map<String, String> configEntries(String group) {
        return Map.of();
    }

    /** The live client's store. */
    static ConfigStore of(ConfigManager configManager) {
        return new ConfigStore() {
            @Override
            public String getRSProfileConfiguration(String group, String key) {
                return configManager.getRSProfileConfiguration(group, key);
            }

            @Override
            public void setRSProfileConfiguration(String group, String key, String value) {
                configManager.setRSProfileConfiguration(group, key, value);
            }

            @Override
            public void unsetRSProfileConfiguration(String group, String key) {
                configManager.unsetRSProfileConfiguration(group, key);
            }

            @Override
            public String getConfiguration(String group, String key) {
                return configManager.getConfiguration(group, key);
            }

            @Override
            public void setConfiguration(String group, String key, String value) {
                configManager.setConfiguration(group, key, value);
            }

            @Override
            public void unsetConfiguration(String group, String key) {
                configManager.unsetConfiguration(group, key);
            }

            @Override
            public String rsProfileKey() {
                return configManager.getRSProfileKey();
            }

            @Override
            public Map<String, String> profileEntries(String group) {
                String profile = configManager.getRSProfileKey();
                Map<String, String> entries = new TreeMap<>();
                if (profile == null) {
                    return entries;
                }
                // Keys come back bare, the group and profile already stripped.
                for (String key : configManager.getRSProfileConfigurationKeys(group, profile, "")) {
                    String value = configManager.getConfiguration(group, profile, key);
                    if (value != null) {
                        entries.put(key, value);
                    }
                }
                return entries;
            }

            @Override
            public Map<String, String> configEntries(String group) {
                String prefix = group + ".";
                Map<String, String> entries = new TreeMap<>();
                // Keys come back whole, the group still on them.
                for (String whole : configManager.getConfigurationKeys(prefix)) {
                    String key = whole.substring(prefix.length());
                    String value = configManager.getConfiguration(group, key);
                    if (value != null) {
                        entries.put(key, value);
                    }
                }
                return entries;
            }
        };
    }
}
