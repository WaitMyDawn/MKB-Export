package yagen.waitmydawn.mkb_export.client;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

/** 类型标记独立于外观状态；Monster 继承关系不等于当前攻击目标或自然生成分类。 */
final class EntityClassification {
    private EntityClassification() {}
    static void describe(JsonObject record,Entity entity){
        java.util.Objects.requireNonNull(entity);
        record.addProperty("isMonster",entity instanceof Monster);
        record.addProperty("isPart",Platform.isPart(entity));
    }
}
