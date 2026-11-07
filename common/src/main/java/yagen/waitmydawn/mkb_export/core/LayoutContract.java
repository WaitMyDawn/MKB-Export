package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;

import java.util.*;

/**
 * 适配器与模板边界校验在发布前执行，避免无效坐标污染整包导入。
 */
public final class LayoutContract {
    private LayoutContract() {
    }

    private static int integer(JsonElement value) {
        return value.getAsBigDecimal().intValueExact();
    }

    private static int[] triple(JsonElement value) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3)
            throw new IllegalArgumentException("Invalid layout vector");
        var a = value.getAsJsonArray();
        return new int[]{integer(a.get(0)), integer(a.get(1)), integer(a.get(2))};
    }

    private static void resource(String id) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid layout block id");
    }

    public static void validate(String category, JsonObject row) {
        resource(row.get("id").getAsString());
        boolean multi = category.equals("multiblocks");
        if (multi) {
            resource(row.get("controller").getAsString());
            String status = row.get("status").getAsString();
            if (status.equals("UNSUPPORTED")) {
                if (!row.has("reason")) throw new IllegalArgumentException("Missing unsupported reason");
                return;
            }
            if (!status.equals("DECLARED") || !row.has("conditions") || !row.get("conditions").isJsonArray() || !row.has("steps") || !row.get("steps").isJsonArray())
                throw new IllegalArgumentException("Invalid multiblock declaration");
        }
        int[] size = triple(row.get("size")), origin = multi ? triple(row.get("origin")) : new int[3];
        for (int i = 0; i < 3; i++)
            if (size[i] < 0 || size[i] > 4096 || origin[i] < 0 || origin[i] >= Math.max(1, size[i]))
                throw new IllegalArgumentException("Invalid layout bounds");
        if (!multi && row.get("geometryStatus").getAsString().equals("METADATA_ONLY")) return;
        var cells = row.getAsJsonArray(multi ? "cells" : "blocks");
        if (cells.size() > 16384) throw new IllegalArgumentException("Layout cell budget exceeded");
        var seen = new HashSet<String>();
        JsonArray palette = null, palettes = null;
        if (!multi) {
            palettes = row.getAsJsonArray("palettes");
            palette = palettes != null && !palettes.isEmpty() ? palettes.get(0).getAsJsonArray() : row.getAsJsonArray("palette");
            if (row.get("blockCount").getAsInt() != cells.size())
                throw new IllegalArgumentException("Template block count mismatch");
        }
        for (var element : cells) {
            var cell = element.getAsJsonObject();
            int[] pos = triple(cell.get("pos"));
            for (int i = 0; i < 3; i++)
                if ((long) pos[i] + origin[i] < 0 || (long) pos[i] + origin[i] >= size[i])
                    throw new IllegalArgumentException("Layout cell out of bounds");
            if (!seen.add(Arrays.toString(pos))) throw new IllegalArgumentException("Duplicate layout position");
            if (multi) resource(cell.get("block").getAsString());
            else {
                int state = integer(cell.get("state"));
                if (state < 0 || palette == null || state >= palette.size())
                    throw new IllegalArgumentException("Invalid template palette state");
                resource(palette.get(state).getAsJsonObject().get("Name").getAsString());
                if (palettes != null) for (var alternative : palettes) {
                    var options = alternative.getAsJsonArray();
                    if (state >= options.size()) throw new IllegalArgumentException("Incomplete palette alternative");
                    resource(options.get(state).getAsJsonObject().get("Name").getAsString());
                }
            }
        }
    }
}
