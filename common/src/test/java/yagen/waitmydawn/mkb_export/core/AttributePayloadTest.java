package yagen.waitmydawn.mkb_export.core;

import org.junit.jupiter.api.Test;
import com.google.gson.*;

import static org.junit.jupiter.api.Assertions.*;

class AttributePayloadTest {
    @Test
    void nonFiniteValuesFailBeforePayloadSerialization() {
        assertEquals(2, AttributeOperations.finite(2));
        assertThrows(IllegalArgumentException.class, () -> AttributeOperations.finite(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> AttributeOperations.finite(Double.POSITIVE_INFINITY));
    }

    @Test
    void operationsHaveTheSameMeaningAcrossLoaders() {
        assertEquals("ADD_VALUE", AttributeOperations.canonical("ADDITION"));
        assertEquals("ADD_MULTIPLIED_BASE", AttributeOperations.canonical("MULTIPLY_BASE"));
        assertEquals("ADD_MULTIPLIED_TOTAL", AttributeOperations.canonical("MULTIPLY_TOTAL"));
        assertThrows(IllegalArgumentException.class, () -> AttributeOperations.canonical("unknown"));
    }

    @Test
    void optionalCapabilitiesKeepVersionOneAndHashedPayloads() {
        var rows = new JsonArray();
        var row = new JsonObject();
        row.addProperty("id", "minecraft:stone");
        rows.add(row);
        var payload = KnowledgePayload.of("item_attributes", rows);
        var json = JsonParser.parseString(new String(payload.bytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(1, json.get("capabilityVersion").getAsInt());
        assertEquals("DEFAULT_ITEM_STACK_SLOT_MODIFIERS", json.get("scope").getAsString());
        assertEquals("COMPLETE", payload.declaration().status());
        assertEquals(payload.hash(), payload.declaration().sha256());
        var initial = SnapshotDescriptor.preparation("CLIENT_ONLY", "ALL", "1".repeat(64), null, null, 0);
        assertEquals("NOT_IMPLEMENTED", initial.categories().get("entity_attributes").status());
    }

    @Test
    void failedAttributeRecordIsCountedInsteadOfClaimingComplete() {
        var rows = new JsonArray();
        var row = new JsonObject();
        row.addProperty("id", "minecraft:player");
        row.addProperty("error", "No default instance");
        rows.add(row);
        var payload = KnowledgePayload.of("entity_attributes", rows);
        assertEquals(1, payload.failed());
        assertEquals("COMPLETE_WITH_ERRORS", payload.declaration().status());
    }
}
