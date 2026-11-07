package yagen.waitmydawn.mkb_export.client;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/** 恢复仅作用于导出副本；不修改玩家的Flywheel配置或共享渲染器标志。 */
final class EntityRenderCompatibility {
    private static final ThreadLocal<Boolean> BASE=ThreadLocal.withInitial(()->false);
    static void reset(){BASE.set(false);}
    static boolean base(){return BASE.get();}
    static void useBase(){BASE.set(true);}
    static String id(Entity e){return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();}
    static boolean canRecover(Entity e){return java.util.Set.of("alexscaves:raycat","alexscaves:underzealot").contains(id(e))&&!base();}
    static void draw(Entity e,double x,double y,double z,float yaw,PoseStack pose,MultiBufferSource buffers,int light)throws Exception {
        if(id(e).equals("create:package")){drawPackage(e,pose,buffers,light);return;}
        if(base()&&java.util.Set.of("alexscaves:raycat","alexscaves:underzealot").contains(id(e))){Platform.renderBaseModel(e,pose,buffers,light);return;}
        Minecraft.getInstance().getEntityRenderDispatcher().render(e,x,y,z,yaw,0,pose,buffers,light);
    }
    private static void drawPackage(Entity e,PoseStack pose,MultiBufferSource buffers,int light)throws Exception {
        String renderer="com.simibubi.create.content.logistics.box.PackageRenderer";Class<?> models=Class.forName("com.simibubi.create.AllPartialModels");var map=(java.util.Map<?,?>)models.getField("PACKAGES").get(null);var ordered=new java.util.TreeMap<String,Object>();map.forEach((key,value)->ordered.put(key.toString(),value));
        var box=(net.minecraft.world.item.ItemStack)e.getClass().getField("box").get(e);String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(box.getItem()).toString();Object model=ordered.get(item);if(model==null)model=ordered.get("create:cardboard_block");if(model==null&&box.isEmpty()&&!ordered.isEmpty())model=ordered.firstEntry().getValue();if(model==null)throw new IllegalStateException("Create package CPU model is unavailable: "+item+"; registered="+ordered.keySet());
        for(var method:Class.forName(renderer).getMethods())if(method.getName().equals("renderBox")&&method.getParameterCount()==6){method.invoke(null,e,0f,pose,buffers,light,model);return;}throw new IllegalStateException("Create package CPU renderer is unavailable");
    }
}
