package yagen.waitmydawn.mkb_export.client;

import java.lang.reflect.*;
import java.util.*;

/** 固定字段映射随目标游戏版本构建；未知工厂不得依赖字段声明顺序猜测语义。 */
final class RuleFields {
    private static final Properties MAPPINGS=new Properties();
    static {try(var input=RuleFields.class.getResourceAsStream("/mkb_export/fields.properties")){if(input!=null)MAPPINGS.load(input);}catch(java.io.IOException invalid){throw new ExceptionInInitializerError(invalid);}}
    static Object get(Object object,String name)throws ReflectiveOperationException {
        Class<?> type=object instanceof Class<?> c?c:object.getClass();
        for(Class<?> owner=type;owner!=null;owner=owner.getSuperclass()){
            for(String candidate:List.of(name,MAPPINGS.getProperty(owner.getName()+"#"+name,name))){
                try{var field=owner.getDeclaredField(candidate);field.setAccessible(true);return field.get(object instanceof Class<?>?null:object);}catch(NoSuchFieldException missing){}
            }
        }
        throw new NoSuchFieldException(type.getName()+"#"+name);
    }
    static int number(Object object,String field)throws ReflectiveOperationException{return ((Number)get(object,field)).intValue();}
    private RuleFields(){}
}
