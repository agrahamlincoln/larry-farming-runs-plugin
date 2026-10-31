package com.larryfarming.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;

/**
 * The changes that turn one JSON tree into another, and their application.
 *
 * <p>A change is an object with a path {@code "p"}, an array of keys and indexes from the
 * root, and one of: {@code "v"}, the value to set there (an index one past an array's end
 * appends); {@code "d"}, delete that key; or {@code "n"}, cut the array there to that length.
 * Arrays are compared index by index, so an item inserted near the front of a long list costs
 * a change per later item. The journal writes a whole tree instead whenever the changes are
 * not the smaller.
 */
public final class JsonDiff {

    private JsonDiff() {
    }

    /** The changes from {@code from} to {@code to}, empty when they are equal. */
    public static JsonArray between(JsonElement from, JsonElement to) {
        JsonArray changes = new JsonArray();
        walk(nullSafe(from), nullSafe(to), new JsonArray(), changes);
        return changes;
    }

    /** {@code base} with the changes applied. {@code base} itself is left untouched. */
    public static JsonElement apply(JsonElement base, JsonArray changes) {
        JsonElement root = nullSafe(base).deepCopy();
        for (JsonElement element : changes) {
            JsonObject change = element.getAsJsonObject();
            JsonArray path = change.getAsJsonArray("p");
            if (path.size() == 0) {
                // Only a set reaches the root: a root that differs in kind is replaced whole.
                root = change.get("v").deepCopy();
                continue;
            }
            JsonElement parent = root;
            for (int i = 0; i < path.size() - 1; i++) {
                parent = child(parent, path.get(i));
            }
            JsonElement last = path.get(path.size() - 1);
            if (parent.isJsonArray()) {
                JsonArray array = parent.getAsJsonArray();
                int index = last.getAsInt();
                if (change.has("n")) {
                    int length = change.get("n").getAsInt();
                    while (array.size() > length) {
                        array.remove(array.size() - 1);
                    }
                } else if (index == array.size()) {
                    array.add(change.get("v").deepCopy());
                } else {
                    array.set(index, change.get("v").deepCopy());
                }
            } else {
                JsonObject object = parent.getAsJsonObject();
                if (change.has("d")) {
                    object.remove(last.getAsString());
                } else {
                    object.add(last.getAsString(), change.get("v").deepCopy());
                }
            }
        }
        return root;
    }

    private static void walk(JsonElement from, JsonElement to, JsonArray path,
            JsonArray changes) {
        if (from.isJsonObject() && to.isJsonObject()) {
            JsonObject before = from.getAsJsonObject();
            JsonObject after = to.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : before.entrySet()) {
                if (!after.has(entry.getKey())) {
                    JsonObject change = new JsonObject();
                    change.add("p", with(path, new JsonPrimitive(entry.getKey())));
                    change.addProperty("d", 1);
                    changes.add(change);
                }
            }
            for (Map.Entry<String, JsonElement> entry : after.entrySet()) {
                JsonArray at = with(path, new JsonPrimitive(entry.getKey()));
                if (before.has(entry.getKey())) {
                    walk(nullSafe(before.get(entry.getKey())), nullSafe(entry.getValue()), at,
                            changes);
                } else {
                    set(at, entry.getValue(), changes);
                }
            }
            return;
        }
        if (from.isJsonArray() && to.isJsonArray()) {
            JsonArray before = from.getAsJsonArray();
            JsonArray after = to.getAsJsonArray();
            int shared = Math.min(before.size(), after.size());
            for (int i = 0; i < shared; i++) {
                walk(nullSafe(before.get(i)), nullSafe(after.get(i)),
                        with(path, new JsonPrimitive(i)), changes);
            }
            for (int i = shared; i < after.size(); i++) {
                set(with(path, new JsonPrimitive(i)), after.get(i), changes);
            }
            if (after.size() < before.size()) {
                JsonObject change = new JsonObject();
                change.add("p", with(path, new JsonPrimitive(after.size())));
                change.addProperty("n", after.size());
                changes.add(change);
            }
            return;
        }
        if (!from.equals(to)) {
            set(path, to, changes);
        }
    }

    private static void set(JsonArray path, JsonElement value, JsonArray changes) {
        JsonObject change = new JsonObject();
        change.add("p", path);
        change.add("v", nullSafe(value));
        changes.add(change);
    }

    private static JsonElement child(JsonElement parent, JsonElement key) {
        return parent.isJsonArray()
                ? parent.getAsJsonArray().get(key.getAsInt())
                : parent.getAsJsonObject().get(key.getAsString());
    }

    private static JsonArray with(JsonArray path, JsonPrimitive key) {
        JsonArray longer = new JsonArray();
        longer.addAll(path);
        longer.add(key);
        return longer;
    }

    private static JsonElement nullSafe(JsonElement element) {
        return element != null ? element : JsonNull.INSTANCE;
    }
}
