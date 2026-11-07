package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 每次任务固定一份朝向配置，避免取景探测与最终渲染使用不同方向。
 */
public record EntityReversalConfig(Set<String> entities) {
    public EntityReversalConfig {
        entities = Set.copyOf(entities);
    }

    public boolean reversed(String id) {
        return entities.contains(id);
    }

    public static EntityReversalConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("mkb_export_reversal.json");
        try {
            Files.writeString(file, "[\n  \"minecraft:ender_dragon\",\n  \"alexscaves:underzealot\"\n]\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException existing) {/* 自定义名单保持不变；已知旧默认由下方一次性迁移处理。 */}
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 65536)
            throw new IOException("Invalid reversal config file: " + file);
        try {
            var value = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!value.isJsonArray() || value.getAsJsonArray().size() > 10000)
                throw new IllegalArgumentException("Expected an array of entity registry IDs");
            var ids = new TreeSet<String>();
            for (var element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Entity registry ID must be a string");
                String id = element.getAsString();
                new ExportJob.Target(id, null);
                ids.add(id);
            }
            Path stamp = directory.resolve(".mkb_export_reversal_defaults_v2");
            if (!Files.exists(stamp, LinkOption.NOFOLLOW_LINKS)) {
                if (ids.equals(Set.of("minecraft:ender_dragon"))) {
                    ids.add("alexscaves:underzealot");
                    Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(ids) + "\n", StandardCharsets.UTF_8);
                }
                try {
                    Files.writeString(stamp, "2\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                } catch (FileAlreadyExistsException concurrent) {
                }
            }
            return new EntityReversalConfig(ids);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid reversal config " + file + ": " + failure.getMessage(), failure);
        }
    }
}
