package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 从已捕获的声明堆叠追加精确状态图标；随机、未知和容器保留状态规则不能被实例化成默认颜色。
 */
public final class RuleIconTargets {
    public static List<ExportJob.Target> extend(List<ExportJob.Target> base, List<KnowledgePayload> payloads) {
        var selected = new HashSet<String>();
        var targets = new TreeMap<String, ExportJob.Target>();
        for (var target : base) {
            selected.add(target.itemId());
            targets.put(target.key(), target);
        }
        for (var payload : payloads)
            if (Set.of("trades", "brewing", "recipes").contains(payload.category()))
                visit(JsonParser.parseString(new String(payload.bytes(), StandardCharsets.UTF_8)), selected, targets, 0);
        if (targets.size() > 50000)
            throw new IllegalArgumentException("Exact rule icon states exceed export target budget");
        return List.copyOf(targets.values());
    }

    private static void visit(JsonElement element, Set<String> selected, Map<String, ExportJob.Target> targets, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Rule icon state nesting exceeds budget");
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) visit(child, selected, targets, depth + 1);
            return;
        }
        if (!element.isJsonObject()) return;
        var row = element.getAsJsonObject();
        if (row.has("error") || row.has("dynamicState") && row.get("dynamicState").getAsBoolean()) return;
        String id = row.has("item") && row.get("item").isJsonPrimitive() ? row.get("item").getAsString() : row.has("id") && row.get("id").isJsonPrimitive() ? row.get("id").getAsString() : "";
        if (selected.contains(id)) {
            var state = new JsonObject();
            if (row.has("components") && row.get("components").isJsonObject() && !row.getAsJsonObject("components").isEmpty())
                state.add("components", row.get("components").deepCopy());
            if (row.has("nbt") && row.get("nbt").isJsonPrimitive()) state.add("nbt", row.get("nbt").deepCopy());
            if (!state.isEmpty()) {
                var target = new ExportJob.Target(id, state);
                targets.putIfAbsent(target.key(), target);
            }
        }
        for (var field : row.entrySet())
            if (!Set.of("components", "nbt", "networkBase64", "randomOutputs").contains(field.getKey()) && (field.getValue().isJsonObject() || field.getValue().isJsonArray()))
                visit(field.getValue(), selected, targets, depth + 1);
    }

    private RuleIconTargets() {
    }
}
