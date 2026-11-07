package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.item.*;
import yagen.waitmydawn.mkb_export.core.*;
import java.util.*;

/** 名称关联使用实际注册对象及翻译键；Tooltip 逐帧采集，避免一次阻塞全部渲染。 */
final class ClientKnowledgeCapture {
    private final Minecraft client=Minecraft.getInstance();
    private final JsonArray registries=new JsonArray();
    private final Map<String,JsonObject> entityRegistry=new HashMap<>();
    private final List<Item> items;
    private final List<KnowledgePayload> payloads=new ArrayList<>();
    private final net.minecraft.server.packs.resources.ResourceManager resources;
    private final Map<String,String> currentLanguage;
    private final String selectedLanguage;
    private int cursor,entityCursor;
    private java.util.concurrent.CompletableFuture<List<EntitySamples.Sample>> pending;
    private final java.util.ArrayDeque<EntitySamples.Sample> samples=new java.util.ArrayDeque<>();
    private boolean observedQueued;
    private int sampleTotal,sampleDone;

    private long entityImageBytes;
    private final Set<String> uniquePixels=new HashSet<>();
    private final EntityReversalConfig reversal;
    private final boolean spawnSamples=Platform.spawnSamples(),ageVariants=Platform.ageVariants();
    private final int entitySize;private final boolean includeNonLiving;private final JsonArray exclusions=new JsonArray();
    private final JsonArray entityIcons=new JsonArray();
    private final JsonArray entityAttributes=new JsonArray();private final Set<String> attributeStates=new HashSet<>();
    private final Set<String> renderedStates=new HashSet<>();
    private final List<net.minecraft.world.entity.EntityType<?>> entities=BuiltInRegistries.ENTITY_TYPE.stream().sorted(Comparator.comparing(type->BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())).toList();
    ClientKnowledgeCapture(int entitySize,boolean includeNonLiving) throws java.io.IOException {
        reversal=EntityReversalConfig.load(client.gameDirectory.toPath().resolve("config"));
        if(entitySize!=256&&entitySize!=512)throw new IllegalArgumentException("Entity size must be 256 or 512");
        this.entitySize=entitySize;this.includeNonLiving=includeNonLiving;
        items=BuiltInRegistries.ITEM.stream().sorted(Comparator.comparing(item->BuiltInRegistries.ITEM.getKey(item).toString())).toList();
        resources=client.getResourceManager();currentLanguage=new TreeMap<>(Language.getInstance().getLanguageData());selectedLanguage=client.options.languageCode;
        payloads.add(KnowledgePayload.of("mod_metadata",Platform.modMetadata()));
        BuiltInRegistries.BLOCK.forEach(block->registries.add(identity("block",BuiltInRegistries.BLOCK.getKey(block).toString(),block.getDescriptionId(),block.getName().getString())));
        BuiltInRegistries.ENTITY_TYPE.forEach(entity->{String id=BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString();var row=identity("entity_type",id,entity.getDescriptionId(),entity.getDescription().getString());row.addProperty("classificationStatus","UNAVAILABLE");entityRegistry.put(id,row);registries.add(row);});
    }
    KnowledgePayload languagePayload() {
        var languages=new JsonArray();
        for(String language:List.of("en_us","zh_cn")) {
            var loaded=ClientLanguage.loadFrom(resources,language.equals("zh_cn")?List.of("en_us","zh_cn"):List.of("en_us"),false);
            var values=new JsonObject();new TreeMap<>(loaded.getLanguageData()).forEach(values::addProperty);
            var row=new JsonObject();row.addProperty("id",language);row.add("translations",values);languages.add(row);
        }
        var current=new JsonObject();current.addProperty("id","current");current.addProperty("language",selectedLanguage);
        var values=new JsonObject();currentLanguage.forEach(values::addProperty);current.add("translations",values);languages.add(current);
        return KnowledgePayload.of("language",languages);
    }
    boolean frame() throws java.io.IOException {try(var scope=EntitySamplingScope.enter()){return frameDetached();}}
    private boolean frameDetached() throws java.io.IOException {
        long deadline=System.nanoTime()+4_000_000;
        for(int count=0;cursor<items.size()&&count<32&&System.nanoTime()<deadline;count++,cursor++) {
            var item=items.get(cursor);String id=BuiltInRegistries.ITEM.getKey(item).toString();
            try {
                var stack=item.getDefaultInstance();var row=identity("item",id,stack.getDescriptionId(),stack.getHoverName().getString());
                row.addProperty("maxStackSize",stack.getMaxStackSize());
                var tooltip=new JsonArray();row.addProperty("tooltipStatus","OK");try{for(var line:Platform.tooltip(stack))tooltip.add(line.getString());}catch(Exception failure){row.addProperty("tooltipError",failure.getClass().getSimpleName());row.addProperty("tooltipStatus","FAILED");}
                row.add("tooltip",tooltip);row.addProperty("tooltipContext","default_stack_current_player_normal_flags");registries.add(row);
            }catch(Exception failure){var row=ServerKnowledgeCapture.failure(id,failure);row.addProperty("registry","item");registries.add(row);}
        }
        if(cursor<items.size())return false;
        if(!samples.isEmpty()){renderSample(samples.removeFirst());sampleDone++;return false;}
        if(pending!=null){if(!pending.isDone())return false;var ready=pending.join();samples.addAll(ready);sampleTotal+=ready.size();pending=null;return false;}
        if(entityCursor<entities.size()) {
            var type=entities.get(entityCursor++);String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            try {
                if(client.level==null)throw new IllegalStateException("Entity capture requires a world");
                var entity=type.create(client.level);if(entity==null){exclude(id,"REQUIRES_CREATION_CONTEXT");return false;}
                if(EntityFactories.matches(type,entity)){var registryRow=entityRegistry.get(id);EntityClassification.describe(registryRow,entity);registryRow.addProperty("classificationStatus","AVAILABLE");}if(id.equals("irons_spellbooks:debug_wizard")){exclude(id,"DEBUG_ENTITY_REQUIRES_SPELL");return false;}if(client.getEntityRenderDispatcher().getRenderer(entity) instanceof net.minecraft.client.renderer.entity.NoopRenderer){exclude(id,"NO_ENTITY_GEOMETRY_EFFECT_OR_LOGIC");return false;}if(!EntityFactories.matches(type,entity))throw new IllegalArgumentException("Entity factory returned a different registry type");
                if(!(entity instanceof net.minecraft.world.entity.LivingEntity)&&!includeNonLiving){var excluded=new JsonObject();excluded.addProperty("id",id);excluded.addProperty("reason","EXCLUDED_NON_LIVING");exclusions.add(excluded);return false;}
                var server=client.getSingleplayerServer();if(server!=null&&client.player!=null){var playerId=client.player.getUUID();pending=server.submit(()->EntitySamples.prepareAll(type,server,playerId,spawnSamples&&entity instanceof net.minecraft.world.entity.Mob,ageVariants)).orTimeout(30,java.util.concurrent.TimeUnit.SECONDS);}
                else {var report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("rootVisibleExpected",true);report.addProperty("status","UNVERIFIED");report.addProperty("reason","Client-only sample");report.addProperty("adapter","mkb_export:client_only");report.addProperty("adapterVersion","1");report.add("extra",new JsonObject());report.add("parts",new JsonArray());renderEntity(entity,id,EntityAppearance.defaults(),false,new EntityComposition.Scene(List.of(),report));}
            }catch(Exception failure){appendEntity(ServerKnowledgeCapture.failure(id,failure));}
            return false;
        }
        if(!observedQueued){observedQueued=true;var server=client.getSingleplayerServer();if(server!=null){pending=server.submit(()->EntitySamples.observed(server));return false;}}
        try{EntityVisualDedup.apply(entityIcons);}catch(java.io.IOException invalid){throw new IllegalStateException("Entity image deduplication failed",invalid);}var paths=EntityFileLayout.assign(entityIcons);for(var element:entityIcons){var row=element.getAsJsonObject();if(!row.has("error")){String key=row.get("id").getAsString()+"|"+row.get("stateKey").getAsString();row.addProperty("file",paths.get(key));}}
        var envelope=new JsonObject();envelope.addProperty("blockedEntitySpawns",EntitySamplingScope.blockedCount());envelope.addProperty("worldSpawnHooks",java.util.stream.StreamSupport.stream(entityIcons.spliterator(),false).anyMatch(row->{var state=row.getAsJsonObject().getAsJsonObject("state");return state!=null&&state.getAsJsonObject("traits").has("worldSpawnHooks")&&state.getAsJsonObject("traits").get("worldSpawnHooks").getAsBoolean();}));envelope.addProperty("imageReferencesVersion",1);envelope.add("exclusions",exclusions);var adapterWarnings=new JsonArray();AppearanceAdapters.warnings().forEach(adapterWarnings::add);envelope.add("adapterWarnings",adapterWarnings);payloads.add(KnowledgePayload.externalEntityImages(entityIcons,envelope));payloads.add(KnowledgePayload.of("registries",registries));if(client.getSingleplayerServer()!=null)payloads.add(KnowledgePayload.of("entity_state_attributes",entityAttributes));return true;
    }
    private void exclude(String id,String reason){var row=new JsonObject();row.addProperty("id",id);row.addProperty("reason",reason);exclusions.add(row);}
    private void appendEntity(JsonObject row){String identity=row.get("id").getAsString()+"|"+(row.has("stateKey")?row.get("stateKey").getAsString():"default");if(renderedStates.add(identity))entityIcons.add(row);}
    static KnowledgePayload renderProbeSamples(List<EntitySamples.Sample> samples)throws java.io.IOException {
        var capture=new ClientKnowledgeCapture(256,false);
        for(var sample:samples)capture.renderSample(sample);
        var paths=EntityFileLayout.assign(capture.entityIcons);
        for(var row:capture.entityIcons){var record=row.getAsJsonObject();if(!record.has("error"))record.addProperty("file",paths.get(record.get("id").getAsString()+"|"+record.get("stateKey").getAsString()));}
        return KnowledgePayload.externalEntityImages(capture.entityIcons,new JsonObject());
    }
    private void renderSample(EntitySamples.Sample sample) {
        if(sample.attributes()!=null){var row=sample.attributes().deepCopy();row.addProperty("id",sample.id());row.add("state",sample.state().deepCopy());row.addProperty("stateKey",EntityAppearance.key(sample.state()));row.addProperty("context","SERVER_ENTITY_SAMPLE");if(attributeStates.add(sample.id()+"|"+EntityAppearance.key(sample.state())))entityAttributes.add(row);}

        for(int i=exclusions.size()-1;i>=0;i--)if(exclusions.get(i).getAsJsonObject().get("id").getAsString().equals(sample.id()))exclusions.remove(i);
        try {
            if(sample.error()!=null)throw new IllegalStateException(sample.error());
            var type=BuiltInRegistries.ENTITY_TYPE.get(Platform.location(sample.id().split(":",2)[0],sample.id().split(":",2)[1]));
            var entity=type.create(client.level);if(entity==null)throw new IllegalArgumentException("Cannot clone entity");
            entity.load(sample.data().copy());entity.getEntityData().assignValues(sample.synced());entity.setPos(0,0,0);
            renderEntity(entity,sample.id(),sample.state(),sample.representative(),EntityComposition.restore(entity,sample.composition()));
        }catch(Exception failure){var row=ServerKnowledgeCapture.failure(sample.id(),new IllegalStateException("Reconstruct/render: "+EntitySamples.describe(failure)));row.addProperty("stage",sample.error()!=null?"SAMPLE_PREPARATION":"RECONSTRUCT_OR_RENDER");row.add("state",sample.state());row.addProperty("stateKey",EntityAppearance.key(sample.state()));appendEntity(row);}
    }
    private void renderEntity(net.minecraft.world.entity.Entity entity,String requestedId,JsonObject state,boolean representative,EntityComposition.Scene scene)throws Exception {
        String id=requestedId;String key=EntityAppearance.key(state);if(renderedStates.contains(id+"|"+key))return;
        EntityRenderCompatibility.reset();var rendered=EntityIconRenderer.render(entity,entitySize,reversal.reversed(id),scene.independent());byte[] bytes=rendered.png();
        if(uniquePixels.add(id+"|"+EntityVisualDedup.pixelHash(bytes))){if(entityImageBytes+bytes.length>32L*1024*1024)throw new IllegalStateException("Entity image budget exceeded");entityImageBytes+=bytes.length;}
        boolean visible=rendered.bounds()!=null;
        var row=new JsonObject();row.addProperty("visibilityAssessment",visible?"VISIBLE":entity.isInvisible()?"ENTITY_INVISIBLE":AppearanceAdapters.find(id)!=null||id.startsWith("minecraft:")?"UNEXPECTED_EMPTY":"EMPTY_UNVERIFIED");row.addProperty("id",id);row.addProperty("status",visible?"OK":"TRANSPARENT");row.addProperty("appearance",state.get("origin").getAsString());row.add("state",state);row.addProperty("stateKey",key);row.addProperty("representative",representative);row.addProperty("reconstruction",key.equals("default")?"DEFAULT_FACTORY":"NBT_AND_SYNCED_DATA");
        row.add("composition",scene.report());var verification=new JsonObject();verification.addProperty("total",scene.independent().size()+1);var missing=new JsonArray();var independentKeys=new ArrayList<String>();var expectedKeys=new HashSet<String>();for(var node:scene.report().getAsJsonArray("parts"))if(node.getAsJsonObject().get("policy").getAsString().equals("INDEPENDENT")){String nodeKey=node.getAsJsonObject().get("key").getAsString();independentKeys.add(nodeKey);if(node.getAsJsonObject().get("visibleExpected").getAsBoolean())expectedKeys.add(nodeKey);}for(int index:rendered.emptyParts())if(index>=0){if(expectedKeys.contains(independentKeys.get(index)))missing.add(independentKeys.get(index));}else if(scene.report().get("rootVisibleExpected").getAsBoolean())missing.add("root");verification.addProperty("status",expectedKeys.isEmpty()&&!scene.report().get("rootVisibleExpected").getAsBoolean()?"NOT_REQUIRED":missing.size()==0?"COMPLETE":"PARTIAL");verification.add("noVisiblePixels",missing);row.add("compositionRender",verification);row.addProperty("file",EntityAppearance.path(id,state));row.addProperty("width",entitySize);row.addProperty("height",entitySize);row.addProperty("yawCorrection",reversal.reversed(id)?180:0);row.addProperty("framing",rendered.framing());row.addProperty("framingAttempts",rendered.attempts());
        if(rendered.bounds()!=null){var bounds=rendered.bounds();var box=new JsonArray();box.add(bounds.left());box.add(bounds.top());box.add(bounds.right());box.add(bounds.bottom());row.add("framingBounds",box);row.addProperty("framingOccupancy",EntityFraming.occupancy(bounds,entitySize));row.addProperty("framingOffsetX",(bounds.left()+bounds.right())*.5/entitySize-.5);row.addProperty("framingOffsetY",(bounds.top()+bounds.bottom())*.5/entitySize-.5);}
        row.addProperty("renderBackend",EntityRenderCompatibility.base()?"STATIC_BASE_MODEL":id.equals("create:package")?"CREATE_CPU_STANDARD":"NATIVE");row.addProperty("renderer",client.getEntityRenderDispatcher().getRenderer(entity).getClass().getName());row.addProperty("actualRegistryType",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());row.addProperty("entityClass",entity.getClass().getName());row.addProperty("physicalWidth",entity.getBbWidth());row.addProperty("physicalHeight",entity.getBbHeight());row.add("variantCoverage",EntityVariants.coverage(id));row.addProperty("classification",entity instanceof net.minecraft.world.entity.LivingEntity?"LIVING":"NON_LIVING");EntityClassification.describe(row,entity);row.addProperty("pngBase64",Base64.getEncoder().encodeToString(bytes));row.addProperty("sha256",ContentDigest.sha256(bytes));appendEntity(row);
    }
    List<KnowledgePayload> payloads(){return List.copyOf(payloads);}
    int completed(){return cursor+entityCursor+sampleDone;}
    int total(){return items.size()+entities.size()+sampleTotal;}
    String selectedLanguage(){return selectedLanguage;}
    private static JsonObject identity(String registry,String id,String key,String name) {
        var row=new JsonObject();row.addProperty("registry",registry);row.addProperty("id",id);row.addProperty("descriptionId",key);row.addProperty("displayName",name);return row;
    }
}
