package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;

/**
 * 外观身份只包含可重现的状态；分辨率、取景与记录顺序不改变同一外观的键。
 */
public final class EntityAppearance {
    private EntityAppearance() {
    }

    public static JsonObject defaults() {
        var state = new JsonObject();
        state.addProperty("origin", "DEFAULT_INSTANCE");
        state.add("traits", new JsonObject());
        return state;
    }

    public static String key(JsonObject state) {
        if (!state.has("origin") || !state.has("traits") || !state.get("traits").isJsonObject())
            throw new IllegalArgumentException("Invalid appearance state");
        // 类型标记属于记录元数据；拒绝误放，避免为相同外观生成额外状态身份。
        for (String field : java.util.List.of("isMonster", "isPart"))
            if (state.has(field) || state.getAsJsonObject("traits").has(field))
                throw new IllegalArgumentException("Classification must be outside appearance state: " + field);
        String origin = state.get("origin").getAsString();
        if (!java.util.Set.of("DEFAULT_INSTANCE", "SPAWN_INITIALIZED", "OBSERVED_INSTANCE", "DECLARED_STATE").contains(origin))
            throw new IllegalArgumentException("Unknown appearance origin");
        String canonical = canonical(state);
        if (canonical.length() > 65536) throw new IllegalArgumentException("Appearance state exceeds budget");
        if (origin.equals("DEFAULT_INSTANCE") && state.getAsJsonObject("traits").size() == 0 && state.size() == 2)
            return "default";
        return ContentDigest.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonical(JsonElement node) {
        if (node.isJsonObject()) {
            var parts = new java.util.ArrayList<String>();
            for (String name : new java.util.TreeSet<>(node.getAsJsonObject().keySet()))
                parts.add(new JsonPrimitive(name).toString() + ":" + canonical(node.getAsJsonObject().get(name)));
            return "{" + String.join(",", parts) + "}";
        }
        if (node.isJsonArray()) {
            var parts = new java.util.ArrayList<String>();
            for (var child : node.getAsJsonArray()) parts.add(canonical(child));
            return "[" + String.join(",", parts) + "]";
        }
        if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isNumber())
            return node.getAsBigDecimal().stripTrailingZeros().toPlainString();
        return node.toString();
    }

    public static String path(String id, JsonObject state) {
        String key = key(state);
        if (key.equals("default")) return path(id, key);
        String origin = state.get("origin").getAsString();
        String prefix = switch (origin) {
            case "SPAWN_INITIALIZED" -> "spawn";
            case "OBSERVED_INSTANCE" -> "observed";
            case "DECLARED_STATE" -> "declared";
            default -> "default";
        };
        var traits = state.getAsJsonObject("traits");
        if (traits.has("age") && java.util.Set.of("adult", "baby").contains(traits.get("age").getAsString()))
            prefix += "_" + traits.get("age").getAsString();
        if (traits.has("color") && traits.get("color").isJsonPrimitive() && traits.get("color").getAsString().matches("[a-z_]{1,20}"))
            prefix += "_" + traits.get("color").getAsString();
        if (traits.has("equipment") && traits.get("equipment").isJsonObject())
            prefix += traits.getAsJsonObject("equipment").size() > 0 ? "_equipped" : "_unequipped";
        return path(id, key).replace(key + ".png", prefix + "_" + key.substring(0, 16) + ".png");
    }

    public static String path(String id, String key) {
        new ExportJob.Target(id, null);
        if (!key.equals("default") && !key.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid appearance key");
        String base = "entities/" + id.replace(':', '/');
        return key.equals("default") ? base + ".png" : base + "/states/" + key + ".png";
    }
}
