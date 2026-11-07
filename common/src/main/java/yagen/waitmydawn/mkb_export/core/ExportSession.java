package yagen.waitmydawn.mkb_export.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static yagen.waitmydawn.mkb_export.core.ExportManifest.*;

/**
 * 单写入线程拥有暂存目录，只有完整任务可发布为导入包；取消保留已有导出。
 */
public final class ExportSession implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path parent, staging;
    private final Environment environment;
    private final int size;
    private final String exporterVersion;
    private final SortedMap<String, Icon> icons = new TreeMap<>();
    private long imageBytes;
    private boolean published, closed;

    public ExportSession(Path gameDirectory, Environment environment, int size, String exporterVersion) throws IOException {
        if (size < 16 || size > 512) throw new IllegalArgumentException("Icon size must be 16..512");
        this.parent = gameDirectory.toRealPath();
        this.environment = Objects.requireNonNull(environment);
        this.size = size;
        this.exporterVersion = Objects.requireNonNull(exporterVersion);
        staging = Files.createTempDirectory(parent, ".mkb-export-");
    }

    public void write(String itemId, byte[] png) throws IOException {
        checkItem(itemId);
        if (png.length == 0 || png.length > 8 * 1024 * 1024 || imageBytes + png.length > 512L * 1024 * 1024)
            throw new IOException("Icon byte budget exceeded");
        String relative = "icons/" + itemId.replace(':', '/') + ".png";
        Path file = staging.resolve(relative).normalize();
        if (!file.startsWith(staging.resolve("icons"))) throw new IOException("Icon path escapes staging directory");
        Files.createDirectories(file.getParent());
        Files.write(file, png, StandardOpenOption.CREATE_NEW);
        icons.put(itemId, new Icon(itemId, "default", "OK", relative, ContentDigest.sha256(png), null));
        imageBytes += png.length;
    }

    public void failure(String itemId, String status, String reason) throws IOException {
        checkItem(itemId);
        if (!Set.of("FAILED", "UNSUPPORTED").contains(status))
            throw new IllegalArgumentException("Invalid failure status");
        reason = Objects.toString(reason, "Unknown error").strip();
        if (reason.isEmpty()) reason = "Unknown error";
        icons.put(itemId, new Icon(itemId, "default", status, null, null, reason.substring(0, Math.min(2000, reason.length()))));
    }

    public Path publish() throws IOException {
        active();
        if (icons.isEmpty()) throw new IOException("No icon records to publish");
        var manifest = new ExportManifest("mkb-export", 1, "GAME_GUI", Instant.now().toString(),
                new Exporter("mkb_export", exporterVersion), environment,
                new Rendering("GUI", size, size, "transparent"), List.copyOf(icons.values()));
        byte[] bytes = JSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16 * 1024 * 1024) throw new IOException("Manifest exceeds 16 MiB");
        Files.write(staging.resolve("manifest.json"), bytes, StandardOpenOption.CREATE_NEW);
        Path destination = parent.resolve("MKB-Export");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            String suffix = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now());
            destination = parent.resolve("MKB-Export-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8));
        }
        // 同一文件系统重命名且不覆盖目标；暂存状态不会被宣告为已完成。
        Files.move(staging, destination);
        published = true;
        return destination;
    }

    private void checkItem(String id) throws IOException {
        active();
        if (id == null || id.length() > 500 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || id.contains("..") || id.contains(":/") || id.endsWith("/") || id.contains("//"))
            throw new IOException("Invalid item ID");
        if (icons.containsKey(id)) throw new IOException("Duplicate item ID: " + id);
        if (icons.size() >= 50_000) throw new IOException("Icon count exceeds v1 protocol limit");
    }

    private void active() throws IOException {
        ContentDigest.interrupted();
        if (published || closed) throw new IOException("Export session already closed");
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (!published) {
            try (var paths = Files.walk(staging)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
