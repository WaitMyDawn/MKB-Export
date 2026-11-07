package yagen.waitmydawn.mkb_export.client;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import yagen.waitmydawn.mkb_export.api.trade.TradeRuleAdapter;
import java.util.*;

/** 一个适配器失败只影响当前候选，不允许覆盖交易池赋予的身份与抽取概率。 */
final class TradeAdapters {
    private static final List<TradeRuleAdapter> PROVIDERS=load();
    private static List<TradeRuleAdapter> load(){var result=new ArrayList<TradeRuleAdapter>();try{for(var provider:ServiceLoader.load(TradeRuleAdapter.class)){if(result.size()>=32)break;result.add(provider);}}catch(ServiceConfigurationError failure){org.slf4j.LoggerFactory.getLogger(TradeAdapters.class).warn("Trade adapter registration failed",failure);}result.sort(Comparator.comparing(p->p.getClass().getName()));return List.copyOf(result);}
    static boolean adapt(JsonObject row,Object factory,MinecraftServer server)throws Exception {
        for(var provider:PROVIDERS){var described=provider.describe(factory,server);if(described.isEmpty())continue;var value=described.get();if(value.toString().length()>262144||!Set.of("DECLARATIVE","RANDOM_RULE").contains(value.has("status")?value.get("status").getAsString():"")||!value.has("inputs")||!value.get("inputs").isJsonArray()||!value.has("output")||!value.get("output").isJsonObject())throw new IllegalArgumentException("Invalid declarative trade adapter output: "+provider.getClass().getName());for(String key:Set.of("index","weight","selectionProbability","factory"))if(value.has(key))throw new IllegalArgumentException("Adapter cannot overwrite pool identity: "+key);for(var field:value.entrySet())row.add(field.getKey(),field.getValue().deepCopy());row.addProperty("adapter",provider.getClass().getName());return true;}return false;
    }
    private TradeAdapters(){}
}
