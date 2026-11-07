package yagen.waitmydawn.mkb_export.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class ResourceArchiveTest {
    @TempDir
    Path temp;

    private byte[] jar() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : List.of("assets/example/custom_render/model.json", "assets/example/textures/item.png", "data/example/recipes/widget.json")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void discoversCustomRootsInOrdinaryJar() throws Exception {
        Path file = temp.resolve("ordinary.jar");
        Files.write(file, jar());
        assertEquals(Set.of("custom_render", "textures"), ResourceArchive.roots(file));
    }

    @Test
    void discoversNestedJarThroughItsProviderAndPreservesItsContentHash() throws Exception {
        Path outer = temp.resolve("outer.jar"), scratch = Files.createDirectory(temp.resolve("scratch"));
        byte[] nested = jar();
        try (var zip = new ZipOutputStream(Files.newOutputStream(outer))) {
            zip.putNextEntry(new ZipEntry("META-INF/jarjar/inner.jar"));
            zip.write(nested);
            zip.closeEntry();
        }
        try (var fs = FileSystems.newFileSystem(outer)) {
            Path path = fs.getPath("/META-INF/jarjar/inner.jar");
            assertTrue(Files.isRegularFile(path));
            assertThrows(UnsupportedOperationException.class, path::toFile);
            assertEquals(Set.of("custom_render", "textures"), ResourceArchive.roots(path, scratch));
            assertEquals(ContentDigest.sha256(nested), ContentDigest.file(path));
        }
        try (var files = Files.list(scratch)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void invalidNestedArchiveFailsWithProviderContextAndRemovesItsTemporaryFile() throws Exception {
        Path outer = temp.resolve("invalid-outer.jar"), scratch = Files.createDirectory(temp.resolve("scratch"));
        try (var zip = new ZipOutputStream(Files.newOutputStream(outer))) {
            zip.putNextEntry(new ZipEntry("broken.jar"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        try (var fs = FileSystems.newFileSystem(outer)) {
            var failure = assertThrows(IOException.class, () -> ResourceArchive.roots(fs.getPath("/broken.jar"), scratch));
            assertTrue(failure.getMessage().contains("[jar] /broken.jar"));
        }
        try (var files = Files.list(scratch)) {
            assertEquals(0, files.count());
        }
    }
}