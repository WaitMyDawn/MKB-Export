package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RuleIconTargetsTest {
    @Test
    void exactPotionStatesAreDeduplicatedAndUncertainAppearanceIsNotInvented() {
        var rows = JsonParser.parseString("[{\"input\":{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:strength\"}}},\"output\":{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:strong_strength\"}}}},{\"output\":{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:strength\"}}}},{\"output\":{\"id\":\"minecraft:potion\",\"dynamicState\":true,\"components\":{\"minecraft:potion_contents\":{\"potion\":\"example:unknown\"}}}}]").getAsJsonArray();
        var plan = RuleIconTargets.extend(List.of(new ExportJob.Target("minecraft:potion", null)), List.of(KnowledgePayload.of("brewing", rows)));
        assertEquals(3, plan.size());
        assertEquals(2, plan.stream().filter(target -> target.state() != null).count());
        assertTrue(plan.stream().noneMatch(target -> target.state() != null && target.state().toString().contains("unknown")));
    }
}
