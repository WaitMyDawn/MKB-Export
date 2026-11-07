package yagen.waitmydawn.mkb_export.api.structure;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import java.util.List;
/** 通过ServiceLoader声明多方块布局。实现必须只读，不得放置方块、创建实体或推进世界tick。
 * 记录需遵循multiblocks能力v1：id/status/name/controller/size/origin/cells/conditions/steps。
 * cells使用相对控制器的整数坐标pos、方块注册名block及可选role。布局是声明的工作示例，
 * 不等同于验证任意世界实例。条件、版本适用范围、替代材料和限制必须明确声明。 */
public interface MultiblockProvider { List<JsonObject> describe(MinecraftServer server)throws Exception; }
