package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 每类为独立、有界的不可变载荷；枚举失败与语义适配缺失使用不同状态。
 */
public record KnowledgePayload(String category, byte[] bytes, int total, int succeeded, int failed,
                               Map<String, byte[]> images) {
    public KnowledgePayload {
        if (bytes.length > 64 * 1024 * 1024 || total < 0 || total > 250000 || succeeded < 0 || failed < 0 || succeeded + failed > total)
            throw new IllegalArgumentException("Payload exceeds budget or has invalid counters");
        bytes = bytes.clone();
        var copied = new LinkedHashMap<String, byte[]>();
        long size = 0;
        for (var entry : images.entrySet()) {
            size += entry.getValue().length;
            if (size > 64L * 1024 * 1024) throw new IllegalArgumentException("Entity images exceed budget");
            copied.put(entry.getKey(), entry.getValue().clone());
        }
        images = Collections.unmodifiableMap(copied);
    }

    public KnowledgePayload(String category, byte[] bytes, int total, int succeeded, int failed) {
        this(category, bytes, total, succeeded, failed, Map.of());
    }

    @Override
    public Map<String, byte[]> images() {
        var copy = new LinkedHashMap<String, byte[]>();
        images.forEach((path, value) -> copy.put(path, value.clone()));
        return Collections.unmodifiableMap(copy);
    }

    public byte[] image(String path) throws java.io.IOException {
        var value = images.get(path);
        if (value == null) throw new java.io.IOException("Missing detached entity PNG: " + path);
        return value.clone();
    }

    public KnowledgePayload externalizeEntityImages() throws java.io.IOException {
        if (!category.equals("entity_icons")) throw new IllegalArgumentException("Not an entity payload");
        var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("imageStorage")) throw new java.io.IOException("Entity image storage is already declared");
        return detached(root, total, succeeded, failed);
    }

    public static KnowledgePayload externalEntityImages(JsonArray records, JsonObject annotations) throws java.io.IOException {
        var root = annotations.deepCopy();
        root.addProperty("format", "mkb-data");
        root.addProperty("schemaVersion", 1);
        root.addProperty("category", "entity_icons");
        root.addProperty("authoritative", true);
        root.add("records", records.deepCopy());
        int failed = 0, transparent = 0;
        for (var value : records) {
            var row = value.getAsJsonObject();
            if (row.has("error")) failed++;
            else if (row.has("status") && row.get("status").getAsString().equals("TRANSPARENT")) transparent++;
        }
        return detached(root, records.size(), records.size() - failed - transparent, failed);
    }

    private static KnowledgePayload detached(JsonObject root, int total, int succeeded, int failed) throws java.io.IOException {
        var files = EntityImageFiles.decode(IconState.canonical(root).getBytes(StandardCharsets.UTF_8));
        for (var value : root.getAsJsonArray("records")) value.getAsJsonObject().remove("pngBase64");
        root.addProperty("imageStorage", "EXTERNAL_PNG");
        return new KnowledgePayload("entity_icons", ReadableJson.bytes(root), total, succeeded, failed, files);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public String hash() {
        return ContentDigest.sha256(bytes);
    }

    public int transparent() {
        if (!category.equals("entity_icons")) return 0;
        int value = 0;
        for (var row : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("records"))
            if (!row.getAsJsonObject().has("error") && row.getAsJsonObject().has("status") && row.getAsJsonObject().get("status").getAsString().equals("TRANSPARENT"))
                value++;
        return value;
    }

    public SnapshotDescriptor.Category declaration() {
        boolean partial = Set.of("trades", "spawn_rules", "structure_templates", "multiblocks").contains(category) && JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject().has("semanticCoverage") && JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject().get("semanticCoverage").getAsString().equals("PARTIAL");
        return new SnapshotDescriptor.Category(failed == 0 && !partial ? "COMPLETE" : "COMPLETE_WITH_ERRORS", total, succeeded, transparent(), 0, failed, "data/" + category + ".json", hash(), partial ? "Enumerable records retained; some rule semantics or registry access require adapters" : failed == 0 ? null : "See failed records in payload");
    }

    public static KnowledgePayload of(String category, JsonArray records) {
        if (!category.matches("[a-z_]{1,64}") || records.size() > 250_000)
            throw new IllegalArgumentException("Payload category or count exceeds budget");
        int failed = 0;
        for (var record : records) if (record.getAsJsonObject().has("error")) failed++;
        var root = new JsonObject();
        root.addProperty("format", "mkb-data");
        root.addProperty("schemaVersion", 1);
        root.addProperty("category", category);
        root.addProperty("authoritative", true);
        root.add("records", records);
        if (Set.of("trades", "brewing", "workstation_rules", "loot_entrypoints", "potions", "effects", "enchantments", "spawn_rules", "structures", "structure_sets", "template_pools", "structure_processors", "structure_templates", "multiblocks").contains(category)) {
            root.addProperty("capabilityVersion", 1);
            root.addProperty("semanticCoverage", category.equals("trades") ? "DECLARATIVE_AND_EXPLICIT_UNSUPPORTED" : category.equals("workstation_rules") ? "VANILLA_BASE_RULES" : Set.of("potions", "effects", "enchantments", "spawn_rules", "structures", "structure_sets", "template_pools", "structure_processors", "structure_templates", "multiblocks").contains(category) ? "REGISTRY_METADATA_WITH_EXECUTABLE_LIMITS" : "DECLARED_RELATIONS");
        }
        if (category.endsWith("_attributes")) {
            root.addProperty("capabilityVersion", category.equals("entity_attributes") ? 3 : 1);
            root.addProperty("scope", category.equals("item_attributes") ? "DEFAULT_ITEM_STACK_SLOT_MODIFIERS" : category.equals("entity_attributes") ? "SERVER_SELECTED_OR_INITIALIZED_LIVING_INSTANCES" : "SERVER_CAPTURED_ENTITY_STATES");
        }
        if (category.equals("structure_templates") || category.equals("multiblocks")) {
            int incomplete = 0;
            for (var value : records) {
                var row = value.getAsJsonObject();
                if (category.equals("structure_templates") && row.has("geometryStatus") && row.get("geometryStatus").getAsString().equals("METADATA_ONLY") || category.equals("multiblocks") && row.has("status") && row.get("status").getAsString().equals("UNSUPPORTED"))
                    incomplete++;
            }
            root.addProperty("semanticCoverage", incomplete > 0 ? "PARTIAL" : category.equals("multiblocks") ? "DECLARED_PROVIDERS_ONLY" : "RESOURCE_TEMPLATE_GEOMETRY");
            root.addProperty("incompleteRecords", incomplete);
            root.addProperty("discovery", category.equals("multiblocks") ? "PROVIDER_DECLARATIONS_ONLY" : "LOADED_RESOURCE_PACKS_NOT_LIVE_PLACEMENTS");
        }
        if (category.equals("spawn_rules")) {
            int unavailable = 0;
            for (var value : records) {
                var row = value.getAsJsonObject();
                if (row.has("placement") && row.getAsJsonObject("placement").get("status").getAsString().equals("UNAVAILABLE"))
                    unavailable++;
            }
            root.addProperty("unavailablePlacements", unavailable);
            root.addProperty("semanticCoverage", unavailable > 0 ? "PARTIAL" : "ENUMERABLE_CONFIGURATION_ONLY");
            root.addProperty("dynamicEvents", "NOT_ENUMERATED");
            root.addProperty("summoning", "NOT_ENUMERATED");
            root.addProperty("customSpawners", "NOT_ENUMERATED");
        }
        if (category.equals("trades")) {
            var tradeEntities = new JsonArray();
            tradeEntities.add("minecraft:villager");
            tradeEntities.add("minecraft:wandering_trader");
            root.add("tradeEntities", tradeEntities);
            int candidates = 0, unsupported = 0;
            for (var value : records) {
                var row = value.getAsJsonObject();
                if (!row.has("entries")) continue;
                for (var element : row.getAsJsonArray("entries")) {
                    candidates++;
                    var entry = element.getAsJsonObject();
                    if (entry.has("status") && entry.get("status").getAsString().equals("UNSUPPORTED")) unsupported++;
                }
            }
            root.addProperty("candidateEntries", candidates);
            root.addProperty("unsupportedEntries", unsupported);
            root.addProperty("semanticCoverage", unsupported == 0 ? "COMPLETE_DECLARED_FACTORIES" : "PARTIAL");
        }
        byte[] bytes = ReadableJson.bytes(root);
        if (bytes.length > 64 * 1024 * 1024) throw new IllegalArgumentException("Payload exceeds 64 MiB: " + category);
        int transparent = 0;
        if (category.equals("entity_icons")) for (var row : records)
            if (!row.getAsJsonObject().has("error") && row.getAsJsonObject().has("status") && row.getAsJsonObject().get("status").getAsString().equals("TRANSPARENT"))
                transparent++;
        return new KnowledgePayload(category, bytes, records.size(), records.size() - failed - transparent, failed);
    }
}
