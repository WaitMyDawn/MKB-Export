package yagen.waitmydawn.mkb_export.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * 对实际资源内容取指纹，而不是用包名替代资源哈希。
 */
public final class ContentDigest {
    @FunctionalInterface
    public interface Input {
        InputStream open() throws IOException;
    }

    private ContentDigest() {
    }

    public static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    public static String file(Path path) throws IOException {
        return file(path, () -> false);
    }

    public static String file(Path path, BooleanSupplier cancelled) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return stream(input, Long.MAX_VALUE, cancelled).hash();
        }
    }

    public static String resources(SortedMap<String, Input> resources) throws IOException {
        return resources(resources, () -> false);
    }

    public static String resources(SortedMap<String, Input> resources, BooleanSupplier cancelled) throws IOException {
        if (resources.size() > 100_000) throw new IOException("Resource pack contains too many resources");
        var digest = digest();
        long budget = 2L * 1024 * 1024 * 1024;
        for (var entry : resources.entrySet()) {
            interrupted();
            try (var input = entry.getValue().open()) {
                var part = stream(input, budget, cancelled);
                budget -= part.bytes();
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                digest.update((name.length + ":").getBytes(StandardCharsets.UTF_8));
                digest.update(name);
                digest.update((":" + part.bytes() + ":" + part.hash() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record Part(String hash, long bytes) {
    }

    private static Part stream(InputStream input, long limit, BooleanSupplier cancelled) throws IOException {
        var digest = digest();
        byte[] buffer = new byte[32 * 1024];
        long bytes = 0;
        for (int count; (count = input.read(buffer)) != -1; ) {
            interrupted();
            if (cancelled.getAsBoolean()) throw new InterruptedIOException("Export cancelled");
            bytes += count;
            if (bytes > limit) throw new IOException("Resource pack content exceeds 2 GiB");
            digest.update(buffer, 0, count);
        }
        return new Part(HexFormat.of().formatHex(digest.digest()), bytes);
    }

    public static void interrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Export interrupted");
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
