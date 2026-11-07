package yagen.waitmydawn.mkb_export.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static yagen.waitmydawn.mkb_export.core.ExportManifest.*;

class TransparentIconTest {
    @TempDir
    Path temp;

    byte[] png(int pixel) throws IOException {
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, pixel);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    Environment env() {
        return new Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of());
    }

    @Test
    void onlyZeroAlphaIsTransparentAndInvalidPngStillFails() throws Exception {
        assertEquals("TRANSPARENT", IconPng.status(png(0x00ffffff), 64));
        assertEquals("OK", IconPng.status(png(0x01000000), 64));
        assertThrows(IOException.class, () -> IconPng.status(new byte[]{1, 2, 3}, 64));
        assertThrows(IOException.class, () -> IconPng.status(png(0), 32));
    }

    @Test
    void transparentIconsPublishResumeAndReuseAsCompletedResults() throws Exception {
        var target = new ExportJob.Target("demo:invisible", null);
        Path work, base;
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(target))) {
            assertEquals("TRANSPARENT", job.write(target, png(0)));
            assertTrue(job.pending().isEmpty());
            assertEquals(1, job.transparentCount());
            work = job.workDirectory();
        }
        try (var job = ExportJob.resume(temp, work.getFileName().toString(), env(), "test")) {
            assertTrue(job.pending().isEmpty());
            assertEquals(1, job.transparentCount());
            base = job.publish();
        }
        var manifest = JsonParser.parseString(Files.readString(base.resolve("manifest.json"))).getAsJsonObject();
        var icon = manifest.getAsJsonArray("icons").get(0).getAsJsonObject();
        assertEquals("TRANSPARENT", icon.get("status").getAsString());
        assertFalse(icon.has("error"));
        assertTrue(Files.isRegularFile(base.resolve(icon.get("file").getAsString())));
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(target))) {
            assertEquals(1, job.reuse(base.getFileName().toString()));
            assertTrue(job.pending().isEmpty());
            assertEquals(1, job.transparentCount());
            job.publish();
        }
    }

    @Test
    void forgedTransparentCheckpointWithVisiblePixelsIsRenderedAgain() throws Exception {
        var target = new ExportJob.Target("demo:visible", null);
        Path work;
        try (var job = new ExportJob(temp, env(), 64, "test", List.of(target))) {
            job.write(target, png(0xff000000));
            work = job.workDirectory();
        }
        Path journal = work.resolve("journal.jsonl");
        Files.writeString(journal, Files.readString(journal).replace("\"OK\"", "\"TRANSPARENT\""));
        try (var job = ExportJob.resume(temp, work.getFileName().toString(), env(), "test")) {
            assertEquals(List.of(target), job.pending());
        }
    }
}