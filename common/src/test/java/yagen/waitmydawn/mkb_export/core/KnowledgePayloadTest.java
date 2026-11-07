package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgePayloadTest {
    @TempDir
    Path temp;

    @Test
    void knowledgeFilesMustBePresentAndMatchDeclarationBeforePublication() throws Exception {
        var row = new JsonObject();
        row.addProperty("id", "demo:recipe");
        row.addProperty("normalizationStatus", "UNSUPPORTED");
        var rows = new JsonArray();
        rows.add(row);
        var payload = KnowledgePayload.of("recipes", rows);
        assertEquals("COMPLETE", payload.declaration().status());
        var environment = new ExportManifest.Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of());
        var snapshot = SnapshotDescriptor.preparation("SINGLEPLAYER", "ALL", "a".repeat(64), "b".repeat(64), "session", 1).withPayloads(List.of(payload));
        var target = new ExportJob.Target("minecraft:stone", null);
        try (var job = new ExportJob(temp, environment, 64, "test", List.of(target), snapshot)) {
            var image = new java.awt.image.BufferedImage(64, 64, 2);
            var bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", bytes);
            job.write(target, bytes.toByteArray());
            assertThrows(java.io.IOException.class, job::publish);
            job.writePayloads(List.of(payload));
            Files.writeString(job.workDirectory().resolve("data/recipes.json"), "{}");
            assertThrows(java.io.IOException.class, job::publish);
            job.writePayloads(List.of(payload));
            Path published = job.publish();
            assertEquals(payload.hash(), ContentDigest.file(published.resolve("data/recipes.json")));
        }
    }

    @Test
    void serializationFailureIsCountedSeparatelyFromUnsupportedSemanticsAndBytesAreImmutable() {
        var rows = new JsonArray();
        var good = new JsonObject();
        good.addProperty("id", "demo:custom");
        good.addProperty("normalizationStatus", "UNSUPPORTED");
        rows.add(good);
        var bad = new JsonObject();
        bad.addProperty("id", "demo:bad");
        bad.addProperty("error", "codec failure");
        rows.add(bad);
        var payload = KnowledgePayload.of("recipes", rows);
        assertEquals(2, payload.total());
        assertEquals(1, payload.succeeded());
        assertEquals(1, payload.failed());
        assertEquals("COMPLETE_WITH_ERRORS", payload.declaration().status());
        String hash = payload.hash();
        payload.bytes()[0] = 0;
        assertEquals(hash, payload.hash());
    }
}
