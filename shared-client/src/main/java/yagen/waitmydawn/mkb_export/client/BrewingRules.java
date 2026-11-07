package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.*;

/** 读取已加载转换表；不对所有物品组合调用酿造判定来猜测规则。 */
final class BrewingRules {
    static JsonArray capture(MinecraftServer server){
        var rows=new JsonArray();Object brewing=Platform.brewingObject(server);boolean modern=Platform.modernTrades();
        try{
            var containers=(List<?>)RuleFields.get(brewing,modern?"containers":"ALLOWED_CONTAINERS");
            for(Object mix:(List<?>)RuleFields.get(brewing,modern?"potionMixes":"POTION_MIXES")){
                var from=(Holder<?>)RuleFields.get(mix,"from");var to=(Holder<?>)RuleFields.get(mix,"to");var ingredient=(Ingredient)RuleFields.get(mix,"ingredient");
                for(Object allowed:containers)for(var stack:((Ingredient)allowed).getItems()){
                    if(stack.isEmpty())continue;
                    var row=new JsonObject();row.addProperty("kind","POTION_CONVERSION");row.addProperty("inputPotionId",BuiltInRegistries.POTION.getKey((Potion)from.value()).toString());row.addProperty("outputPotionId",BuiltInRegistries.POTION.getKey((Potion)to.value()).toString());row.add("inputEffects",Platform.potionEffects((Potion)from.value()));row.add("outputEffects",Platform.potionEffects((Potion)to.value()));row.add("input",Platform.potionStack(stack.getItem(),(Holder<Potion>)from,server));row.add("ingredient",Platform.ingredientJson(ingredient,server));row.add("output",Platform.potionStack(stack.getItem(),(Holder<Potion>)to,server));add(rows,row);
                }
            }
            for(Object mix:(List<?>)RuleFields.get(brewing,modern?"containerMixes":"CONTAINER_MIXES")){
                var from=(Holder<?>)RuleFields.get(mix,"from");var to=(Holder<?>)RuleFields.get(mix,"to");var row=new JsonObject();row.addProperty("kind","CONTAINER_CONVERSION");row.addProperty("preservePotion",true);row.add("input",TradeRules.stack((Item)from.value(),1,server));row.add("ingredient",Platform.ingredientJson((Ingredient)RuleFields.get(mix,"ingredient"),server));row.add("output",TradeRules.stack((Item)to.value(),1,server));add(rows,row);
            }
        }catch(Exception failure){rows.add(ServerKnowledgeCapture.failure("mkb_export:brewing/vanilla",failure));}
        int index=0;
        for(Object recipe:Platform.customBrewing(server))try{if(recipe.getClass().getSimpleName().equals("VanillaBrewingRecipe"))continue;var row=Platform.customBrewingRule(recipe,server);row.addProperty("kind","LOADER_DECLARED");row.addProperty("order",index++);add(rows,row);}catch(Exception failure){rows.add(ServerKnowledgeCapture.failure("mkb_export:brewing/custom/"+index++,failure));}
        return rows;
    }
    private static void add(JsonArray rows,JsonObject row){row.addProperty("ticks",400);row.addProperty("fuel","minecraft:blaze_powder");row.addProperty("fuelUses",20);row.addProperty("bottlesPerOperation",3);String hash=yagen.waitmydawn.mkb_export.core.ContentDigest.sha256(row.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));row.addProperty("id","mkb_export:brewing/"+hash);row.addProperty("limitation","Declared conversion; loader recipe precedence, scripted overrides and fuel/time hooks may change effective behavior");for(var value:rows)if(value.getAsJsonObject().get("id").equals(row.get("id")))return;rows.add(row);}
    private BrewingRules(){}
}
