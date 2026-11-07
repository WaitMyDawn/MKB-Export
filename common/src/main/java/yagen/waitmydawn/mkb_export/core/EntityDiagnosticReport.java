package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

/**
 * 报告由已校验载荷派生，不包含图像编码和完整实体数据。
 */
public final class EntityDiagnosticReport {
    private EntityDiagnosticReport() {
    }

    public static String text(byte[] payload) {
        var root = JsonParser.parseString(new String(payload, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var out = new StringBuilder("MKB entity diagnostics\n");
        var counts = new TreeMap<String, Integer>();
        for (var e : root.getAsJsonArray("records")) {
            var r = e.getAsJsonObject();
            String status = r.has("error") ? "FAILED" : r.has("visibilityAssessment") ? r.get("visibilityAssessment").getAsString() : r.get("status").getAsString();
            counts.merge(status, 1, Integer::sum);
        }
        long unique = root.getAsJsonArray("records").asList().stream().filter(e -> !e.getAsJsonObject().has("error") && !e.getAsJsonObject().has("imageRef")).count();
        out.append("Image assets: ").append(unique).append("; state records: ").append(root.getAsJsonArray("records").size()).append("\n");
        if (root.has("exclusions")) out.append("Excluded: ").append(root.get("exclusions")).append("\n");
        if (root.has("adapterWarnings"))
            out.append("Adapter warnings: ").append(root.get("adapterWarnings")).append("\n");
        for (var element : root.getAsJsonArray("records")) {
            var row = element.getAsJsonObject();
            String status = row.has("error") ? "FAILED" : row.has("visibilityAssessment") ? row.get("visibilityAssessment").getAsString() : row.get("status").getAsString();
            out.append(row.get("id").getAsString()).append(" | ").append(row.has("stateKey") ? row.get("stateKey").getAsString() : "default").append(" | ").append(status);
            if (row.has("error")) out.append(" | ").append(row.get("error").getAsString());
            if (row.has("composition")) {
                var c = row.getAsJsonObject("composition");
                out.append(" | composition=").append(c.get("status").getAsString()).append(" | ").append(c.get("reason").getAsString());
            }
            if (row.has("compositionRender")) out.append(" | part visibility=").append(row.get("compositionRender"));
            for (String field : List.of("stage", "renderBackend", "imageRef"))
                if (row.has(field)) out.append(" | ").append(field).append("=").append(row.get(field));
            if (row.has("framing")) out.append(" | ").append(row.get("framing").getAsString());
            out.append('\n');
            if (out.length() > 4 * 1024 * 1024) {
                out.append("Report truncated; inspect data/entity_icons.json\n");
                break;
            }
        }
        out.insert(0, "Summary: " + counts + "\n");
        return out.toString();
    }

    public static byte[] compatibility(byte[] payload, ExportManifest.Environment environment, String exporter) {
        var source = JsonParser.parseString(new String(payload, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var out = new JsonObject();
        out.addProperty("format", "mkb-entity-compatibility");
        out.addProperty("schemaVersion", 1);
        out.addProperty("exporterVersion", exporter);
        out.add("environment", new Gson().toJsonTree(environment));
        var records = new JsonArray();
        out.add("records", records);
        int budget = 0;
        boolean truncated = false;
        for (var element : source.getAsJsonArray("records")) {
            var input = element.getAsJsonObject();
            var row = new JsonObject();
            for (String key : List.of("id", "stateKey", "appearance", "status", "error", "visibilityAssessment", "framing", "composition", "compositionRender", "stage", "renderer", "entityClass", "renderBackend", "imageRef", "variantCoverage"))
                if (input.has(key)) row.add(key, input.get(key));
            budget += row.toString().length();
            if (budget > 4 * 1024 * 1024) {
                truncated = true;
                break;
            }
            records.add(row);
        }
        out.addProperty("truncated", truncated);
        if (source.has("adapterWarnings")) out.add("adapterWarnings", source.get("adapterWarnings"));
        return new GsonBuilder().setPrettyPrinting().create().toJson(out).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
