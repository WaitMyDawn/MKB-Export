package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** 结构模板中容器的 LootTable NBT 是可确认入口，不从文件名推断世界生成结构。 */
final class TemplateLoot {
    static void append(MinecraftServer server,JsonArray rows){
        var resources=new TreeMap<net.minecraft.resources.ResourceLocation,net.minecraft.server.packs.resources.Resource>();
        for(String folder:List.of("structures","structure"))resources.putAll(server.getResourceManager().listResources(folder,id->id.getPath().endsWith(".nbt")));
        long total=0;
        for(var entry:resources.entrySet()){
            String id=entry.getKey().toString(),record="mkb_export:template/"+id.replace(':','/');
            try(var input=entry.getValue().open()){
                byte[] bytes=input.readNBytes(2*1024*1024+1);total+=bytes.length;if(bytes.length>2*1024*1024||total>128L*1024*1024)throw new IllegalArgumentException("Structure template capture byte budget exceeded");
                var tag=Platform.readTemplate(bytes);var blocks=tag.getList("blocks",10);
                for(int i=0;i<blocks.size();i++){var block=blocks.getCompound(i);var nbt=block.getCompound("nbt");if(!nbt.contains("LootTable",8))continue;var row=new JsonObject();row.addProperty("id",record+"/"+i);row.addProperty("source",id.substring(0,id.length()-4).replaceFirst(":structures?/",":"));row.addProperty("sourceKind","structure");row.addProperty("sourceSubtype","STRUCTURE_TEMPLATE_CONTAINER");row.addProperty("container",nbt.getString("id"));row.addProperty("position",block.getList("pos",3).toString());row.addProperty("lootTable",nbt.getString("LootTable"));row.addProperty("relation","TEMPLATE_BLOCK_NBT");row.addProperty("limitation","Template placement declaration only; not proof of natural generation, placement frequency, cached template mutation or processor/event outcomes");rows.add(row);}
            }catch(Exception failure){rows.add(ServerKnowledgeCapture.failure(record,failure));}if(total>128L*1024*1024)break;
        }
    }
    private TemplateLoot(){}
}
