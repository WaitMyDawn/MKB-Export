package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import java.util.Set;

final class RecipeCaptureSupport {
    private static final Set<String> TYPES=Set.of("minecraft:crafting_shaped","minecraft:crafting_shapeless","minecraft:smelting","minecraft:blasting","minecraft:smoking","minecraft:campfire_cooking","minecraft:stonecutting","minecraft:smithing_transform","minecraft:smithing_trim");
    static JsonObject record(String id,String serializer,String type,boolean special,JsonObject definition,String serialization) {
        var row=new JsonObject();row.addProperty("id",id);row.addProperty("serializer",serializer);row.addProperty("recipeType",type);
        row.addProperty("runtimeLoaded",true);row.addProperty("serialization",serialization);row.addProperty("special",special);
        String status=special||serializer.equals("minecraft:smithing_trim")?"DYNAMIC":definition!=null&&TYPES.contains(serializer)?"STATIC":"UNSUPPORTED";
        row.addProperty("normalizationStatus",status);
        if(definition!=null)row.add("definition",yagen.waitmydawn.mkb_export.core.RecipeKeys.normalize(definition));
        if(!status.equals("STATIC"))row.addProperty("normalizationReason",status.equals("DYNAMIC")?"Output depends on input state or runtime matching":"Custom recipe semantics require an adapter");
        return row;
    }
}
