package yagen.waitmydawn.mkb_export.core;

import org.junit.jupiter.api.Test;
import com.google.gson.*;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingCoverageTest {
    @Test
    void reportsLoadedUnsupportedAndFailedRecipesSeparately() {
        var rows = JsonParser.parseString("[{\"id\":\"example:a\",\"recipeType\":\"example:machine\",\"serializer\":\"example:custom\",\"normalizationStatus\":\"UNSUPPORTED\"},{\"id\":\"example:b\",\"error\":\"broken\"}]").getAsJsonArray();
        var result = ProcessingCoverage.describe(rows);
        assertEquals(2, result.get("total").getAsInt());
        assertEquals(1, result.get("failedRecords").getAsInt());
        assertEquals(1, result.getAsJsonObject("serializers").getAsJsonObject("example:machine / example:custom").get("UNSUPPORTED").getAsInt());
    }
}
