package yagen.waitmydawn.mkb_export.client;

import com.google.gson.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import yagen.waitmydawn.mkb_export.api.EntityVariantProvider;
import java.util.*;

/** 有限外观域优先完整组合；超预算时保留基线、单轴覆盖及有界组合，并报告遗漏。 */
final class EntityVariants {
    private static final Map<String,JsonObject> COVERAGE=new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<EntityVariantProvider> PROVIDERS=providers();
    private record Bound(EntityVariantProvider provider,EntityVariantProvider.Axis axis) {}
    private static List<EntityVariantProvider> providers(){var out=new ArrayList<EntityVariantProvider>();out.add(new Common());out.add(new IceAndFireVariants());var it=ServiceLoader.load(EntityVariantProvider.class).iterator();for(int i=0;i<128;i++)try{if(!it.hasNext())break;out.add(it.next());}catch(ServiceConfigurationError|RuntimeException failed){com.mojang.logging.LogUtils.getLogger().warn("Variant provider discovery failed",failed);}return List.copyOf(out);}
    static void clear(){COVERAGE.clear();}
    static JsonObject coverage(String id){var c=COVERAGE.get(id);return c==null?new JsonObject():c.deepCopy();}
    static List<EntitySamples.Sample> sample(Mob source,net.minecraft.server.MinecraftServer server)throws Exception {
        String id=BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString();var axes=new ArrayList<Bound>();var names=new HashSet<String>();
        for(var provider:PROVIDERS)if(AppearanceAdapters.invoke(()->provider.supports(source)))for(var axis:AppearanceAdapters.invoke(()->provider.axes(source))){if(!axis.id().matches("[a-z0-9_.-]{1,40}")||axes.size()>=16||!names.add(axis.id())||axis.values().isEmpty()||axis.values().size()>256)throw new IllegalArgumentException("Invalid variant axis");var values=new HashSet<String>();for(var value:axis.values())if(value.id().length()>128||value.label().length()>128||!values.add(value.id()))throw new IllegalArgumentException("Invalid variant value");axes.add(new Bound(provider,axis));}
        var info=new JsonObject();var domains=new JsonObject();info.add("axes",domains);info.addProperty("scope","DECLARED_AXES_ONLY");info.addProperty("exhaustiveEntityAppearance",false);long possible=1;for(var axis:axes){var values=new JsonArray();for(var value:axis.axis().values()){var v=new JsonObject();v.addProperty("id",value.id());v.addProperty("label",value.label());values.add(v);}domains.add(axis.axis().id(),values);possible=Math.min(1000000000L,possible*axis.axis().values().size());}
        int cap=AppearanceAdapters.policy().variants();var choices=new LinkedHashSet<List<Integer>>();var baseline=new ArrayList<Integer>(Collections.nCopies(axes.size(),0));
        if(!axes.isEmpty()&&cap>0){choices.add(List.copyOf(baseline));if(possible<=cap)cartesian(axes,0,baseline,choices,cap);else{for(int a=0;a<axes.size()&&choices.size()<cap;a++)for(int v=0;v<axes.get(a).axis().values().size()&&choices.size()<cap;v++){var row=new ArrayList<>(Collections.nCopies(axes.size(),0));row.set(a,v);choices.add(List.copyOf(row));}for(int a=0;a<axes.size()&&choices.size()<cap;a++)for(int b=a+1;b<axes.size()&&choices.size()<cap;b++)for(int x=1;x<axes.get(a).axis().values().size()&&choices.size()<cap;x++)for(int y=1;y<axes.get(b).axis().values().size()&&choices.size()<cap;y++){var row=new ArrayList<>(Collections.nCopies(axes.size(),0));row.set(a,x);row.set(b,y);choices.add(List.copyOf(row));}}}
        info.addProperty("possible",axes.isEmpty()?0:possible);info.addProperty("sampled",choices.size());info.addProperty("budget",cap);info.addProperty("truncated",!axes.isEmpty()&&choices.size()<possible);info.addProperty("strategy",possible<=cap?"FULL_CARTESIAN":"BOUNDED_SINGLE_AND_PAIRWISE");COVERAGE.put(id,info);
        var result=new ArrayList<EntitySamples.Sample>();var data=EntitySamples.sanitized(source);
        for(var choice:choices){try{var copy=source.getType().create(source.level());if(!(copy instanceof Mob))throw new IllegalArgumentException("Variant has no Mob factory");copy.load(data.copy());var selected=new JsonObject();var providers=new JsonObject();for(int a=0;a<axes.size();a++){var bound=axes.get(a);String value=bound.axis().values().get(choice.get(a)).id();AppearanceAdapters.invoke(()->{bound.provider().apply(copy,bound.axis().id(),value);return null;});selected.addProperty(bound.axis().id(),value);providers.addProperty(bound.provider().id(),bound.provider().version());}var sample=EntitySamples.sample(copy,"DECLARED_STATE",false,server);sample.state().getAsJsonObject("traits").add("axes",selected);sample.state().getAsJsonObject("traits").add("variantProviders",providers);yagen.waitmydawn.mkb_export.core.EntityAppearance.key(sample.state());result.add(sample);}catch(Exception failure){info.addProperty("lastFailure",EntitySamples.describe(failure));info.addProperty("failed",info.has("failed")?info.get("failed").getAsInt()+1:1);}}
        info.addProperty("prepared",result.size());if(result.size()<choices.size())info.addProperty("truncated",true);return List.copyOf(result);
    }
    private static void cartesian(List<Bound> axes,int a,List<Integer> row,Set<List<Integer>> output,int cap){if(output.size()>=cap)return;if(a==axes.size()){output.add(List.copyOf(row));return;}for(int v=0;v<axes.get(a).axis().values().size();v++){row.set(a,v);cartesian(axes,a+1,row,output,cap);}}
    private static final class Common implements EntityVariantProvider {
        public String id(){return "mkb_export:common_variants";}public String version(){return "1";}public boolean supports(Entity e){return e instanceof Mob;}
        private Axis axis(String id,String... values){return new Axis(id,Arrays.stream(values).map(v->new Value(v,v)).toList());}
        public List<Axis> axes(Entity e)throws Exception {var out=new ArrayList<Axis>();
            if(e instanceof Sheep){out.add(new Axis("color",Arrays.stream(DyeColor.values()).map(v->new Value(v.getName(),net.minecraft.network.chat.Component.translatable("color.minecraft."+v.getName()).getString())).toList()));out.add(axis("sheared","false","true"));}
            if(e instanceof Creeper)out.add(axis("charged","false","true"));
            if(e instanceof TamableAnimal)out.add(axis("tamed","false","true"));
            if(e instanceof Villager){out.add(new Axis("profession",BuiltInRegistries.VILLAGER_PROFESSION.keySet().stream().sorted(Comparator.comparing((net.minecraft.resources.ResourceLocation v)->!v.getPath().equals("none")).thenComparing(Object::toString)).map(v->new Value(v.toString(),net.minecraft.network.chat.Component.translatable("entity.minecraft.villager."+v.getPath()).getString())).toList()));out.add(new Axis("biome",BuiltInRegistries.VILLAGER_TYPE.keySet().stream().sorted().map(v->new Value(v.toString(),v.getPath())).toList()));out.add(axis("level","1","2","3","4","5"));}
            if(e instanceof VariantHolder<?> holder){Object variant=holder.getVariant();if(variant instanceof Enum<?> en)out.add(new Axis("variant",Arrays.stream(en.getDeclaringClass().getEnumConstants()).map(v->new Value(((Enum<?>)v).name().toLowerCase(Locale.ROOT),((Enum<?>)v).name().toLowerCase(Locale.ROOT))).toList()));else if(variant!=null){String kind=e instanceof Cat?"cat_variant":BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()+"_variant";try{var registry=e.level().registryAccess().registryOrThrow(ResourceKey.createRegistryKey(Platform.location("minecraft",kind)));out.add(new Axis("variant",registry.keySet().stream().sorted().map(v->new Value(v.toString(),v.getPath())).toList()));}catch(RuntimeException unavailable){}}}
            var copy=e.getType().create(e.level());if(copy instanceof Mob mob){mob.setBaby(true);if(mob.isBaby()&&!(e instanceof Villager)&&!IceAndFireVariants.dragon(e))out.add(axis("age","adult","baby"));}return out;
        }
        @SuppressWarnings({"unchecked","rawtypes"}) public void apply(Entity e,String axis,String value)throws Exception {switch(axis){
            case "color"->((Sheep)e).setColor(DyeColor.byName(value,DyeColor.WHITE));case "sheared"->((Sheep)e).setSheared(Boolean.parseBoolean(value));case "age"->((Mob)e).setBaby(value.equals("baby"));
            case "charged"->{var data=EntitySamples.sanitized(e);data.putBoolean("powered",Boolean.parseBoolean(value));e.load(data);}
            case "tamed"->{var tame=(TamableAnimal)e;for(var method:tame.getClass().getMethods())if(method.getName().equals("setTame")&&method.getParameterCount()>0&&method.getParameterTypes()[0]==boolean.class){if(method.getParameterCount()==1)method.invoke(tame,Boolean.parseBoolean(value));else if(method.getParameterCount()==2)method.invoke(tame,Boolean.parseBoolean(value),false);break;}if(tame.isTame()!=Boolean.parseBoolean(value))throw new IllegalArgumentException("Tame state was not applied");}
            case "profession"->{var mob=(Villager)e;mob.setVillagerData(mob.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(location(value))));}
            case "biome"->{var mob=(Villager)e;mob.setVillagerData(mob.getVillagerData().setType(BuiltInRegistries.VILLAGER_TYPE.get(location(value))));}
            case "level"->{var mob=(Villager)e;mob.setVillagerData(mob.getVillagerData().setLevel(Integer.parseInt(value)));}
            case "variant"->{var holder=(VariantHolder)e;Object current=holder.getVariant();if(current instanceof Enum<?> en){for(var item:en.getDeclaringClass().getEnumConstants())if(((Enum<?>)item).name().equalsIgnoreCase(value)){holder.setVariant(item);return;}throw new IllegalArgumentException("Unknown enum variant");}String kind=e instanceof Cat?"cat_variant":BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()+"_variant";var key=ResourceKey.createRegistryKey(Platform.location("minecraft",kind));var registry=e.level().registryAccess().registryOrThrow(key);holder.setVariant(current instanceof net.minecraft.core.Holder<?>?registry.getHolder(ResourceKey.create(key,location(value))).orElseThrow():registry.get(location(value)));}
            default->throw new IllegalArgumentException("Unknown variant axis");}}
        private net.minecraft.resources.ResourceLocation location(String value){String[] p=value.split(":",2);return Platform.location(p[0],p[1]);}
    }
}
