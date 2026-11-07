package yagen.waitmydawn.mkb_export.client;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import yagen.waitmydawn.mkb_export.core.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** 服务端线程取得资源引用与代次，后台只对该代次的资源取指纹。 */
public final class WorldCapture {
    private static final ReloadEpoch EPOCH=new ReloadEpoch();
    private static final Map<MinecraftServer,String> SESSIONS=new WeakHashMap<>();
    public record Lease(MinecraftServer server,ResourceManager resources,String session,long generation,
                        SortedMap<String,ContentDigest.Input> entries) {
        public boolean valid(){return server.isRunning() && !server.isStopped() && EPOCH.valid(generation);}
        public String fingerprint(BooleanSupplier cancelled)throws IOException {
            if(!valid())throw new IOException("Server snapshot invalidated");
            String hash=ContentDigest.resources(entries,()->cancelled.getAsBoolean()||!valid());
            if(!valid())throw new IOException("Server snapshot invalidated");return hash;
        }
    }
    private WorldCapture() {}
    public static void reloadStarted(){EPOCH.begin();ExportController.instance().cancel("server data reloading");}
    public static void ready(){EPOCH.ready();}
    public static boolean readyForCapture(){return EPOCH.readyForCapture();}
    public static Lease acquire(MinecraftServer server) {
        if(!server.isSameThread())throw new IllegalStateException("Server snapshot requires the server thread");
        if(!server.isReady()||!server.isRunning()||server.isStopped())throw new IllegalStateException("Singleplayer server is not ready");
        long generation=EPOCH.acquire();var manager=server.getResourceManager();
        var resources=new TreeMap<String,ContentDigest.Input>();
        // Forge 的包实现拒绝空路径；标准目录及动态注册表根目录必须分别枚举。
        var roots=new TreeSet<>(List.of("recipes","recipe","tags","loot_tables","loot_table","loot_modifiers","advancements","advancement","predicates","predicate","item_modifiers","item_modifier","functions","function","worldgen","dimension","dimension_type","structures","structure","damage_type","enchantment","jukebox_song","trim_material","trim_pattern","data_maps","forge","neoforge"));
        server.registryAccess().registries().forEach(entry->{var id=entry.key().location();roots.add(id.getPath().split("/")[0]);if(!id.getNamespace().equals("minecraft"))roots.add(id.getNamespace());});
        for(String root:roots)manager.listResources(root,id->true).forEach((id,resource)->resources.put(id.toString(),resource::open));
        if(resources.size()>100_000)throw new IllegalStateException("Server resource enumeration exceeds budget");
        String session=SESSIONS.computeIfAbsent(server,ignored->UUID.randomUUID().toString());
        return new Lease(server,manager,session,generation,Collections.unmodifiableSortedMap(resources));
    }
}
