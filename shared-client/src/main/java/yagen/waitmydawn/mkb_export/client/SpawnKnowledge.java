package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.core.registries.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.biome.MobSpawnSettings;
import yagen.waitmydawn.mkb_export.core.AttributeOperations;
import java.util.*;

/** 读取生效配置而不运行随机生成、谓词或世界事件；池条目不能证明实际生成。 */
final class SpawnKnowledge {
    private static final int MAX_RELATIONS=100000;
    static JsonArray capture(MinecraftServer server){
        var rows=new TreeMap<String,JsonObject>();var failures=new JsonArray();int[] count={0};
        for(var type:BuiltInRegistries.ENTITY_TYPE){String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();var row=new JsonObject();row.addProperty("id",id);row.addProperty("mobCategory",type.getCategory().getName());row.add("biomeSpawns",new JsonArray());row.add("structureSpawns",new JsonArray());row.add("placement",placement(type));row.addProperty("coverage","ENUMERABLE_CONFIGURATION_ONLY");row.addProperty("limitation","No entry is not proof of no spawn. Dynamic events, custom spawners, summon rituals, entity construction, structure-piece logic and block/entity NBT spawners are not exhaustively enumerable. Weights are pool selection weights, not encounter probabilities. Registered predicates are not executed or decompiled.");rows.put(id,row);}
        var dimensions=new TreeMap<String,SortedSet<String>>();
        for(var level:server.getAllLevels())try{for(var biome:level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes())biome.unwrapKey().ifPresent(key->dimensions.computeIfAbsent(key.location().toString(),ignored->new TreeSet<>()).add(level.dimension().location().toString()));}catch(Exception e){failures.add(ServerKnowledgeCapture.failure("mkb_export:spawn/dimension/"+level.dimension().location().toString().replace(':','/'),e));}
        var biomes=server.registryAccess().registryOrThrow(Registries.BIOME);
        for(var biome:biomes){String id=biomes.getKey(biome).toString();try{var settings=biome.getMobSettings();for(var category:MobCategory.values()){
            var pool=settings.getMobs(category).unwrap();long total=0;for(var entry:pool)total+=entry.getWeight().asInt();
            int index=0;for(var entry:pool){var row=entry(entry,index++,total,category);row.addProperty("biome",id);row.addProperty("creatureGenerationProbability",AttributeOperations.finite(settings.getCreatureProbability()));var worlds=new JsonArray();for(String world:dimensions.getOrDefault(id,new TreeSet<>()))worlds.add(world);row.add("referencedByLoadedDimensions",worlds);var cost=settings.getMobSpawnCost(entry.type);if(cost!=null){var value=new JsonObject();value.addProperty("energyBudget",AttributeOperations.finite(cost.energyBudget()));value.addProperty("charge",AttributeOperations.finite(cost.charge()));row.add("spawnCost",value);}append(rows,entry.type,"biomeSpawns",row,count);}
        }}catch(Exception e){failures.add(ServerKnowledgeCapture.failure("mkb_export:spawn/biome/"+id.replace(':','/'),e));}}
        var structures=server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for(var structure:structures){String id=structures.getKey(structure).toString();try{for(var pair:structure.spawnOverrides().entrySet()){
            var pool=pair.getValue().spawns().unwrap();long total=0;for(var entry:pool)total+=entry.getWeight().asInt();int index=0;
            for(var entry:pool){var row=entry(entry,index++,total,pair.getKey());row.addProperty("structure",id);row.addProperty("boundingBox",pair.getValue().boundingBox().toString());var allowed=new TreeSet<String>();structure.biomes().stream().forEach(holder->holder.unwrapKey().ifPresent(key->allowed.add(key.location().toString())));var values=new JsonArray();allowed.forEach(values::add);row.add("allowedBiomes",values);row.addProperty("relation","STRUCTURE_POOL_OVERRIDE");append(rows,entry.type,"structureSpawns",row,count);}
        }}catch(Exception e){failures.add(ServerKnowledgeCapture.failure("mkb_export:spawn/structure/"+id.replace(':','/'),e));}}
        var result=new JsonArray();rows.values().forEach(result::add);failures.forEach(result::add);return result;
    }
    private static JsonObject entry(MobSpawnSettings.SpawnerData entry,int index,long total,MobCategory category){if(entry.getWeight().asInt()<0||entry.minCount<0||entry.maxCount<entry.minCount)throw new IllegalArgumentException("Invalid spawn pool entry");var row=new JsonObject();row.addProperty("entryIndex",index);row.addProperty("category",category.getName());row.addProperty("weight",entry.getWeight().asInt());row.addProperty("poolWeight",total);row.addProperty("minCount",entry.minCount);row.addProperty("maxCount",entry.maxCount);return row;}
    private static void append(Map<String,JsonObject> rows,EntityType<?> type,String collection,JsonObject entry,int[] count){if(++count[0]>MAX_RELATIONS)throw new IllegalStateException("Spawn relation budget exceeded");String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();var owner=rows.get(id);if(owner==null)throw new IllegalStateException("Unregistered spawn entity");owner.getAsJsonArray(collection).add(entry);}
    private static JsonObject placement(EntityType<?> type){var row=new JsonObject();try{var map=(Map<?,?>)RuleFields.get(SpawnPlacements.class,"DATA_BY_TYPE");Object data=map.get(type);row.addProperty("status",data==null?"NOT_REGISTERED":"REGISTERED");if(data!=null){row.addProperty("type",Platform.spawnPlacementName(type));row.addProperty("heightmap",SpawnPlacements.getHeightmapType(type).toString());String predicate=RuleFields.get(data,"predicate").getClass().getName();row.addProperty("predicate",yagen.waitmydawn.mkb_export.core.SpawnConfiguration.stableCodeName(predicate));row.addProperty("conditionCoverage","OPAQUE_CODE");}}catch(Exception e){row.addProperty("status","UNAVAILABLE");row.addProperty("reason",EntitySamples.describe(e));}return row;}
}
