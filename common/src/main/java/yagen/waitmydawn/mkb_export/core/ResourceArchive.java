package yagen.waitmydawn.mkb_export.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * 加载器的嵌套 JAR 路径可能属于虚拟文件系统，不能通过 Path.toFile 打开。
 */
public final class ResourceArchive {
    private static final Pattern ASSET = Pattern.compile("(?:^|/)assets/[^/]+/([^/]+)");
    private static final long MAX_TEMP_BYTES = 2L * 1024 * 1024 * 1024;

    private ResourceArchive() {
    }

    public static Set<String> roots(Path archive) throws IOException {
        return roots(archive, null);
    }

    static Set<String> roots(Path archive, Path temporaryDirectory) throws IOException {
        if (archive.getFileSystem().equals(FileSystems.getDefault())) return scan(archive);
        // 复制压缩字节后只读中央目录，避免用 ZipInputStream 解压整个模组来枚举文件名。
        Path temporary = temporaryDirectory == null ? Files.createTempFile("mkb-export-archive-", ".jar")
                : Files.createTempFile(temporaryDirectory, "mkb-export-archive-", ".jar");
        try {
            try (var input = Files.newInputStream(archive); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                long bytes = 0;
                for (int count; (count = input.read(buffer)) != -1; ) {
                    ContentDigest.interrupted();
                    if ((bytes += count) > MAX_TEMP_BYTES) throw new IOException("Nested archive exceeds 2 GiB");
                    output.write(buffer, 0, count);
                }
            }
            return scan(temporary);
        } catch (IOException failure) {
            throw new IOException("Cannot scan archive [" + archive.getFileSystem().provider().getScheme() + "] " + archive, failure);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Set<String> scan(Path archive) throws IOException {
        var roots = new TreeSet<String>();
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ContentDigest.interrupted();
                var matcher = ASSET.matcher(entries.nextElement().getName());
                if (matcher.find()) roots.add(matcher.group(1));
                if (roots.size() > 4096) throw new IOException("Too many resource root directories");
            }
        }
        return Set.copyOf(roots);
    }
}