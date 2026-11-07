package yagen.waitmydawn.mkb_export.client;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import java.util.*;
/** 已核对版本的固定交易直接读取字段，避免调用工厂或按相似字段名推测数量方向。 */
final class VerifiedStaticTrades {
    private static final Map<String,String> HASHES=Map.of(
        "com.iafenvoy.iceandfire.util.trade.factory.BuyWithPrice","a4f7b776133faa7852f94596d35a784400e03037d1b4782121c29b17d59f7dbb",
        "com.github.alexthe666.alexsmobs.misc.ItemsForEmeraldsTrade","0450843c0bbfcd74c13c4315d8a166dfcddfa7a7ec8ea16f8b9494236ea6eef8",
        "com.github.alexthe666.alexsmobs.misc.EmeraldsForItemsTrade","8d33b82762176926fab966074f02f921dc88e026bf08c7e0b9520900a882a632");
    static boolean adapt(JsonObject row,Object factory,MinecraftServer server)throws Exception {
        String type=factory.getClass().getName(),hash=HASHES.get(type);if(hash==null||!VerifiedIronsTrades.matches(factory,type,hash))return false;var inputs=new JsonArray();JsonObject output;
        if(type.endsWith("BuyWithPrice")){
            for(String field:List.of("input1","input2")){var stack=(ItemStack)RuleFields.get(factory,field);if(stack!=null&&!stack.isEmpty())inputs.add(TradeRules.stack(stack.getItem(),stack.getCount(),server));}
            output=Platform.knowledgeStack((ItemStack)RuleFields.get(factory,"output"),server);row.addProperty("villagerXp",RuleFields.number(factory,"experience"));row.addProperty("priceMultiplier",((Number)RuleFields.get(factory,"multiplier")).doubleValue());
        }else{
            if(type.endsWith("ItemsForEmeraldsTrade")){inputs.add(TradeRules.stack(Items.EMERALD,RuleFields.number(factory,"emeraldCount"),server));output=TradeRules.stack(((ItemStack)RuleFields.get(factory,"sellingItem")).getItem(),RuleFields.number(factory,"sellingItemCount"),server);}
            else{inputs.add(TradeRules.stack((Item)RuleFields.get(factory,"tradeItem"),1,server));output=TradeRules.stack(Items.EMERALD,RuleFields.number(factory,"count"),server);}
            row.addProperty("villagerXp",RuleFields.number(factory,"xpValue"));row.addProperty("priceMultiplier",((Number)RuleFields.get(factory,"priceMultiplier")).doubleValue());
        }
        row.add("inputs",inputs);row.add("output",output);row.addProperty("maxUses",RuleFields.number(factory,"maxUses"));row.addProperty("status","DECLARATIVE");row.addProperty("adapter","bytecode-sha256:"+hash);return true;
    }
    private VerifiedStaticTrades(){}
}
