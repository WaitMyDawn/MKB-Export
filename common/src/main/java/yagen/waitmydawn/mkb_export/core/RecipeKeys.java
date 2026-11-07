package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

/**
 * 只重命名有序配方的材料符号；完整材料条件参与去重，不合并物品与标签或重排候选数组。
 */
public final class RecipeKeys {
    private RecipeKeys() {
    }

    public static JsonObject normalize(JsonObject input) {
        var result = input.deepCopy();
        if (!result.has("type") || !result.get("type").getAsString().equals("minecraft:crafting_shaped")) return result;
        var old = result.getAsJsonObject("key");
        var keys = new JsonObject();
        var pattern = new JsonArray();
        var assigned = new LinkedHashMap<String, String>();
        var used = new HashSet<Character>();
        for (var row : result.getAsJsonArray("pattern")) {
            var line = new StringBuilder();
            for (char token : row.getAsString().toCharArray()) {
                if (token == ' ') {
                    line.append(' ');
                    continue;
                }
                var ingredient = old.get(String.valueOf(token));
                if (ingredient == null) throw new IllegalArgumentException("Pattern has no ingredient: " + token);
                String identity = IconState.canonical(ingredient), symbol = assigned.get(identity);
                if (symbol == null) {
                    symbol = Character.toString(choose(ingredient, used));
                    assigned.put(identity, symbol);
                    keys.add(symbol, ingredient.deepCopy());
                }
                line.append(symbol);
            }
            pattern.add(line.toString());
        }
        result.add("key", keys);
        result.add("pattern", pattern);
        return result;
    }

    private static char choose(JsonElement ingredient, Set<Character> used) {
        String path = "";
        if (ingredient.isJsonPrimitive() && ingredient.getAsJsonPrimitive().isString()) path = ingredient.getAsString();
        else if (ingredient.isJsonObject()) {
            var object = ingredient.getAsJsonObject();
            for (String field : List.of("item", "tag"))
                if (object.has(field) && object.get(field).isJsonPrimitive()) {
                    path = object.get(field).getAsString();
                    break;
                }
        }
        path = path.substring(path.lastIndexOf(':') + 1);
        path = path.substring(path.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT);
        var candidates = new StringBuilder();
        for (String word : path.split("_")) if (!word.isEmpty()) candidates.append(word.charAt(0));
        candidates.append(path).append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        for (char key : candidates.toString().toCharArray()) if (key >= 'A' && key <= 'Z' && used.add(key)) return key;
        throw new IllegalArgumentException("Too many unique recipe ingredients");
    }

    public static boolean needsPreview(JsonObject row) {
        return !row.get("normalizationStatus").getAsString().equals("STATIC") || !row.has("definition") || !row.getAsJsonObject("definition").has("result");
    }
}
