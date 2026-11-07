package yagen.waitmydawn.mkb_export.client;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import java.util.*;

/** 数据驱动定义只建立明确引用，不从模板文件名推断生成位置或概率。 */
final class StructureKnowledge {
    static JsonArray registry(MinecraftServer server,String category){return switch(category){
        case "structures"->encode(server,server.registryAccess().registryOrThrow(Registries.STRUCTURE),Structure.DIRECT_CODEC);
        case "structure_sets"->encode(server,server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET),StructureSet.DIRECT_CODEC);
        case "template_pools"->encode(server,server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL),StructureTemplatePool.DIRECT_CODEC);
        case "structure_processors"->encode(server,server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST),StructureProcessorType.DIRECT_CODEC);
        default->throw new IllegalArgumentException("Unknown structure category");};}
    private static <T> JsonArray encode(MinecraftServer server,Registry<T> registry,Codec<T> codec){var rows=new JsonArray();var ops=RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,server.registryAccess());for(var value:registry){String id=registry.getKey(value).toString();try{var row=new JsonObject();row.addProperty("id",id);row.addProperty("runtimeLoaded",true);row.add("definition",codec.encodeStart(ops,value).result().orElseThrow(()->new IllegalStateException("Structure codec could not serialize current value")));row.addProperty("limitation","Registered definition, not a located structure or a guaranteed generation result; custom Java pieces and runtime events may add behavior");rows.add(row);}catch(Exception e){rows.add(ServerKnowledgeCapture.failure(id,e));}}return rows;}
    static JsonArray templates(MinecraftServer server){var rows=new JsonArray();var resources=new TreeMap<ResourceLocation,net.minecraft.server.packs.resources.Resource>();for(String folder:List.of("structures","structure"))resources.putAll(server.getResourceManager().listResources(folder,id->id.getPath().endsWith(".nbt")));long compressed=0,geometry=0;var payloads=new HashMap<ResourceLocation,byte[]>();var normalized=new TreeMap<String,ResourceLocation>();long buffered=0;
        for(var entry:resources.entrySet()){String source=entry.getKey().toString(),id=source.substring(0,source.length()-4).replaceFirst(":structures?/",":");var previous=normalized.get(id);boolean preferred=entry.getKey().getPath().startsWith(Platform.modernTrades()?"structure/":"structures/");if(previous==null||preferred)normalized.put(id,entry.getKey());}
        for(var key:normalized.values())try(var stream=resources.get(key).open()){byte[] bytes=stream.readNBytes(2097153);buffered+=bytes.length;if(bytes.length>2097152||buffered>134217728)throw new IllegalArgumentException("Template resource byte budget exceeded");payloads.put(key,bytes);}catch(Exception error){String source=key.toString();rows.add(ServerKnowledgeCapture.failure(source.substring(0,source.length()-4).replaceFirst(":structures?/",":"),error));if(buffered>134217728)break;}
        var ordered=new ArrayList<>(payloads.keySet());ordered.sort(Comparator.<ResourceLocation>comparingInt(key->payloads.get(key).length).thenComparing(ResourceLocation::toString));
        for(var key:ordered){var resource=resources.get(key);String source=key.toString(),id=source.substring(0,source.length()-4).replaceFirst(":structures?/",":");try{
            byte[] bytes=payloads.get(key);compressed+=bytes.length;if(bytes.length>2097152||compressed>134217728)throw new IllegalArgumentException("Template resource byte budget exceeded");var tag=Platform.readTemplate(bytes);var row=new JsonObject();row.addProperty("id",id);row.addProperty("resource",source);row.addProperty("sourcePack",resource.sourcePackId());row.addProperty("sha256",yagen.waitmydawn.mkb_export.core.ContentDigest.sha256(bytes));row.addProperty("runtimeLoaded",true);row.add("size",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,tag.getList("size",3)));var blocks=tag.getList("blocks",10);row.addProperty("blockCount",blocks.size());row.addProperty("entityCount",tag.getList("entities",10).size());row.addProperty("limitation","Resource template only; not a live placed structure or cached in-memory template mutation. Palette alternatives, processors, jigsaw rules and entity NBT may change placement. Material counts are template blocks, not obtainable loot.");
            if(blocks.size()>16384||geometry>12000000){row.addProperty("geometryStatus","METADATA_ONLY");row.addProperty("geometryReason",blocks.size()>16384?"Block count exceeds 16384":"Aggregate geometry budget exceeded");}
            else{var raw=new JsonObject();raw.add("palette",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,tag.getList("palette",10)));raw.add("palettes",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,tag.getList("palettes",9)));raw.add("blocks",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,blocks));raw.add("entities",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,tag.getList("entities",10)));int length=raw.toString().length();if(length>1000000||geometry+length>12000000){row.addProperty("geometryStatus","METADATA_ONLY");row.addProperty("geometryReason","Decoded geometry byte budget exceeded");}else{geometry+=length;row.addProperty("geometryStatus","COMPLETE");for(var field:raw.entrySet())row.add(field.getKey(),field.getValue());}}
            yagen.waitmydawn.mkb_export.core.LayoutContract.validate("structure_templates",row);rows.add(row);
        }catch(Exception e){rows.add(ServerKnowledgeCapture.failure(id,e));}if(compressed>134217728)break;}return rows;
    }
}
