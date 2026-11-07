package yagen.waitmydawn.mkb_export.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static yagen.waitmydawn.mkb_export.core.ExportManifest.*;

class ExportSessionTest {
    @TempDir
    Path temp;

    Environment environment() {
        return new Environment("1.21.1", "neoforge", "21.1.265", List.of(new Mod("example", "1.0", null)), List.of());
    }

    @Test
    void publishesExactIdsHashesAndFailureRecordsWithoutOverwritingExistingExport() throws Exception {
        byte[] png = new byte[]{1, 2, 3};
        Path first;
        try (var session = new ExportSession(temp, environment(), 64, "test")) {
            session.write("a:widget", png);
            session.write("b:widget", new byte[]{4});
            session.failure("a:special", "UNSUPPORTED", "world context");
            first = session.publish();
        }
        var json = JsonParser.parseString(Files.readString(first.resolve("manifest.json"))).getAsJsonObject();
        assertEquals("GAME_GUI", json.get("origin").getAsString());
        var rows = json.getAsJsonArray("icons");
        assertEquals(3, rows.size());
        assertFalse(rows.get(0).getAsJsonObject().has("file"));
        assertEquals(ContentDigest.sha256(png), rows.get(1).getAsJsonObject().get("sha256").getAsString());
        assertArrayEquals(png, Files.readAllBytes(first.resolve("icons/a/widget.png")));
        try (var second = new ExportSession(temp, environment(), 64, "test")) {
            second.write("a:widget", new byte[]{9});
            assertNotEquals(first, second.publish());
        }
        assertArrayEquals(png, Files.readAllBytes(first.resolve("icons/a/widget.png")));
    }

    @Test
    void cancellationRemovesOnlyOwnStagingAndNeverPublishesManifest() throws Exception {
        Files.writeString(temp.resolve("existing.txt"), "preserve");
        try (var session = new ExportSession(temp, environment(), 64, "test")) {
            session.write("a:widget", new byte[]{1});
        }
        try (var files = Files.list(temp)) {
            assertEquals(List.of("existing.txt"), files.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void rejectsDuplicateIdsTraversalAndProtocolOverflow() throws Exception {
        try (var session = new ExportSession(temp, environment(), 64, "test")) {
            assertThrows(IOException.class, () -> session.write("a:../escape", new byte[]{1}));
            assertThrows(IOException.class, () -> session.write("a:/absolute", new byte[]{1}));
            assertThrows(IOException.class, () -> session.write("a:huge", new byte[8 * 1024 * 1024 + 1]));
            session.write("a:widget", new byte[]{1});
            assertThrows(IOException.class, () -> session.failure("a:widget", "FAILED", "duplicate"));
        }
    }

    @Test
    void fingerprintsAreIndependentOfEnumerationOrderButReflectContentAndPaths() throws Exception {
        var a = new TreeMap<String, ContentDigest.Input>();
        a.put("n:a", () -> new ByteArrayInputStream(new byte[]{1}));
        a.put("n:b", () -> new ByteArrayInputStream(new byte[]{2}));
        var b = new TreeMap<>(a);
        assertEquals(ContentDigest.resources(a), ContentDigest.resources(b));
        b.put("n:b", () -> new ByteArrayInputStream(new byte[]{3}));
        assertNotEquals(ContentDigest.resources(a), ContentDigest.resources(b));
        b.remove("n:b");
        b.put("n:c", () -> new ByteArrayInputStream(new byte[]{2}));
        assertNotEquals(ContentDigest.resources(a), ContentDigest.resources(b));
    }

    @Test
    void resourceHashingRespondsToCancellation() {
        var entries = new TreeMap<String, ContentDigest.Input>();
        entries.put("n:a", () -> new ByteArrayInputStream(new byte[1024]));
        assertThrows(InterruptedIOException.class, () -> ContentDigest.resources(entries, () -> true));
    }
}
