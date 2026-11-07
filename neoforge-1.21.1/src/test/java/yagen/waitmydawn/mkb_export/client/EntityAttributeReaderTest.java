package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.world.entity.ai.attributes.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class EntityAttributeReaderTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final net.minecraft.core.Holder<Attribute> HEALTH = net.minecraft.core.Holder.direct(new RangedAttribute("test", 1, -10000, 10000));
    private static final net.minecraft.core.Holder<Attribute> ATTACK = net.minecraft.core.Holder.direct(new RangedAttribute("test", 1, -10000, 10000));
    private static final net.minecraft.core.Holder<Attribute> ARMOR = net.minecraft.core.Holder.direct(new RangedAttribute("test", 1, -10000, 10000));
    private static final net.minecraft.core.Holder<Attribute> SPEED = net.minecraft.core.Holder.direct(new RangedAttribute("test", 1, -10000, 10000));
    private static final java.util.Map<String, net.minecraft.core.Holder<Attribute>> KEYS = java.util.Map.of(
            "test:max_health", HEALTH, "test:attack_damage", ATTACK, "test:armor", ARMOR, "test:movement_speed", SPEED);

    @Test
    void entityDefaultsAndTransientModifiersSurviveWithoutMutatingSource() throws Exception {
        var supplier = AttributeSupplier.builder().add(HEALTH, 1000)
                .add(ATTACK, 10).add(ARMOR, 15)
                .add(SPEED, .21).build();
        var source = new AttributeMap(supplier);
        var attack = source.getInstance(ATTACK);
        attack.setBaseValue(12);
        attack.addTransientModifier(new AttributeModifier(net.minecraft.resources.ResourceLocation.parse("mkb_export:test"), 2, AttributeModifier.Operation.ADD_VALUE));
        var before = new java.util.HashMap<>((java.util.Map<?, ?>) RuleFields.get(source, "attributes"));
        var rows = EntityAttributeReader.capture(source, KEYS);
        assertEquals(before, RuleFields.get(source, "attributes"));
        var damage = find(rows, "attack_damage");
        assertEquals(12, damage.get("base").getAsDouble());
        assertEquals(14, damage.get("effective").getAsDouble());
        assertEquals(10, damage.get("entityTypeBase").getAsDouble());
        assertEquals(1, damage.getAsJsonArray("modifiers").size());
        assertEquals(1000, find(rows, "max_health").get("base").getAsDouble());
        assertEquals(15, find(rows, "armor").get("effective").getAsDouble());
        assertEquals(.21, find(rows, "movement_speed").get("base").getAsDouble());
    }

    @Test
    void missingAttributesAreOmittedAndRegisteredDefaultsAreNotSubstituted() throws Exception {
        var source = new AttributeMap(AttributeSupplier.builder().add(ATTACK, 1.25).build());
        var rows = EntityAttributeReader.capture(source, KEYS);
        assertEquals(1, rows.size());
        assertEquals(1.25, find(rows, "attack_damage").get("base").getAsDouble());
        assertTrue(((java.util.Map<?, ?>) RuleFields.get(source, "attributes")).isEmpty());
    }

    @Test
    void registrationOverridesAreExportedInsteadOfBuilderDeclarations() throws Exception {
        var declared = AttributeSupplier.builder().add(ATTACK, 1.25).build();
        var merged = new AttributeSupplier.Builder(declared);
        merged.combine(AttributeSupplier.builder().add(ATTACK, 1));
        var rows = EntityAttributeReader.capture(new AttributeMap(merged.build()), KEYS);
        assertEquals(1, find(rows, "attack_damage").get("base").getAsDouble());
    }

    @Test
    void registeredVanillaAttributesMatchPublicApiWithoutLazySourceMutation() throws Exception {
        var source = new AttributeMap(AttributeSupplier.builder().add(Attributes.MAX_HEALTH, 1000)
                .add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.ARMOR, 15)
                .add(Attributes.MOVEMENT_SPEED, .21).build());
        var rows = EntityAttributeReader.capture(source);
        assertEquals(4, rows.size());
        assertEquals(10, find(rows, "attack_damage").get("base").getAsDouble());
        assertEquals(1000, find(rows, "max_health").get("effective").getAsDouble());
        assertEquals(15, find(rows, "armor").get("base").getAsDouble());
        assertEquals(.21, find(rows, "movement_speed").get("effective").getAsDouble());
        assertTrue(((java.util.Map<?, ?>) RuleFields.get(source, "attributes")).isEmpty());
    }

    @Test
    void nonFiniteValuesFailInsteadOfProducingValidLookingRecords() {
        var source = new AttributeMap(AttributeSupplier.builder().add(ATTACK, Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> EntityAttributeReader.capture(source, KEYS));
    }

    private static JsonObject find(JsonArray rows, String suffix) {
        for (var value : rows) {
            var row = value.getAsJsonObject();
            if (row.get("attribute").getAsString().endsWith(suffix)) return row;
        }
        throw new AssertionError("Missing attribute: " + suffix);
    }
}
