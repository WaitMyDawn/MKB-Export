package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

/**
 * 文件名由整批状态分配；默认图优先保留，排序和占用检查避免编号漂移与覆盖。
 */
public final class EntityFileLayout {
    private EntityFileLayout() {
    }

    public static Map<String, String> assign(JsonArray records) {
        var sorted = new TreeMap<String, JsonObject>();
        for (var element : records) {
            var row = element.getAsJsonObject();
            if (row.has("error") || row.has("imageRef") || !row.has("file")) continue;
            String key = row.has("state") ? EntityAppearance.key(row.getAsJsonObject("state")) : "default";
            sorted.put(row.get("id").getAsString() + "|" + key, row);
        }
        var result = new HashMap<String, String>();
        var used = new HashSet<String>();
        for (var entry : sorted.entrySet())
            if (entry.getKey().endsWith("|default")) {
                String path = EntityAppearance.path(entry.getValue().get("id").getAsString(), "default");
                used.add(path);
                result.put(entry.getKey(), path);
            }
        for (var entry : sorted.entrySet())
            if (!result.containsKey(entry.getKey())) {
                var row = entry.getValue();
                String id = row.get("id").getAsString();
                String readable = EntityAppearance.path(id, row.getAsJsonObject("state"));
                String suffix = readable.substring(readable.lastIndexOf("/states/") + 8).replaceFirst("_[0-9a-f]{16}\\.png$", "");
                String base = EntityAppearance.path(id, "default").replaceFirst("\\.png$", "") + "_" + suffix;
                String path = base + ".png";
                int index = 2;
                while (!used.add(path)) path = base + "_" + index++ + ".png";
                result.put(entry.getKey(), path);
            }
        for (var value : records) {
            var row = value.getAsJsonObject();
            if (row.has("imageRef")) {
                String id = row.get("id").getAsString(), key = row.get("stateKey").getAsString();
                String owner = result.get(id + "|" + row.get("imageRef").getAsString());
                if (owner == null) throw new IllegalArgumentException("Missing canonical image");
                result.put(id + "|" + key, owner);
            }
        }
        return Map.copyOf(result);
    }
}
