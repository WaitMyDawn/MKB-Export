package yagen.waitmydawn.mkb_export.api.trade;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import java.util.Optional;

/** 通过 ServiceLoader 注册程序式交易的声明适配器。不得生成随机 Offer、推进世界或产生实体。 */
public interface TradeRuleAdapter {
    /** 不支持时返回 empty；成功值必须含 inputs、output 及 DECLARATIVE/RANDOM_RULE 状态，并保留随机参数与条件。 */
    Optional<JsonObject> describe(Object factory,MinecraftServer server)throws Exception;
}
