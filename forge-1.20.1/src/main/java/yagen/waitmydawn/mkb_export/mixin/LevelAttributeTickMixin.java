package yagen.waitmydawn.mkb_export.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

import yagen.waitmydawn.mkb_export.client.EntityAttributeSampler;
import yagen.waitmydawn.mkb_export.core.EntitySamplingScope;

/**
 * 仅接管采样器拥有的实体；错误成为失败记录，不改变其他实体的异常处理。
 */
@Mixin(Level.class)
abstract class LevelAttributeTickMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void guardInstalled(CallbackInfo callback) {
        EntityAttributeSampler.tickGuardInstalled();
    }

    @Inject(method = {"guardEntityTick", "m_46653_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private <T extends Entity> void sampleTick(Consumer<T> tick, T entity, CallbackInfo callback) {
        if (!EntityAttributeSampler.owns(entity)) return;
        try (var scope = EntitySamplingScope.enter()) {
            if (Boolean.getBoolean("mkb_export.attributeProbe") && Boolean.getBoolean("mkb_export.attributeProbe.failTick"))
                throw new IllegalStateException("Injected sampling tick failure");
            tick.accept(entity);
        } catch (Exception failure) {
            EntityAttributeSampler.entityFailed(entity, failure);
        }
        callback.cancel();
    }
}
