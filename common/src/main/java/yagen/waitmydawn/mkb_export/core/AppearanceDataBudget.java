package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

/**
 * 在复制或规范化扩展数据前拒绝环、超深结构和非有限数，避免单个适配器破坏整包。
 */
public final class AppearanceDataBudget {
    private AppearanceDataBudget() {
    }

    public static void validate(JsonObject extra) {
        int[] nodes = {0};
        walk(extra, 0, nodes, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (extra.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384)
            throw new IllegalArgumentException("Appearance extra exceeds 16 KiB");
    }

    private static void walk(JsonElement node, int depth, int[] count, Set<JsonElement> path) {
        if (depth > 32 || ++count[0] > 4096 || node == null)
            throw new IllegalArgumentException("Appearance extra structure exceeds budget");
        if (node.isJsonObject() || node.isJsonArray()) {
            if (!path.add(node)) throw new IllegalArgumentException("Cyclic appearance extra");
            if (node.isJsonObject()) {
                for (var e : node.getAsJsonObject().entrySet()) {
                    if (e.getKey().length() > 256) throw new IllegalArgumentException("Appearance extra key too long");
                    walk(e.getValue(), depth + 1, count, path);
                }
            } else for (var child : node.getAsJsonArray()) walk(child, depth + 1, count, path);
            path.remove(node);
        } else if (node.isJsonPrimitive()) {
            var p = node.getAsJsonPrimitive();
            if (p.isNumber() && !Double.isFinite(p.getAsDouble()))
                throw new IllegalArgumentException("Non-finite appearance extra");
            if (p.isString() && p.getAsString().length() > 16384)
                throw new IllegalArgumentException("Appearance extra string too long");
        }
    }
}
