package yagen.waitmydawn.mkb_export.core;

/**
 * 隐藏类地址和Lambda序号不是规则身份，不能进入持久化配置指纹。
 */
public final class SpawnConfiguration {
    private SpawnConfiguration() {
    }

    public static String stableCodeName(String name) {
        int slash = name.indexOf('/');
        String stable = slash < 0 ? name : name.substring(0, slash);
        int lambda = stable.indexOf("$$Lambda");
        return lambda < 0 ? stable : stable.substring(0, lambda) + "$$Lambda";
    }
}
