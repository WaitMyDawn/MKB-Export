package yagen.waitmydawn.mkb_export.core;

import java.nio.file.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class EntityReversalConfigTest {
    @TempDir
    Path temp;

    @Test
    void createsDragonDefaultAndKeepsTaskSnapshotImmutable() throws Exception {
        Path config = temp.resolve("config");
        var first = EntityReversalConfig.load(config);
        assertTrue(first.reversed("minecraft:ender_dragon"));
        assertFalse(first.reversed("minecraft:cow"));
        Path file = config.resolve("mkb_export_reversal.json");
        String custom = "[\"demo:boss\",\"demo:boss\"]";
        Files.writeString(file, custom);
        var next = EntityReversalConfig.load(config);
        assertEquals(1, next.entities().size());
        assertTrue(next.reversed("demo:boss"));
        assertFalse(next.reversed("minecraft:ender_dragon"));
        assertTrue(first.reversed("minecraft:ender_dragon"));
        assertEquals(custom, Files.readString(file));
        Files.writeString(file, "[]");
        assertTrue(EntityReversalConfig.load(config).entities().isEmpty());
    }

    @Test
    void rejectsMalformedEntriesWithoutReplacingTheFile() throws Exception {
        Files.createDirectories(temp.resolve("config"));
        Path file = temp.resolve("config/mkb_export_reversal.json");
        for (String text : java.util.List.of("{}", "[4]", "[\"../escape\"]", "[\"minecraft:../cow\"]", "[")) {
            Files.writeString(file, text);
            assertThrows(java.io.IOException.class, () -> EntityReversalConfig.load(file.getParent()));
            assertEquals(text, Files.readString(file));
        }
    }

    @Test
    void stockMigrationIsOneTimeAndUnknownNamespacesAreSafe() throws Exception {
        Path config = temp.resolve("legacy");
        Files.createDirectories(config);
        Path file = config.resolve("mkb_export_reversal.json");
        Files.writeString(file, "[\"minecraft:ender_dragon\"]");
        var migrated = EntityReversalConfig.load(config);
        assertTrue(migrated.reversed("alexscaves:underzealot"));
        assertFalse(migrated.reversed("minecraft:cow"));
        Files.writeString(file, "[\"minecraft:ender_dragon\"]");
        assertFalse(EntityReversalConfig.load(config).reversed("alexscaves:underzealot"));
        Path custom = temp.resolve("custom");
        Files.createDirectories(custom);
        Files.writeString(custom.resolve("mkb_export_reversal.json"), "[]");
        assertTrue(EntityReversalConfig.load(custom).entities().isEmpty());
    }
}
