package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 状态身份只由显式组件或 NBT 决定，不将栈数量、显示文本或短注册名作为身份。
 */
public final class IconState {
    private IconState() {
    }

    public static String canonical(JsonElement node) {
        if (node.isJsonObject()) {
            var sorted = new JsonObject();
            var keys = new TreeSet<>(node.getAsJsonObject().keySet());
            for (String key : keys) sorted.add(key, JsonParser.parseString(canonical(node.getAsJsonObject().get(key))));
            return sorted.toString();
        }
        if (node.isJsonArray()) {
            var array = new JsonArray();
            node.getAsJsonArray().forEach(v -> array.add(JsonParser.parseString(canonical(v))));
            return array.toString();
        }
        return node.toString();
    }

    public static String variant(JsonObject state) {
        if (state == null) return "default";
        if (state.size() != 1 || !(state.has("components") && state.get("components").isJsonObject() && !state.getAsJsonObject("components").isEmpty()
                || state.has("nbt") && state.get("nbt").isJsonPrimitive() && state.getAsJsonPrimitive("nbt").isString() && !state.get("nbt").getAsString().isBlank()))
            throw new IllegalArgumentException("State must contain components or nbt");
        String json = canonical(state);
        if (json.length() > 65536) throw new IllegalArgumentException("State exceeds 64 KiB");
        return "state:" + ContentDigest.sha256(json.getBytes(StandardCharsets.UTF_8));
    }
}
