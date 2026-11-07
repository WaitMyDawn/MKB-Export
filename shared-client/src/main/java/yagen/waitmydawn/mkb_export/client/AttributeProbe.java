package yagen.waitmydawn.mkb_export.client;

/** 显式隔离世界实验入口，普通导出不改变世界难度或写入实验报告。 */
public final class AttributeProbe {
    static java.util.concurrent.CompletableFuture<Void> start(net.minecraft.server.MinecraftServer server) {
        return EntityAttributeSampler.study(server).thenCompose(stages->ServerKnowledgeCapture.captureInitialized(server,()->false))
                .thenCompose(result->{
                    try {
                        var payload=result.payloads().stream().filter(value->value.category().equals("entity_attributes")).findFirst().orElseThrow();
                        var root=com.google.gson.JsonParser.parseString(new String(payload.bytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        var fire=root.getAsJsonArray("records").asList().stream().map(com.google.gson.JsonElement::getAsJsonObject).filter(row->row.get("id").getAsString().equals("irons_spellbooks:fire_boss")).findFirst().orElseThrow();
                        if(fire.has("error"))throw new IllegalStateException(fire.toString());
                        var values=fire.getAsJsonArray("attributes").asList().stream().map(com.google.gson.JsonElement::getAsJsonObject).toList();
                        double resist=values.stream().filter(row->row.get("attribute").getAsString().equals("irons_spellbooks:fire_magic_resist")).findFirst().orElseThrow().get("effective").getAsDouble();
                        double damage=values.stream().filter(row->row.get("attribute").getAsString().endsWith("attack_damage")).findFirst().orElseThrow().get("effective").getAsDouble();
                        if(resist!=1.5||damage!=20||fire.get("sampleTicks").getAsInt()!=1)throw new IllegalStateException("Initialized export regression: "+fire);
                        java.nio.file.Files.write(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("mkb-attribute-export.json"),payload.bytes());
                        com.mojang.logging.LogUtils.getLogger().info("MKB_INITIALIZED_EXPORT_VERIFIED records={} failed={} fireResistance={} attackDamage={}",payload.total(),payload.failed(),resist,damage);
                    }catch(Exception failure){return java.util.concurrent.CompletableFuture.<Void>failedFuture(failure);}
                    var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
                    var future=EntityAttributeSampler.start(server,java.util.List.of("irons_spellbooks:fire_boss","minecraft:zombie"),cancelled::get);
                    cancelled.set(true);
                    return future.handle((rows,failure)->{
                        if(failure==null)throw new IllegalStateException("Cancellation was ignored");
                        com.mojang.logging.LogUtils.getLogger().info("MKB_ATTRIBUTE_CANCEL_VERIFIED");return (Void)null;
                    }).thenCompose(ignored->{
                        System.setProperty("mkb_export.attributeProbe.failTick","true");
                        return EntityAttributeSampler.start(server,java.util.List.of("minecraft:zombie"),()->false).handle((rows,failure)->{
                            System.clearProperty("mkb_export.attributeProbe.failTick");
                            if(failure!=null||rows.size()!=1||!rows.get(0).getAsJsonObject().has("error"))throw new IllegalStateException("Tick exception was not isolated",failure);
                            com.mojang.logging.LogUtils.getLogger().info("MKB_ATTRIBUTE_TICK_FAILURE_VERIFIED");return (Void)null;
                        });
                    });
                });
    }
    public static void tick(net.minecraft.server.MinecraftServer server){EntityAttributeSampler.tick(server);}
    public static void stop(net.minecraft.server.MinecraftServer server){EntityAttributeSampler.stop(server);}
    private AttributeProbe() {}
}
