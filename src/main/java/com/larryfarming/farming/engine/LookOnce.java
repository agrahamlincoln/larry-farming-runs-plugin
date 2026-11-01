package com.larryfarming.farming.engine;

import com.larryfarming.farming.detection.HouseLocation;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.Skill;
import com.larryfarming.snapshot.FarmingHouse;
import java.util.ArrayList;
import java.util.List;

/**
 * The stores Larry has never looked in, asked for in one line: "Check your bank, seed vault
 * and house." Larry assumes nothing about a store he has not seen, so its contents can
 * change the packing list and the routes once the player opens it.
 *
 * <p>Only stores the player can reach: the seed vault from the Farming Guild's level, the
 * house once the game says the player owns one.
 */
final class LookOnce {

    private LookOnce() {
    }

    /** The line, or empty when Larry has seen every store the player can reach. */
    static String sentence(PlayerState state, FarmingHouse house) {
        if (state == null) {
            return "";
        }
        List<String> stores = new ArrayList<>();
        if (state.getBankLastSeen() == null) {
            stores.add("bank");
        }
        // An unread level never asks: getSkillLevel answers 1 for it.
        if (state.getSeedVaultLastSeen() == null && state.getSkillLevel(Skill.FARMING) >= SeedSelector.FARMING_GUILD_LEVEL) {
            stores.add("seed vault");
        }
        if (Boolean.TRUE.equals(HouseLocation.ownsHouse(state.getVarbits())) && house != null) {
            if (house.getSight() == FarmingHouse.Sight.NOT_SEEN) {
                stores.add("house");
            } else if (house.getSight() == FarmingHouse.Sight.NEXUS_NOT_SEEN) {
                stores.add("Portal Nexus");
            }
        }
        if (stores.isEmpty()) {
            return "";
        }
        return "Check your " + joined(stores, "and") + ".";
    }

    /** "bank", "bank and house", "bank, seed vault and house", with {@code and} as given. */
    static String joined(List<String> names, String and) {
        int last = names.size() - 1;
        return last == 0 ? names.get(0) : String.join(", ", names.subList(0, last)) + " " + and + " " + names.get(last);
    }
}
