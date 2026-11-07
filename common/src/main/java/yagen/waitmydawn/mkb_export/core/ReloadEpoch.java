package yagen.waitmydawn.mkb_export.core;

/**
 * 重载开始立即使旧租约失效；成功同步之后才允许获取新租约。
 */
public final class ReloadEpoch {
    private long generation;
    private boolean loading;

    public synchronized long begin() {
        loading = true;
        return ++generation;
    }

    public synchronized void ready() {
        loading = false;
    }

    public synchronized long acquire() {
        if (loading) throw new IllegalStateException("Server data is reloading");
        return generation;
    }

    public synchronized boolean valid(long lease) {
        return !loading && generation == lease;
    }

    public synchronized boolean readyForCapture() {
        return !loading;
    }
}
