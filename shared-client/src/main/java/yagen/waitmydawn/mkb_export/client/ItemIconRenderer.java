package yagen.waitmydawn.mkb_export.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import java.io.IOException;

/** 使用游戏 GUI 物品渲染，保留模型、方块面、颜色处理和模组自定义渲染。 */
final class ItemIconRenderer implements AutoCloseable {
    private final Minecraft minecraft = Minecraft.getInstance();
    private final int size;
    private TextureTarget target;

    ItemIconRenderer(int size) { this.size = size; }
    NativeImage render(ItemStack stack) throws IOException {
        RenderSystem.assertOnRenderThread();
        if (stack.isEmpty()) throw new IOException("Empty default item stack");
        try (var state = new RenderState()) {
            var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 42);
            if (model == minecraft.getModelManager().getMissingModel()) throw new IOException("Missing baked item model");
            if (target == null) {
                target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
                target.setClearColor(0, 0, 0, 0);
            }
            RenderSystem.disableScissor();
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 16, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.colorMask(true, true, true, true);
            Lighting.setupFor3DItems();
            var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            try { graphics.renderItem(stack, 0, 0, 42); }
            finally { graphics.flush(); }
            var image = new NativeImage(size, size, true);
            try {
                GlStateManager._bindTexture(target.getColorTextureId());
                image.downloadTexture(0, false); image.flipY();
                return image;
            } catch (Throwable failure) { image.close(); throw failure; }
        }
    }
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (target != null) {
            try (var state = new RenderState()) { target.destroyBuffers(); target = null; }
        }
    }
}
