package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityFileLayoutTest {
    JsonObject row(String item) {
        var state = new JsonObject();
        state.addProperty("origin", "SPAWN_INITIALIZED");
        var traits = new JsonObject();
        traits.addProperty("age", "adult");
        var equipment = new JsonObject();
        equipment.addProperty("head", item);
        traits.add("equipment", equipment);
        state.add("traits", traits);
        var row = new JsonObject();
        row.addProperty("id", "demo:mage");
        row.add("state", state);
        row.addProperty("stateKey", EntityAppearance.key(state));
        row.addProperty("file", EntityAppearance.path("demo:mage", state));
        return row;
    }

    @Test
    void numberingIsStableAndDefaultFilesAreReserved() {
        var a = row("demo:a");
        var b = row("demo:b");
        var rows = new JsonArray();
        rows.add(a);
        rows.add(b);
        var first = EntityFileLayout.assign(rows);
        var reversed = new JsonArray();
        reversed.add(b);
        reversed.add(a);
        assertEquals(first, EntityFileLayout.assign(reversed));
        assertEquals(java.util.Set.of("entities/demo/mage_spawn_adult_equipped.png", "entities/demo/mage_spawn_adult_equipped_2.png"), new java.util.HashSet<>(first.values()));
        var original = new JsonObject();
        original.addProperty("id", "demo:mage_spawn_adult_equipped");
        original.addProperty("file", "entities/demo/mage_spawn_adult_equipped.png");
        rows.add(original);
        var paths = EntityFileLayout.assign(rows);
        assertEquals("entities/demo/mage_spawn_adult_equipped.png", paths.get("demo:mage_spawn_adult_equipped|default"));
        assertTrue(paths.values().contains("entities/demo/mage_spawn_adult_equipped_3.png"));
    }
}
