package yagen.waitmydawn.mkb_export.core;

import java.util.function.IntBinaryOperator;

/**
 * 实际 Alpha 轮廓用于取景，碰撞箱只提供初始比例。
 */
public final class EntityFraming {
    private EntityFraming() {
    }

    public record Bounds(double left, double top, double right, double bottom) {
        public double width() {
            return right - left;
        }

        public double height() {
            return bottom - top;
        }

        public Bounds union(Bounds other) {
            return new Bounds(Math.min(left, other.left), Math.min(top, other.top), Math.max(right, other.right), Math.max(bottom, other.bottom));
        }

        public boolean near(Bounds other, double tolerance) {
            return Math.abs(left - other.left) <= tolerance && Math.abs(top - other.top) <= tolerance && Math.abs(right - other.right) <= tolerance && Math.abs(bottom - other.bottom) <= tolerance;
        }
    }

    public static Bounds bounds(int width, int height, IntBinaryOperator alpha) {
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (alpha.applyAsInt(x, y) > 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
        return right < 0 ? null : new Bounds(left, top, right + 1, bottom + 1);
    }

    public static boolean edge(Bounds b, int size) {
        return b != null && (b.left() < 2 || b.top() < 2 || b.right() > size - 2 || b.bottom() > size - 2);
    }

    public static Bounds world(Bounds pixels, int size, double scale, double x, double y) {
        return new Bounds((pixels.left() * 128 / size - x) / scale, (pixels.top() * 128 / size - y) / scale, (pixels.right() * 128 / size - x) / scale, (pixels.bottom() * 128 / size - y) / scale);
    }

    public record Fit(double scale, double x, double y) {
    }

    public static Fit fit(Bounds b) {
        double scale = 102.4 / Math.max(b.width(), b.height());
        return new Fit(scale, 64 - (b.left() + b.right()) * .5 * scale, 64 - (b.top() + b.bottom()) * .5 * scale);
    }

    public static double occupancy(Bounds b, int size) {
        return b == null ? 0 : Math.max(b.width(), b.height()) / size;
    }

    public static boolean acceptable(Bounds b, int size) {
        return b != null && !edge(b, size) && occupancy(b, size) >= .76 && occupancy(b, size) <= .84 && Math.abs((b.left() + b.right()) * .5 - size * .5) <= size * .02 && Math.abs((b.top() + b.bottom()) * .5 - size * .5) <= size * .02;
    }

    /**
     * 用最终像素闭环修正，避免粗探测的过大历史轮廓永久限制主体尺寸。
     */
    public static Fit refine(Fit current, Bounds pixels, int size) {
        if (edge(pixels, size)) {
            double factor = .65;
            return new Fit(current.scale() * factor, 64 + (current.x() - 64) * factor, 64 + (current.y() - 64) * factor);
        }
        double factor = Math.max(.5, Math.min(2, .8 / occupancy(pixels, size)));
        return new Fit(current.scale() * factor, 64 + (current.x() - (pixels.left() + pixels.right()) * .5 * 128 / size) * factor, 64 + (current.y() - (pixels.top() + pixels.bottom()) * .5 * 128 / size) * factor);
    }
}
