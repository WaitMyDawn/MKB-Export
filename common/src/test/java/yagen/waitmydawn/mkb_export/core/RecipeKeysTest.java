package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeKeysTest {
    JsonObject shaped(String[] pattern, String... ingredients) {
        var root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shaped");
        var rows = new JsonArray();
        for (String line : pattern) rows.add(line);
        root.add("pattern", rows);
        var keys = new JsonObject();
        for (int i = 0; i < ingredients.length; i++)
            keys.add(Character.toString((char) ('A' + i)), JsonParser.parseString(ingredients[i]));
        root.add("key", keys);
        return root;
    }

    void sameSlots(JsonObject before, JsonObject after) {
        var a = before.getAsJsonArray("pattern");
        var b = after.getAsJsonArray("pattern");
        assertEquals(a.size(), b.size());
        for (int y = 0; y < a.size(); y++) {
            String left = a.get(y).getAsString(), right = b.get(y).getAsString();
            assertEquals(left.length(), right.length());
            for (int x = 0; x < left.length(); x++) {
                if (left.charAt(x) == ' ') assertEquals(' ', right.charAt(x));
                else
                    assertEquals(IconState.canonical(before.getAsJsonObject("key").get(String.valueOf(left.charAt(x)))), IconState.canonical(after.getAsJsonObject("key").get(String.valueOf(right.charAt(x)))));
            }
        }
    }

    @Test
    void duplicateDiamondAndWoodenTagsBecomeDDW() {
        var before = shaped(new String[]{"A", "B", "C"}, "{\"tag\":\"forge:gems/diamond\"}", "{\"tag\":\"forge:gems/diamond\"}", "{\"tag\":\"forge:rods/wooden\"}");
        var after = RecipeKeys.normalize(before);
        assertEquals("[\"D\",\"D\",\"W\"]", after.get("pattern").toString());
        assertEquals(2, after.getAsJsonObject("key").size());
        sameSlots(before, after);
        assertEquals(after, RecipeKeys.normalize(after));
    }

    @Test
    void compassRecipeUsesDSD_SCS_DSD() {
        String d = "{\"item\":\"minecraft:diamond_block\"}", s = "{\"item\":\"paraglider:spirit_orb\"}", c = "{\"item\":\"minecraft:compass\"}";
        var before = shaped(new String[]{"ABC", "DEF", "GHI"}, d, s, d, s, c, s, d, s, d);
        var after = RecipeKeys.normalize(before);
        assertEquals("[\"DSD\",\"SCS\",\"DSD\"]", after.get("pattern").toString());
        assertEquals(3, after.getAsJsonObject("key").size());
        sameSlots(before, after);
    }

    @Test
    void itemTagAndComponentConditionsRemainDistinctDespiteInitialCollisions() {
        var before = shaped(new String[]{"ABC", " DE"}, "{\"item\":\"minecraft:diamond\"}", "{\"tag\":\"forge:gems/diamond\"}", "{\"item\":\"demo:diamond\",\"components\":{\"mode\":1}}", "{\"components\":{\"mode\":2},\"item\":\"demo:diamond\"}", "[ {\"item\":\"minecraft:diamond\"}, {\"tag\":\"demo:other\"} ]");
        var after = RecipeKeys.normalize(before);
        assertEquals(5, after.getAsJsonObject("key").size());
        sameSlots(before, after);
        assertEquals(after, RecipeKeys.normalize(before));
    }

    @Test
    void modernStringIngredientsAndBlankSlotsArePreserved() {
        var before = shaped(new String[]{" A ", " B "}, "\"minecraft:diamond\"", "\"minecraft:diamond\"");
        var after = RecipeKeys.normalize(before);
        assertEquals(" D ", after.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals(" D ", after.getAsJsonArray("pattern").get(1).getAsString());
        sameSlots(before, after);
        var custom = before.deepCopy();
        custom.addProperty("type", "demo:custom");
        assertEquals(custom, RecipeKeys.normalize(custom));
    }

    @Test
    void previewsAreNeededOnlyWhenDefinitionCannotProvideStaticOutput() {
        var row = new JsonObject();
        row.addProperty("normalizationStatus", "STATIC");
        var definition = new JsonObject();
        definition.add("result", new JsonObject());
        row.add("definition", definition);
        assertFalse(RecipeKeys.needsPreview(row));
        row.addProperty("normalizationStatus", "DYNAMIC");
        assertTrue(RecipeKeys.needsPreview(row));
        row.addProperty("normalizationStatus", "STATIC");
        definition.remove("result");
        assertTrue(RecipeKeys.needsPreview(row));
    }
}
