package yagen.waitmydawn.mkb_export.client;

/**
 * 隔离世界装备回归的版本适配，不参与普通采样。
 */
final class EquipmentProbePlatform {
    static net.minecraft.world.item.ItemStack sword(net.minecraft.server.MinecraftServer server) {
        var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD);
        sword.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 3);
        return sword;
    }
}
