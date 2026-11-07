package yagen.waitmydawn.mkb_export.client;
import yagen.waitmydawn.mkb_export.api.EntityVariantProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

/** 冰火各移植版通过自身颜色域和巨魔类型取值，不将未知整数作为通用变体枚举。 */
final class IceAndFireVariants implements EntityVariantProvider {
    static boolean dragon(Entity e){return Set.of("iceandfire:fire_dragon","iceandfire:ice_dragon","iceandfire:lightning_dragon").contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());}
    public String id(){return "mkb_export:iceandfire_variants";}public String version(){return "2";}
    public boolean supports(Entity e){return dragon(e)||BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("iceandfire:troll");}
    private static String name(Object value)throws Exception {if(value instanceof Enum<?> v)return v.name().toLowerCase(Locale.ROOT);try{return value.getClass().getMethod("getName").invoke(value).toString();}catch(NoSuchMethodException record){return value.getClass().getMethod("name").invoke(value).toString();}}
    private static LinkedHashMap<String,Object> domain(Entity e)throws Exception {
        var values=new LinkedHashMap<String,Object>();
        if(dragon(e)){try{var type=e.getClass().getField("dragonType").get(e);List<?> colors;try{colors=(List<?>)type.getClass().getMethod("colors").invoke(type);}catch(NoSuchMethodException legacy){colors=(List<?>)Class.forName("com.iafenvoy.iceandfire.data.DragonColor").getMethod("getColorsByType",type.getClass()).invoke(null,type);}for(var value:colors)values.put(name(value),name(value));}
            catch(NoSuchFieldException|NoSuchMethodException|ClassNotFoundException absent){for(int i=0;i<64;i++){String value=e.getClass().getMethod("getVariantName",int.class).invoke(e,i).toString();if(values.containsKey(value))break;values.put(value,i);}}}
        else {var type=e.getClass().getMethod("getTrollType").getReturnType();Object[] entries=type.isEnum()?type.getEnumConstants():((List<?>)type.getMethod("values").invoke(null)).toArray();for(var value:entries)values.put(name(value),value);}
        if(values.isEmpty())throw new IllegalArgumentException("IceAndFire variant domain is empty");return values;
    }
    private static String label(String name){return switch(name){case "red"->"红色";case "green"->"绿色";case "bronze"->"青铜色";case "gray"->"灰色";case "blue"->"蓝色";case "white"->"白色";case "sapphire"->"宝石蓝";case "silver"->"银色";case "electric"->"电蓝色";case "amethyst"->"紫晶色";case "copper"->"铜色";case "black"->"黑色";case "forest"->"森林";case "frost"->"霜冻";case "mountain"->"山地";default->name;};}
    public List<Axis> axes(Entity e)throws Exception{var axes=new ArrayList<Axis>();axes.add(new Axis("variant",domain(e).keySet().stream().map(v->new Value(v,label(v))).toList()));if(dragon(e)){axes.add(new Axis("growth_stage",List.of(new Value("1","第1阶段"),new Value("2","第2阶段"),new Value("3","第3阶段"),new Value("4","第4阶段"),new Value("5","第5阶段"))));axes.add(new Axis("sex",List.of(new Value("female","雌性"),new Value("male","雄性"))));}return List.copyOf(axes);}
    public void apply(Entity e,String axis,String value)throws Exception {if(dragon(e)&&axis.equals("growth_stage")){int stage=Integer.parseInt(value);if(stage<1||stage>5)throw new IllegalArgumentException("Invalid dragon stage");e.getClass().getMethod("setAgeInDays",int.class).invoke(e,(stage-1)*25);int actual=((Number)e.getClass().getMethod("getDragonStage").invoke(e)).intValue();if(actual!=stage)throw new IllegalArgumentException("Dragon stage setter did not take effect");return;}if(dragon(e)&&axis.equals("sex")){if(!Set.of("female","male").contains(value))throw new IllegalArgumentException("Invalid dragon sex");e.getClass().getMethod("setGender",boolean.class).invoke(e,value.equals("male"));return;}if(!axis.equals("variant"))throw new IllegalArgumentException("Unknown IceAndFire axis");var entries=domain(e);Object selected=entries.get(value);if(selected==null)throw new IllegalArgumentException("Unknown IceAndFire variant: "+value);
        if(dragon(e)){if(selected instanceof Integer index)e.getClass().getMethod("setVariant",int.class).invoke(e,index);else e.getClass().getMethod("setVariant",String.class).invoke(e,selected.toString());}
        else e.getClass().getMethod("setTrollType",e.getClass().getMethod("getTrollType").getReturnType()).invoke(e,selected);
    }
    static void normalizeFactory(Entity e)throws Exception {if(!dragon(e))return;var setter=e.getClass().getMethod("getVariant");if(setter.getReturnType()!=String.class)return;var values=domain(e);if(!values.containsKey(setter.invoke(e).toString()))new IceAndFireVariants().apply(e,"variant",values.keySet().iterator().next());}
    static void prepareDetached(Entity entity)throws Exception {normalizeFactory(entity);if(dragon(entity))entity.getClass().getMethod("setAgeInDays",int.class).invoke(entity,100);}
    static void describe(Entity entity,com.google.gson.JsonObject traits){if(!dragon(entity))return;try{int stage=((Number)entity.getClass().getMethod("getDragonStage").invoke(entity)).intValue();traits.addProperty("age",stage==1?"baby":stage>=4?"adult":"juvenile");traits.addProperty("growthStage",stage);traits.addProperty("ageDays",((Number)entity.getClass().getMethod("getAgeInDays").invoke(entity)).intValue());traits.addProperty("sex",(boolean)entity.getClass().getMethod("isMale").invoke(entity)?"male":"female");}catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot read dragon growth/sex",failure);}}
}
