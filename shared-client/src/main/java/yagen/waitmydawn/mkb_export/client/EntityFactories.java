package yagen.waitmydawn.mkb_export.client;
import net.minecraft.world.entity.*;
import net.minecraft.core.registries.BuiltInRegistries;

/** 仅放行已知构造器别名；请求注册名与实际类型仍分别保留。 */
final class EntityFactories {
    static boolean matches(EntityType<?> requested,Entity entity){if(entity.getType()==requested)return true;String id=BuiltInRegistries.ENTITY_TYPE.getKey(requested).toString(),actual=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),name=entity.getClass().getName();return id.equals("irons_spellbooks:catacombs_zombie")&&actual.equals("minecraft:zombie")&&name.endsWith(".CatacombsZombie")||id.equals("irons_spellbooks:magehunter_vindicator")&&actual.equals("minecraft:vindicator")&&name.endsWith(".MagehunterVindicator");}
    private EntityFactories(){}
}
