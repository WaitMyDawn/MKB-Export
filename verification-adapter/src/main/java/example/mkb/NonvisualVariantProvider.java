package example.mkb;

import yagen.waitmydawn.mkb_export.api.EntityVariantProvider;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * 非视觉标签用于验证外部轴发现与精确图像复用，不声明原生品种。
 */
public final class NonvisualVariantProvider implements EntityVariantProvider {
    public String id() {
        return "mkb_example:nonvisual_marker";
    }

    public String version() {
        return "1";
    }

    public boolean supports(Entity source) {
        return source instanceof net.minecraft.world.entity.animal.Pig;
    }

    public List<Axis> axes(Entity source) {
        return List.of(new Axis("example_marker", List.of(new Value("a", "Example A"), new Value("b", "Example B"))));
    }

    public void apply(Entity copy, String axis, String value) {
        if (!axis.equals("example_marker") || !List.of("a", "b").contains(value))
            throw new IllegalArgumentException("Unknown example marker");
    }
}
