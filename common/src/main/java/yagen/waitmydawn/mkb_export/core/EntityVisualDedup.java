package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.io.*;
import java.util.*;
import java.security.*;
import javax.imageio.ImageIO;

/**
 * 状态身份保持独立，仅将同一注册类型的精确像素复用为一个资产。
 */
public final class EntityVisualDedup {
    private EntityVisualDedup() {
    }

    public static String pixelHash(byte[] png) throws IOException {
        var image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null || image.getWidth() > 512 || image.getHeight() > 512)
            throw new IOException("Invalid entity PNG");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, image.getWidth());
            update(digest, image.getHeight());
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    update(digest, (pixel >>> 24) == 0 ? 0 : pixel);
                }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void update(MessageDigest digest, int value) {
        for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (value >>> shift));
    }

    public static void apply(JsonArray records) throws IOException {
        var sorted = new ArrayList<JsonObject>();
        for (var value : records) {
            var row = value.getAsJsonObject();
            if (!row.has("error")) sorted.add(row);
        }
        sorted.sort(Comparator.comparing((JsonObject row) -> !row.get("stateKey").getAsString().equals("default")).thenComparing(row -> row.get("stateKey").getAsString()));
        var assets = new HashMap<String, JsonObject>();
        for (var row : sorted) {
            byte[] png = Base64.getDecoder().decode(row.get("pngBase64").getAsString());
            String pixels = pixelHash(png);
            row.addProperty("pixelSha256", pixels);
            String identity = row.get("id").getAsString() + "|" + pixels;
            var owner = assets.putIfAbsent(identity, row);
            if (owner != null) {
                row.addProperty("imageRef", owner.get("stateKey").getAsString());
                row.remove("pngBase64");
                row.add("sha256", owner.get("sha256").deepCopy());
            }
        }
    }

    public static JsonObject resolve(JsonObject row, Map<String, JsonObject> rows) throws IOException {
        if (!row.has("imageRef")) return row;
        String ref = row.get("imageRef").getAsString();
        var owner = rows.get(row.get("id").getAsString() + "|" + ref);
        if (owner == null || owner == row || owner.has("error") || owner.has("imageRef") || row.has("pngBase64"))
            throw new IOException("Invalid/cyclic entity image reference");
        for (String field : List.of("sha256", "pixelSha256", "width", "height", "status", "file"))
            if (!row.has(field) || !row.get(field).equals(owner.get(field)))
                throw new IOException("Entity image reference disagrees: " + field);
        var copy = row.deepCopy();
        if (owner.has("pngBase64")) copy.add("pngBase64", owner.get("pngBase64").deepCopy());
        return copy;
    }
}
