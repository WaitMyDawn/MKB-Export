package yagen.waitmydawn.mkb_export.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradePoolSemanticsTest {
    @Test
    void singleAndDuplicateRulesUseWithoutReplacementProbability() {
        assertEquals(.2, TradePoolSemantics.inclusion(10, 1, 2), 1e-12);
        assertEquals(17.0 / 45, TradePoolSemantics.inclusion(10, 2, 2), 1e-12);
        assertEquals(1, TradePoolSemantics.inclusion(2, 1, 5));
        assertEquals(0, TradePoolSemantics.inclusion(0, 0, 5));
    }

    @Test
    void unresolvedFactoriesAreNotAdvertisedAsComplete() {
        var rows = com.google.gson.JsonParser.parseString("[{\"id\":\"example:pool\",\"entries\":[{\"status\":\"UNSUPPORTED\"}]}]").getAsJsonArray();
        var payload = KnowledgePayload.of("trades", rows);
        assertEquals("COMPLETE_WITH_ERRORS", payload.declaration().status());
        assertEquals(0, payload.failed());
        assertTrue(new String(payload.bytes(), java.nio.charset.StandardCharsets.UTF_8).contains("unsupportedEntries"));
    }

    @Test
    void impossibleCandidateCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TradePoolSemantics.inclusion(2, 3, 1));
    }
}
