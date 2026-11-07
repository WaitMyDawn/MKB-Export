package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityVisualDedupTest {
    byte[] png(int color) throws Exception {
        var image = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(30, 40, color);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    JsonObject row(String id, String key, byte[] bytes) {
        var row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("stateKey", key);
        row.addProperty("file", "entities/minecraft/blaze.png");
        row.addProperty("width", 256);
        row.addProperty("height", 256);
        row.addProperty("status", "OK");
        row.addProperty("sha256", ContentDigest.sha256(bytes));
        row.addProperty("pngBase64", Base64.getEncoder().encodeToString(bytes));
        return row;
    }

    @Test
    void exactPixelsShareAssetsButKeepStateMetadata() throws Exception {
        var bytes = png(0xffffffff);
        var rows = new JsonArray();
        var state = row("minecraft:blaze", "a".repeat(64), bytes);
        state.addProperty("meaning", "adult");
        rows.add(state);
        rows.add(row("minecraft:blaze", "default", bytes));
        EntityVisualDedup.apply(rows);
        assertEquals("default", state.get("imageRef").getAsString());
        assertFalse(state.has("pngBase64"));
        assertEquals("adult", state.get("meaning").getAsString());
        assertEquals(2, rows.size());
    }

    @Test
    void changedPixelsAndDifferentEntitiesRemainDistinct() throws Exception {
        var rows = new JsonArray();
        rows.add(row("minecraft:blaze", "default", png(0xffffffff)));
        rows.add(row("minecraft:blaze", "b".repeat(64), png(0xfffffffe)));
        rows.add(row("minecraft:cow", "default", png(0xffffffff)));
        EntityVisualDedup.apply(rows);
        for (var r : rows) assertFalse(r.getAsJsonObject().has("imageRef"));
    }

    @Test
    void referencesCannotFormChainsOrDisagreeOnAssets() throws Exception {
        var a = row("minecraft:blaze", "default", png(0xffffffff));
        a.addProperty("pixelSha256", "a".repeat(64));
        var b = a.deepCopy();
        b.addProperty("stateKey", "b".repeat(64));
        b.addProperty("imageRef", "default");
        b.remove("pngBase64");
        var index = new HashMap<String, JsonObject>();
        index.put("minecraft:blaze|default", a);
        assertTrue(EntityVisualDedup.resolve(b, index).has("pngBase64"));
        a.addProperty("imageRef", b.get("stateKey").getAsString());
        assertThrows(java.io.IOException.class, () -> EntityVisualDedup.resolve(b, index));
    }
}
