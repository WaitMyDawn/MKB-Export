package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static yagen.waitmydawn.mkb_export.core.ExportManifest.*;

class ExportJobTest {
    @TempDir
    Path temp;

    Environment env() {
        return new Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of());
    }

    byte[] png() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 64, 2), "png", out);
        return out.toByteArray();
    }

    ExportJob.Target target(String id) {
        return new ExportJob.Target(id, null);
    }

    @Test
    void cancelResumeVerifiesFilesAndRetriesFailures() throws Exception {
        var a = target("minecraft:stone");
        var b = target("minecraft:book");
        var c = target("minecraft:glass");
        Path work;
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(a, b, c))) {
            job.write(a, png());
            job.failure(b, "render failure");
            work = job.workDirectory();
        }
        assertFalse(Files.exists(temp.resolve("MKB-Export")));
        try (var job = ExportJob.resume(temp, work.getFileName().toString(), env(), "test")) {
            assertEquals(List.of(b, c), job.pending());
            job.write(b, png());
            job.write(c, png());
            assertTrue(Files.exists(job.publish().resolve("manifest.json")));
        }
    }

    @Test
    void damagedCheckpointImageIsRenderedAgainAndEnvironmentMismatchIsRejected() throws Exception {
        var a = target("minecraft:stone");
        Path work;
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(a))) {
            job.write(a, png());
            work = job.workDirectory();
        }
        Files.write(work.resolve("icons/default/minecraft/stone.png"), new byte[]{1});
        assertThrows(java.io.IOException.class, () -> ExportJob.resume(temp, work.getFileName().toString(), env(), "changed"));
        try (var job = ExportJob.resume(temp, work.getFileName().toString(), env(), "test")) {
            assertEquals(List.of(a), job.pending());
            job.write(a, png());
            job.publish();
        }
    }

    @Test
    void reuseIsHashVerifiedAndDoesNotCrossRenderEnvironments() throws Exception {
        var a = target("minecraft:stone");
        Path base;
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(a))) {
            job.write(a, png());
            base = job.publish();
        }
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(a))) {
            assertEquals(1, job.reuse(base.getFileName().toString()));
            assertTrue(job.pending().isEmpty());
            assertNotEquals(base, job.publish());
        }
        try (var job = new ExportJob(temp, env(), 128, "test", List.of(a))) {
            assertEquals(0, job.reuse(base.getFileName().toString()));
            assertEquals(List.of(a), job.pending());
        }
        Files.write(base.resolve("icons/default/minecraft/stone.png"), new byte[]{9});
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(a))) {
            assertEquals(0, job.reuse(base.getFileName().toString()));
        }
    }

    @Test
    void stateIdentityIsCanonicalAndDistinctFromDefault() throws Exception {
        var state = JsonParser.parseString("{\"components\":{\"minecraft:dyed_color\":{\"rgb\":16711680,\"show_in_tooltip\":true}}}").getAsJsonObject();
        var ordered = JsonParser.parseString("{\"components\":{\"minecraft:dyed_color\":{\"show_in_tooltip\":true,\"rgb\":16711680}}}").getAsJsonObject();
        var normal = target("minecraft:leather_helmet");
        var dyed = new ExportJob.Target(normal.itemId(), state);
        assertEquals(dyed.variant(), new ExportJob.Target(normal.itemId(), ordered).variant());
        assertNotEquals(normal.key(), dyed.key());
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(normal, dyed))) {
            job.write(normal, png());
            job.write(dyed, png());
            var manifest = JsonParser.parseString(Files.readString(job.publish().resolve("manifest.json"))).getAsJsonObject();
            assertEquals(2, manifest.get("schemaVersion").getAsInt());
            assertEquals(2, manifest.getAsJsonArray("icons").size());
        }
    }

    @Test
    void checkpointHasOneWriterAndPathsCannotEscapeGameDirectory() throws Exception {
        var a = target("minecraft:stone");
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(a))) {
            assertThrows(java.io.IOException.class, () -> ExportJob.resume(temp, job.workDirectory().getFileName().toString(), env(), "test"));
            assertThrows(java.io.IOException.class, () -> job.reuse("../other"));
            assertThrows(java.io.IOException.class, job::publish);
        }
    }

    @Test
    void compactionAndTruncatedJournalKeepOnlyDurableRecords() throws Exception {
        var plan = new ArrayList<ExportJob.Target>();
        for (int i = 0; i < 70; i++) plan.add(target("demo:item_" + i));
        Path work;
        try (var job = new ExportJob(temp, env(), 64, "test", plan)) {
            for (int i = 0; i < 68; i++) job.write(plan.get(i), png());
            work = job.workDirectory();
        }
        Files.writeString(work.resolve("journal.jsonl"), "{unfinished", StandardOpenOption.APPEND);
        try (var job = ExportJob.resume(temp, work.getFileName().toString(), env(), "test")) {
            assertEquals(plan.subList(68, 70), job.pending());
            for (var target : job.pending()) job.write(target, png());
            job.publish();
        }
    }
}
