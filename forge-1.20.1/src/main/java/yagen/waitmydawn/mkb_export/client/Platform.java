package yagen.waitmydawn.mkb_export.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;

import java.util.List;

final class Platform {
    static com.google.gson.JsonObject itemAttributes(net.minecraft.world.item.ItemStack stack) {
        var row = new com.google.gson.JsonObject();
        row.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        row.addProperty("schemaVersion", 1);
        row.addProperty("context", "SERVER_DEFAULT_STACK");
        row.addProperty("maxStackSize", stack.getMaxStackSize());
        row.addProperty("maxDamage", stack.getMaxDamage());
        row.addProperty("damageable", stack.isDamageableItem());
        var modifiers = new com.google.gson.JsonArray();
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
            stack.getAttributeModifiers(slot).forEach((attribute, modifier) -> {
                var m = new com.google.gson.JsonObject();
                m.addProperty("slot", slot.getName());
                m.addProperty("attribute", net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
                m.addProperty("modifierId", modifier.getId().toString());
                m.addProperty("amount", yagen.waitmydawn.mkb_export.core.AttributeOperations.finite(modifier.getAmount()));
                m.addProperty("operation", yagen.waitmydawn.mkb_export.core.AttributeOperations.canonical(modifier.getOperation().toString()));
                modifiers.add(m);
            });
        row.add("modifiers", modifiers);
        row.addProperty("limitation", "Default-stack slot modifiers; not final player damage or contextual event results");
        return row;
    }

    static com.google.gson.JsonObject entityAttributes(net.minecraft.world.entity.LivingEntity living) throws ReflectiveOperationException {
        var row = new com.google.gson.JsonObject();
        row.addProperty("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString());
        row.addProperty("schemaVersion", 1);
        EntityClassification.describe(row, living);
        var values = EntityAttributeReader.capture(living.getAttributes());
        try {
            row.addProperty("lootTable", living.getLootTable().toString());
        } catch (Exception unavailable) {
            row.addProperty("lootTableFailure", EntitySamples.describe(unavailable));
        }
        row.add("attributes", values);
        row.addProperty("captureVersion", 2);
        row.addProperty("valueSource", "SERVER_ATTRIBUTE_MAP_API");
        row.addProperty("validation", "PUBLIC_API_MATCH");
        row.addProperty("attributeMapOnly", true);
        row.addProperty("worldTickAdvanced", false);
        row.addProperty("health", yagen.waitmydawn.mkb_export.core.AttributeOperations.finite(living.getHealth()));
        row.addProperty("baby", living.isBaby());
        row.addProperty("difficulty", living.level().getDifficulty().toString());
        row.addProperty("limitation", "Server AttributeMap snapshot after registration events; effective attack damage is not final hit damage. Equipment, effects, spawn initialization, difficulty and runtime events may change results");
        return row;
    }

    static void setAttributeProbeBase(net.minecraft.world.entity.LivingEntity living, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        living.getAttribute(attribute).setBaseValue(value);
    }

    static int attributeCommandValue(net.minecraft.server.MinecraftServer server, net.minecraft.world.entity.Entity entity, String id) {
        String command = "attribute " + entity.getUUID() + " " + id + " get 1000";
        return server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
    }

    static void initializeAppearanceSample(net.minecraft.world.entity.Mob mob, net.minecraft.server.level.ServerLevel level) {
        net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), net.minecraft.world.entity.MobSpawnType.NATURAL, null, null);
    }

    static void initializeAttributeSample(net.minecraft.world.entity.Mob mob, net.minecraft.server.level.ServerLevel level) {
        net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
    }

