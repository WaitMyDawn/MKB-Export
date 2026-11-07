package yagen.waitmydawn.mkb_export.core;

/**
 * 世界方向转为根实体局部方向；所有子部件使用同一参考系。
 */
public final class EntityLocalTransform {
    private EntityLocalTransform() {
    }

    public record Offset(double x, double y, double z) {
    }

    public static Offset toRoot(double x, double y, double z, double yaw) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(yaw) || x * x + y * y + z * z > 65536)
            throw new IllegalArgumentException("Invalid composition offset");
        double angle = Math.toRadians(yaw), c = Math.cos(angle), s = Math.sin(angle);
        return new Offset(x * c + z * s, y, z * c - x * s);
    }
}
