package yagen.waitmydawn.mkb_export.core;

import java.util.*;

/**
 * 独立于图标清单的知识协议；未实现类别不得用空集合伪装为完整快照。
 */
public record SnapshotDescriptor(String format, int schemaVersion, String captureMode, String coverage,
                                 String capturedAt, String clientFingerprint, String serverFingerprint,
                                 String serverSession, long serverGeneration, Map<String, Category> categories) {
    public static final List<String> REQUIRED = List.of("mod_metadata", "registries", "language", "item_icons", "recipes", "tags", "loot_tables", "loot_modifiers");

    public record Category(String status, int total, int succeeded, int transparent, int unsupported, int failed,
                           String file, String sha256, String reason) {
        public Category {
            if (!Set.of("COMPLETE", "COMPLETE_WITH_ERRORS", "NOT_IMPLEMENTED", "FAILED").contains(status)
                    || total < 0 || succeeded < 0 || transparent < 0 || unsupported < 0 || failed < 0
                    || (long) succeeded + transparent + unsupported + failed != total)
                throw new IllegalArgumentException("Invalid category accounting");
            if (status.equals("COMPLETE") && (failed != 0 || unsupported != 0))
                throw new IllegalArgumentException("Complete category has failures");
            if (Set.of("COMPLETE", "COMPLETE_WITH_ERRORS").contains(status)
                    && (file == null || sha256 == null || !sha256.matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("Completed category requires a hashed payload");
        }
    }

    public SnapshotDescriptor {
        if (!"mkb-snapshot".equals(format) || schemaVersion != 1 || !Set.of("SINGLEPLAYER", "CLIENT_ONLY").contains(captureMode)
                || !Set.of("ALL", "SELECTION").contains(coverage) || clientFingerprint == null || !clientFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid snapshot identity");
        if (captureMode.equals("SINGLEPLAYER") && (serverSession == null || serverGeneration < 0 || serverFingerprint == null || !serverFingerprint.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Missing server identity");
        categories = Collections.unmodifiableMap(new TreeMap<>(categories));
        if (!categories.keySet().containsAll(REQUIRED))
            throw new IllegalArgumentException("Missing required category declarations");
    }

    public SnapshotDescriptor withIcons(List<ExportManifest.Icon> icons, byte[] manifest) {
        int ok = 0, transparent = 0, unsupported = 0, failed = 0;
        for (var icon : icons)
            switch (icon.status()) {
                case "OK" -> ok++;
                case "TRANSPARENT" -> transparent++;
                case "UNSUPPORTED" -> unsupported++;
                default -> failed++;
            }
        var updated = new TreeMap<>(categories);
        updated.put("item_icons", new Category(failed + unsupported == 0 ? "COMPLETE" : "COMPLETE_WITH_ERRORS", icons.size(), ok, transparent, unsupported, failed,
                "manifest.json", ContentDigest.sha256(manifest), null));
        return new SnapshotDescriptor(format, schemaVersion, captureMode, coverage, capturedAt, clientFingerprint, serverFingerprint, serverSession, serverGeneration, updated);
    }

    public SnapshotDescriptor withPayloads(List<KnowledgePayload> payloads) {
        var updated = new TreeMap<>(categories);
        for (var payload : payloads) updated.put(payload.category(), payload.declaration());
        return new SnapshotDescriptor(format, schemaVersion, captureMode, coverage, capturedAt, clientFingerprint, serverFingerprint, serverSession, serverGeneration, updated);
    }

    public static SnapshotDescriptor preparation(String mode, String coverage, String client, String server, String session, long generation) {
        var categories = new TreeMap<String, Category>();
        for (String key : REQUIRED)
            categories.put(key, new Category("NOT_IMPLEMENTED", 0, 0, 0, 0, 0, null, null, "Exporter capability is not implemented"));
        for (String key : List.of("item_attributes", "entity_attributes", "entity_state_attributes", "trades", "brewing", "workstation_rules", "loot_entrypoints", "potions", "effects", "enchantments", "spawn_rules", "structures", "structure_sets", "template_pools", "structure_processors", "structure_templates", "multiblocks"))
            categories.put(key, new Category("NOT_IMPLEMENTED", 0, 0, 0, 0, 0, null, null, "Optional capability v1 not captured"));
        categories.put("entity_icons", new Category("NOT_IMPLEMENTED", 0, 0, 0, 0, 0, null, null, "Entity renderer is not implemented"));
        return new SnapshotDescriptor("mkb-snapshot", 1, mode, coverage, java.time.Instant.now().toString(), client, server, session, generation, categories);
    }

    /**
     * 时间戳不影响恢复；会话与重载代次约束防止混用不同存档及玩家上下文。
     */
    public boolean compatible(SnapshotDescriptor current) {
        return current != null && captureMode.equals(current.captureMode)
                && clientFingerprint.equals(current.clientFingerprint) && Objects.equals(serverFingerprint, current.serverFingerprint)
                && Objects.equals(serverSession, current.serverSession) && serverGeneration == current.serverGeneration;
    }
}
