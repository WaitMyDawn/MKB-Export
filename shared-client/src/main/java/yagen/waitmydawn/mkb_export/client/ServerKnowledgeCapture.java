package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import yagen.waitmydawn.mkb_export.core.*;
import java.util.*;

/** 在单次服务端任务内序列化生效对象，不以数据包原文件替代脚本修改后的配方。 */
final class ServerKnowledgeCapture {
    record Result(WorldCapture.Lease lease,List<KnowledgePayload> payloads) {
        String objectFingerprint(){return ContentDigest.sha256(payloads.stream().filter(payload->!payload.category().endsWith("_attributes")).map(payload->payload.category()+":"+payload.hash()).sorted().collect(java.util.stream.Collectors.joining("\n")).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
    static Result capture(MinecraftServer server) {return capture(server,true);}
    static java.util.concurrent.CompletableFuture<Result> captureInitialized(MinecraftServer server,java.util.function.BooleanSupplier cancelled) {
        var initial=capture(server);
        var ids=new ArrayList<String>();
        for(var payload:initial.payloads())if(payload.category().equals("entity_attributes")) {
            var root=JsonParser.parseString(new String(payload.bytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var value:root.getAsJsonArray("records")){var row=value.getAsJsonObject();if(!row.has("error")&&row.has("defaultsOnly")&&row.get("defaultsOnly").getAsBoolean())ids.add(row.get("id").getAsString());}
        }
        if(ids.isEmpty())return java.util.concurrent.CompletableFuture.completedFuture(initial);
        return EntityAttributeSampler.start(server,ids,cancelled).thenApply(sampled->{
            if(!initial.lease().valid()||cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException("Attribute snapshot invalidated");
            var byId=new HashMap<String,JsonObject>();for(var value:sampled){var row=value.getAsJsonObject();byId.put(row.get("id").getAsString(),row);}
            var payloads=new ArrayList<KnowledgePayload>();
            for(var payload:initial.payloads()) {
                if(!payload.category().equals("entity_attributes")){payloads.add(payload);continue;}
                var original=JsonParser.parseString(new String(payload.bytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("records");
                var rows=new JsonArray();for(var value:original){
                    var row=value.getAsJsonObject();var replacement=byId.getOrDefault(row.get("id").getAsString(),row);
                    if(replacement!=row&&replacement.has("error"))replacement.add("factorySnapshot",row.deepCopy());
                    rows.add(replacement);
                }
                payloads.add(KnowledgePayload.of("entity_attributes",rows));
            }
            return new Result(initial.lease(),List.copyOf(payloads));
        });
    }

    static Result verify(MinecraftServer server) {return capture(server,false);}
    private static Result capture(MinecraftServer server,boolean attributes) {
        var lease=WorldCapture.acquire(server);
        var recipes=Platform.recipes(server);var lootEntrypoints=Platform.lootEntrypoints();TemplateLoot.append(server,lootEntrypoints);var itemAttributes=new JsonArray();var entityAttributes=new JsonArray();
        if(attributes)try(var sampling=EntitySamplingScope.enter()) {
            var actualAttributes=EntityAttributeSelection.capture(server);
            for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM){String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();try{var stack=item.getDefaultInstance();if(stack.getItem()!=item)throw new IllegalArgumentException("Default stack belongs to another item");itemAttributes.add(Platform.itemAttributes(stack));}catch(Exception e){itemAttributes.add(failure(id,e));}}
            for(var type:net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE){String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();try{if(actualAttributes.containsKey(id)){entityAttributes.add(actualAttributes.get(id));continue;}var entity=type.create(server.overworld());if(entity==null)throw new IllegalArgumentException("No default entity instance");if(!EntityFactories.matches(type,entity))throw new IllegalArgumentException("Factory returned another registry type");if(entity instanceof net.minecraft.world.entity.LivingEntity living){var row=Platform.entityAttributes(living);row.addProperty("factoryEntityType",row.get("id").getAsString());row.addProperty("id",id);row.addProperty("context","SERVER_DEFAULT_INSTANCE");row.addProperty("sampleOrigin","DEFAULT_INSTANCE");row.addProperty("defaultsOnly",true);row.addProperty("sampleSelection","NO_LOADED_OR_EXPLICIT_SAMPLE");entityAttributes.add(row);}}catch(Exception e){entityAttributes.add(failure(id,e));}}
        }

        var tags=new JsonArray();
        server.registryAccess().registries().sorted(Comparator.comparing(e->e.key().location().toString())).forEach(e->tags(e.value(),tags));
        if(!lease.valid())throw new IllegalStateException("Server changed during knowledge capture");
        return new Result(lease,List.of(KnowledgePayload.of("item_attributes",itemAttributes),KnowledgePayload.of("entity_attributes",entityAttributes),KnowledgePayload.of("loot_entrypoints",lootEntrypoints),optional("structures",()->StructureKnowledge.registry(server,"structures")),optional("structure_sets",()->StructureKnowledge.registry(server,"structure_sets")),optional("template_pools",()->StructureKnowledge.registry(server,"template_pools")),optional("structure_processors",()->StructureKnowledge.registry(server,"structure_processors")),optional("structure_templates",()->StructureKnowledge.templates(server)),optional("multiblocks",()->MultiblockKnowledge.capture(server)),optional("spawn_rules",()->SpawnKnowledge.capture(server)),optional("potions",()->MagicKnowledge.potions(server)),optional("effects",()->MagicKnowledge.effects(server)),optional("enchantments",()->Platform.enchantments(server)),optional("trades",()->TradeRules.capture(server)),optional("brewing",()->BrewingRules.capture(server)),optional("workstation_rules",()->WorkstationRules.capture(recipes)),KnowledgePayload.of("recipes",recipes),KnowledgePayload.of("tags",tags),KnowledgePayload.of("loot_tables",Platform.lootTables(server)),KnowledgePayload.of("loot_modifiers",Platform.lootModifiers(server))));
    }
    private static KnowledgePayload optional(String category,java.util.function.Supplier<JsonArray> capture){try{return KnowledgePayload.of(category,capture.get());}catch(Exception failure){var rows=new JsonArray();rows.add(failure("mkb_export:capture/"+category,failure));return KnowledgePayload.of(category,rows);}}
    private static <T> void tags(Registry<T> registry,JsonArray records) {
        registry.getTags().sorted(Comparator.comparing(pair->pair.getFirst().location().toString())).forEach(pair->{
            var row=new JsonObject();row.addProperty("registry",registry.key().location().toString());row.addProperty("id",pair.getFirst().location().toString());
            var members=new JsonArray();pair.getSecond().stream().map(holder->registry.getKey(holder.value()).toString()).sorted().forEach(members::add);
            row.add("members",members);records.add(row);
        });
    }
    static JsonObject failure(String id,Exception failure) {
        var row=new JsonObject();row.addProperty("id",id);String error=failure.getClass().getSimpleName()+": "+Objects.toString(failure.getMessage(),"capture failed");
        row.addProperty("error",error.substring(0,Math.min(2000,error.length())));return row;
    }
}
