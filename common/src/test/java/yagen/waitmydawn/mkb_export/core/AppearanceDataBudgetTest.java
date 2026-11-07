package yagen.waitmydawn.mkb_export.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceDataBudgetTest {
    @Test
    void rejectsCyclesAndNonFiniteNumbersBeforeCopying() {
        var cycle = new JsonObject();
        cycle.add("self", cycle);
        assertThrows(IllegalArgumentException.class, () -> AppearanceDataBudget.validate(cycle));
        var bad = new JsonObject();
        bad.addProperty("normal", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> AppearanceDataBudget.validate(bad));
    }

    @Test
    void acceptsSharedFiniteDataButRejectsUnboundedText() {
        var child = new JsonObject();
        child.addProperty("x", 1);
        var root = new JsonObject();
        root.add("a", child);
        root.add("b", child);
        AppearanceDataBudget.validate(root);
        root.addProperty("huge", "x".repeat(16385));
        assertThrows(IllegalArgumentException.class, () -> AppearanceDataBudget.validate(root));
    }
}
