package yagen.waitmydawn.mkb_export.api;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** 服务端 capture/variants 只读源对象；客户端 prepare 仅接收未加入世界的副本。 */
public interface EntityAppearanceAdapter {
    String id();
    String version();
    boolean supports(String registryId);
    default boolean supportsPart(String registryId){return false;}
    default Entity rootOf(Entity hit)throws Exception{return hit;}
    default boolean rootIsContainer(Entity source){return false;}
    record Part(String key,String parent,Entity entity,String policy) {}
    record Capture(JsonObject extra,List<Part> parts,boolean complete,String reason) {}
    record Variant(String label,net.minecraft.nbt.CompoundTag data) {}
    Capture capture(Entity source,boolean observed)throws Exception;
    default JsonObject captureClient(Entity visible)throws Exception{return new JsonObject();}
    default void prepare(Entity copy,JsonObject extra,Map<String,Entity> nodes)throws Exception {}
    default List<Variant> variants(Entity initialized)throws Exception{return List.of();}
}
