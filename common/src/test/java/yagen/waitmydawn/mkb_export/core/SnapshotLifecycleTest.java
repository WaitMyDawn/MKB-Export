package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotLifecycleTest {
    @TempDir
    Path temp;
    String hash = "a".repeat(64);

    SnapshotDescriptor descriptor(String server, String session, long generation) {
        return SnapshotDescriptor.preparation("SINGLEPLAYER", "ALL", hash, server, session, generation);
    }

    ExportManifest.Environment environment() {
        return new ExportManifest.Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of());
    }

    byte[] png() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 64, 2), "png", output);
        return output.toByteArray();
    }

    @Test
    void publicationDeclaresMissingCapabilitiesAndHashesTheActualIconManifest() throws Exception {
        var target = new ExportJob.Target("minecraft:stone", null);
        try (var job = new ExportJob(temp, environment(), 64, "test", List.of(target), descriptor(hash, "session", 1))) {
            job.write(target, png());
            Path output = job.publish();
            var snapshot = JsonParser.parseString(Files.readString(output.resolve("snapshot.json"))).getAsJsonObject();
            var categories = snapshot.getAsJsonObject("categories");
            assertEquals("NOT_IMPLEMENTED", categories.getAsJsonObject("recipes").get("status").getAsString());
            var icons = categories.getAsJsonObject("item_icons");
            assertEquals(1, icons.get("transparent").getAsInt());
            assertEquals("COMPLETE", icons.get("status").getAsString());
            assertEquals(ContentDigest.file(output.resolve("manifest.json")), icons.get("sha256").getAsString());
        }
    }

    @Test
    void cancelledPublicationDoesNotPublishAndResumeRejectsChangedDataAndWorld() throws Exception {
        var target = new ExportJob.Target("minecraft:stone", null);
        Path checkpoint;
        try (var job = new ExportJob(temp, environment(), 64, "test", List.of(target), descriptor(hash, "session", 1))) {
            job.write(target, png());
            checkpoint = job.workDirectory();
            assertThrows(java.io.InterruptedIOException.class, () -> job.publish(() -> true));
            assertFalse(Files.exists(temp.resolve("MKB-Export")));
        }
        for (var changed : List.of(descriptor("b".repeat(64), "session", 1), descriptor(hash, "other", 1), descriptor(hash, "session", 2)))
            assertThrows(java.io.IOException.class, () -> ExportJob.resume(temp, checkpoint.getFileName().toString(), environment(), "test", changed));
        try (var resumed = ExportJob.resume(temp, checkpoint.getFileName().toString(), environment(), "test", descriptor(hash, "session", 1))) {
            assertTrue(resumed.pending().isEmpty());
            resumed.publish();
        }
    }

    @Test
    void reloadLeaseStaysInvalidAfterCompletionAndCannotBeAcquiredDuringReload() {
        var epoch = new ReloadEpoch();
        long before = epoch.acquire();
        epoch.begin();
        assertFalse(epoch.valid(before));
        assertThrows(IllegalStateException.class, epoch::acquire);
        epoch.ready();
        assertFalse(epoch.valid(before));
        assertTrue(epoch.valid(epoch.acquire()));
    }
}
