package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayoutContractTest {
    private JsonObject layout() {
        return JsonParser.parseString("{\"id\":\"example:machine\",\"status\":\"DECLARED\",\"controller\":\"minecraft:furnace\",\"size\":[1,1,1],\"origin\":[0,0,0],\"cells\":[{\"pos\":[0,0,0],\"block\":\"minecraft:furnace\"}],\"conditions\":[],\"steps\":[]}").getAsJsonObject();
    }

    @Test
    void boundsAndDuplicateCellsFailBeforePublishing() {
        var valid = layout();
        assertDoesNotThrow(() -> LayoutContract.validate("multiblocks", valid));
        var duplicate = valid.deepCopy();
        duplicate.getAsJsonArray("cells").add(duplicate.getAsJsonArray("cells").get(0));
        assertThrows(IllegalArgumentException.class, () -> LayoutContract.validate("multiblocks", duplicate));
        var outside = valid.deepCopy();
        outside.getAsJsonArray("cells").get(0).getAsJsonObject().getAsJsonArray("pos").set(0, new JsonPrimitive(3));
        assertThrows(IllegalArgumentException.class, () -> LayoutContract.validate("multiblocks", outside));
    }

    @Test
    void unsupportedAndMetadataOnlyAreDeclaredPartial() {
        var rows = new JsonArray();
        var row = new JsonObject();
        row.addProperty("id", "example:large");
        row.addProperty("geometryStatus", "METADATA_ONLY");
        rows.add(row);
        var payload = KnowledgePayload.of("structure_templates", rows);
        assertEquals("COMPLETE_WITH_ERRORS", payload.declaration().status());
        assertEquals(0, payload.failed());
        var raw = JsonParser.parseString(new String(payload.bytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, raw.get("capabilityVersion").getAsInt());
        assertEquals(1, raw.get("incompleteRecords").getAsInt());
    }

    @Test
    void unknownAdapterCannotClaimACompleteLayout() {
        var rows = new JsonArray();
        var row = layout();
        row.addProperty("status", "UNSUPPORTED");
        row.addProperty("reason", "Code version not reviewed");
        rows.add(row);
        assertDoesNotThrow(() -> LayoutContract.validate("multiblocks", row));
        assertEquals("COMPLETE_WITH_ERRORS", KnowledgePayload.of("multiblocks", rows).declaration().status());
        assertEquals("NOT_IMPLEMENTED", SnapshotDescriptor.preparation("CLIENT_ONLY", "ALL", "1".repeat(64), null, null, 0).categories().get("multiblocks").status());
    }
}
