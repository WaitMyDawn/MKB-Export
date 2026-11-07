package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.world.entity.ai.attributes.*;
import yagen.waitmydawn.mkb_export.core.AttributeOperations;

/**
 * 在独立容器中枚举修饰符，避免懒实例化改变被观察实体的持久化属性集合。
 */
final class EntityAttributeReader {
    private EntityAttributeReader() {
    }

    static JsonArray capture(AttributeMap source) throws ReflectiveOperationException {
        var keys = new java.util.LinkedHashMap<String, net.minecraft.core.Holder<Attribute>>();
        for (var attribute : net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE)
            keys.put(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString(), net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
        return capture(source, keys);
    }

    static JsonArray capture(AttributeMap source, java.util.Map<String, net.minecraft.core.Holder<Attribute>> keys) throws ReflectiveOperationException {
        var supplier = (AttributeSupplier) RuleFields.get(source, "supplier");
        var detached = new AttributeMap(supplier);
        detached.assignAllValues(source);
        var rows = new JsonArray();
        for (var entry : keys.entrySet()) {
            var key = entry.getValue();
            if (!source.hasAttribute(key)) continue;
            var instance = detached.getInstance(key);
            if (instance == null) throw new IllegalStateException("Attribute instance missing: " + key);
            double base = AttributeOperations.finite(source.getBaseValue(key));
            double effective = AttributeOperations.finite(source.getValue(key));
            // 供应器经过加载器及模组事件修改；构建器源码不是最终运行值的替代来源。
            if (Double.compare(base, instance.getBaseValue()) != 0 || Double.compare(effective, instance.getValue()) != 0)
                throw new IllegalStateException("Detached attribute differs from server API: " + key);
            var row = new JsonObject();
            row.addProperty("attribute", entry.getKey());
            row.addProperty("base", base);
            row.addProperty("effective", effective);
            row.addProperty("registryDefault", AttributeOperations.finite(key.value().getDefaultValue()));
            if (supplier.hasAttribute(key))
                row.addProperty("entityTypeBase", AttributeOperations.finite(supplier.getBaseValue(key)));
            var modifiers = new JsonArray();
            for (var modifier : instance.getModifiers()) {
                var value = new JsonObject();
                value.addProperty("id", modifier.id().toString());
                value.addProperty("amount", AttributeOperations.finite(modifier.amount()));
                value.addProperty("operation", AttributeOperations.canonical(modifier.operation().toString()));
                modifiers.add(value);
            }
            row.add("modifiers", modifiers);
            rows.add(row);
        }
        return rows;
    }
}
