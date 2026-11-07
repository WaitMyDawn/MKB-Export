package yagen.waitmydawn.mkb_export.client;

import net.minecraft.server.packs.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import yagen.waitmydawn.mkb_export.core.ResourceArchive;

/** 资源 API 不支持空目录前缀；从真实包及原版公开根路径发现一级资源目录。 */
final class ResourceRoots {
    private ResourceRoots() {}
    static Set<String> discover(List<EnvironmentCapture.ModSource> mods, List<PackResources> packs, Path gameDirectory) throws IOException {
        var roots = new TreeSet<>(List.of("textures", "models", "blockstates", "atlases", "font", "particles", "shaders",
                "texts", "lang", "sounds", "sounds.json", "optifine", "cit", "mcpatcher", "animations", "geo", "geckolib", "equipment"));
        var files = new HashSet<Path>();
        for (var mod : mods) if (mod.file() != null) files.add(mod.file());
        for (var pack : packs) {
            if (pack instanceof VanillaPackResources vanilla) {
                for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
                    var paths = new ArrayList<Path>();
                    vanilla.listRawPaths(PackType.CLIENT_RESOURCES, Platform.location(namespace, "textures"), paths::add);
                    for (Path path : paths) children(path.getParent(), roots);
                }
            }
            if (pack.packId().startsWith("file/")) {
                Path root = gameDirectory.resolve("resourcepacks").toAbsolutePath().normalize();
                Path file = root.resolve(pack.packId().substring(5)).normalize();
                if (!file.startsWith(root)) throw new IOException("Resource pack path escapes resourcepacks directory");
                files.add(file);
            }
        }
        for (Path path : files) {
            yagen.waitmydawn.mkb_export.core.ContentDigest.interrupted();
            if (Files.isRegularFile(path)) {
                roots.addAll(ResourceArchive.roots(path));
            } else if (Files.isDirectory(path.resolve("assets"))) {
                try (var namespaces = Files.list(path.resolve("assets"))) {
                    for (Path namespace : namespaces.toList()) children(namespace, roots);
                }
            }
        }
        roots.removeIf(name -> !name.matches("[a-z0-9_.-]+") || name.equals(".") || name.equals(".."));
        if (roots.size() > 4096) throw new IOException("Too many resource root directories");
        return Set.copyOf(roots);
    }
    private static void children(Path directory, Set<String> roots) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var children = Files.list(directory)) { children.forEach(path -> roots.add(path.getFileName().toString())); }
    }
}
