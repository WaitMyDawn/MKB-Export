package yagen.waitmydawn.mkb_export.core;

import java.util.List;

/**
 * 与桌面 MKB-Export v1 保持字段兼容；状态变体需要独立升级协议。
 */
public record ExportManifest(String format, int schemaVersion, String origin, String exportedAt,
                             Exporter exporter, Environment environment, Rendering rendering, List<Icon> icons) {
    public record Exporter(String id, String version) {
    }

    public record Mod(String id, String version, String sha256) {
    }

    public record ResourcePack(String id, String sha256) {
    }

    public record Environment(String minecraftVersion, String loader, String loaderVersion,
                              List<Mod> mods, List<ResourcePack> resourcePacks, String language) {
        public Environment(String minecraftVersion, String loader, String loaderVersion, List<Mod> mods, List<ResourcePack> resourcePacks) {
            this(minecraftVersion, loader, loaderVersion, mods, resourcePacks, null);
        }

        public Environment {
            mods = List.copyOf(mods);
            resourcePacks = List.copyOf(resourcePacks);
        }
    }

    public record Rendering(String context, int width, int height, String background) {
    }

    public record Icon(String itemId, String variant, String status, String file, String sha256, String error,
                       com.google.gson.JsonObject state) {
        public Icon(String itemId, String variant, String status, String file, String sha256, String error) {
            this(itemId, variant, status, file, sha256, error, null);
        }
    }
}
