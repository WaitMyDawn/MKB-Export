package yagen.waitmydawn.mkb_export.client;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import java.util.*;

/** 仅由显式 world 烟测调用，修改临时世界内存数据以验证导出读取真实管理器。 */
final class KnowledgeSmokeFixture {
    static Map<String,com.google.gson.JsonObject> clientAppearances() {
        var id=Platform.location("irons_spellbooks","ice_spider");if(!BuiltInRegistries.ENTITY_TYPE.containsKey(id))return Map.of();
        try {var copy=BuiltInRegistries.ENTITY_TYPE.get(id).create(net.minecraft.client.Minecraft.getInstance().level);var out=new HashMap<String,com.google.gson.JsonObject>();for(var normal:List.of(new net.minecraft.world.phys.Vec3(0,1,0),new net.minecraft.world.phys.Vec3(1,0,0))){copy.getClass().getField("normal").set(copy,normal);copy.getClass().getField("lastNormal").set(copy,normal);var packet=AppearanceAdapters.clientState(copy);if(!packet.get("adapter").getAsString().equals("mkb_export:ice_spider")||!packet.getAsJsonObject("state").get("normal").equals(AppearanceAdapters.vector(normal))||!normal.equals(copy.getClass().getField("normal").get(copy)))throw new IllegalStateException("Client capture changed or lost surface normal");out.put(normal.y>0?"standing":"wall",packet);}com.mojang.logging.LogUtils.getLogger().info("MKB_ADAPTER_CLIENT_CAPTURE_VERIFIED poses=2 sourceUnchanged=true");return Map.copyOf(out);}catch(Exception failure){throw new IllegalStateException("Client appearance fixture failed",failure);}
    }
    static void install(MinecraftServer server,Map<String,com.google.gson.JsonObject> clientAppearances) {
        Platform.installSmokeRecipes(server);
        var player=server.getPlayerList().getPlayers().get(0);var level=player.serverLevel();
        var forbidden=net.minecraft.world.entity.EntityType.CHICKEN.create(level);boolean added;try(var scope=yagen.waitmydawn.mkb_export.core.EntitySamplingScope.enter()){added=level.addFreshEntity(forbidden);}if(added||level.getEntity(forbidden.getUUID())!=null)throw new IllegalStateException("Sampling scope inserted an entity into the world");com.mojang.logging.LogUtils.getLogger().info("MKB_EXPORT_SPAWN_GUARD_VERIFIED rejected=true worldEntityAbsent=true");

        var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(level);sheep.setBaby(true);sheep.setColor(net.minecraft.world.item.DyeColor.RED);sheep.setPos(player.getX(),player.getY(),player.getZ());level.addFreshEntity(sheep);
        var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);zombie.setPos(player.getX(),player.getY(),player.getZ());zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));level.addFreshEntity(zombie);
        for(var entity:List.of(sheep,zombie)){
            String before=entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString();
            EntitySamples.capture(server,player.getUUID(),entity.getUUID(),true,entity==sheep?"红色幼年羊":"钻石头盔样本");
            if(!before.equals(entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString()))throw new IllegalStateException("Capture modified the live entity");
        }
        verifyCompositionFixtures(server,player,clientAppearances);
        com.mojang.logging.LogUtils.getLogger().info("MKB_ENTITY_CAPTURE_VERIFIED liveEntitiesUnchanged=true observed=2");
        var tags=new HashMap<TagKey<Item>,List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(pair->tags.put(pair.getFirst(),pair.getSecond().stream().toList()));
        tags.put(TagKey.create(Registries.ITEM,Platform.location("mkb_export","smoke_materials")),List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.DIRT)));
        BuiltInRegistries.ITEM.bindTags(tags);
    }
    private static void verifyCompositionFixtures(MinecraftServer server,net.minecraft.server.level.ServerPlayer player,Map<String,com.google.gson.JsonObject> clientAppearances) {
        var registry=BuiltInRegistries.ENTITY_TYPE;var level=player.serverLevel();
        try {
            var spiderId=Platform.location("irons_spellbooks","ice_spider");if(registry.containsKey(spiderId)){
                var spider=registry.get(spiderId).create(level);spider.setPos(player.getX()+4,player.getY(),player.getZ());if(spider instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);level.addFreshEntity(spider);
                for(var normal:List.of(new net.minecraft.world.phys.Vec3(0,1,0),new net.minecraft.world.phys.Vec3(1,0,0))){var serverNormal=new net.minecraft.world.phys.Vec3(0,1,0);spider.getClass().getField("normal").set(spider,serverNormal);spider.getClass().getField("lastNormal").set(spider,serverNormal);String before=spider.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString();EntitySamples.capture(server,player.getUUID(),spider.getUUID(),true,normal.y>0?"冰蜘蛛站立测试":"冰蜘蛛墙面测试",clientAppearances.get(normal.y>0?"standing":"wall"));if(!before.equals(spider.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString())||!serverNormal.equals(spider.getClass().getField("normal").get(spider)))throw new IllegalStateException("Spider capture mutated source");}
                com.mojang.logging.LogUtils.getLogger().info("MKB_ICE_SPIDER_CAPTURE_VERIFIED poses=2 sourceUnchanged=true");
            }
            var wormId=Platform.location("alexsmobs","void_worm");if(registry.containsKey(wormId)){
                var head=registry.get(wormId).create(level);head.setPos(player.getX()+8,player.getY(),player.getZ());head.getClass().getMethod("setSegmentCount",int.class).invoke(head,3);if(head instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);level.addFreshEntity(head);var all=new ArrayList<net.minecraft.world.entity.Entity>();all.add(head);net.minecraft.world.entity.Entity parent=head;
                for(int i=0;i<3;i++){var part=registry.get(Platform.location("alexsmobs","void_worm_part")).create(level);part.setPos(head.getX()+i*.3,head.getY(),head.getZ()+1.5+i);part.getClass().getMethod("setTail",boolean.class).invoke(part,i==2);part.getClass().getMethod("setWormScale",float.class).invoke(part,i==2?.75f:1f);part.getClass().getMethod("setParent",net.minecraft.world.entity.Entity.class).invoke(part,parent);level.addFreshEntity(part);parent.getClass().getMethod("setChildId",java.util.UUID.class).invoke(parent,part.getUUID());parent=part;all.add(part);}
                var before=all.stream().map(e->e.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString()).toList();EntitySamples.capture(server,player.getUUID(),parent.getUUID(),false,"虚空蠕虫完整链测试");for(int i=0;i<all.size();i++)if(!before.get(i).equals(all.get(i).saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString()))throw new IllegalStateException("Void worm capture mutated source");var sample=EntitySamples.observed(server).stream().filter(v->v.id().equals("alexsmobs:void_worm")).findFirst().orElseThrow();if(!sample.composition().complete()||sample.composition().nodes().size()!=3)throw new IllegalStateException("Void worm actual chain incomplete");com.mojang.logging.LogUtils.getLogger().info("MKB_VOID_WORM_CAPTURE_VERIFIED parts=3 sourceUnchanged=true");
            }
            var headId=Platform.location("alexsmobs","centipede_head");if(registry.containsKey(headId)){
                var head=registry.get(headId).create(level);head.setPos(player.getX()+6,player.getY(),player.getZ());head.setYRot(90);if(head instanceof net.minecraft.world.entity.LivingEntity living){living.yBodyRot=living.yBodyRotO=living.yHeadRot=living.yHeadRotO=90;}head.getClass().getMethod("setSegmentCount",int.class).invoke(head,3);if(head instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);level.addFreshEntity(head);net.minecraft.world.entity.Entity parent=head;var all=new ArrayList<net.minecraft.world.entity.Entity>();all.add(head);
                for(int j=0;j<3;j++){var partId=Platform.location("alexsmobs",j==2?"centipede_tail":"centipede_body");if(!registry.containsKey(partId))throw new IllegalStateException("Centipede fixture type missing: "+partId);var part=registry.get(partId).create(level);part.setPos(head.getX()+1.3*(j+1),head.getY(),head.getZ());part.setYRot(90);if(part instanceof net.minecraft.world.entity.LivingEntity living){living.yBodyRot=living.yBodyRotO=living.yHeadRot=living.yHeadRotO=90;}if(part instanceof net.minecraft.world.entity.Mob mob)mob.setNoAi(true);part.getClass().getMethod("setParent",net.minecraft.world.entity.Entity.class).invoke(part,parent);part.getClass().getMethod("setBodyIndex",int.class).invoke(part,j);level.addFreshEntity(part);parent.getClass().getMethod("setChildId",java.util.UUID.class).invoke(parent,part.getUUID());parent=part;all.add(part);}
                if(AppearanceAdapters.rootOf(all.get(3))!=head)throw new IllegalStateException("Tail did not resolve to head");
                var before=all.stream().map(e->e.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString()).toList();EntitySamples.capture(server,player.getUUID(),all.get(3).getUUID(),true,"三节蜈蚣测试");for(int j=0;j<all.size();j++)if(!before.get(j).equals(all.get(j).saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString()))throw new IllegalStateException("Centipede capture mutated source");
                var sample=EntitySamples.observed(server).stream().filter(v->v.id().equals("alexsmobs:centipede_head")).findFirst().orElseThrow();if(!sample.composition().complete()||sample.composition().nodes().size()!=3)throw new IllegalStateException("Centipede composition incomplete");com.mojang.logging.LogUtils.getLogger().info("MKB_CENTIPEDE_CAPTURE_VERIFIED independentParts=3 sourceUnchanged=true");
            }
        }catch(Exception failure){throw new IllegalStateException("Composition fixture failed",failure);}
    }
}
