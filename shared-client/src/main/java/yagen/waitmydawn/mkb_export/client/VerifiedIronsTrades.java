package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** 仅适配已核对的工厂字节码；版本或候选组改变时回退到未解析，避免沿用旧价格。 */
final class VerifiedIronsTrades {
    private static final String ROOT="io.redspace.ironsspellbooks.player.AdditionalWanderingTrades";
    private static final String ROOT_HASH="32b90d19308beadbba54ed85ad2113339ec87926a8b422b8e83a2f964a1aca15";
    static boolean adapt(JsonObject row,Object factory,MinecraftServer server)throws Exception {
        String type=factory.getClass().getName();if(!type.equals(ROOT+"$InkSellTrade")&&!type.equals(ROOT+"$InkBuyTrade"))return false;
        String expected=type.endsWith("$InkSellTrade")?"f36515bca976fd5590eb1d9d948b055149ccc4b8d4bb0a8803b34048fc8b7768":"8a55ba5e4a1887a0af0cc1e754b3252354d1241c46bcde7b0b5028ece5490064";
        if(!matches(factory,ROOT,ROOT_HASH)||!matches(factory,type,expected))return false;
        Object callback=RuleFields.get(factory,"getOffer");Item captured=null;for(var field:callback.getClass().getDeclaredFields()){field.setAccessible(true);Object value=field.get(callback);if(value instanceof Item item){if(captured!=null)throw new IllegalArgumentException("Ambiguous captured trade item");captured=item;}}
        if(captured==null)return false;Object rarity=captured.getClass().getMethod("getRarity").invoke(captured);int rank=((Number)rarity.getClass().getMethod("getValue").invoke(rarity)).intValue();var inputs=new JsonArray();
        if(type.endsWith("$InkSellTrade")){inputs.add(range(Items.EMERALD,8*rank+2,8*rank+3,server));row.add("output",TradeRules.stack(captured,1,server));row.addProperty("maxUses",4);}
        else{inputs.add(TradeRules.stack(captured,1,server));row.add("output",range(Items.EMERALD,5*rank+2,5*rank+3,server));var variants=new JsonArray();var emerald=range(Items.EMERALD,5*rank+2,5*rank+3,server);emerald.addProperty("probability",0.5);variants.add(emerald);var essence=range(find("irons_spellbooks:arcane_essence"),5*rank/2+2,5*rank/2+3,server);essence.addProperty("probability",0.5);variants.add(essence);row.add("outputAlternatives",variants);row.addProperty("maxUses",8);}
        row.add("inputs",inputs);row.addProperty("status","RANDOM_RULE");row.addProperty("randomCount","uniform inclusive minCount..maxCount");row.addProperty("villagerXp",1);row.addProperty("priceMultiplier",0.05);row.addProperty("adapter","irons_spellbooks:3.16.3-bytecode-verified");return true;
    }
    static void groups(JsonObject pool,Object[] factories,MinecraftServer server){
        var matching=Arrays.stream(factories).filter(f->f.getClass().getName().equals(ROOT+"$SimpleTrade")).toList();if(matching.size()!=2||!pool.get("entity").getAsString().equals("minecraft:wandering_trader")||pool.get("level").getAsInt()!=2)return;
        Object first=matching.get(0);if(!matches(first,ROOT,ROOT_HASH)||!matches(first,ROOT+"$SimpleTrade","97f93850fb4d0fdc3f648e7e5604aa9f25d7c1643ec456cb737f673bb0740ee9"))return;
        try{for(Object factory:matching){Object callback=RuleFields.get(factory,"getOffer");if(!callback.getClass().getNestHost().getName().equals(ROOT)||callback.getClass().getDeclaredFields().length!=0)return;}
            var rules=new JsonArray();var fragment=new JsonObject();var inputs=new JsonArray();inputs.add(range(Items.EMERALD,56,63,server));inputs.add(range(Items.ECHO_SHARD,1,3,server));fragment.add("inputs",inputs);fragment.add("output",TradeRules.stack(find("irons_spellbooks:ancient_knowledge_fragment"),1,server));fragment.addProperty("maxUses",8);rules.add(fragment);
            var wand=new JsonObject();inputs=new JsonArray();inputs.add(TradeRules.stack(Items.EMERALD,64,server));inputs.add(range(Items.EMERALD,48,64,server));wand.add("inputs",inputs);wand.add("output",TradeRules.stack(find("irons_spellbooks:hither_thither_wand"),1,server));wand.addProperty("maxUses",1);rules.add(wand);
            for(var element:rules){var rule=element.getAsJsonObject();rule.addProperty("status","RANDOM_RULE");rule.addProperty("scope","VERIFIED_PAIRED_FACTORY_GROUP");rule.addProperty("identityLimitation","Rules are known for this complete two-factory group, not mapped to an opaque lambda instance index");rule.addProperty("adapter","irons_spellbooks:3.16.3-bytecode-verified");rule.addProperty("selectionProbability",1.0/pool.get("candidateCount").getAsInt());rule.addProperty("randomCount","independent uniform inclusive ranges");}
            pool.add("verifiedGroupRules",rules);
        }catch(Exception unavailable){pool.addProperty("groupAdapterFailure",EntitySamples.describe(unavailable));}
    }
    private static Item find(String name){var id=ResourceLocation.tryParse(name);if(id==null||!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Missing registered item: "+name);return BuiltInRegistries.ITEM.get(id);}
    private static JsonObject range(Item item,int min,int max,MinecraftServer server){var row=TradeRules.stack(item,1,server);row.remove("count");row.addProperty("minCount",min);row.addProperty("maxCount",max);return row;}
    static boolean matches(Object factory,String type,String expected){try(var input=factory.getClass().getClassLoader().getResourceAsStream(type.replace('.','/')+".class")){if(input==null)return false;byte[] bytes=input.readNBytes(262145);return bytes.length<=262144&&yagen.waitmydawn.mkb_export.core.ContentDigest.sha256(bytes).equals(expected);}catch(java.io.IOException invalid){return false;}}
    private VerifiedIronsTrades(){}
}
