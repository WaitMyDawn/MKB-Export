package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EntityDiagnosticReportTest {
    @Test
    void reportsUnexpectedEmptyAndFailureWithoutEmbeddingImagePayload() {
        var rows = new JsonArray();
        var empty = new JsonObject();
        empty.addProperty("id", "demo:spider");
        empty.addProperty("status", "TRANSPARENT");
        empty.addProperty("visibilityAssessment", "UNEXPECTED_EMPTY");
        empty.addProperty("pngBase64", "IMAGE_PAYLOAD_NOT_FOR_REPORT");
        rows.add(empty);
        var failure = new JsonObject();
        failure.addProperty("id", "demo:broken");
        failure.addProperty("error", "Missing renderer state");
        rows.add(failure);
        var payload = KnowledgePayload.of("entity_icons", rows);
        var text = EntityDiagnosticReport.text(payload.bytes());
        assertTrue(text.contains("UNEXPECTED_EMPTY"));
        assertTrue(text.contains("Missing renderer state"));
        assertFalse(text.contains("IMAGE_PAYLOAD"));
        var bytes = EntityDiagnosticReport.compatibility(payload.bytes(), new ExportManifest.Environment("1.21.1", "neoforge", "21.1.265", List.of(), List.of()), "0.9.0");
        var out = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("0.9.0", out.get("exporterVersion").getAsString());
        assertEquals(2, out.getAsJsonArray("records").size());
        assertFalse(out.toString().contains("IMAGE_PAYLOAD"));
    }
}
