package yagen.waitmydawn.mkb_export.core;

import java.util.concurrent.atomic.LongAdder;

/**
 * 仅在当前采样线程禁止实体入场，关闭后不影响正常世界生成。
 */
public final class EntitySamplingScope implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final LongAdder BLOCKED = new LongAdder();
    private static final ThreadLocal<Object> PERMITTED = new ThreadLocal<>();
    private final Object previousPermitted;
    private final Thread owner = Thread.currentThread();
    private boolean closed;

    private EntitySamplingScope() {
        this(null);
    }

    private EntitySamplingScope(Object permitted) {
        previousPermitted = PERMITTED.get();
        PERMITTED.set(permitted);
        DEPTH.set(DEPTH.get() + 1);
    }

    public static EntitySamplingScope enter() {
        return new EntitySamplingScope();
    }

    public static EntitySamplingScope enterPermittingEntity(Object entity) {
        return new EntitySamplingScope(java.util.Objects.requireNonNull(entity));
    }

    public static boolean rejectEntityJoin(Object entity) {
        if (DEPTH.get() > 0 && entity == PERMITTED.get()) return false;
        return rejectEntityJoin();
    }

    public static boolean rejectEntityJoin() {
        if (DEPTH.get() == 0) return false;
        BLOCKED.increment();
        return true;
    }

    public static long blockedCount() {
        return BLOCKED.sum();
    }

    public void close() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Sampling scope closed on another thread");
        if (closed) return;
        closed = true;
        int remaining = DEPTH.get() - 1;
        if (remaining == 0) DEPTH.remove();
        else DEPTH.set(remaining);
        if (previousPermitted == null) PERMITTED.remove();
        else PERMITTED.set(previousPermitted);
    }
}
