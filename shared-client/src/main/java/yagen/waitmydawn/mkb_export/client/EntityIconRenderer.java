package yagen.waitmydawn.mkb_export.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** 只渲染未加入世界的独立实体实例；不会生成、移动或 tick 世界内实体。 */
final class EntityIconRenderer {
    record Result(byte[] png,String framing,int attempts,yagen.waitmydawn.mkb_export.core.EntityFraming.Bounds bounds,java.util.List<Integer> emptyParts) {}
    static Result render(Entity entity,int size,boolean reversed,java.util.List<Entity> parts)throws Exception {
        if(size!=256&&size!=512)throw new IllegalArgumentException("Entity size must be 256 or 512");
        float dimension=Math.max(entity.getBbHeight(),entity.getBbWidth());if(!Float.isFinite(dimension))throw new IllegalArgumentException("Non-finite entity bounds");if(dimension<=0)dimension=1.8f;
        double initialScale=40/dimension;double scale=initialScale;var all=(yagen.waitmydawn.mkb_export.core.EntityFraming.Bounds)null;
        var previous=(yagen.waitmydawn.mkb_export.core.EntityFraming.Bounds)null;int attempts=0;
        for(int i=0;i<6;i++,scale/=2) {
            try(var image=pass(entity,128,scale,64,64,reversed,parts)) {
                attempts++;var bounds=yagen.waitmydawn.mkb_export.core.EntityFraming.bounds(128,128,(x,y)->image.getPixelRGBA(x,y)>>>24);
                if(bounds==null)continue;var world=yagen.waitmydawn.mkb_export.core.EntityFraming.world(bounds,128,scale,64,64);all=all==null?world:all.union(world);
                double tolerance=Math.max(world.width(),world.height())*.06+2/scale;
                if(!yagen.waitmydawn.mkb_export.core.EntityFraming.edge(bounds,128)&&previous!=null&&world.near(previous,tolerance)){break;}previous=world;
            }
        }
        if(all==null&&EntityRenderCompatibility.canRecover(entity)){EntityRenderCompatibility.useBase();return render(entity,size,reversed,parts);}
        var fit=all==null?new yagen.waitmydawn.mkb_export.core.EntityFraming.Fit(initialScale,64,64):yagen.waitmydawn.mkb_export.core.EntityFraming.fit(all);
        yagen.waitmydawn.mkb_export.core.EntityFraming.Bounds last=null;
        for(int i=0;i<8;i++) {
            try(var image=pass(entity,size,fit.scale(),fit.x(),fit.y(),reversed,parts)) {
                attempts++;var bounds=yagen.waitmydawn.mkb_export.core.EntityFraming.bounds(size,size,(x,y)->image.getPixelRGBA(x,y)>>>24);
                if(bounds==null)return new Result(image.asByteArray(),"EMPTY_RENDER",attempts,null,java.util.stream.IntStream.range(-1,parts.size()).boxed().toList());
                boolean edge=yagen.waitmydawn.mkb_export.core.EntityFraming.edge(bounds,size);
                boolean accepted=yagen.waitmydawn.mkb_export.core.EntityFraming.acceptable(bounds,size)&&last!=null&&bounds.near(last,size*.02);
                if(accepted||i==7){var missing=new java.util.ArrayList<Integer>();if(!parts.isEmpty())try(var rootCheck=pass(entity,size,fit.scale(),fit.x(),fit.y(),reversed,java.util.List.of(),true)){if(yagen.waitmydawn.mkb_export.core.EntityFraming.bounds(size,size,(x,y)->rootCheck.getPixelRGBA(x,y)>>>24)==null)missing.add(-1);}for(int partIndex=0;partIndex<parts.size();partIndex++)try(var check=pass(entity,size,fit.scale(),fit.x(),fit.y(),reversed,java.util.List.of(parts.get(partIndex)),false)){if(yagen.waitmydawn.mkb_export.core.EntityFraming.bounds(size,size,(x,y)->check.getPixelRGBA(x,y)>>>24)==null)missing.add(partIndex);}return new Result(image.asByteArray(),accepted?"FITTED":edge?"POSSIBLY_CLIPPED":"UNSTABLE_FRAMING",attempts,bounds,java.util.List.copyOf(missing));}
                last=bounds;
                if(!yagen.waitmydawn.mkb_export.core.EntityFraming.acceptable(bounds,size))fit=yagen.waitmydawn.mkb_export.core.EntityFraming.refine(fit,bounds,size);
            }
        }throw new IllegalStateException("Unreachable framing state");
    }
    private static NativeImage pass(Entity entity,int size,double scale,double offsetX,double offsetY,boolean reversed,java.util.List<Entity> parts)throws Exception {
        return pass(entity,size,scale,offsetX,offsetY,reversed,parts,true);
    }
    private static NativeImage pass(Entity entity,int size,double scale,double offsetX,double offsetY,boolean reversed,java.util.List<Entity> parts,boolean includeRoot)throws Exception {
        var client=Minecraft.getInstance();RenderSystem.assertOnRenderThread();
        entity.setYRot(0);entity.setXRot(0);entity.yRotO=0;entity.xRotO=0;
        if(entity instanceof net.minecraft.world.entity.LivingEntity living){living.yBodyRot=0;living.yBodyRotO=0;living.yHeadRot=0;living.yHeadRotO=0;}
        var dispatcher=client.getEntityRenderDispatcher();var previousCamera=new Quaternionf(dispatcher.cameraOrientation());
        var view=new Quaternionf().rotateX((float)Math.toRadians(30)).rotateY((float)Math.toRadians(-45+(reversed?180:0)));
        try(var state=new RenderState()) {
            dispatcher.overrideCameraOrientation(view.conjugate(new Quaternionf()).rotateY((float)Math.PI));
            var target=new TextureTarget(size,size,true,Minecraft.ON_OSX);
            try {
                target.setClearColor(0,0,0,0);RenderSystem.disableScissor();target.clear(Minecraft.ON_OSX);target.bindWrite(true);
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,128,128,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.setShaderColor(1,1,1,1);RenderSystem.enableDepthTest();RenderSystem.depthMask(true);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.colorMask(true,true,true,true);
                com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
                var pose=new PoseStack();pose.translate(offsetX,offsetY,0);pose.scale((float)scale,(float)-scale,(float)scale);pose.mulPose(view);pose.translate(0,-entity.getBbHeight()*.5,0);
                var buffers=client.renderBuffers().bufferSource();
                try{if(includeRoot)EntityRenderCompatibility.draw(entity,0,0,0,0,pose,buffers,15728880);for(var part:parts)EntityRenderCompatibility.draw(part,part.getX(),part.getY(),part.getZ(),part.getYRot(),pose,buffers,15728880);}finally{buffers.endBatch();}
                var image=new NativeImage(size,size,true);try{GlStateManager._bindTexture(target.getColorTextureId());image.downloadTexture(0,false);image.flipY();return image;}catch(Throwable failure){image.close();throw failure;}
            }finally{target.destroyBuffers();}
        }finally{dispatcher.overrideCameraOrientation(previousCamera);}
    }
}
