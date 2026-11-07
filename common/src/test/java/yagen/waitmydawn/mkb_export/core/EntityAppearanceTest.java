package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityAppearanceTest {
    @Test
    void stateIdentityIsCanonicalAndVariantsHaveSeparatePaths() {
        assertEquals("default", EntityAppearance.key(EntityAppearance.defaults()));
        var a = JsonParser.parseString("{\"origin\":\"DECLARED_STATE\",\"traits\":{\"age\":\"baby\",\"color\":\"red\"}}").getAsJsonObject();
        var b = JsonParser.parseString("{\"traits\":{\"color\":\"red\",\"age\":\"baby\"},\"origin\":\"DECLARED_STATE\"}").getAsJsonObject();
        String key = EntityAppearance.key(a);
        assertEquals(key, EntityAppearance.key(b));
        assertNotEquals(EntityAppearance.path("demo:mob", "default"), EntityAppearance.path("demo:mob", key));
        b.getAsJsonObject("traits").addProperty("age", "adult");
        assertNotEquals(key, EntityAppearance.key(b));
        assertThrows(IllegalArgumentException.class, () -> EntityAppearance.path("demo:mob", "../bad"));
    }

    @Test
    void classificationMetadataPreservesExistingAppearanceIdentityAndPath() {
        var state = EntityAppearance.defaults();
        var record = new JsonObject();
        record.add("state", state);
        String key = EntityAppearance.key(state), path = EntityAppearance.path("minecraft:zombie", state);
        record.addProperty("isMonster", true);
        record.addProperty("isPart", false);
        assertEquals("default", key);
        assertEquals(key, EntityAppearance.key(record.getAsJsonObject("state")));
        assertEquals(path, EntityAppearance.path("minecraft:zombie", record.getAsJsonObject("state")));
    }

    @Test
    void classificationCannotAccidentallyCreateAdditionalAppearanceStates() {
        for (String field : java.util.List.of("isMonster", "isPart")) {
            var root = EntityAppearance.defaults();
            root.addProperty(field, true);
            assertThrows(IllegalArgumentException.class, () -> EntityAppearance.key(root));
            var traits = EntityAppearance.defaults();
            traits.getAsJsonObject("traits").addProperty(field, true);
            assertThrows(IllegalArgumentException.class, () -> EntityAppearance.key(traits));
        }
    }

    @Test
    void entityStatisticsKeepTransparencySeparateFromVisibleSuccess() {
        var rows = new JsonArray();
        var visible = new JsonObject();
        visible.addProperty("id", "demo:visible");
        visible.addProperty("status", "OK");
        rows.add(visible);
        var empty = new JsonObject();
        empty.addProperty("id", "demo:empty");
        empty.addProperty("status", "TRANSPARENT");
        rows.add(empty);
        var failed = new JsonObject();
        failed.addProperty("id", "demo:bad");
        failed.addProperty("error", "renderer failed");
        rows.add(failed);
        var payload = KnowledgePayload.of("entity_icons", rows);
        assertEquals(1, payload.succeeded());
        assertEquals(1, payload.transparent());
        assertEquals(1, payload.failed());
        assertEquals(3, payload.declaration().total());
    }

    @Test
    void unicodeAndNumericStateHaveTheSharedDesktopIdentity() {
        var state = JsonParser.parseString("{\"origin\":\"DECLARED_STATE\",\"traits\":{\"age\":\"adult\",\"color\":\"white\",\"variant\":\"雪地\",\"scale\":1.00}}").getAsJsonObject();
        assertEquals("4ce2de9e4a237f02a6c338605c145f4d4bced77252da049dea2ba8c9e117515b", EntityAppearance.key(state));
    }

    @Test
    void readableNamesKeepFullIdentityAndUseTheSharedDesktopLayout() {
        var state = JsonParser.parseString("{\"origin\":\"DECLARED_STATE\",\"traits\":{\"age\":\"adult\",\"color\":\"white\",\"variant\":\"雪地\",\"scale\":1}}").getAsJsonObject();
        assertEquals("entities/demo/mob/states/declared_adult_white_4ce2de9e4a237f02.png", EntityAppearance.path("demo:mob", state));
        assertEquals(64, EntityAppearance.key(state).length());
        assertEquals("entities/demo/mob.png", EntityAppearance.path("demo:mob", EntityAppearance.defaults()));
    }
}
