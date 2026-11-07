package yagen.waitmydawn.mkb_export.client;
import com.google.gson.*;
/** 铁砧是依赖两份堆叠和玩家上下文的操作，不伪装成静态 RecipeManager 配方。 */
final class WorkstationRules {
    static JsonArray capture(JsonArray recipes){
        var rows=new JsonArray();var row=new JsonObject();row.addProperty("id","minecraft:anvil");row.addProperty("station","minecraft:anvil");row.addProperty("coverage","VANILLA_BASE_RULES");row.addProperty("inputSlots",2);row.addProperty("outputSlots",1);var operations=new JsonArray();
        operations.add("材料修复：每个有效修复材料最多恢复最大耐久的四分之一；有效材料由物品的修复判定决定");
        operations.add("同类物品合并：剩余耐久相加并附加最大耐久12%的奖励，上限为最大耐久");
        operations.add("附魔合并：同级且可提升时升一级，否则取较高等级；仍受等级上限、冲突和物品适用性限制");
        operations.add("重命名通常增加1级费用；既有工作惩罚、附魔稀有度和附魔书影响费用，不能只由物品注册名确定");
        operations.add("生存模式通常在费用达到40级时拒绝操作；仅重命名有单独的39级处理，创造模式具有不同限制");
        row.add("operations",operations);row.addProperty("limitation","Baseline vanilla AnvilMenu semantics, not a captured final result. Forge/NeoForge anvil hooks, custom repair methods, components/NBT, enchantments, prior-work cost and player mode may override behavior");rows.add(row);rows.add(yagen.waitmydawn.mkb_export.core.ProcessingCoverage.describe(recipes));return rows;
    }
    private WorkstationRules(){}
}
