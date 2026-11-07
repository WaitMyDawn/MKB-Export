package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import java.util.*;
import yagen.waitmydawn.mkb_export.core.*;

/** 服务端线程准备离世界实例；客户端接收独立 NBT 与同步数据副本，不修改真实实体。 */
final class EntitySamples {
    record Sample(String id,CompoundTag data,List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> synced,JsonObject state,boolean representative,String error,EntityComposition.Snapshot composition,JsonObject attributes) {}
    private static final Map<MinecraftServer,LinkedHashMap<String,Sample>> OBSERVED=Collections.synchronizedMap(new WeakHashMap<>());
    private EntitySamples() {}
    static List<Sample> prepareAll(EntityType<?> type,MinecraftServer server,java.util.UUID playerId,boolean spawn,boolean ages){try(var scope=yagen.waitmydawn.mkb_export.core.EntitySamplingScope.enter()){return prepareAllDetached(type,server,playerId,spawn,ages);}}
    private static List<Sample> prepareAllDetached(EntityType<?> type,MinecraftServer server,java.util.UUID playerId,boolean spawn,boolean ages){
        requireServer(server);String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();var list=new ArrayList<Sample>();try{var player=server.getPlayerList().getPlayer(playerId);if(player==null)throw new IllegalStateException("Player unavailable");var source=type.create(player.serverLevel());if(source==null||!EntityFactories.matches(type,source))throw new IllegalArgumentException("Entity factory missing or returned another registry type");IceAndFireVariants.normalizeFactory(source);list.add(sample(source,"DEFAULT_INSTANCE",false,server));}catch(Exception failure){list.add(new Sample(id,null,List.of(),EntityAppearance.defaults(),false,describe(failure),null,null));}if(spawn)list.addAll(prepare(type,server,playerId,ages));return requested(id,list);
    }
    static List<Sample> prepare(EntityType<?> type,MinecraftServer server,java.util.UUID playerId,boolean ageVariants) {
        requireServer(server);String id=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        var player=server.getPlayerList().getPlayer(playerId);if(player==null)return List.of();
        var samples=new ArrayList<Sample>();
        try {
            var entity=type.create(player.serverLevel());if(!(entity instanceof Mob mob))return List.of();
            if(!EntityFactories.matches(type,entity))throw new IllegalArgumentException("Entity factory returned another registry type");
            entity.setPos(player.getX(),player.getY(),player.getZ());
            IceAndFireVariants.prepareDetached(entity);
            // 龙的成长外观沿用有限声明适配；其他 Mob 恢复原有出生赋装，入场仍由采样作用域拦截。
            if(!IceAndFireVariants.dragon(entity))Platform.initializeAppearanceSample(mob,player.serverLevel());
            mob.setBaby(false);samples.add(sample(mob,"SPAWN_INITIALIZED",true,server));
            if(ageVariants&&!IceAndFireVariants.dragon(mob))mob.setBaby(true);if(ageVariants&&!IceAndFireVariants.dragon(mob)&&mob.isBaby())samples.add(sample(mob,"SPAWN_INITIALIZED",false,server));
            mob.setBaby(false);if(mob.getType()==type)samples.addAll(EntityVariants.sample(mob,server));var adapter=AppearanceAdapters.find(id);if(adapter!=null){long start=System.nanoTime();var variants=AppearanceAdapters.invoke(()->adapter.variants(mob));if(variants.size()>Platform.maxLegacyVariants()||System.nanoTime()-start>AppearanceAdapters.policy().millis()*1000000L)throw new IllegalArgumentException("Adapter variant budget exceeded");for(var variant:variants){if(variant.label().isBlank()||variant.label().length()>80||variant.data().toString().length()>262144)throw new IllegalArgumentException("Invalid adapter variant");var copy=type.create(player.serverLevel());if(copy==null)throw new IllegalArgumentException("Variant factory missing");copy.load(variant.data().copy());var prepared=sample(copy,"DECLARED_STATE",false,server);prepared.state().getAsJsonObject("traits").addProperty("variant",variant.label());EntityAppearance.key(prepared.state());samples.add(prepared);}}

        }catch(Exception failure){var state=new JsonObject();state.addProperty("origin","SPAWN_INITIALIZED");state.add("traits",new JsonObject());samples.add(new Sample(id,null,List.of(),state,false,describe(failure),null,null));}
        return requested(id,samples);
    }
    private static List<Sample> requested(String id,List<Sample> samples){return samples.stream().map(s->new Sample(id,s.data(),s.synced(),s.state(),s.representative(),s.error(),s.composition(),s.attributes())).toList();}
    static Sample sample(Entity entity,String origin,boolean representative,MinecraftServer server) {
        requireServer(server);var data=sanitized(entity);
        var state=new JsonObject();state.addProperty("origin",origin);var traits=new JsonObject();
        if(entity instanceof LivingEntity living) {
            traits.addProperty("age",living.isBaby()?"baby":"adult");var equipment=new JsonObject();
            for(var slot:EquipmentSlot.values()){var stack=living.getItemBySlot(slot);if(!stack.isEmpty())equipment.add(slot.getName(),Platform.entityEquipment(stack,server));}
            traits.add("equipment",equipment);
        }
        IceAndFireVariants.describe(entity,traits);
        if(entity instanceof net.minecraft.world.entity.animal.Sheep sheep)traits.addProperty("color",sheep.getColor().getName());
        if(origin.equals("SPAWN_INITIALIZED")){boolean initialized=!IceAndFireVariants.dragon(entity);traits.addProperty("initialization",initialized?"LOADER_FINALIZE_SPAWN":"SAFE_DETACHED_SETUP");traits.addProperty("worldSpawnHooks",initialized);if(initialized)traits.addProperty("spawnReason","NATURAL");}
        traits.addProperty("appearanceHash",ContentDigest.sha256(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));state.add("traits",traits);
        var synced=entity.getEntityData().getNonDefaultValues();synced=synced==null?List.of():List.copyOf(synced);traits.addProperty("syncedHash",Platform.syncedFingerprint(synced,server));
        EntityComposition.Snapshot composition;try{composition=EntityComposition.capture(entity,origin.equals("OBSERVED_INSTANCE"));}catch(Exception failure){throw new IllegalStateException("Composition capture failed",failure);}traits.add("composition",EntityComposition.description(composition));
        if(origin.equals("DEFAULT_INSTANCE"))state=EntityAppearance.defaults();
        var attributes=attributeSnapshot(entity);if(attributes!=null){attributes.addProperty("sampleOrigin",origin);attributes.addProperty("defaultsOnly",!origin.equals("OBSERVED_INSTANCE"));}
        EntityAppearance.key(state);return new Sample(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),data.copy(),synced,state,representative,null,composition,attributes);
    }
    private static JsonObject attributeSnapshot(Entity entity){if(!(entity instanceof LivingEntity living))return null;try{var row=Platform.entityAttributes(living);row.addProperty("defaultsOnly",true);return row;}catch(Exception failure){return ServerKnowledgeCapture.failure(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),failure);}}
    static CompoundTag sanitized(Entity entity){var data=new CompoundTag();entity.saveWithoutId(data);
        // 空间、身份和瞬态战斗字段不参与外观身份，也不转移到渲染实例。
        for(String field:List.of("UUID","ParentUUID","ChildUUID","Pos","Motion","Rotation","Passengers","Leash","Brain","CustomName","CustomNameVisible","Health","HurtTime","HurtByTimestamp","DeathTime","Fire","Air","FallDistance","OnGround","PortalCooldown"))data.remove(field);
        if(data.toString().length()>262144)throw new IllegalArgumentException("Entity state exceeds 256 KiB");
        return data;
    }
    static int capture(MinecraftServer server,java.util.UUID playerId,java.util.UUID targetId,boolean representative,String label) {
        return capture(server,playerId,targetId,representative,label,new JsonObject());
    }
    static int capture(MinecraftServer server,java.util.UUID playerId,java.util.UUID targetId,boolean representative,String label,JsonObject clientExtra) {
        try(var scope=yagen.waitmydawn.mkb_export.core.EntitySamplingScope.enter()){return captureDetached(server,playerId,targetId,representative,label,clientExtra);}
    }
    private static int captureDetached(MinecraftServer server,java.util.UUID playerId,java.util.UUID targetId,boolean representative,String label,JsonObject clientExtra) {
        requireServer(server);var player=server.getPlayerList().getPlayer(playerId);if(player==null)throw new IllegalStateException("Player unavailable");
        var target=player.serverLevel().getEntity(targetId);if(target==null||target.isRemoved()||target.distanceToSqr(player)>4096)throw new IllegalArgumentException("Target unavailable or beyond 64 blocks");
        try{target=AppearanceAdapters.rootOf(target);}catch(Exception failure){throw new IllegalArgumentException("Cannot resolve appearance root",failure);}
        if(target instanceof net.minecraft.world.entity.player.Player)throw new IllegalArgumentException("Player capture is not supported");
        var value=sample(target,"OBSERVED_INSTANCE",representative,server);if(clientExtra.toString().length()>16384)throw new IllegalArgumentException("Client state budget exceeded");if(clientExtra.has("state")&&value.composition().adapter().equals(clientExtra.get("adapter").getAsString())&&value.composition().version().equals(clientExtra.get("version").getAsString()))value.composition().extra().add("client",clientExtra.getAsJsonObject("state").deepCopy());if(label.length()>80)throw new IllegalArgumentException("Capture label exceeds 80 characters");if(!label.isBlank())value.state().getAsJsonObject("traits").addProperty("variant",label);EntityAppearance.key(value.state());var values=OBSERVED.computeIfAbsent(server,ignored->new LinkedHashMap<>());
        if(values.size()>=64&&!values.containsKey(value.id()+"|"+EntityAppearance.key(value.state())))throw new IllegalStateException("Capture limit reached (64); export or clear captures");
        if(representative)values.replaceAll((key,old)->old.id().equals(value.id())?new Sample(old.id(),old.data(),old.synced(),old.state(),false,old.error(),old.composition(),old.attributes()):old);
        values.put(value.id()+"|"+EntityAppearance.key(value.state()),value);return values.size();
    }
    static List<Sample> observed(MinecraftServer server){requireServer(server);return List.copyOf(OBSERVED.getOrDefault(server,new LinkedHashMap<>()).values());}
    static void clear(MinecraftServer server){requireServer(server);OBSERVED.remove(server);}
    private static void requireServer(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("Entity sampling requires the server thread");}
    static String describe(Exception failure){Throwable cause=failure;var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());for(int i=0;i<32&&cause.getCause()!=null&&seen.add(cause.getCause());i++)cause=cause.getCause();String reason=cause.getClass().getSimpleName()+": "+Objects.toString(cause.getMessage(),"capture failed");return reason.substring(0,Math.min(1600,reason.length()));}
}
