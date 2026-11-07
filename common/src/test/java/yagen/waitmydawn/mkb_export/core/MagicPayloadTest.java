package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MagicPayloadTest {
    @Test
    void registeredMagicCategoriesAreOptionalAndVersioned() {
        for (String category : java.util.List.of("potions", "effects", "enchantments")) {
            var initial = SnapshotDescriptor.preparation("CLIENT_ONLY", "ALL", "1".repeat(64), null, null, 0);
            assertEquals("NOT_IMPLEMENTED", initial.categories().get(category).status());
            var rows = new JsonArray();
            var row = new JsonObject();
            row.addProperty("id", "example:broken");
            row.addProperty("error", "Unavailable definition");
            rows.add(row);
            var payload = KnowledgePayload.of(category, rows);
            var json = JsonParser.parseString(new String(payload.bytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1, json.get("capabilityVersion").getAsInt());
            assertEquals("REGISTRY_METADATA_WITH_EXECUTABLE_LIMITS", json.get("semanticCoverage").getAsString());
            assertEquals("COMPLETE_WITH_ERRORS", payload.declaration().status());
            assertEquals(1, payload.failed());
        }
    }
}
