package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 旧载荷解码内嵌图片，新载荷从受限读取器获取独立 PNG，并执行相同的身份和像素校验。
 */
final class EntityImageFiles {
    @FunctionalInterface
    interface Reader {
        byte[] read(String path) throws IOException;
    }

    static Map<String, byte[]> decode(byte[] payload) throws IOException {
        return decode(payload, null);
    }

    static Map<String, byte[]> decode(byte[] payload, Reader reader) throws IOException {
        var result = new LinkedHashMap<String, byte[]>();
        long total = 0;
        try {
            var envelope = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean external = envelope.has("imageStorage");
            if (external && !envelope.get("imageStorage").getAsString().equals("EXTERNAL_PNG"))
                throw new IOException("Unknown entity image storage");
            var records = envelope.getAsJsonArray("records");
            var flat = EntityFileLayout.assign(records);
            var rows = new HashMap<String, JsonObject>();
            for (var value : records) {
                var r = value.getAsJsonObject();
                if (!r.has("error"))
                    rows.put(r.get("id").getAsString() + "|" + (r.has("stateKey") ? r.get("stateKey").getAsString() : r.has("state") ? EntityAppearance.key(r.getAsJsonObject("state")) : "default"), r);
            }
            for (var element : records) {
                var row = element.getAsJsonObject();
                if (row.has("error") || !row.has("file")) continue;
                String id = new ExportJob.Target(row.get("id").getAsString(), null).itemId();
                String key = row.has("state") ? EntityAppearance.key(row.getAsJsonObject("state")) : "default";
                if (row.has("stateKey") && !key.equals(row.get("stateKey").getAsString()))
                    throw new IOException("Appearance identity mismatch");
                String path = EntityAppearance.path(id, key);
                String supplied = row.get("file").getAsString();
                if (!supplied.equals(flat.get(id + "|" + key)) && !path.equals(supplied) && !(row.has("state") && EntityAppearance.path(id, row.getAsJsonObject("state")).equals(supplied)))
                    throw new IOException("Invalid entity PNG path");
                path = supplied;
                boolean alias = row.has("imageRef");
                row = EntityVisualDedup.resolve(row, rows);
                byte[] bytes;
                if (external) {
                    if (row.has("pngBase64") || reader == null)
                        throw new IOException("External PNG payload requires independent image files");
                    bytes = result.get(path);
                    if (bytes == null) bytes = reader.read(path);
                    if (bytes == null) throw new IOException("Missing entity PNG: " + path);
                } else {
                    String encoded = row.get("pngBase64").getAsString();
                    if (encoded.length() > 3 * 1024 * 1024) throw new IOException("Entity PNG exceeds budget");
                    bytes = Base64.getDecoder().decode(encoded);
                }
                if (!alias) total += bytes.length;
                if (bytes.length > 2 * 1024 * 1024 || total > 64L * 1024 * 1024)
                    throw new IOException("Entity PNG budget exceeded");
                int size = row.has("width") ? row.get("width").getAsInt() : 128;
                if (!java.util.Set.of(128, 256, 512).contains(size) || row.has("height") && row.get("height").getAsInt() != size)
                    throw new IOException("Invalid entity dimensions");
                if (!ContentDigest.sha256(bytes).equals(row.get("sha256").getAsString()) || !IconPng.status(bytes, size).equals(row.get("status").getAsString()))
                    throw new IOException("Entity PNG hash or visibility mismatch");
                if (alias) continue;
                if (result.putIfAbsent(path, bytes) != null)
                    throw new IOException("Duplicate entity PNG path: " + path);
            }
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException failure) {
            throw new IOException("Invalid entity image record", failure);
        }
        return result;
    }
}
