package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static yagen.waitmydawn.mkb_export.core.ExportManifest.*;

/**
 * 单写入者任务日志；取消保留可校验的检查点，发布包保持不可变。
 */
public final class ExportJob implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    public record Target(String itemId, JsonObject state) {
        public Target {
            if (itemId == null || !itemId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || itemId.length() > 500 || itemId.contains("..") || itemId.contains(":/") || itemId.endsWith("/") || itemId.contains("//"))
                throw new IllegalArgumentException("Invalid item ID");
            state = state == null ? null : state.deepCopy();
            IconState.variant(state);
        }

        public String variant() {
            return IconState.variant(state);
        }

        public String key() {
            return itemId + "|" + variant();
        }
    }

    private record Checkpoint(int version, Environment environment, int size, String exporterVersion,
                              List<Target> targets, List<Icon> icons, SnapshotDescriptor snapshot) {
    }

    private final Path parent, work;
    private final Environment environment;
    private final int size;
    private final String exporterVersion;
    private final SnapshotDescriptor snapshot;
    private final LinkedHashMap<String, Target> targets = new LinkedHashMap<>();
    private final SortedMap<String, Icon> icons = new TreeMap<>();
    private FileChannel lockChannel;
    private FileLock lock;
    private final Path lockPath;
    private long imageBytes;
    private int journalEntries;
    private boolean closed, published;

    public ExportJob(Path gameDirectory, Environment environment, int size, String exporterVersion, List<Target> plan) throws IOException {
        this(gameDirectory, environment, size, exporterVersion, plan, null, null);
        checkpoint();
    }

    public ExportJob(Path gameDirectory, Environment environment, int size, String exporterVersion, List<Target> plan, SnapshotDescriptor snapshot) throws IOException {
        this(gameDirectory, environment, size, exporterVersion, plan, null, snapshot);
        checkpoint();
    }

    private ExportJob(Path gameDirectory, Environment environment, int size, String exporterVersion, List<Target> plan, Path existing, SnapshotDescriptor snapshot) throws IOException {
        this.snapshot = snapshot;
        parent = gameDirectory.toRealPath();
        this.environment = Objects.requireNonNull(environment);
        this.size = size;
        this.exporterVersion = Objects.requireNonNull(exporterVersion);
        if (size < 16 || size > 512 || plan == null || plan.isEmpty() || plan.size() > 50000)
            throw new IOException("Invalid export plan");
        for (var target : plan)
            if (targets.putIfAbsent(target.key(), target) != null) throw new IOException("Duplicate target");
        work = existing == null ? Files.createTempDirectory(parent, ".mkb-export-") : existing;
        lockPath = safe(parent, work.getFileName() + ".lock");
        try {
            lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            if (lock == null) throw new IOException("Export checkpoint is already in use");
        } catch (IOException | RuntimeException failure) {
            if (lockChannel != null) lockChannel.close();
            throw new IOException("Cannot lock export checkpoint", failure);
        }
    }

    public static ExportJob resume(Path gameDirectory, String directory, Environment current, String version) throws IOException {
        return resume(gameDirectory, directory, current, version, null);
    }

    public static ExportJob resume(Path gameDirectory, String directory, Environment current, String version, SnapshotDescriptor snapshot) throws IOException {
        Path parent = gameDirectory.toRealPath(), work = child(parent, directory, true);
        Checkpoint saved = JSON.fromJson(read(safe(work, "checkpoint.json"), 16 * 1024 * 1024), Checkpoint.class);
        if (saved == null || saved.version() != 1 || !same(saved.environment(), current) || !version.equals(saved.exporterVersion()))
            throw new IOException("Checkpoint environment or exporter changed; start a new export");
        if (saved.snapshot() == null ? snapshot != null : !saved.snapshot().compatible(snapshot))
            throw new IOException("Checkpoint server session or data changed; start a new export");
        var job = new ExportJob(parent, current, saved.size(), version, saved.targets(), work, snapshot);
        try {
            // 锁内再次读取，避免使用另一个写入者正在替换的检查点。
            var stable = JSON.fromJson(read(safe(work, "checkpoint.json"), 16 * 1024 * 1024), Checkpoint.class);
            if (!same(saved, stable)) throw new IOException("Checkpoint changed while opening");
            if (saved.icons() == null || saved.icons().size() > 50000)
                throw new IOException("Invalid checkpoint records");
            var restored = new LinkedHashMap<String, Icon>();
            for (var icon : saved.icons()) {
                String key = icon.itemId() + "|" + icon.variant();
                if (restored.putIfAbsent(key, icon) != null) throw new IOException("Duplicate checkpoint identity");
            }
            Path journal = safe(work, "journal.jsonl");
            if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                String text = read(journal, 16 * 1024 * 1024);
                int complete = text.lastIndexOf('\n');
                if (complete >= 0) for (String line : text.substring(0, complete).split("\n")) {
                    if (line.isBlank()) continue;
                    var icon = COMPACT.fromJson(line, Icon.class);
                    restored.put(icon.itemId() + "|" + icon.variant(), icon);
                }
            }
            for (var icon : restored.values()) {
                String key = icon.itemId() + "|" + icon.variant();
                var target = job.targets.get(key);
                if (target == null || !target.variant().equals(IconState.variant(icon.state())))
                    throw new IOException("Invalid checkpoint identity");
                if (complete(icon)) try {
                    byte[] bytes = job.validated(work, icon, target);
                    if (job.imageBytes + bytes.length > 512L * 1024 * 1024)
                        throw new IOException("Recovered image budget exceeded");
                    job.icons.put(key, icon);
                    job.imageBytes += bytes.length;
                } catch (IOException damaged) {/* 损坏或超出预算的图标重新排队，不视为已完成。 */}
            }
            job.checkpoint();
            return job;
        } catch (Exception failure) {
            job.close();
            throw failure;
        }
    }

    public int reuse(String directory) throws IOException {
        active();
        Path base = child(parent, directory, false);
        var saved = JSON.fromJson(read(safe(base, "manifest.json"), 16 * 1024 * 1024), ExportManifest.class);
        if (saved == null || !"mkb-export".equals(saved.format()) || !"GAME_GUI".equals(saved.origin()) || !Set.of(1, 2).contains(saved.schemaVersion()) || saved.icons() == null || saved.icons().size() > 50000)
            throw new IOException("Invalid baseline manifest");
        if (!same(saved.environment(), environment) || saved.exporter() == null || !exporterVersion.equals(saved.exporter().version()) || saved.rendering() == null
                || saved.rendering().width() != size || saved.rendering().height() != size || !"GUI".equals(saved.rendering().context()) || !"transparent".equals(saved.rendering().background()))
            return 0;
        if (snapshot != null) {
            Path descriptor = safe(base, "snapshot.json");
            if (!Files.exists(descriptor, LinkOption.NOFOLLOW_LINKS)) return 0;
            var previous = JSON.fromJson(read(descriptor, 1024 * 1024), SnapshotDescriptor.class);
            if (previous == null || !previous.compatible(snapshot)) return 0;
        }
        int reused = 0;
        var seen = new HashSet<String>();
        for (var icon : saved.icons()) {
            ContentDigest.interrupted();
            String key = icon.itemId() + "|" + icon.variant();
            if (!seen.add(key)) throw new IOException("Duplicate baseline identity");
            var target = targets.get(key);
            if (target == null || !complete(icon)) continue;
            try {
                byte[] bytes = validated(base, icon, target);
                write(target, bytes);
                reused++;
            } catch (IOException damaged) {/* 基线损坏或状态不一致时重新渲染。 */}
        }
        return reused;
    }

    public List<Target> pending() {
        return targets.values().stream().filter(t -> !icons.containsKey(t.key()) || !complete(icons.get(t.key()))).toList();
    }

    private static boolean complete(Icon icon) {
        return icon != null && ("OK".equals(icon.status()) || "TRANSPARENT".equals(icon.status()));
    }

    public int transparentCount() {
        return (int) icons.values().stream().filter(i -> "TRANSPARENT".equals(i.status())).count();
    }

    public int size() {
        return size;
    }

    public int total() {
        return targets.size();
    }

    public Path workDirectory() {
        return work;
    }

    public void writePayloads(List<KnowledgePayload> payloads) throws IOException {
        active();
        long bytes = 0;
        for (var payload : payloads) {
            bytes += payload.bytes().length;
            if (bytes > 256L * 1024 * 1024) throw new IOException("Knowledge payload budget exceeded");
            var declaration = snapshot == null ? null : snapshot.categories().get(payload.category());
            if (declaration == null || !payload.hash().equals(declaration.sha256()))
                throw new IOException("Payload does not match snapshot declaration");
            if (payload.category().equals("entity_icons")) {
                atomic(work.resolve("entity-compatibility.json"), EntityDiagnosticReport.compatibility(payload.bytes(), environment, exporterVersion));
                atomic(work.resolve("entity-report.txt"), EntityDiagnosticReport.text(payload.bytes()).getBytes(StandardCharsets.UTF_8));
                var images = EntityImageFiles.decode(payload.bytes(), payload::image);
                var expected = new HashSet<Path>();
                for (String path : images.keySet()) expected.add(safe(work, path));
                Path entityRoot = safe(work, "entities");
                if (Files.isDirectory(entityRoot, LinkOption.NOFOLLOW_LINKS)) try (var files = Files.walk(entityRoot)) {
                    for (Path stale : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.getFileName().toString().endsWith(".png")).toList())
                        if (!expected.contains(stale)) {
                            ContentDigest.interrupted();
                            Files.delete(stale);
                        }
                }
                for (var image : images.entrySet()) {
                    ContentDigest.interrupted();
                    Path png = safe(work, image.getKey());
                    Files.createDirectories(png.getParent());
                    atomic(png, image.getValue());
                }
            }
            Path file = safe(work, declaration.file());
            Files.createDirectories(file.getParent());
            atomic(file, payload.bytes());
        }
    }

    public String write(Target target, byte[] png) throws IOException {
        active();
        check(target);
        if (png.length == 0 || png.length > 8 * 1024 * 1024 || imageBytes + png.length > 512L * 1024 * 1024)
            throw new IOException("Icon budget exceeded");
        String result = IconPng.status(png, size);
        Path file = safe(work, path(target));
        Files.createDirectories(file.getParent());
        atomic(file, png);
        var icon = new Icon(target.itemId(), target.variant(), result, path(target), ContentDigest.sha256(png), null, target.state());
        icons.put(target.key(), icon);
        imageBytes += png.length;
        record(icon);
        return result;
    }

    public void failure(Target target, String error) throws IOException {
        active();
        check(target);
        String reason = Objects.toString(error, "Unknown error");
        if (reason.isBlank()) reason = "Unknown error";
        var icon = new Icon(target.itemId(), target.variant(), "FAILED", null, null, reason.substring(0, Math.min(2000, reason.length())), target.state());
        icons.put(target.key(), icon);
        record(icon);
    }

    public Path publish() throws IOException {
        return publish(() -> false);
    }

    public Path publish(java.util.function.BooleanSupplier cancelled) throws IOException {
        active();
        if (icons.size() != targets.size()) throw new IOException("Export plan is incomplete");
        if (snapshot != null) for (var category : snapshot.categories().entrySet()) {
            if (category.getKey().equals("item_icons") || category.getValue().file() == null) continue;
            Path file = safe(work, category.getValue().file());
            if (!ContentDigest.file(file).equals(category.getValue().sha256()))
                throw new IOException("Knowledge payload changed: " + category.getKey());
            if (category.getKey().equals("entity_icons"))
                for (var image : EntityImageFiles.decode(Files.readAllBytes(file), path -> readBytes(safe(work, path), 2 * 1024 * 1024)).entrySet()) {
                    Path png = safe(work, image.getKey());
                    if (!ContentDigest.file(png).equals(ContentDigest.sha256(image.getValue())))
                        throw new IOException("Entity PNG changed before publication: " + image.getKey());
                }
        }
        int schema = targets.values().stream().anyMatch(t -> t.state() != null) ? 2 : 1;
        var manifest = new ExportManifest("mkb-export", schema, "GAME_GUI", Instant.now().toString(), new Exporter("mkb_export", exporterVersion), environment, new Rendering("GUI", size, size, "transparent"), List.copyOf(icons.values()));
        byte[] bytes = JSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16 * 1024 * 1024) throw new IOException("Manifest budget exceeded");
        atomic(work.resolve("manifest.json"), bytes);
        if (snapshot != null)
            atomic(work.resolve("snapshot.json"), JSON.toJson(snapshot.withIcons(List.copyOf(icons.values()), bytes)).getBytes(StandardCharsets.UTF_8));
        Path destination = parent.resolve("MKB-Export");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            destination = parent.resolve("MKB-Export-" + UUID.randomUUID());
        if (cancelled.getAsBoolean())
            throw new java.io.InterruptedIOException("Snapshot invalidated before publication");
        Files.move(work, destination);
        published = true;
        closed = true;
        release();
        Files.deleteIfExists(lockPath);
        Files.deleteIfExists(destination.resolve("checkpoint.json"));
        Files.deleteIfExists(destination.resolve("journal.jsonl"));
        Files.deleteIfExists(destination.resolve("session.lock"));
        return destination;
    }

    private void check(Target target) throws IOException {
        if (!targets.containsKey(target.key()) || !same(targets.get(target.key()), target) || icons.containsKey(target.key()) && complete(icons.get(target.key())))
            throw new IOException("Unknown or already complete target");
    }

    private byte[] validated(Path root, Icon icon, Target target) throws IOException {
        if (!target.variant().equals(icon.variant()) || !target.variant().equals(IconState.variant(icon.state())) || !path(target).equals(icon.file()) || icon.sha256() == null || !icon.sha256().matches("[0-9a-f]{64}"))
            throw new IOException("Invalid icon mapping");
        byte[] bytes = readBytes(safe(root, icon.file()), 8 * 1024 * 1024);
        if (!ContentDigest.sha256(bytes).equals(icon.sha256())) throw new IOException("Icon hash mismatch");
        if (!IconPng.status(bytes, size).equals(icon.status()))
            throw new IOException("PNG visibility does not match manifest status");
        return bytes;
    }

    private static String path(Target target) {
        return "icons/" + (target.state() == null ? "default/" : "state/" + target.variant().substring(6) + "/") + target.itemId().replace(':', '/') + ".png";
    }

    private static boolean same(Object a, Object b) {
        return IconState.canonical(JSON.toJsonTree(a)).equals(IconState.canonical(JSON.toJsonTree(b)));
    }

    private static Path child(Path parent, String name, boolean checkpoint) throws IOException {
        if (name == null || !(checkpoint ? name.matches("\\.mkb-export-[a-zA-Z0-9_-]+") : name.matches("MKB-Export(?:-[a-zA-Z0-9_-]+)?")))
            throw new IOException("Expected an export directory name under this game instance");
        Path result = safe(parent, name);
        if (!Files.isDirectory(result, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Export directory missing");
        return result;
    }

    private static Path safe(Path root, String relative) throws IOException {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root) || Path.of(relative).isAbsolute()) throw new IOException("Path escapes export root");
        for (Path current = path; current != null && current.startsWith(root); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links are not allowed");
        return path;
    }

    private static String read(Path path, int max) throws IOException {
        return new String(readBytes(path, max), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(Path path, int max) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > max)
            throw new IOException("Invalid file or byte budget");
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(max + 1);
            if (bytes.length > max) throw new IOException("Byte budget exceeded");
            return bytes;
        }
    }

    private void checkpoint() throws IOException {
        byte[] bytes = JSON.toJson(new Checkpoint(1, environment, size, exporterVersion, List.copyOf(targets.values()), List.copyOf(icons.values()), snapshot)).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 16 * 1024 * 1024) throw new IOException("Checkpoint budget exceeded");
        atomic(work.resolve("checkpoint.json"), bytes);
        Files.write(safe(work, "journal.jsonl"), new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        journalEntries = 0;
    }

    private void record(Icon icon) throws IOException {
        // 每项仅追加日志，定期压实，避免全量导出反复重写整个清单造成平方级写入量。
        byte[] bytes = (COMPACT.toJson(icon) + "\n").getBytes(StandardCharsets.UTF_8);
        try (var channel = FileChannel.open(safe(work, "journal.jsonl"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        if (++journalEntries >= 64) checkpoint();
    }

    private static void atomic(Path path, byte[] bytes) throws IOException {
        Path tmp = Files.createTempFile(path.getParent(), "write-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void active() throws IOException {
        ContentDigest.interrupted();
        if (closed || published) throw new IOException("Export job closed");
    }

    private void release() throws IOException {
        if (lock != null) {
            lock.release();
            lock = null;
        }
        if (lockChannel != null) {
            lockChannel.close();
            lockChannel = null;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        release();
    }
}
