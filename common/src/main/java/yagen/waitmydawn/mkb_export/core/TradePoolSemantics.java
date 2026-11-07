package yagen.waitmydawn.mkb_export.core;

/**
 * 均匀不放回抽取中，候选组至少入选一次的概率；不包含 Offer 内的额外随机条件。
 */
public final class TradePoolSemantics {
    public static double inclusion(int candidates, int matching, int draws) {
        if (candidates < 0 || matching < 0 || matching > candidates || draws < 0)
            throw new IllegalArgumentException("Invalid trade pool");
        int count = Math.min(draws, candidates);
        if (matching == 0 || count == 0) return 0;
        if (count > candidates - matching) return 1;
        double miss = 1;
        for (int i = 0; i < count; i++) miss *= (candidates - matching - i) / (double) (candidates - i);
        return 1 - miss;
    }

    private TradePoolSemantics() {
    }
}
