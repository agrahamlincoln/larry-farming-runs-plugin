package com.larryfarming.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.larryfarming.model.ItemStack;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** What a container gained and lost between two readings, for a journal event. */
public final class ItemDelta {

    private ItemDelta() {
    }

    /**
     * One line per item whose count moved, as {@code {"id", "name", "n"}} with {@code n} the
     * change, gains positive. Ordered by item id; empty when nothing moved.
     */
    public static JsonArray between(Collection<ItemStack> before, Collection<ItemStack> after) {
        Map<Integer, Integer> counts = new TreeMap<>();
        Map<Integer, String> names = new HashMap<>();
        tally(before, -1, counts, names);
        tally(after, 1, counts, names);
        JsonArray lines = new JsonArray();
        for (Map.Entry<Integer, Integer> count : counts.entrySet()) {
            if (count.getValue() == 0) {
                continue;
            }
            JsonObject line = new JsonObject();
            line.addProperty("id", count.getKey());
            line.addProperty("name", names.get(count.getKey()));
            line.addProperty("n", count.getValue());
            lines.add(line);
        }
        return lines;
    }

    private static void tally(Collection<ItemStack> stacks, int sign, Map<Integer, Integer> counts,
            Map<Integer, String> names) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItemId() <= 0) {
                continue;
            }
            counts.merge(stack.getItemId(), sign * stack.getQuantity(), Integer::sum);
            if (stack.getName() != null) {
                names.putIfAbsent(stack.getItemId(), stack.getName());
            }
        }
    }
}
