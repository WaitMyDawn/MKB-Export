package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.nio.file.*;
import yagen.waitmydawn.mkb_export.core.*;

/** 显式 world 烟测使用真实捕获与普通重建渲染链路，不打开现有存档。 */
final class EquipmentProbe {
    private static CompletableFuture<List<EntitySamples.Sample>> pending;
    static boolean frame()throws Exception {
        var client=Minecraft.getInstance();var server=client.getSingleplayerServer();
        if(pending==null){pending=server.submit(()->prepare(server));return false;}
        if(!pending.isDone())return false;
        var samples=pending.join();
        var payload=ClientKnowledgeCapture.renderProbeSamples(samples);
        Path output=client.gameDirectory.toPath().resolve("equipment-probe");Files.createDirectories(output);
        Files.write(output.resolve("entity_icons.json"),payload.bytes());
        for(var entry:payload.images().entrySet()){var path=output.resolve(entry.getKey());Files.createDirectories(path.getParent());Files.write(path,entry.getValue());}
        if(payload.failed()!=0||payload.transparent()!=0)throw new IllegalStateException("Equipment probe had failed/transparent rows");
        com.mojang.logging.LogUtils.getLogger().info("MKB_EQUIPMENT_PROBE_VERIFIED pid={} records={} output={}",ProcessHandle.current().pid(),payload.total(),output);
        return true;
    }
    private static List<EntitySamples.Sample> prepare(net.minecraft.server.MinecraftServer server){
        var player=server.getPlayerList().getPlayers().get(0);var level=player.serverLevel();var samples=new ArrayList<EntitySamples.Sample>();
        var zombie=EntityType.ZOMBIE.create(level);zombie.setPos(player.getX()+4,player.getY(),player.getZ());zombie.setNoAi(true);zombie.setInvulnerable(true);
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));zombie.setItemSlot(EquipmentSlot.MAINHAND,EquipmentProbePlatform.sword(server));
        if(!level.addFreshEntity(zombie))throw new IllegalStateException("Zombie fixture not admitted");
        try{
            String before=zombie.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString();
            EntitySamples.capture(server,player.getUUID(),zombie.getUUID(),true,"p2-equipped-zombie");
            if(!before.equals(zombie.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString()))throw new IllegalStateException("Capture mutated zombie");
            var observed=EntitySamples.observed(server).stream().filter(s->s.id().equals("minecraft:zombie")).findFirst().orElseThrow();
            if(!zombie.getMainHandItem().isEnchanted()||observed.state().getAsJsonObject("traits").getAsJsonObject("equipment").size()!=2)throw new IllegalStateException("Fixture equipment missing");
            samples.add(observed);
            var clone=EntityType.ZOMBIE.create(level);clone.load(observed.data().copy());clone.getEntityData().assignValues(observed.synced());
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.MAINHAND))if(!Platform.entityEquipment(zombie.getItemBySlot(slot),server).equals(Platform.entityEquipment(clone.getItemBySlot(slot),server)))throw new IllegalStateException("Reconstruction lost equipment: "+slot);
            for(var slot:EquipmentSlot.values())clone.setItemSlot(slot,ItemStack.EMPTY);
            var bare=EntitySamples.sample(clone,"DECLARED_STATE",false,server);bare.state().getAsJsonObject("traits").addProperty("variant","p2-unequipped-control");samples.add(bare);
        }finally{zombie.discard();}
        var mageId=Platform.location("irons_spellbooks","cryomancer");
        if(BuiltInRegistries.ENTITY_TYPE.containsKey(mageId)){
            var type=BuiltInRegistries.ENTITY_TYPE.get(mageId);var automatic=EntitySamples.prepareAll(type,server,player.getUUID(),true,false);
            if(automatic.stream().filter(s->s.state().get("origin").getAsString().equals("SPAWN_INITIALIZED")).noneMatch(s->s.error()==null&&s.state().getAsJsonObject("traits").getAsJsonObject("equipment").size()>0))throw new IllegalStateException("Automatic mage lost spawn equipment");samples.addAll(automatic);
            var mage=(Mob)type.create(level);mage.setPos(player.getX()+8,player.getY(),player.getZ());
            try(var scope=EntitySamplingScope.enter()){Platform.initializeAppearanceSample(mage,level);}
            mage.setNoAi(true);if(!level.addFreshEntity(mage))throw new IllegalStateException("Mage fixture not admitted");
            try{String before=mage.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString();EntitySamples.capture(server,player.getUUID(),mage.getUUID(),true,"p2-natural-mage");if(!before.equals(mage.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString()))throw new IllegalStateException("Capture mutated mage");var observed=EntitySamples.observed(server).stream().filter(s->s.id().equals("irons_spellbooks:cryomancer")).findFirst().orElseThrow();if(observed.state().getAsJsonObject("traits").getAsJsonObject("equipment").size()==0)throw new IllegalStateException("Initialized mage lacks equipment");samples.add(observed);}finally{mage.discard();}
        }
        var marker=new JsonObject();EntityClassification.describe(marker,zombie);if(!marker.get("isMonster").getAsBoolean()||marker.get("isPart").getAsBoolean())throw new IllegalStateException("Zombie type markers are incorrect");
        var pig=EntityType.PIG.create(level);EntityClassification.describe(marker,pig);if(marker.get("isMonster").getAsBoolean()||marker.get("isPart").getAsBoolean())throw new IllegalStateException("Pig type markers are incorrect");
        var dragon=EntityType.ENDER_DRAGON.create(level);EntityClassification.describe(marker,Platform.parts(dragon)[0]);if(!marker.get("isPart").getAsBoolean()||marker.get("isMonster").getAsBoolean())throw new IllegalStateException("Dragon part marker is incorrect");
        com.mojang.logging.LogUtils.getLogger().info("MKB_ENTITY_CLASSIFICATION_VERIFIED zombieMonster=true pigMonster=false dragonPart=true");
        var forbidden=EntityType.CHICKEN.create(level);try(var scope=EntitySamplingScope.enter()){if(level.addFreshEntity(forbidden)||level.getEntity(forbidden.getUUID())!=null)throw new IllegalStateException("Sampling allowed a child join");}
        if(level.getEntity(zombie.getUUID())!=null)throw new IllegalStateException("Zombie fixture leaked");
        com.mojang.logging.LogUtils.getLogger().info("MKB_EQUIPMENT_SOURCE_VERIFIED sourceUnchanged=true rootCleaned=true blockedJoins={}",EntitySamplingScope.blockedCount());
        return List.copyOf(samples);
    }
}