    static com.google.gson.JsonArray lootEntrypoints() {
        var rows = new com.google.gson.JsonArray();
        for (var type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            try {
                var table = type.getDefaultLootTable();
                var row = new com.google.gson.JsonObject();
                row.addProperty("id", "mkb_export:entity/" + id.replace(':', '/'));
                row.addProperty("source", id);
                row.addProperty("sourceKind", "entity");
                row.addProperty("lootTable", table.toString());
                row.addProperty("relation", "DEFAULT_DECLARATION");
                row.addProperty("limitation", "Runtime entity overrides and event replacement may change this entrypoint");
                rows.add(row);
            } catch (Exception failure) {
                rows.add(ServerKnowledgeCapture.failure("mkb_export:entity/" + id.replace(':', '/'), failure));
            }
        }
        for (var type : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(type).toString();
            try {
                var table = type.getLootTable();
                var row = new com.google.gson.JsonObject();
                row.addProperty("id", "mkb_export:block/" + id.replace(':', '/'));
                row.addProperty("source", id);
                row.addProperty("sourceKind", "block");
                row.addProperty("lootTable", table.toString());
                row.addProperty("relation", "DEFAULT_DECLARATION");
                row.addProperty("limitation", "Block state, tool, player context and event replacement may change this entrypoint");
                rows.add(row);
            } catch (Exception failure) {
                rows.add(ServerKnowledgeCapture.failure("mkb_export:block/" + id.replace(':', '/'), failure));
            }
        }
        return rows;
    }

    static boolean modernTrades() {
        return false;
    }

    static com.google.gson.JsonObject knowledgeStack(net.minecraft.world.item.ItemStack stack, net.minecraft.server.MinecraftServer server) {
        return stackJson(stack);
    }

    static boolean tradeRebalance(net.minecraft.server.MinecraftServer server) {
        return false;
    }

    static java.util.Map<net.minecraft.world.entity.npc.VillagerProfession, it.unimi.dsi.fastutil.ints.Int2ObjectMap<net.minecraft.world.entity.npc.VillagerTrades.ItemListing[]>> experimentalTrades() {
        return net.minecraft.world.entity.npc.VillagerTrades.TRADES;
    }

    static java.util.List<org.apache.commons.lang3.tuple.Pair<net.minecraft.world.entity.npc.VillagerTrades.ItemListing[], Integer>> experimentalWandering() {
        return java.util.List.of();
    }

    static com.google.gson.JsonObject knowledgeCost(Object cost, net.minecraft.server.MinecraftServer server) {
        return knowledgeStack((net.minecraft.world.item.ItemStack) cost, server);
    }

    static void tradeProvider(com.google.gson.JsonObject row, Object factory) throws Exception {
    }

    static com.google.gson.JsonArray tradeEnchantments(Object factory, net.minecraft.server.MinecraftServer server) throws Exception {
        var result = new com.google.gson.JsonArray();
        for (var value : net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT)
            if (value.isTradeable()) {
                var row = new com.google.gson.JsonObject();
                row.addProperty("enchantment", net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(value).toString());
                row.addProperty("minLevel", value.getMinLevel());
                row.addProperty("maxLevel", value.getMaxLevel());
                row.addProperty("doublePrice", value.isTreasureOnly());
                result.add(row);
            }
        for (var element : result)
            element.getAsJsonObject().addProperty("enchantmentSelectionProbability", 1.0 / result.size());
        return result;
    }

    static Object brewingObject(net.minecraft.server.MinecraftServer server) {
        return net.minecraft.world.item.alchemy.PotionBrewing.class;
    }

    static com.google.gson.JsonElement ingredientJson(net.minecraft.world.item.crafting.Ingredient ingredient, net.minecraft.server.MinecraftServer server) {
        return ingredient.toJson();
    }

    static java.util.List<?> customBrewing(net.minecraft.server.MinecraftServer server) {
        return net.minecraftforge.common.brewing.BrewingRecipeRegistry.getRecipes();
    }

    static com.google.gson.JsonObject customBrewingRule(Object value, net.minecraft.server.MinecraftServer server) {
        if (!(value instanceof net.minecraftforge.common.brewing.BrewingRecipe recipe))
            throw new IllegalArgumentException("Programmatic brewing recipe requires adapter: " + value.getClass().getName());
        var row = new com.google.gson.JsonObject();
        row.add("input", ingredientJson(recipe.getInput(), server));
        row.add("ingredient", ingredientJson(recipe.getIngredient(), server));
        row.add("output", knowledgeStack(recipe.getOutput(), server));
        return row;
    }

    static com.google.gson.JsonObject potionStack(net.minecraft.world.item.Item item, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion, net.minecraft.server.MinecraftServer server) {
        var stack = new net.minecraft.world.item.ItemStack(item);
        net.minecraft.world.item.alchemy.PotionUtils.setPotion(stack, potion.value());
        return knowledgeStack(stack, server);
    }

