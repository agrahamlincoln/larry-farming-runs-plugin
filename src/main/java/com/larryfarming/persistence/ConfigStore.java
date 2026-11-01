package com.larryfarming.persistence;

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
        };
    }
}
