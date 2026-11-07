package yagen.waitmydawn.mkb_export.client;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import yagen.waitmydawn.mkb_export.api.structure.MultiblockProvider;
import java.util.*;
/** 无通用多方块注册表，已适配声明不能代表全部模组机制。 */
final class MultiblockKnowledge {
    static JsonArray capture(MinecraftServer server){var rows=new JsonArray();var seen=new HashSet<String>();VerifiedDragonForge.append(rows);rows.forEach(row->seen.add(row.getAsJsonObject().get("id").getAsString()));int providers=0;try{for(var provider:ServiceLoader.load(MultiblockProvider.class)){if(++providers>32)throw new IllegalArgumentException("Multiblock provider budget exceeded");try{var declared=provider.describe(server);if(declared.size()>64)throw new IllegalArgumentException("Provider record budget exceeded");for(var row:declared){if(row.toString().length()>262144)throw new IllegalArgumentException("Multiblock record too large");yagen.waitmydawn.mkb_export.core.LayoutContract.validate("multiblocks",row);String id=row.get("id").getAsString();if(!seen.add(id))throw new IllegalArgumentException("Duplicate multiblock identity");var copy=row.deepCopy();copy.addProperty("provider",provider.getClass().getName());rows.add(copy);}}catch(Exception e){rows.add(ServerKnowledgeCapture.failure("mkb_export:multiblock/provider/"+providers,e));}}}catch(ServiceConfigurationError|RuntimeException e){rows.add(ServerKnowledgeCapture.failure("mkb_export:multiblock/providers",new IllegalStateException(e)));}return rows;}
}
