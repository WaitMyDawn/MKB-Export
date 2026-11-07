package yagen.waitmydawn.mkb_export.core;

/**
 * 不同游戏版本的修饰器枚举映射到统一运算名。
 */
public final class AttributeOperations {
    private AttributeOperations() {
    }

    public static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite attribute value");
        return value;
    }

    public static String canonical(String operation) {
        return switch (operation) {
            case "ADDITION", "ADD_VALUE" -> "ADD_VALUE";
            case "MULTIPLY_BASE", "ADD_MULTIPLIED_BASE" -> "ADD_MULTIPLIED_BASE";
            case "MULTIPLY_TOTAL", "ADD_MULTIPLIED_TOTAL" -> "ADD_MULTIPLIED_TOTAL";
            default -> throw new IllegalArgumentException("Unknown attribute operation: " + operation);
        };
    }
}
