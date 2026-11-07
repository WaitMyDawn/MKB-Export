package yagen.waitmydawn.mkb_export.client;

import com.google.gson.JsonObject;
import yagen.waitmydawn.mkb_export.core.EntityAppearance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** 实际世界实例优先于离世界工厂样本；显式指定的代表状态具有最高优先级。 */
final class EntityAttributeSelection {
    static Map<String,JsonObject> capture(MinecraftServer server) {
        var selected=new TreeMap<String,JsonObject>();
        var entities=new ArrayList<LivingEntity>();
        for(var level:server.getAllLevels())for(var entity:level.getAllEntities())
            if(entity instanceof LivingEntity living&&living.isAlive())entities.add(living);
        entities.sort(Comparator.comparing(entity->entity.getUUID().toString()));
        for(var living:entities) {
            String id=BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
            if(selected.containsKey(id))continue;
            try {
                var row=Platform.entityAttributes(living);
                row.addProperty("context","SERVER_LOADED_INSTANCE");
                row.addProperty("sampleOrigin","OBSERVED_INSTANCE");
                row.addProperty("sampleUuid",living.getUUID().toString());
                row.addProperty("dimension",living.level().dimension().location().toString());
                row.addProperty("entityTickCount",living.tickCount);
                row.addProperty("sampleSelection","FIRST_LOADED_BY_UUID");
                row.addProperty("defaultsOnly",false);
                selected.put(id,row);
            }catch(Exception failure){selected.put(id,ServerKnowledgeCapture.failure(id,failure));}
        }
        for(var sample:EntitySamples.observed(server)) {
            if(!sample.representative()||sample.attributes()==null)continue;
            var row=sample.attributes().deepCopy();
            row.addProperty("id",sample.id());
            row.addProperty("context","SERVER_EXPLICIT_CAPTURE");
            row.addProperty("sampleOrigin","OBSERVED_INSTANCE");
            row.addProperty("sampleSelection","EXPLICIT_REPRESENTATIVE");
            row.addProperty("stateKey",EntityAppearance.key(sample.state()));
            row.add("state",sample.state().deepCopy());
            row.addProperty("defaultsOnly",false);
            selected.put(sample.id(),row);
        }
        return selected;
    }
    private EntityAttributeSelection() {}
}