    static net.minecraft.nbt.CompoundTag readTemplate(byte[] bytes) throws java.io.IOException {
        try (var input = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes)))) {
            return net.minecraft.nbt.NbtIo.read(input, new net.minecraft.nbt.NbtAccounter(64L * 1024 * 1024));
        }
    }

    static com.google.gson.JsonArray potionEffects(net.minecraft.world.item.alchemy.Potion potion) {
        var rows = new com.google.gson.JsonArray();
        for (var effect : potion.getEffects()) {
            var row = new com.google.gson.JsonObject();
            row.addProperty("effect", net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()).toString());
            row.addProperty("name", effect.getEffect().getDisplayName().getString());
            row.addProperty("amplifier", effect.getAmplifier());
            row.addProperty("durationTicks", effect.getDuration());
            rows.add(row);
        }
        return rows;
    }

    static com.google.gson.JsonObject stewStack(Object factory, net.minecraft.server.MinecraftServer server) throws Exception {
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SUSPICIOUS_STEW);
        net.minecraft.world.item.SuspiciousStewItem.saveMobEffect(stack, (net.minecraft.world.effect.MobEffect) RuleFields.get(factory, "effect"), RuleFields.number(factory, "duration"));
        return knowledgeStack(stack, server);
    }

    static com.google.gson.JsonArray tradedPotionOutputs(Object factory, net.minecraft.server.MinecraftServer server) throws Exception {
        var result = new com.google.gson.JsonArray();
        for (var potion : net.minecraft.core.registries.BuiltInRegistries.POTION) {
            var holder = net.minecraft.core.registries.BuiltInRegistries.POTION.wrapAsHolder(potion);
            if (!potion.getEffects().isEmpty() && net.minecraft.world.item.alchemy.PotionBrewing.isBrewablePotion(potion)) {
                var value = potionStack(((net.minecraft.world.item.ItemStack) RuleFields.get(factory, "toItem")).getItem(), holder, server);
                value.addProperty("count", RuleFields.number(factory, "toCount"));
                result.add(value);
            }
        }
        return result;
    }

    private Platform() {
    }

    static int maxParts() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.MAX_PARTS.get();
    }

    static int maxLegacyVariants() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.LEGACY_VARIANTS.get();
    }

    static int maxVariants() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.MAX_VARIANTS.get();
    }

    static int adapterBudgetMillis() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.ADAPTER_BUDGET.get();
    }

    static boolean adaptersEnabled() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.ADAPTERS.get();
    }

    static net.minecraftforge.entity.PartEntity<?>[] parts(net.minecraft.world.entity.Entity entity) {
        var parts = entity.getParts();
        return parts == null ? new net.minecraftforge.entity.PartEntity<?>[0] : parts;
    }

    static boolean isPart(net.minecraft.world.entity.Entity entity) {
        return entity instanceof net.minecraftforge.entity.PartEntity<?>;
    }

    static net.minecraft.world.entity.Entity captureTarget(net.minecraft.world.entity.Entity target) {
        return target instanceof net.minecraftforge.entity.PartEntity<?> part ? part.getParent() : target;
    }

    static boolean spawnSamples() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.SPAWN_SAMPLES.get();
    }

    static boolean ageVariants() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.AGE_VARIANTS.get();
    }

    static String syncedFingerprint(java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values, net.minecraft.server.MinecraftServer server) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            buffer.writeVarInt(values.size());
            for (var value : values.stream().sorted(java.util.Comparator.comparingInt(net.minecraft.network.syncher.SynchedEntityData.DataValue::id)).toList())
                value.write(buffer);
            if (buffer.readableBytes() > 262144)
                throw new IllegalArgumentException("Synced entity data exceeds 256 KiB");
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(0, bytes);
            return yagen.waitmydawn.mkb_export.core.ContentDigest.sha256(bytes);
        } finally {
            buffer.release();
        }
    }

    static com.google.gson.JsonObject entityEquipment(net.minecraft.world.item.ItemStack stack, net.minecraft.server.MinecraftServer server) {
        var row = new com.google.gson.JsonObject();
        row.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        row.addProperty("count", stack.getCount());
        row.addProperty("nbt", stack.save(new net.minecraft.nbt.CompoundTag()).toString());
        return row;
    }


    static int itemSize() {
        return yagen.waitmydawn.mkb_export.config.ExportConfig.ITEM_SIZE.get();
    }

    static int entitySize() {
        return Boolean.getBoolean("mkb_export.smoke") ? Integer.getInteger("mkb_export.entitySize", yagen.waitmydawn.mkb_export.config.ExportConfig.ENTITY_SIZE.get()) : yagen.waitmydawn.mkb_export.config.ExportConfig.ENTITY_SIZE.get();
    }

    static boolean includeNonLiving() {
        return Boolean.getBoolean("mkb_export.smoke") && System.getProperty("mkb_export.entityNonLiving") != null ? Boolean.getBoolean("mkb_export.entityNonLiving") : yagen.waitmydawn.mkb_export.config.ExportConfig.INCLUDE_NON_LIVING.get();
    }

    static void itemSize(int pixels) {
        yagen.waitmydawn.mkb_export.config.ExportConfig.itemSize(pixels);
    }

    static void entitySize(int pixels) {
        yagen.waitmydawn.mkb_export.config.ExportConfig.entitySize(pixels);
    }

    static void includeNonLiving(boolean enabled) {
        yagen.waitmydawn.mkb_export.config.ExportConfig.includeNonLiving(enabled);
    }

    static void createSmokeWorld() {
        var client = net.minecraft.client.Minecraft.getInstance();
        var settings = new net.minecraft.world.level.LevelSettings("MKB lifecycle smoke", net.minecraft.world.level.GameType.CREATIVE, false,
                net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel("MKB-Smoke-" + java.util.UUID.randomUUID(), settings,
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false), net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions);
    }

    static com.google.gson.JsonArray lootTables(net.minecraft.server.MinecraftServer server) {
        var rows = new com.google.gson.JsonArray();
        server.getLootData().getKeys(net.minecraft.world.level.storage.loot.LootDataType.TABLE).stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(id -> {
            try {
                var table = server.getLootData().getLootTable(id);
                var row = new com.google.gson.JsonObject();
                row.addProperty("id", id.toString());
                row.addProperty("runtimeLoaded", true);
                row.add("definition", net.minecraft.world.level.storage.loot.Deserializers.createLootTableSerializer().create().toJsonTree(table));
                rows.add(row);
            } catch (Exception failure) {
                rows.add(ServerKnowledgeCapture.failure(id.toString(), failure));
            }
        });
        return rows;
    }

    static com.google.gson.JsonArray lootModifiers(net.minecraft.server.MinecraftServer server) {
        var rows = new com.google.gson.JsonArray();
        int order = 0;
        // 加载器未公开管理器访问器；仅访问固定的开发命名方法，失败作为类别错误保留。
        final net.minecraftforge.common.loot.LootModifierManager manager;
        try {
            var accessor = net.minecraftforge.common.ForgeInternalHandler.class.getDeclaredMethod("getLootModifierManager");
            accessor.setAccessible(true);
            manager = (net.minecraftforge.common.loot.LootModifierManager) accessor.invoke(null);
        } catch (ReflectiveOperationException failure) {
            rows.add(ServerKnowledgeCapture.failure("mkb_export:modifier_capture", failure));
            return rows;
        }
        for (var modifier : manager.getAllLootMods()) {
            String id = "mkb_export:global_modifier/" + order++;
            try {
                var row = new com.google.gson.JsonObject();
                row.addProperty("id", id);
                row.addProperty("order", order - 1);
                row.addProperty("identity", "CAPTURE_ORDER");
                row.addProperty("runtimeLoaded", true);
                row.addProperty("javaClass", modifier.getClass().getName());
                row.add("definition", net.minecraftforge.common.loot.IGlobalLootModifier.DIRECT_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, modifier).getOrThrow(false, message -> {
                    throw new IllegalStateException(message);
                }));
                rows.add(row);
            } catch (Exception failure) {
                rows.add(ServerKnowledgeCapture.failure(id, failure));
            }
        }
        return rows;
    }

    static String loader() {
        return "forge";
    }

    static com.google.gson.JsonArray recipes(net.minecraft.server.MinecraftServer server) {
        return RuntimeRecipes.capture(server);
    }

    static void installSmokeRecipes(net.minecraft.server.MinecraftServer server) {
        var recipes = new java.util.ArrayList<>(server.getRecipeManager().getRecipes());
        recipes.removeIf(recipe -> java.util.Set.of("minecraft:diamond_block", "minecraft:gold_block").contains(recipe.getId().toString()));
        var definition = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"mkb_export:smoke_materials\"}],\"result\":{\"item\":\"minecraft:bread\",\"count\":2}}").getAsJsonObject();
        recipes.add(net.minecraft.world.item.crafting.RecipeManager.fromJson(location("minecraft", "gold_block"), definition));
        recipes.add(net.minecraft.world.item.crafting.RecipeManager.fromJson(location("mkb_export", "smoke_added"), definition));
        server.getRecipeManager().replaceRecipes(recipes);
    }

    static List<net.minecraft.network.chat.Component> tooltip(net.minecraft.world.item.ItemStack stack) {
        return stack.getTooltipLines(net.minecraft.client.Minecraft.getInstance().player, net.minecraft.world.item.TooltipFlag.NORMAL);
    }

    static com.google.gson.JsonObject stackJson(net.minecraft.world.item.ItemStack stack) {
        var result = new com.google.gson.JsonObject();
        result.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        result.addProperty("count", stack.getCount());
        if (stack.hasTag() && !java.util.Objects.equals(stack.getTag(), stack.getItem().getDefaultInstance().getTag()))
            result.addProperty("nbt", stack.getTag().toString());
        return result;
    }

    static com.google.gson.JsonArray modMetadata() {
        var result = new com.google.gson.JsonArray();
        ModList.get().getMods().stream().sorted(java.util.Comparator.comparing(m -> m.getModId())).forEach(mod -> {
            var row = new com.google.gson.JsonObject();
            row.addProperty("id", mod.getModId());
            row.addProperty("version", mod.getVersion().toString());
            row.addProperty("displayName", mod.getDisplayName());
            row.addProperty("description", mod.getDescription());
            result.add(row);
        });
        return result;
    }

    static com.google.gson.JsonObject state(net.minecraft.world.item.ItemStack stack) {
        if (!stack.hasTag()) return null;
        var result = new com.google.gson.JsonObject();
        result.addProperty("nbt", stack.getTag().toString());
        return result;
    }

    static net.minecraft.world.item.ItemStack stack(yagen.waitmydawn.mkb_export.core.ExportJob.Target target) {
        String[] id = target.itemId().split(":", 2);
        var location = location(id[0], id[1]);
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(location))
            throw new IllegalArgumentException("Unknown item");
        var result = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location));
        if (target.state() != null) {
            if (!target.state().has("nbt")) throw new IllegalArgumentException("Expected NBT state");
            try {
                result.setTag(net.minecraft.nbt.TagParser.parseTag(target.state().get("nbt").getAsString()));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
                throw new IllegalArgumentException("Invalid NBT state", invalid);
            }
        }
        return result;
    }

    static net.minecraft.world.item.ItemStack dyed(String id, int rgb) {
        var stack = stack(new yagen.waitmydawn.mkb_export.core.ExportJob.Target(id, null));
        if (!(stack.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem dyeable))
            throw new IllegalArgumentException("Item is not dyeable");
        dyeable.setColor(stack, rgb);
        return stack;
    }

    static net.minecraft.resources.ResourceLocation location(String namespace, String path) {
        return new net.minecraft.resources.ResourceLocation(namespace, path);
    }

    static String loaderVersion() {
        return ForgeVersion.getVersion();
    }

    static String exporterVersion() {
        return ModList.get().getModContainerById("mkb_export").orElseThrow().getModInfo().getVersion().toString();
    }

    static List<EnvironmentCapture.ModSource> mods() {
        return ModList.get().getMods().stream().map(mod -> new EnvironmentCapture.ModSource(mod.getModId(), mod.getVersion().toString(),
                mod.getOwningFile().getFile().getFilePath())).toList();
    }

    static void pushModelView() {
        var stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        stack.translate(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();
    }

    static void popModelView() {
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void renderBaseModel(net.minecraft.world.entity.Entity entity, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light) throws Exception {
        var renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer livingRenderer) || !(entity instanceof net.minecraft.world.entity.LivingEntity living))
            throw new IllegalArgumentException("No base model");
        var model = livingRenderer.getModel();
        java.lang.reflect.Field burial = null;
        boolean previous = false;
        try {
            burial = model.getClass().getField("noBurrowing");
            previous = burial.getBoolean(model);
            burial.setBoolean(model, true);
        } catch (NoSuchFieldException absent) {
        }
        pose.pushPose();
        try {
            pose.scale(-1, -1, 1);
            pose.translate(0, -1.501, 0);
            model.young = living.isBaby();
            model.prepareMobModel(living, 0, 0, 0);
            model.setupAnim(living, 0, 0, 0, 0, 0);
            model.renderToBuffer(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(livingRenderer.getTextureLocation(living))), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        } finally {
            pose.popPose();
            if (burial != null) burial.setBoolean(model, previous);
        }
    }

    static com.google.gson.JsonArray enchantments(net.minecraft.server.MinecraftServer server) {
        var registry = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT;
        var rows = new com.google.gson.JsonArray();
        for (var enchantment : registry) {
            String id = registry.getKey(enchantment).toString();
            try {
                var row = new com.google.gson.JsonObject();
                row.addProperty("id", id);
                row.addProperty("descriptionId", "enchantment." + id.replace(':', '.'));
                row.addProperty("minLevel", enchantment.getMinLevel());
                row.addProperty("maxLevel", enchantment.getMaxLevel());
                var levels = new com.google.gson.JsonArray();
                for (int level = enchantment.getMinLevel(); level <= Math.min(255, enchantment.getMaxLevel()); level++) {
                    var cost = new com.google.gson.JsonObject();
                    cost.addProperty("level", level);
                    cost.addProperty("minCost", enchantment.getMinCost(level));
                    cost.addProperty("maxCost", enchantment.getMaxCost(level));
                    levels.add(cost);
                }
                row.add("costs", levels);
                row.addProperty("costSampleMaxLevel", Math.min(255, enchantment.getMaxLevel()));
                var supported = new com.google.gson.JsonArray();
                for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM)
                    if (enchantment.canEnchant(item.getDefaultInstance()))
                        supported.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
                row.add("supportedItems", supported);
                var incompatible = new com.google.gson.JsonArray();
                for (var other : registry)
                    if (enchantment != other && !enchantment.isCompatibleWith(other))
                        incompatible.add(registry.getKey(other).toString());
                row.add("incompatible", incompatible);
                row.addProperty("treasureOnly", enchantment.isTreasureOnly());
                row.addProperty("curse", enchantment.isCurse());
                row.addProperty("tradeable", enchantment.isTradeable());
                row.addProperty("discoverable", enchantment.isDiscoverable());
                row.addProperty("rarity", enchantment.getRarity().toString());
                row.addProperty("coverage", "LEGACY_API_METADATA");
                row.addProperty("limitation", "Default stack applicability and declared rules only; custom NBT/components, events and contextual effects may override. Costs are enchanting power bounds, not anvil levels or trade price.");
                rows.add(row);
            } catch (Exception e) {
                rows.add(ServerKnowledgeCapture.failure(id, e));
            }
        }
        return rows;
    }

    static com.google.gson.JsonArray effectModifiers(net.minecraft.world.effect.MobEffect effect) {
        var result = new com.google.gson.JsonArray();
        for (int amplifier = 0; amplifier < 5; amplifier++) {
            final int amp = amplifier;
            effect.getAttributeModifiers().forEach((attribute, modifier) -> {
                var row = new com.google.gson.JsonObject();
                row.addProperty("amplifier", amp);
                row.addProperty("attribute", net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
                row.addProperty("amount", yagen.waitmydawn.mkb_export.core.AttributeOperations.finite(effect.getAttributeModifierValue(amp, modifier)));
                row.addProperty("operation", yagen.waitmydawn.mkb_export.core.AttributeOperations.canonical(modifier.getOperation().toString()));
                result.add(row);
            });
        }
        return result;
    }

    static String spawnPlacementName(net.minecraft.world.entity.EntityType<?> type) {
        return net.minecraft.world.entity.SpawnPlacements.getPlacementType(type).name();
    }

}
