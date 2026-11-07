package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.*;

import java.util.*;

/**
 * Forge 1.20.1 没有统一 JSON 编码器；已适配类型读取实例，其他类型保留有界网络表示。
 */
final class RuntimeRecipes {
    static JsonArray capture(MinecraftServer server) {
        var records = new JsonArray();
        server.getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(recipe -> recipe.getId().toString())).forEach(recipe -> {
            String id = recipe.getId().toString();
            var buffer = new FriendlyByteBuf(Unpooled.buffer(1024, 2 * 1024 * 1024));
            try {
                String serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString();
                JsonObject definition = null;
                String adaptationError = null;
                try {
                    definition = definition(recipe, serializer, server, buffer);
                } catch (Exception unsupported) {
                    adaptationError = EntitySamples.describe(unsupported);
                    buffer.clear();
                }
                var row = RecipeCaptureSupport.record(id, serializer, BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(), recipe.isSpecial(), definition, definition == null ? "NETWORK_ONLY" : "RUNTIME_ADAPTER");
                if (adaptationError != null) row.addProperty("adaptationError", adaptationError);
                if (definition == null || !row.get("normalizationStatus").getAsString().equals("STATIC")) {
                    try {
                        if (buffer.writerIndex() == 0) encode(recipe, buffer);
                        byte[] bytes = new byte[buffer.writerIndex()];
                        buffer.getBytes(0, bytes);
                        row.addProperty("networkBase64", Base64.getEncoder().encodeToString(bytes));
                    } catch (Exception optional) {
                        if (definition == null) throw optional;
                        row.addProperty("networkError", EntitySamples.describe(optional));
                    }
                }
                if (yagen.waitmydawn.mkb_export.core.RecipeKeys.needsPreview(row)) try {
                    var preview = recipe.getResultItem(server.registryAccess());
                    if (!preview.isEmpty()) row.add("outputPreview", Platform.stackJson(preview));
                } catch (Exception optional) {
                    row.addProperty("previewError", EntitySamples.describe(optional));
                }
                if (recipe.getClass() == SmithingTransformRecipe.class || recipe.getClass() == SmithingTrimRecipe.class)
                    row.addProperty("ingredientRepresentation", "NETWORK_EXPANDED_CANDIDATES");
                records.add(row);
            } catch (Exception failure) {
                records.add(ServerKnowledgeCapture.failure(id, failure));
            } finally {
                buffer.release();
            }
        });
        return records;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void encode(Recipe recipe, FriendlyByteBuf buffer) {
        recipe.getSerializer().toNetwork(buffer, recipe);
    }

    private static JsonObject definition(Recipe<?> recipe, String type, MinecraftServer server, FriendlyByteBuf network) {
        var root = new JsonObject();
        root.addProperty("type", type);
        root.addProperty("group", recipe.getGroup());
        var ingredients = recipe.getIngredients();
        if (recipe.getClass() == ShapedRecipe.class) {
            var shaped = (ShapedRecipe) recipe;
            var pattern = new JsonArray();
            var keys = new JsonObject();
            int index = 0;
            for (int y = 0; y < shaped.getHeight(); y++) {
                var line = new StringBuilder();
                for (int x = 0; x < shaped.getWidth(); x++, index++) {
                    var ingredient = ingredients.get(index);
                    char key = (char) ('A' + index);
                    line.append(ingredient.isEmpty() ? ' ' : key);
                    if (!ingredient.isEmpty()) keys.add(String.valueOf(key), ingredient.toJson());
                }
                pattern.add(line.toString());
            }
            root.add("pattern", pattern);
            root.add("key", keys);
        } else if (recipe.getClass() == ShapelessRecipe.class) {
            var inputs = new JsonArray();
            ingredients.forEach(ingredient -> inputs.add(ingredient.toJson()));
            root.add("ingredients", inputs);
        } else if (Set.of(SmeltingRecipe.class, BlastingRecipe.class, SmokingRecipe.class, CampfireCookingRecipe.class).contains(recipe.getClass())) {
            var cooking = (AbstractCookingRecipe) recipe;
            root.add("ingredient", ingredients.get(0).toJson());
            root.addProperty("cookingtime", cooking.getCookingTime());
            root.addProperty("experience", cooking.getExperience());
        } else if (recipe.getClass() == StonecutterRecipe.class) root.add("ingredient", ingredients.get(0).toJson());
        else if (recipe.getClass() == SmithingTransformRecipe.class || recipe.getClass() == SmithingTrimRecipe.class) {
            if (network.writerIndex() == 0) encode(recipe, network);
            network.readerIndex(0);
            for (String key : List.of("template", "base", "addition"))
                root.add(key, Ingredient.fromNetwork(network).toJson());
        } else return null;
        if (recipe.getClass() != SmithingTrimRecipe.class)
            root.add("result", Platform.stackJson(recipe.getResultItem(server.registryAccess())));
        return root;
    }
}
