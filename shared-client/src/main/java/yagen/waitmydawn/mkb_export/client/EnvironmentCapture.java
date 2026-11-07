package yagen.waitmydawn.mkb_export.client;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import yagen.waitmydawn.mkb_export.core.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static yagen.waitmydawn.mkb_export.core.ExportManifest.*;

/** 已加载资源快照只用于后台读取；资源重载时控制器取消整个任务。 */
public final class EnvironmentCapture {
    public record ModSource(String id, String version, Path file) {}
    private EnvironmentCapture() {}
    public static Environment capture(String loader, String loaderVersion, List<ModSource> sources,
                                      List<PackResources> resources, Path gameDirectory, BooleanSupplier cancelled) throws IOException {
        var mods = new ArrayList<Mod>();
        var fileHashes = new HashMap<Path, String>();
        for (var source : sources) {
            ContentDigest.interrupted();
            String hash = null;
            if (source.file() != null && Files.isRegularFile(source.file())) {
                hash = fileHashes.get(source.file());
                if (hash == null) { hash = ContentDigest.file(source.file(), cancelled); fileHashes.put(source.file(), hash); }
            }
            mods.add(new Mod(source.id(), source.version(), hash));
        }
        mods.sort(Comparator.comparing(Mod::id));
        var packs = new ArrayList<ResourcePack>();
        var roots = ResourceRoots.discover(sources, resources, gameDirectory);
        if (resources.size() > 256 || mods.size() > 4096) throw new IOException("Environment exceeds v1 protocol limits");
        int index = 0;
        for (var pack : resources) {
            ContentDigest.interrupted();
            var entries = new TreeMap<String, ContentDigest.Input>();
            var metadata = pack.getRootResource("pack.mcmeta");
            if (metadata != null) entries.put("pack.mcmeta", metadata::get);
            var namespaces = new TreeSet<>(pack.getNamespaces(PackType.CLIENT_RESOURCES));
            for (String namespace : namespaces) {
                for (String root : roots) {
                    ContentDigest.interrupted();
                    pack.listResources(PackType.CLIENT_RESOURCES, namespace, root, (id, input) -> entries.put(id.toString(), input::get));
                }
                if (entries.size() > 100_000) throw new IOException("Resource enumeration exceeds budget");
            }
            String id = "content-v1:" + index++ + ":" + pack.packId();
            if (id.length() > 512) throw new IOException("Resource pack ID exceeds v1 protocol limit");
            com.mojang.logging.LogUtils.getLogger().info("Fingerprinting resource pack {}: {} resources", pack.packId(), entries.size());
            packs.add(new ResourcePack(id, ContentDigest.resources(entries, cancelled)));
        }
        return new Environment(SharedConstants.getCurrentVersion().getName(), loader, loaderVersion, mods, packs);
    }
}
