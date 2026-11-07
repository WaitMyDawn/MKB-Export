package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

/**
 * 统计实际管理器覆盖，不把未适配序列化器或管理器外机制计为不存在。
 */
public final class ProcessingCoverage {
    public static JsonObject describe(JsonArray recipes) {
        var groups = new TreeMap<String, TreeMap<String, Integer>>();
        int failed = 0;
        for (var value : recipes) {
            var row = value.getAsJsonObject();
            if (row.has("error")) {
                failed++;
                continue;
            }
            String type = row.has("recipeType") ? row.get("recipeType").getAsString() : "unknown";
            String serializer = row.has("serializer") ? row.get("serializer").getAsString() : "unknown";
            String status = row.has("normalizationStatus") ? row.get("normalizationStatus").getAsString() : "UNSUPPORTED";
            groups.computeIfAbsent(type + " / " + serializer, ignored -> new TreeMap<>()).merge(status, 1, Integer::sum);
        }
        var result = new JsonObject();
        result.addProperty("id", "mkb_export:recipe_manager_coverage");
        result.addProperty("station", "mkb_export:recipe_manager");
        result.addProperty("coverage", "PROCESSING_INVENTORY");
        result.addProperty("total", recipes.size());
        result.addProperty("failedRecords", failed);
        var operations = new JsonArray();
        var details = new JsonObject();
        groups.forEach((key, counts) -> {
            operations.add(key + " : " + counts);
            var numbers = new JsonObject();
            counts.forEach(numbers::addProperty);
            details.add(key, numbers);
        });
        result.add("operations", operations);
        result.add("serializers", details);
        result.addProperty("limitation", "Inventory of loaded RecipeManager entries only; STATIC is normalized data coverage, not proof of workstation interaction or arbitrary custom block rules. Brewing is captured separately; anvil baseline rules are contextual. Unlisted programmatic processing requires a dedicated adapter");
        return result;
    }

    private ProcessingCoverage() {
    }
}
