package yagen.waitmydawn.mkb_export.api;
import net.minecraft.world.entity.Entity;
import java.util.List;

/** 轴域发现只读源对象；apply只作用于未加入世界的副本，不得tick或修改世界。 */
public interface EntityVariantProvider {
    record Value(String id,String label) {}
    record Axis(String id,List<Value> values) {}
    String id();
    String version();
    boolean supports(Entity initialized);
    List<Axis> axes(Entity initialized)throws Exception;
    void apply(Entity copy,String axis,String value)throws Exception;
}
