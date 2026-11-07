package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ReadableExportTest {
    @TempDir
    Path temp;
    EntityImageFilesTest fixture = new EntityImageFilesTest();

    KnowledgePayload payload() throws Exception {
        var rows = new JsonArray();
        var owner = fixture.row("demo:mob", fixture.png(128, true), "OK");
        owner.addProperty("stateKey", "default");
        owner.addProperty("width", 128);
        owner.addProperty("height", 128);
        rows.add(owner);
        var alias = owner.deepCopy();
        var state = new JsonObject();
        state.addProperty("origin", "DECLARED_STATE");
        state.add("traits", JsonParser.parseString("{\"age\":\"adult\"}"));
        alias.add("state", state);
        alias.addProperty("stateKey", EntityAppearance.key(state));
        rows.add(alias);
        EntityVisualDedup.apply(rows);
        var paths = EntityFileLayout.assign(rows);
        for (var value : rows) {
            var row = value.getAsJsonObject();
            row.addProperty("file", paths.get(row.get("id").getAsString() + "|" + row.get("stateKey").getAsString()));
        }
        var inline = KnowledgePayload.of("entity_icons", rows);
        var root = JsonParser.parseString(new String(inline.bytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        root.addProperty("imageReferencesVersion", 1);
        return new KnowledgePayload("entity_icons", ReadableJson.bytes(root), 2, 2, 0).externalizeEntityImages();
    }

    @Test
    void prettySerializationPreservesCanonicalIdentityAndUnicode() {
        var source = JsonParser.parseString("{\"z\":[{\"text\":\"实体名称\",\"none\":null}],\"a\":true}");
        String before = IconState.canonical(source), pretty = new String(ReadableJson.bytes(source), StandardCharsets.UTF_8);
        assertTrue(pretty.contains("\n  "));
        assertTrue(pretty.contains("实体名称"));
        assertTrue(pretty.endsWith("\n"));
        assertEquals(before, IconState.canonical(JsonParser.parseString(pretty)));
        assertArrayEquals(ReadableJson.bytes(source), ReadableJson.bytes(JsonParser.parseString(pretty)));
        var rows = new JsonArray();
        var row = new JsonObject();
        row.addProperty("id", "demo:entry");
        row.add("definition", source);
        rows.add(row);
        for (String category : List.of("mod_metadata", "registries", "language", "recipes", "tags", "loot_tables", "loot_modifiers"))
            assertTrue(new String(KnowledgePayload.of(category, rows).bytes(), StandardCharsets.UTF_8).contains("\n  "));
    }

    @Test
    void externalPayloadRetainsAliasesWithoutEmbeddingImagesAndProtectsByteOwnership() throws Exception {
        var data = payload();
        String text = new String(data.bytes(), StandardCharsets.UTF_8);
        assertFalse(text.contains("pngBase64"));
        assertTrue(text.contains("EXTERNAL_PNG"));
        assertTrue(text.contains("imageRef"));
        assertEquals(1, data.images().size());
        var files = EntityImageFiles.decode(data.bytes(), data::image);
        assertEquals(1, files.size());
        String path = files.keySet().iterator().next();
        byte[] original = data.image(path);
        var exposed = data.images();
        exposed.get(path)[0] = 0;
        assertArrayEquals(original, data.image(path));
        assertThrows(java.io.IOException.class, () -> EntityImageFiles.decode(data.bytes()));
        assertThrows(java.io.IOException.class, () -> EntityImageFiles.decode(data.bytes(), name -> new byte[]{1, 2, 3}));
    }

    @Test
    void publicationAndResumeCheckExternalFilesAndFormattedPayloadHashes() throws Exception {
        var data = payload();
        var env = new ExportManifest.Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of());
        var snapshot = SnapshotDescriptor.preparation("SINGLEPLAYER", "ALL", "a".repeat(64), "b".repeat(64), "session", 1).withPayloads(List.of(data));
        var target = new ExportJob.Target("minecraft:stone", null);
        Path work;
        try (var job = new ExportJob(temp, env, 64, "0.11.0", List.of(target), snapshot)) {
            job.write(target, fixture.png(64, true));
            job.writePayloads(List.of(data));
            work = job.workDirectory();
            Files.write(work.resolve("entities/demo/mob.png"), new byte[]{1});
            assertThrows(java.io.IOException.class, job::publish);
        }
        try (var job = ExportJob.resume(temp, work.getFileName().toString(), env, "0.11.0", snapshot)) {
            job.writePayloads(List.of(data));
            Path output = job.publish();
            assertEquals(data.hash(), ContentDigest.file(output.resolve("data/entity_icons.json")));
            assertArrayEquals(data.image("entities/demo/mob.png"), Files.readAllBytes(output.resolve("entities/demo/mob.png")));
            try (var paths = Files.walk(output)) {
                for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    String text = Files.readString(path);
                    assertTrue(text.contains("\n"), path.toString());
                    JsonParser.parseString(text);
                }
            }
        }
    }
}
