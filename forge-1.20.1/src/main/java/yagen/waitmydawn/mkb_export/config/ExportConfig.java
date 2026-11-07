package yagen.waitmydawn.mkb_export.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 加载器负责启动加载、校验与文件监听；命令显式保存，任务开始时读取快照。
 */
public final class ExportConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_PARTS, MAX_VARIANTS, LEGACY_VARIANTS, ADAPTER_BUDGET;
    public static final ForgeConfigSpec.BooleanValue ADAPTERS;
    public static final ForgeConfigSpec.IntValue ITEM_SIZE;
    public static final ForgeConfigSpec.ConfigValue<Integer> ENTITY_SIZE;
    public static final ForgeConfigSpec.BooleanValue INCLUDE_NON_LIVING;
    public static final ForgeConfigSpec.BooleanValue SPAWN_SAMPLES, AGE_VARIANTS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("rendering");
        ITEM_SIZE = builder.comment("Default item GUI PNG resolution; explicit command size arguments override this for one export.").defineInRange("itemSize", 64, 16, 512);
        ENTITY_SIZE = builder.comment("Entity PNG resolution. Allowed values: 256, 512.").define("entitySize", 256, value -> value instanceof Integer pixels && (pixels == 256 || pixels == 512));
        INCLUDE_NON_LIVING = builder.comment("Include non-LivingEntity types such as projectiles and display entities.").define("includeNonLiving", false);
        SPAWN_SAMPLES = builder.comment("Prepare detached Mob samples without finalizeSpawn, AI ticks, or world insertion.").define("spawnSamples", true);
        AGE_VARIANTS = builder.comment("Include baby variants when the Mob supports setBaby/isBaby, in addition to the adult sample.").define("ageVariants", true);
        MAX_PARTS = builder.comment("Maximum nodes in one appearance composition.").defineInRange("maxCompositionParts", 64, 1, 128);
        LEGACY_VARIANTS = builder.comment("Maximum legacy appearance adapter variants per entity.").defineInRange("maxAdapterVariants", 8, 0, 32);
        MAX_VARIANTS = builder.comment("Maximum samples across declared appearance axes per entity.").defineInRange("maxVariantSamples", 128, 0, 1024);
        ADAPTER_BUDGET = builder.comment("Post-call adapter budget in milliseconds; callbacks must not block.").defineInRange("adapterBudgetMillis", 1000, 10, 10000);
        ADAPTERS = builder.comment("Enable built-in and service-provided appearance adapters.").define("appearanceAdapters", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ExportConfig() {
    }

    public static void itemSize(int pixels) {
        if (pixels < 16 || pixels > 512) throw new IllegalArgumentException("Item size must be 16..512");
        ITEM_SIZE.set(pixels);
        SPEC.save();
    }

    public static void entitySize(int pixels) {
        if (pixels != 256 && pixels != 512) throw new IllegalArgumentException("Entity size must be 256 or 512");
        ENTITY_SIZE.set(pixels);
        SPEC.save();
    }

    public static void includeNonLiving(boolean enabled) {
        INCLUDE_NON_LIVING.set(enabled);
        SPEC.save();
    }
}
