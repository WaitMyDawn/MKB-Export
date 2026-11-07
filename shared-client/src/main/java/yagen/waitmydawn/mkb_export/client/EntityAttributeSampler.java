package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 分批执行加载器生成初始化并等待真实服务端 tick；完成、失败或取消均清理本批实体。 */
public final class EntityAttributeSampler {
    private static boolean tickGuardInstalled;
    public static void tickGuardInstalled(){tickGuardInstalled=true;}
    private static final Map<MinecraftServer,Run> RUNS=new WeakHashMap<>();
    private record Sample(LivingEntity entity,String id,int initialTicks,JsonObject stages) {}
    private static final class Run {
        final MinecraftServer server;
        final ServerLevel level;
        final CompletableFuture<JsonArray> completion=new CompletableFuture<>();
        final ArrayDeque<String> pending=new ArrayDeque<>();
        final List<Sample> active=new ArrayList<>();
        final Set<Long> forced=new HashSet<>();
        final JsonArray results=new JsonArray();
        final boolean diagnostics;
        final java.util.function.BooleanSupplier cancelled;
        final int batchSize=Math.max(1,Math.min(8,Integer.getInteger("mkb_export.attributeBatchSize",4)));
        final boolean disableAi;
        int created,cleaned,deadline;
        final Set<java.util.UUID> owned=new HashSet<>();
        Run(MinecraftServer server,List<String> ids,boolean diagnostics,java.util.function.BooleanSupplier cancelled) {
            this.server=server;level=server.overworld();this.diagnostics=diagnostics;this.cancelled=cancelled;
            disableAi=!diagnostics||!Boolean.getBoolean("mkb_export.attributeProbeAi");pending.addAll(ids);
        }
        void nextBatch() throws Exception {
            if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException("Attribute sampling cancelled");
            deadline=server.getTickCount()+200;
            var player=server.getPlayerList().getPlayers().get(0);
            for(int slot=0;slot<batchSize&&!pending.isEmpty();slot++) {
                String id=pending.removeFirst();
                try {
                var key=Platform.location(id.split(":")[0],id.split(":")[1]);
                if(!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                    var missing=new JsonObject();missing.addProperty("id",id);missing.addProperty("error","Entity type not installed");results.add(missing);continue;
                }
                var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(key);
                LivingEntity mob;try(var scope=yagen.waitmydawn.mkb_export.core.EntitySamplingScope.enter()){mob=(LivingEntity)type.create(level);}
                if(mob==null)throw new IllegalStateException("Factory returned null: "+id);
                if(!EntityFactories.matches(type,mob))throw new IllegalStateException("Factory returned another entity type: "+id);
                if(!(mob instanceof Mob)){mob.discard();results.add(ServerKnowledgeCapture.failure(id,new IllegalStateException("Non-Mob living entity requires an observed sample or dedicated initialization adapter")));continue;}
                var stages=new JsonObject();
                var sample=new Sample(mob,id,mob.tickCount,stages);active.add(sample);owned.add(mob.getUUID());created++;
                int x=player.blockPosition().getX()+128+slot*64,z=player.blockPosition().getZ()+128;
                var chunk=new ChunkPos(x>>4,z>>4);long packed=chunk.toLong();
                if(!level.getForcedChunks().contains(packed)){level.setChunkForced(chunk.x,chunk.z,true);forced.add(packed);}
                mob.setPos(x+.5,level.getMaxBuildHeight()-32,z+.5);
                if(diagnostics)record(sample,"CREATED");
                try(var scope=yagen.waitmydawn.mkb_export.core.EntitySamplingScope.enter()){
                    IceAndFireVariants.normalizeFactory(mob);
                    Platform.initializeAttributeSample((Mob)mob,level);
                }
                if(diagnostics)record(sample,"INITIALIZED");
                if(mob instanceof Mob spawned){spawned.setNoAi(disableAi);spawned.setPersistenceRequired();}mob.setNoGravity(true);mob.setInvulnerable(true);mob.setSilent(true);
                try(var scope=yagen.waitmydawn.mkb_export.core.EntitySamplingScope.enterPermittingEntity(mob)){if(!level.addFreshEntity(mob))throw new IllegalStateException("Entity join rejected: "+id);}
                if(diagnostics)record(sample,"JOINED");
                }catch(Exception failure) {
                    if(diagnostics)throw failure;
                    for(var sample:List.copyOf(active))if(sample.id().equals(id)){sample.entity().discard();active.remove(sample);cleaned++;}
                    results.add(ServerKnowledgeCapture.failure(id,failure));
                }
            }
        }
        void record(Sample sample,String stage) throws Exception {
            var row=Platform.entityAttributes(sample.entity());
            row.addProperty("elapsedEntityTicks",sample.entity().tickCount-sample.initialTicks());
            row.addProperty("serverTick",server.getTickCount());
            if(diagnostics&&stage.equals("TICK_5")&&sample.id().equals("irons_spellbooks:fire_boss")) {
                var checks=new JsonObject();
                for(String id:List.of("irons_spellbooks:spell_power","irons_spellbooks:spell_resist","irons_spellbooks:fire_magic_resist")) {
                    int value=Platform.attributeCommandValue(server,sample.entity(),id);
                    checks.addProperty(id,value);
                }
                row.add("attributeCommandValuesTimes1000",checks);
                row.addProperty("attributeNbtAfterCommands",sample.entity().getAttributes().save().toString());
            }
            sample.stages().add(stage,row);
            if(diagnostics)com.mojang.logging.LogUtils.getLogger().info("MKB_ATTRIBUTE_STAGE {} {} entityTicks={} attributeCount={}",sample.id(),stage,sample.entity().tickCount-sample.initialTicks(),row.getAsJsonArray("attributes").size());
        }
        void advance() throws Exception {
            if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException("Attribute sampling cancelled");
            for(var sample:List.copyOf(active))try {
                if(sample.entity().isRemoved())throw new IllegalStateException("Sample removed before capture: "+sample.id());
                int ticks=sample.entity().tickCount-sample.initialTicks();
                if(ticks>=1&&!sample.stages().has("TICK_1")) {
                    if(ticks!=1)throw new IllegalStateException("Missed exact first entity tick: "+sample.id()+" ticks="+ticks);
                    record(sample,"TICK_1");
                }
                if(diagnostics&&ticks>=5&&!sample.stages().has("TICK_5")) {
                    if(ticks!=5)throw new IllegalStateException("Missed exact fifth entity tick: "+sample.id()+" ticks="+ticks);
                    record(sample,"TICK_5");
                }
            }catch(Exception failure) {
                if(diagnostics)throw failure;
                sample.entity().discard();active.remove(sample);cleaned++;
                results.add(ServerKnowledgeCapture.failure(sample.id(),failure));
            }
            if(!active.isEmpty()&&active.stream().allMatch(sample->sample.stages().has(diagnostics?"TICK_5":"TICK_1"))) {
                for(var sample:active){
                    if(diagnostics){var row=new JsonObject();row.addProperty("id",sample.id());row.add("stages",sample.stages());results.add(row);}
                    else {
                        var row=sample.stages().getAsJsonObject("TICK_1").deepCopy();
                        row.addProperty("factoryEntityType",row.get("id").getAsString());row.addProperty("id",sample.id());row.addProperty("context","SERVER_INITIALIZED_TICK_SAMPLE");
                        row.addProperty("sampleOrigin","SPAWN_INITIALIZED");row.addProperty("sampleSelection","SPACED_BATCH_ONE_TICK");
                        row.addProperty("defaultsOnly",false);row.addProperty("worldTickAdvanced",true);row.addProperty("sampleTicks",1);
                        row.addProperty("initialization","LOADER_FINALIZE_SPAWN");row.addProperty("spawnReason","COMMAND");
                        row.addProperty("samplingAiDisabled",disableAi);row.addProperty("samplingGravityDisabled",true);
                        row.addProperty("samplingInvulnerable",true);row.addProperty("dimension",level.dimension().location().toString());
                        results.add(row);
                    }
                }
                cleanup();
            }
            if(active.isEmpty()) {
                if(pending.isEmpty()){finish(null);return;}
                nextBatch();
            }
            if(server.getTickCount()>deadline) {
                if(diagnostics)throw new IllegalStateException("Samples did not receive required real entity ticks within 200 server ticks");
                for(var sample:active)results.add(ServerKnowledgeCapture.failure(sample.id(),new IllegalStateException("Entity tick timeout")));
                cleanup();
            }
        }
        void cleanup() {
            RuntimeException failure=null;
            for(var sample:active)try {
                sample.entity().discard();
                if(!sample.entity().isRemoved())throw new IllegalStateException("Sample was not removed: "+sample.id());
                cleaned++;
            }catch(RuntimeException problem){if(failure==null)failure=problem;else failure.addSuppressed(problem);}
            active.clear();
            for(long value:forced)try{var chunk=new ChunkPos(value);level.setChunkForced(chunk.x,chunk.z,false);}
            catch(RuntimeException problem){if(failure==null)failure=problem;else failure.addSuppressed(problem);}
            forced.clear();
            if(failure!=null)throw failure;
        }
        void finish(Throwable failure) {
            try{cleanup();}catch(Exception cleanupFailure){if(failure==null)failure=cleanupFailure;else failure.addSuppressed(cleanupFailure);}
            try {
                var report=new JsonObject();report.addProperty("batchSize",batchSize);report.addProperty("samplingMode","ISOLATED_WORLD_REAL_SERVER_TICKS");
                report.addProperty("aiDisabledAfterInitialization",disableAi);
                report.addProperty("createdSamples",created);report.addProperty("cleanedSamples",cleaned);
                report.addProperty("remainingOwnedEntities",owned.stream().filter(uuid->{var entity=level.getEntity(uuid);return entity!=null&&!entity.isRemoved();}).count());report.addProperty("spacingBlocks",64);report.addProperty("gravityDisabled",true);
                report.add("entities",results);if(failure!=null)report.addProperty("error",failure.toString());
                if(diagnostics)java.nio.file.Files.writeString(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("mkb-attribute-stages.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
            }catch(Exception writeFailure){if(failure==null)failure=writeFailure;else failure.addSuppressed(writeFailure);}
            long remaining=owned.stream().filter(uuid->{var entity=level.getEntity(uuid);return entity!=null&&!entity.isRemoved();}).count();
            com.mojang.logging.LogUtils.getLogger().info("MKB_ATTRIBUTE_BATCH_END diagnostics={} created={} cleaned={} remaining={} aborted={} recordErrors={}",diagnostics,created,cleaned,remaining,failure!=null,results.asList().stream().filter(value->value.getAsJsonObject().has("error")).count());
            if(remaining!=0&&failure==null)failure=new IllegalStateException("Sampling entities remain after cleanup");
            RUNS.remove(server);
            if(failure==null)completion.complete(results);else completion.completeExceptionally(failure);
        }
    }
    static CompletableFuture<JsonArray> start(MinecraftServer server,List<String> ids,java.util.function.BooleanSupplier cancelled) {
        return begin(server,ids,false,cancelled);
    }
    static CompletableFuture<JsonArray> study(MinecraftServer server) {
        if(!Boolean.getBoolean("mkb_export.attributeProbe"))throw new IllegalStateException("Attribute probe requires explicit smoke mode");
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        return begin(server,List.of("irons_spellbooks:fire_boss","irons_spellbooks:dead_king",
                "irons_spellbooks:pyromancer","irons_spellbooks:cryomancer","irons_spellbooks:priest",
                "irons_spellbooks:archevoker","minecraft:zombie","minecraft:pig","minecraft:iron_golem"),true,()->false);
    }
    private static CompletableFuture<JsonArray> begin(MinecraftServer server,List<String> ids,boolean diagnostics,java.util.function.BooleanSupplier cancelled) {
        if(!server.isSameThread())throw new IllegalStateException("Attribute sampling requires server thread");
        if(!tickGuardInstalled)throw new IllegalStateException("Attribute tick guard is not installed; refusing to join sampling entities");
        if(RUNS.containsKey(server))throw new IllegalStateException("Attribute sampler already running");
        var run=new Run(server,ids,diagnostics,cancelled);RUNS.put(server,run);
        try{run.nextBatch();}catch(Exception failure){run.finish(failure);}
        return run.completion;
    }
    public static boolean owns(net.minecraft.world.entity.Entity entity) {
        if(!(entity.level() instanceof ServerLevel level))return false;
        var run=RUNS.get(level.getServer());return run!=null&&run.active.stream().anyMatch(sample->sample.entity()==entity);
    }
    public static void entityFailed(net.minecraft.world.entity.Entity entity,Exception failure) {
        var run=RUNS.get(((ServerLevel)entity.level()).getServer());if(run==null)return;
        if(run.diagnostics){run.finish(failure);return;}
        for(var sample:List.copyOf(run.active))if(sample.entity()==entity){
            run.results.add(ServerKnowledgeCapture.failure(sample.id(),failure));entity.discard();run.active.remove(sample);run.cleaned++;
        }
    }
    public static void tick(MinecraftServer server) {
        var run=RUNS.get(server);if(run==null)return;
        try{run.advance();}catch(Exception failure){run.finish(failure);}
    }
    public static void stop(MinecraftServer server) {
        var run=RUNS.get(server);if(run!=null)run.finish(new IllegalStateException("Server stopped during attribute probe"));
    }
    private EntityAttributeSampler() {}
}
