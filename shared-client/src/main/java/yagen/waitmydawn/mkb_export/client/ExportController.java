package yagen.waitmydawn.mkb_export.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import org.slf4j.Logger;
import yagen.waitmydawn.mkb_export.core.ExportJob;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** 控制器只在客户端渲染线程推进；后台线程独占 PNG 编码和暂存目录写入。 */
public final class ExportController {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> SMOKE_ITEMS = List.of("minecraft:stone", "minecraft:grass_block", "minecraft:oak_log",
            "minecraft:diamond_sword", "minecraft:chest", "minecraft:shield", "minecraft:water_bucket", "minecraft:glass");
    private static final ExportController INSTANCE = new ExportController();
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "mkb-export-io"); thread.setDaemon(true); return thread;
    });
    private record Write(ExportJob.Target target,int attempt,CompletableFuture<String> future) {}
    private final ArrayDeque<Write> writes = new ArrayDeque<>();
    private final ArrayDeque<ExportJob.Target> retries = new ArrayDeque<>();
    private final Map<String,Integer> attempts = new HashMap<>();
    private CompletableFuture<ExportJob> preparation;
    private CompletableFuture<Path> publication;
    private CompletableFuture<Void> cleanup;
    private ExportJob session;
    private ItemIconRenderer renderer;
    private List<ExportJob.Target> items = List.of();
    private Object startingLevel;
    private net.minecraft.server.MinecraftServer startingServer;
    private volatile WorldCapture.Lease serverLease;
    private ClientKnowledgeCapture clientCapture;
    private CompletableFuture<List<yagen.waitmydawn.mkb_export.core.KnowledgePayload>> clientReady;
    private volatile String serverObjectFingerprint;
    private String entitySummary="";
    private int cursor, completed, transparent, failed, size,total,reused,retryCount;
    private volatile String cancellation;
    private String status = "idle";
    private boolean smokeStarted, smokeFinished;
    private boolean taskFailed;
    private int smokeWaitFrames;
    private int smokeIdleFrames;
    private int smokePhase;
    private Path smokePublished;
    private String checkpointName;
    private boolean smokeWorldCreated;
    private long smokeWorldDeadline;
    private CompletableFuture<Void> smokeKnowledgeSetup;

    private ExportController() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            io.shutdownNow();
            try {
                if (io.awaitTermination(5, TimeUnit.SECONDS)) {
                    var current = session;
                    if (current == null && preparation != null && preparation.isDone() && !preparation.isCompletedExceptionally()) current = preparation.getNow(null);
                    if (current != null) current.close();
                }
            }
            catch (Exception e) { LOGGER.warn("Unable to release interrupted export checkpoint", e); }
        }, "mkb-export-shutdown"));
    }
    public static ExportController instance() { return INSTANCE; }

    public <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("mkb_export")
                .executes(context -> help())
                .then(LiteralArgumentBuilder.<S>literal("capture_entity").executes(context->captureEntity(false,"")).then(RequiredArgumentBuilder.<S,Boolean>argument("representative",BoolArgumentType.bool()).executes(context->captureEntity(BoolArgumentType.getBool(context,"representative"),"")).then(RequiredArgumentBuilder.<S,String>argument("label",StringArgumentType.greedyString()).executes(context->captureEntity(BoolArgumentType.getBool(context,"representative"),StringArgumentType.getString(context,"label"))))))
                .then(LiteralArgumentBuilder.<S>literal("capture_clear").executes(context->{if(active())return 0;var server=Minecraft.getInstance().getSingleplayerServer();if(server==null)return 0;server.execute(()->EntitySamples.clear(server));tell("已清空当前世界会话的实体捕获队列。");return 1;}))
                .then(LiteralArgumentBuilder.<S>literal("item_size").then(RequiredArgumentBuilder.<S,Integer>argument("pixels",IntegerArgumentType.integer(16,512)).executes(context->{if(active())return 0;int pixels=IntegerArgumentType.getInteger(context,"pixels");return saveSetting("物品分辨率："+pixels,()->Platform.itemSize(pixels));})))
                .then(LiteralArgumentBuilder.<S>literal("entity_size").then(RequiredArgumentBuilder.<S,Integer>argument("pixels",IntegerArgumentType.integer(256,512)).executes(context->{if(active())return 0;int pixels=IntegerArgumentType.getInteger(context,"pixels");if(pixels!=256&&pixels!=512){tell("实体分辨率仅支持 256 或 512");return 0;}return saveSetting("实体分辨率："+pixels,()->Platform.entitySize(pixels));})))
                .then(LiteralArgumentBuilder.<S>literal("entity_nonliving").then(RequiredArgumentBuilder.<S,Boolean>argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(context->{if(active())return 0;boolean enabled=com.mojang.brigadier.arguments.BoolArgumentType.getBool(context,"enabled");return saveSetting("包含非生物实体："+enabled,()->Platform.includeNonLiving(enabled));})))
                .then(LiteralArgumentBuilder.<S>literal("all").executes(context -> start("*", Platform.itemSize()))
                        .then(RequiredArgumentBuilder.<S, Integer>argument("size", IntegerArgumentType.integer(16, 512))
                                .executes(context -> start("*", IntegerArgumentType.getInteger(context, "size")))))
                .then(LiteralArgumentBuilder.<S>literal("mod")
                        .then(RequiredArgumentBuilder.<S, String>argument("namespace", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    BuiltInRegistries.ITEM.keySet().stream().map(id -> id.getNamespace()).distinct().sorted()
                                            .filter(id -> id.startsWith(builder.getRemaining())).forEach(builder::suggest);
                                    return builder.buildFuture();
                                }).executes(context -> start(StringArgumentType.getString(context, "namespace") + ":*", Platform.itemSize()))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("size", IntegerArgumentType.integer(16, 512))
                                        .executes(context -> start(StringArgumentType.getString(context, "namespace") + ":*", IntegerArgumentType.getInteger(context, "size"))))))
                .then(LiteralArgumentBuilder.<S>literal("item")
                        .then(RequiredArgumentBuilder.<S, net.minecraft.resources.ResourceLocation>argument("id", ResourceLocationArgument.id())
                                .suggests((context, builder) -> {
                                    BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).sorted()
                                            .filter(id -> id.startsWith(builder.getRemaining())).forEach(builder::suggest);
                                    return builder.buildFuture();
                                }).executes(context -> start(context.getArgument("id", net.minecraft.resources.ResourceLocation.class).toString(), Platform.itemSize()))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("size", IntegerArgumentType.integer(16, 512))
                                        .executes(context -> start(context.getArgument("id", net.minecraft.resources.ResourceLocation.class).toString(), IntegerArgumentType.getInteger(context, "size"))))))
                .then(LiteralArgumentBuilder.<S>literal("held").executes(context -> held()))
                .then(LiteralArgumentBuilder.<S>literal("checkpoints").executes(context -> checkpoints()))
                .then(LiteralArgumentBuilder.<S>literal("dyed").then(RequiredArgumentBuilder.<S,net.minecraft.resources.ResourceLocation>argument("id",ResourceLocationArgument.id())
                        .then(RequiredArgumentBuilder.<S,Integer>argument("rgb",IntegerArgumentType.integer(0,0xffffff))
                                .executes(context->dyed(context.getArgument("id",net.minecraft.resources.ResourceLocation.class).toString(),IntegerArgumentType.getInteger(context,"rgb"))))))
                .then(LiteralArgumentBuilder.<S>literal("update").then(RequiredArgumentBuilder.<S,String>argument("directory",StringArgumentType.word())
                        .executes(context->start("*",Platform.itemSize(),StringArgumentType.getString(context,"directory"),null))
                        .then(RequiredArgumentBuilder.<S,Integer>argument("size",IntegerArgumentType.integer(16,512))
                                .executes(context->start("*",IntegerArgumentType.getInteger(context,"size"),StringArgumentType.getString(context,"directory"),null)))))
                .then(LiteralArgumentBuilder.<S>literal("resume").then(RequiredArgumentBuilder.<S,String>argument("directory",StringArgumentType.word())
                        .executes(context->start("*",Platform.itemSize(),null,StringArgumentType.getString(context,"directory")))))
                .then(LiteralArgumentBuilder.<S>literal("status").executes(context -> { tell(progress()); return 1; }))
                .then(LiteralArgumentBuilder.<S>literal("cancel").executes(context -> { cancel("user cancelled"); return 1; })));
    }
    private int captureEntity(boolean representative,String label) {
        if(active()){tell("请等待导出完成后再捕获。");return 0;}
        var client=Minecraft.getInstance();var server=client.getSingleplayerServer();
        if(server==null||client.player==null||!(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)){tell("请在单人世界准星指向实体后执行；不支持玩家实体。");return 0;}
        var targetId=Platform.captureTarget(hit.getEntity()).getUUID();var playerId=client.player.getUUID();
        AppearanceAdapters.freeze();final com.google.gson.JsonObject clientExtra;try{clientExtra=AppearanceAdapters.clientState(Platform.captureTarget(hit.getEntity()));}catch(Exception failure){tell("客户端外观读取失败："+describe(failure));return 0;}
        server.submit(()->EntitySamples.capture(server,playerId,targetId,representative,label,clientExtra)).whenComplete((count,failure)->client.execute(()->{if(failure!=null)tell("实体捕获失败："+describe(failure));else tell("已保存实体外观到当前世界会话队列（"+count+" 项）；执行 /mkb_export all 写入完整快照，退出世界前请完成导出。");}));return 1;
    }
    private int saveSetting(String label,Runnable save) {
        try{save.run();tell(label+"；已保存到 config/mkb_export-common.toml");return 1;}
        catch(Exception failure){LOGGER.error("Cannot save export config",failure);tell("配置保存失败："+describe(failure));return 0;}
    }
    private int help() {
        tell("/mkb_export all [size] | mod <namespace> [size] | item <namespace:id> [size] | held | dyed <id> <rgb> | update <directory> [size] | resume <checkpoint> | capture_entity [representative] [label] | capture_clear | item_size <16..512> | entity_size <256|512> | entity_nonliving <true|false> | status | cancel"); return 1;
    }
    private int start(String selector, int requestedSize) {
        return start(selector,requestedSize,null,null);
    }
    private int start(String selector,int requestedSize,String baseline,String checkpoint) {
        if (active()) { tell("已有导出任务：" + progress()); return 0; }
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null) { tell("资源仍在加载，请稍后重试。"); return 0; }
        var selected = new ArrayList<Item>();
        BuiltInRegistries.ITEM.forEach(item -> {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (item != Items.AIR && (selector.equals("*") || id.equals(selector)
                    || selector.endsWith(":*") && id.startsWith(selector.substring(0, selector.length() - 1))
                    || (selector.equals("@smoke")||selector.equals("@states")) && SMOKE_ITEMS.contains(id))) selected.add(item);
        });
        selected.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        if (selected.isEmpty() || selected.size() > 50_000) { tell("没有匹配物品，或物品数量超过协议上限。"); return 0; }
        var plan=selected.stream().map(item->new ExportJob.Target(BuiltInRegistries.ITEM.getKey(item).toString(),null)).toList();
        if(selector.equals("@states")) {
            var variants=new ArrayList<ExportJob.Target>();variants.add(new ExportJob.Target("minecraft:leather_helmet",null));
            for(int rgb:new int[]{0xff0000,0x0000ff}){var stack=Platform.dyed("minecraft:leather_helmet",rgb);variants.add(new ExportJob.Target("minecraft:leather_helmet",Platform.state(stack)));}
            plan=variants;
        }
        return startPlan(plan,requestedSize,baseline,checkpoint,selector.equals("*"));
    }
    private int held() {
        var player=Minecraft.getInstance().player;if(player==null||player.getMainHandItem().isEmpty()){tell("请先在主手持有要导出的物品。");return 0;}
        var stack=player.getMainHandItem().copy();stack.setCount(1);
        return startPlan(List.of(new ExportJob.Target(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),Platform.state(stack))),Platform.itemSize(),null,null);
    }
    private int checkpoints() {
        try(var paths=java.nio.file.Files.list(Minecraft.getInstance().gameDirectory.toPath())) {
            paths.filter(p->!java.nio.file.Files.isSymbolicLink(p)&&p.getFileName().toString().matches("\\.mkb-export-[a-zA-Z0-9_-]+")
                    &&java.nio.file.Files.isRegularFile(p.resolve("checkpoint.json"),java.nio.file.LinkOption.NOFOLLOW_LINKS)).limit(30).forEach(p->tell("可恢复检查点："+p.getFileName()));return 1;
        }catch(Exception failure){tell("读取检查点失败："+describe(failure));return 0;}
    }
    private int dyed(String id,int rgb) {
        try {var stack=Platform.dyed(id,rgb);return startPlan(List.of(new ExportJob.Target(id,Platform.state(stack))),Platform.itemSize(),null,null);}
        catch(Exception invalid){tell("染色物品无效："+describe(invalid));return 0;}
    }
    private int startPlan(List<ExportJob.Target> plan,int requestedSize,String baseline,String checkpoint) {
        return startPlan(plan,requestedSize,baseline,checkpoint,false);
    }
    private int startPlan(List<ExportJob.Target> plan,int requestedSize,String baseline,String checkpoint,boolean all) {
        if(active()){tell("已有导出任务："+progress());return 0;}
        var minecraft=Minecraft.getInstance();if(minecraft.getOverlay()!=null){tell("资源仍在加载，请稍后重试。");return 0;}
        startingServer=minecraft.getSingleplayerServer();
        if(!Boolean.getBoolean("mkb_export.smoke") && (minecraft.level==null||minecraft.player==null||startingServer==null||!startingServer.isReady()||!startingServer.isRunning())) {
            tell("请先进入单人世界，等待世界及数据加载完成后再导出。");return 0;
        }
        entitySummary="";cursor = completed = transparent = failed = reused = retryCount = 0; total=plan.size();size = requestedSize;retries.clear();attempts.clear();
        cancellation = null; publication = null; taskFailed = false; writes.clear(); startingLevel = minecraft.level;
        serverLease=null;
        try{AppearanceAdapters.freeze();clientCapture=new ClientKnowledgeCapture(Platform.entitySize(),Platform.includeNonLiving());clientReady=new CompletableFuture<>();}catch(Exception failure){tell("无法准备客户端知识快照："+describe(failure));return 0;}
        status = "preparing environment";
        var packs = minecraft.getResourceManager().listPacks().toList();
        var mods = Platform.mods(); String version = Platform.exporterVersion();
        final var capturedClient=clientCapture;
        CompletableFuture<ServerKnowledgeCapture.Result> leaseFuture=startingServer==null?CompletableFuture.completedFuture(null):startingServer.submit(()->ServerKnowledgeCapture.captureInitialized(startingServer,()->cancellation!=null)).thenCompose(future->future);
        preparation = leaseFuture.thenCombineAsync(clientReady,(serverData,clientPayloads) -> {
            try {
                var lease=serverData==null?null:serverData.lease();
                serverLease=lease;
                var payloads=new ArrayList<yagen.waitmydawn.mkb_export.core.KnowledgePayload>(clientPayloads);payloads.add(capturedClient.languagePayload());
                if(serverData!=null)payloads.addAll(serverData.payloads());
                var capturedEnvironment=EnvironmentCapture.capture(Platform.loader(),Platform.loaderVersion(),mods,packs,minecraft.gameDirectory.toPath(),()->cancellation!=null);
                var environment=new yagen.waitmydawn.mkb_export.core.ExportManifest.Environment(capturedEnvironment.minecraftVersion(),capturedEnvironment.loader(),capturedEnvironment.loaderVersion(),capturedEnvironment.mods(),capturedEnvironment.resourcePacks(),capturedClient.selectedLanguage());
                String clientHash=yagen.waitmydawn.mkb_export.core.ContentDigest.sha256(yagen.waitmydawn.mkb_export.core.IconState.canonical(new com.google.gson.Gson().toJsonTree(environment)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                serverObjectFingerprint=serverData==null?null:serverData.objectFingerprint();
                String serverHash=lease==null?null:yagen.waitmydawn.mkb_export.core.ContentDigest.sha256((lease.fingerprint(()->cancellation!=null)+"\n"+serverObjectFingerprint).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var snapshot=yagen.waitmydawn.mkb_export.core.SnapshotDescriptor.preparation(lease==null?"CLIENT_ONLY":"SINGLEPLAYER",all?"ALL":"SELECTION",clientHash,serverHash,lease==null?null:lease.session(),lease==null?0:lease.generation()).withPayloads(payloads);
                if(cancellation!=null)throw new java.io.InterruptedIOException("Export cancelled during preparation");
                var effectivePlan=all?yagen.waitmydawn.mkb_export.core.RuleIconTargets.extend(plan,payloads):plan;total=effectivePlan.size();
                var job=checkpoint!=null?ExportJob.resume(minecraft.gameDirectory.toPath(),checkpoint,environment,version,snapshot):new ExportJob(minecraft.gameDirectory.toPath(),environment,requestedSize,version,effectivePlan,snapshot);
                try{job.writePayloads(payloads);if(baseline!=null)job.reuse(baseline);return job;}catch(Exception failure){job.close();throw failure;}
            }
            catch (Exception e) { throw new CompletionException(e); }
        }, io);
        tell("准备导出注册信息、语言、Tooltip、最终配方、标签、掉落规则及默认实体外观。");
        return 1;
    }
    public void cancel(String reason) { cancellation = reason;var ready=clientReady;if(ready!=null&&!ready.isDone())ready.completeExceptionally(new CancellationException(reason)); }
    public void frame() {
        var minecraft = Minecraft.getInstance();
        boolean worldSmoke=Boolean.getBoolean("mkb_export.smoke") && System.getProperty("mkb_export.smoke.selector","").equals("@world");
        if(worldSmoke && smokeWorldCreated && !smokeFinished && !active()) {
            if(System.nanoTime()>smokeWorldDeadline){finish("failed","Singleplayer lifecycle smoke timed out");return;}
            if(minecraft.player!=null && minecraft.level!=null && minecraft.getOverlay()==null && minecraft.getSingleplayerServer()!=null
                    && minecraft.getSingleplayerServer().isReady() && WorldCapture.readyForCapture() && ++smokeWaitFrames>=30) {
                if(smokePhase==3) {
                    if(Boolean.getBoolean("mkb_export.equipmentProbe")){try{if(EquipmentProbe.frame()){smokeFinished=true;minecraft.stop();}}catch(Exception failure){finish("failed",describe(failure));}return;}
                    if(smokeKnowledgeSetup==null&&Boolean.getBoolean("mkb_export.attributeProbe")){smokeKnowledgeSetup=minecraft.getSingleplayerServer().submit(()->AttributeProbe.start(minecraft.getSingleplayerServer())).thenCompose(future->future);return;}
                    if(smokeKnowledgeSetup==null){var clientAppearances=KnowledgeSmokeFixture.clientAppearances();smokeKnowledgeSetup=minecraft.getSingleplayerServer().submit(()->KnowledgeSmokeFixture.install(minecraft.getSingleplayerServer(),clientAppearances));return;}
                    if(!smokeKnowledgeSetup.isDone())return;
                    try{smokeKnowledgeSetup.join();}catch(Exception failure){finish("failed",describe(failure));return;}
                    if(Boolean.getBoolean("mkb_export.attributeProbe")){smokeFinished=true;minecraft.stop();return;}
                }
                smokeWaitFrames=0;
                start(smokePhase==3?"*":"@smoke",64);
                smokePhase=smokePhase==3?4:1;
            }
            return;
        }
        if(Boolean.getBoolean("mkb_export.smoke")&&!smokeStarted&&minecraft.getOverlay()==null&&minecraft.screen!=null) {
            if(minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))minecraft.setScreen(new TitleScreen());
            if(++smokeIdleFrames>1800){smokeStarted=true;finish("failed","Smoke did not reach the title screen: "+minecraft.screen.getClass().getSimpleName());return;}
        }
        if (Boolean.getBoolean("mkb_export.smoke") && !smokeStarted && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            // 等待资源加载后的首批正常帧，避免把加载屏幕状态用于图标渲染。
            if (++smokeWaitFrames >= 10) {
                smokeStarted = true;
                LOGGER.info("MKB_EXPORT_SMOKE_START pid={} selector={}", ProcessHandle.current().pid(), System.getProperty("mkb_export.smoke.selector", "@smoke"));
                if(System.getProperty("mkb_export.smoke.selector","").startsWith("@config")){configSmoke();return;}
                if(worldSmoke) {
                    if(Boolean.getBoolean("mkb_export.smoke.singlePass"))smokePhase=3;
                    smokeWorldCreated=true;smokeWorldDeadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(15);smokeWaitFrames=0;
                    Platform.createSmokeWorld();return;
                }
                var commands = new CommandDispatcher<Object>(); register(commands);
                for (String input : List.of("mkb_export item minecraft:stone 64", "mkb_export mod minecraft 64", "mkb_export all 64", "mkb_export held", "mkb_export dyed minecraft:leather_helmet 16711680", "mkb_export update MKB-Export 64", "mkb_export resume .mkb-export-example", "mkb_export checkpoints", "mkb_export status", "mkb_export cancel")) {
                    var parsed = commands.parse(input, new Object());
                    if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) throw new IllegalStateException("Client command parse failed: " + input);
                }
                start(System.getProperty("mkb_export.smoke.selector", "@smoke"), 64);
            }
        }
        if (!active()) return;
        if (cleanup != null) {
            if (cleanup.isDone()) {
                try { cleanup.join(); } catch (Exception e) { LOGGER.warn("Export staging cleanup failed", e); taskFailed = true; }
                cleanup = null;
                finish(taskFailed ? "failed" : "cancelled", "导出已停止，未发布不完整清单。");
            }
            return;
        }
        if (minecraft.level != startingLevel) cancellation = "world changed";
        if (minecraft.getSingleplayerServer()!=startingServer || serverLease!=null&&!serverLease.valid())cancellation="server data changed";
        if (minecraft.getOverlay() != null) cancellation = "resources reloading";
        try {
            if(clientCapture!=null && clientReady!=null && !clientReady.isDone()) {
                if(cancellation!=null)clientReady.completeExceptionally(new CancellationException(cancellation));
                else if(clientCapture.frame()){var payloads=clientCapture.payloads();for(var payload:payloads)if(payload.category().equals("entity_icons")){var root=com.google.gson.JsonParser.parseString(new String(payload.bytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();entitySummary="；实体：可见 "+payload.succeeded()+"，透明 "+payload.transparent()+"，失败 "+payload.failed()+"，跳过 "+root.getAsJsonArray("exclusions").size();}tell(entitySummary);clientReady.complete(payloads);clientCapture=null;}
                else status="client knowledge "+clientCapture.completed()+"/"+clientCapture.total();
            }
            if (preparation != null && preparation.isDone()) {
                if (cancellation != null && preparation.isCompletedExceptionally()) {
                    preparation = null; finishCancelled(); return;
                }
                session = preparation.join(); preparation = null;
                if (cancellation == null) {
                    size=session.size();total=session.total();items=session.pending();reused=total-items.size();transparent=session.transparentCount();completed=reused-transparent;renderer = new ItemIconRenderer(size); status = "rendering";
                    tell("检查点："+session.workDirectory()+"；复用 "+reused+"，待渲染 "+items.size()+"。");
                    checkpointName=session.workDirectory().getFileName().toString();
                }
            }
            if(worldSmoke && smokePhase==1 && session!=null && cancellation==null) {
                smokePhase=2;
                startingServer.execute(()->startingServer.reloadResources(startingServer.getPackRepository().getSelectedIds()));
                return;
            }
            if(worldSmoke && smokePhase==2 && cancellation==null)return;
            while (!writes.isEmpty() && writes.peekFirst().future().isDone()) {
                var write=writes.removeFirst();String result=write.future().join();if(result.equals("OK"))completed++;else if(result.equals("TRANSPARENT"))transparent++;
                else if(write.attempt()<3){retries.addLast(write.target());retryCount++;}
                else failed++;
            }
            if(smokeStarted&&System.getProperty("mkb_export.smoke.selector","").equals("@states")&&smokePhase==2&&completed>0&&publication==null)cancel("smoke checkpoint pause");
            // 发布仅包含已完成图标；进入重命名阶段后不能再撤销已经发布的目录。
            if (cancellation != null) {
                if (publication == null) {
                    if (preparation == null && writes.isEmpty()) finishCancelled();
                    return;
                }
            }
            if (publication != null && publication.isDone()) {
                Path result = publication.join(); publication = null; session = null;smokePublished=result;
                finish("completed", "导出完成：" + result + "；物品成功 " + completed + "，完全透明 " + transparent + "，失败 " + failed + entitySummary+"。实体诊断见目录内 entity-report.txt；在桌面软件文件入口选择此目录。");
                return;
            }
            if (session == null || renderer == null || publication != null || writes.size() >= 2) return;
            if (cursor == items.size()&&retries.isEmpty()) {
                if (writes.isEmpty()) {
                    renderer.close(); renderer = null; status = "publishing";
                    CompletableFuture<Void> consistent=startingServer==null?CompletableFuture.completedFuture(null):startingServer.submit(()->{
                        var current=ServerKnowledgeCapture.verify(startingServer);
                        if(!current.objectFingerprint().equals(serverObjectFingerprint))throw new IllegalStateException("Runtime recipes or tags changed during export");
                    });
                    publication = consistent.thenApplyAsync(ignored -> {
                        try { Path result = session.publish(()->cancellation!=null||serverLease!=null&&!serverLease.valid()); session.close(); return result; }
                        catch (Exception e) { throw new CompletionException(e); }
                    }, io);
                }
                return;
            }
            var target=retries.isEmpty()?items.get(cursor++):retries.removeFirst();String id=target.itemId();int attempt=attempts.merge(target.key(),1,Integer::sum);
            NativeImage image;
            try {
                if(smokeStarted&&System.getProperty("mkb_export.smoke.selector","").equals("@states")&&smokePhase==4&&attempt==1&&target.key().equals(items.get(0).key()))throw new java.io.IOException("Smoke injected transient render failure");
                image = renderer.render(Platform.stack(target));
            }
            catch (Exception e) {
                LOGGER.warn("Item render failed for {}", id, e);
                writes.add(new Write(target,attempt,CompletableFuture.supplyAsync(() -> {
                    try { session.failure(target,describe(e)); return "FAILED"; }
                    catch (Exception failure) { throw new CompletionException(failure); }
                }, io)));
                return;
            }
            writes.add(new Write(target,attempt,CompletableFuture.supplyAsync(() -> {
                try (image) { return session.write(target, image.asByteArray()); }
                catch (Exception e) {
                    try { session.failure(target,describe(e)); return "FAILED"; }
                    catch (Exception failure) { throw new CompletionException(failure); }
                }
            }, io)));
            if (cursor % 100 == 0) tell(progress());
        } catch (Exception failure) { abort(failure); }
    }
    private void finishCancelled() {
        if (renderer != null) { renderer.close(); renderer = null; }
        var current = session; session = null;
        cleanup = CompletableFuture.runAsync(() -> {
            try { if (current != null) current.close(); }
            catch (Exception e) { throw new CompletionException(e); }
        }, io);
        cancellation = null;
    }
    private void abort(Exception failure) {
        LOGGER.error("Export failed", failure);
        taskFailed = true;
        if (renderer != null) { renderer.close(); renderer = null; }
        cancellation = "error: " + describe(failure);
        if (preparation != null && preparation.isCompletedExceptionally()) preparation = null;
        writes.removeIf(write->write.future().isCompletedExceptionally());
        if (publication != null && publication.isCompletedExceptionally()) publication = null;
        tell("导出失败，正在保留检查点：" + describe(failure));
        if (!active()) finish("failed", "导出失败，未发布清单。");
    }
    private void configSmoke() {
        try {
            Path config=Minecraft.getInstance().gameDirectory.toPath().resolve("config");
            if(!java.nio.file.Files.isRegularFile(config.resolve("mkb_export-common.toml"))||!java.nio.file.Files.isRegularFile(config.resolve("mkb_export_reversal.json")))throw new IllegalStateException("Startup config files missing");
            boolean restore=System.getProperty("mkb_export.smoke.selector").equals("@config_restore");
            if(Platform.itemSize()!=(restore?128:64)||Platform.entitySize()!=(restore?512:256)||Platform.includeNonLiving()!=restore)throw new IllegalStateException("Unexpected loaded config values");
            if(!restore) {
                var commands=new CommandDispatcher<Object>();register(commands);
                for(String command:List.of("mkb_export item_size 128","mkb_export entity_size 512","mkb_export entity_nonliving true"))if(commands.execute(command,new Object())!=1)throw new IllegalStateException("Config command failed: "+command);
                if(Platform.itemSize()!=128||Platform.entitySize()!=512||!Platform.includeNonLiving())throw new IllegalStateException("Config command values missing");
            }
            LOGGER.info("MKB_EXPORT_CONFIG_VERIFIED restore={} itemSize={} entitySize={} includeNonLiving={}",restore,Platform.itemSize(),Platform.entitySize(),Platform.includeNonLiving());
            finish("completed","Configuration lifecycle verified");
        }catch(Exception failure){LOGGER.error("Configuration lifecycle failed",failure);finish("failed",describe(failure));}
    }
    private void finish(String state, String message) {
        status = state; tell(message);
        if (smokeStarted && !smokeFinished) {
            if(System.getProperty("mkb_export.smoke.selector","").equals("@world")) {
                LOGGER.info("MKB_EXPORT_WORLD_PHASE phase={} state={} success={} failed={}",smokePhase,state,completed,failed);
                if(smokePhase==2 && state.equals("cancelled")){smokePhase=3;return;}
                if(smokePhase!=4 || !state.equals("completed") || failed!=0 || serverLease==null)state="failed";
            }
            if(System.getProperty("mkb_export.smoke.selector","").equals("@states")) {
                LOGGER.info("MKB_EXPORT_R4_PHASE phase={} state={} success={} failed={} reused={} retries={} path={}",smokePhase,state,completed,failed,reused,retryCount,smokePublished);
                boolean expected=failed==0&&(smokePhase==2?state.equals("cancelled")&&completed>0:state.equals("completed")&&completed==3)
                        &&(smokePhase!=1||reused==3)&&(smokePhase!=3||reused>0)&&(smokePhase!=4||retryCount==1);
                if(!expected){taskFailed=true;state="failed";}
                else if(smokePhase==0){smokePhase=1;start("@states",64,smokePublished.getFileName().toString(),null);return;}
                else if(smokePhase==1){smokePhase=2;start("@states",64);return;}
                else if(smokePhase==2){smokePhase=3;start("*",64,null,checkpointName);return;}
                else if(smokePhase==3){smokePhase=4;start("@states",64);return;}
            }
            smokeFinished = true;
            LOGGER.info("MKB_EXPORT_SMOKE {} pid={} success={} failed={}", state, ProcessHandle.current().pid(), completed, failed);
            Minecraft.getInstance().stop();
        }
    }
    private boolean active() { return preparation != null || session != null || publication != null || cleanup != null; }
    private String progress() { return status + " | " + (completed + transparent + failed) + "/" + total + " | OK=" + completed + " TRANSPARENT="+transparent+" FAILED=" + failed+" REUSED="+reused+" RETRIES="+retryCount+entitySummary; }
    private void tell(String message) {
        LOGGER.info("[MKB Export] {}", message);
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal("[MKB Export] " + message), false);
    }
    private static String describe(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "render error");
    }
}
