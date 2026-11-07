package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Comparator;

final class RuntimeRecipes {
    static JsonArray capture(MinecraftServer server) {
        var records = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        server.getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(holder -> holder.id().toString())).forEach(holder -> {
            String id = holder.id().toString();
            var recipe = holder.value();
            try {
                var definition = Recipe.CODEC.encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
                String serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString();
                var row = RecipeCaptureSupport.record(id, serializer, BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString(), recipe.isSpecial(), definition, "CODEC_JSON");
                if (yagen.waitmydawn.mkb_export.core.RecipeKeys.needsPreview(row)) try {
                    var preview = recipe.getResultItem(server.registryAccess());
                    if (!preview.isEmpty()) row.add("outputPreview", Platform.stackJson(preview, server));
                } catch (Exception optional) {
                    row.addProperty("previewError", EntitySamples.describe(optional));
                }
                records.add(row);
            } catch (Exception failure) {
                records.add(ServerKnowledgeCapture.failure(id, failure));
            }
        });
        return records;
    }
}
