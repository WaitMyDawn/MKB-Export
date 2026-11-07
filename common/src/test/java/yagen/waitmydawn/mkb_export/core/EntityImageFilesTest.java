package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class EntityImageFilesTest {
    @TempDir
    Path temp;

    byte[] png(int size, boolean visible) throws Exception {
        var image = new java.awt.image.BufferedImage(size, size, 2);
        if (visible) image.setRGB(2, 2, 0xff778899);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    JsonObject row(String id, byte[] bytes, String status) {
        var row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("file", "entities/" + id.replace(':', '/') + ".png");
        row.addProperty("pngBase64", Base64.getEncoder().encodeToString(bytes));
        row.addProperty("sha256", ContentDigest.sha256(bytes));
        row.addProperty("status", status);
        return row;
    }

    @Test
    void publishesVisibleAndTransparentPngsAndRechecksThemBeforePublication() throws Exception {
        byte[] visible = png(128, true), transparent = png(128, false);
        var rows = new JsonArray();
        rows.add(row("demo:mob/nested", visible, "OK"));
        rows.add(row("demo:empty", transparent, "TRANSPARENT"));
        var bad = new JsonObject();
        bad.addProperty("id", "demo:failed");
        bad.addProperty("error", "no default instance");
        rows.add(bad);
        var payload = KnowledgePayload.of("entity_icons", rows);
        var env = new ExportManifest.Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of());
        var snapshot = SnapshotDescriptor.preparation("SINGLEPLAYER", "ALL", "a".repeat(64), "b".repeat(64), "session", 1).withPayloads(List.of(payload));
        var target = new ExportJob.Target("minecraft:stone", null);
        try (var job = new ExportJob(temp, env, 64, "0.6.1", List.of(target), snapshot)) {
            job.write(target, png(64, true));
            job.writePayloads(List.of(payload));
            Path path = job.workDirectory().resolve("entities/demo/mob/nested.png");
            assertArrayEquals(visible, Files.readAllBytes(path));
            Files.write(path, transparent);
            assertThrows(java.io.IOException.class, job::publish);
            job.writePayloads(List.of(payload));
            Files.delete(path);
            assertThrows(java.io.IOException.class, job::publish);
            job.writePayloads(List.of(payload));
            Path output = job.publish();
            assertArrayEquals(visible, Files.readAllBytes(output.resolve("entities/demo/mob/nested.png")));
            assertArrayEquals(transparent, Files.readAllBytes(output.resolve("entities/demo/empty.png")));
            assertFalse(Files.exists(output.resolve("entities/demo/failed.png")));
        }
    }

    @Test
    void rejectsTraversalHashMismatchAndIncorrectTransparency() throws Exception {
        var good = row("demo:mob", png(128, true), "OK");
        for (String kind : List.of("path", "hash", "status")) {
            var bad = good.deepCopy();
            if (kind.equals("path")) bad.addProperty("file", "../escape.png");
            if (kind.equals("hash")) bad.addProperty("sha256", "0".repeat(64));
            if (kind.equals("status")) bad.addProperty("status", "TRANSPARENT");
            var rows = new JsonArray();
            rows.add(bad);
            var payload = KnowledgePayload.of("entity_icons", rows);
            assertThrows(java.io.IOException.class, () -> EntityImageFiles.decode(payload.bytes()));
        }
    }

    @Test
    void multipleStatesOfOneEntityRetainSeparatePngFilesAtHigherResolution() throws Exception {
        var first = row("demo:mob", png(256, true), "OK");
        first.addProperty("width", 256);
        first.addProperty("height", 256);
        var state = JsonParser.parseString("{\"origin\":\"DECLARED_STATE\",\"traits\":{\"age\":\"baby\"}}").getAsJsonObject();
        String key = EntityAppearance.key(state);
        var second = row("demo:mob", png(512, true), "OK");
        second.addProperty("width", 512);
        second.addProperty("height", 512);
        second.add("state", state);
        second.addProperty("stateKey", key);
        second.addProperty("file", EntityAppearance.path("demo:mob", key));
        var rows = new JsonArray();
        rows.add(first);
        rows.add(second);
        var payload = KnowledgePayload.of("entity_icons", rows);
        var files = EntityImageFiles.decode(payload.bytes());
        assertEquals(2, files.size());
        assertTrue(files.containsKey("entities/demo/mob.png"));
        assertTrue(files.containsKey("entities/demo/mob/states/" + key + ".png"));
    }

    @Test
    void resumedPlanRemovesOnlyObsoleteEntityPngsFromItsUnpublishedWorkspace() throws Exception {
        var first = new JsonArray();
        first.add(row("demo:visible", png(256, true), "OK"));
        first.get(0).getAsJsonObject().addProperty("width", 256);
        first.add(row("demo:empty", png(128, false), "TRANSPARENT"));
        var payload = KnowledgePayload.of("entity_icons", first);
        var env = new ExportManifest.Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of());
        var snapshot = SnapshotDescriptor.preparation("SINGLEPLAYER", "ALL", "a".repeat(64), "b".repeat(64), "session", 1).withPayloads(List.of(payload));
        var target = new ExportJob.Target("minecraft:stone", null);
        Path work;
        try (var job = new ExportJob(temp, env, 64, "0.7.0", List.of(target), snapshot)) {
            job.write(target, png(64, true));
            job.writePayloads(List.of(payload));
            work = job.workDirectory();
        }
        var current = new JsonArray();
        current.add(first.get(0));
        var replacement = KnowledgePayload.of("entity_icons", current);
        try (var job = ExportJob.resume(temp, work.getFileName().toString(), env, "0.7.0", snapshot.withPayloads(List.of(replacement)))) {
            job.writePayloads(List.of(replacement));
            var output = job.publish();
            assertTrue(Files.exists(output.resolve("entities/demo/visible.png")));
            assertFalse(Files.exists(output.resolve("entities/demo/empty.png")));
            assertTrue(Files.exists(output.resolve("icons/default/minecraft/stone.png")));
        }
    }

    @Test
    void readableAndLegacyPathsAreValidatedAgainstTheSameState() throws Exception {
        var state = EntityAppearance.defaults();
        state.addProperty("origin", "SPAWN_INITIALIZED");
        state.getAsJsonObject("traits").addProperty("age", "adult");
        String key = EntityAppearance.key(state);
        var image = row("demo:mob", png(256, true), "OK");
        image.add("state", state);
        image.addProperty("stateKey", key);
        image.addProperty("width", 256);
        for (String path : List.of(EntityAppearance.path("demo:mob", state), EntityAppearance.path("demo:mob", key))) {
            image.addProperty("file", path);
            var records = new JsonArray();
            records.add(image);
            assertTrue(EntityImageFiles.decode(KnowledgePayload.of("entity_icons", records).bytes()).containsKey(path));
        }
        image.addProperty("file", "entities/demo/mob/states/spawn_adult_wrong.png");
        var records = new JsonArray();
        records.add(image);
        assertThrows(java.io.IOException.class, () -> EntityImageFiles.decode(KnowledgePayload.of("entity_icons", records).bytes()));
    }
}
