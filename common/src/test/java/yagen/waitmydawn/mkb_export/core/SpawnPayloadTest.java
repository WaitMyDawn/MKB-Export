package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpawnPayloadTest {
    @Test
    void codeIdentityExcludesJvmAddresses() {
        assertEquals("example.Rules$$Lambda", SpawnConfiguration.stableCodeName("example.Rules$$Lambda/0x00001234"));
        assertEquals("example.Rules$$Lambda", SpawnConfiguration.stableCodeName("example.Rules$$Lambda$112/0x987"));
        assertEquals("example.Rules$Nested", SpawnConfiguration.stableCodeName("example.Rules$Nested"));
    }

    @Test
    void unavailablePlacementIsPartialWithoutDroppingTheEntity() {
        var rows = new JsonArray();
        var row = new JsonObject();
        row.addProperty("id", "example:mob");
        var placement = new JsonObject();
        placement.addProperty("status", "UNAVAILABLE");
        row.add("placement", placement);
        rows.add(row);
        var payload = KnowledgePayload.of("spawn_rules", rows);
        assertEquals(0, payload.failed());
        assertEquals("COMPLETE_WITH_ERRORS", payload.declaration().status());
        var json = JsonParser.parseString(new String(payload.bytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, json.get("capabilityVersion").getAsInt());
        assertEquals("NOT_ENUMERATED", json.get("summoning").getAsString());
        assertEquals("NOT_ENUMERATED", json.get("dynamicEvents").getAsString());
        assertEquals("NOT_IMPLEMENTED", SnapshotDescriptor.preparation("CLIENT_ONLY", "ALL", "1".repeat(64), null, null, 0).categories().get("spawn_rules").status());
    }
}
