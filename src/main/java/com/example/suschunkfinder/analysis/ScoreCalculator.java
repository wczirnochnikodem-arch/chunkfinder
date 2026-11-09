package com.example.suschunkfinder.analysis;

import com.example.suschunkfinder.config.ConfigManager;
import com.example.suschunkfinder.data.Model.*;
import com.example.suschunkfinder.Finder;

import java.util.*;

public final class ScoreCalculator {
    private ScoreCalculator() {}

    public static ChunkResult compute(ChunkData d, Finder m, ConfigManager cfg, Set<Long> observed) {
        List<Anomaly> all = AnomalyDetector.detect(d, m, cfg, observed);
        List<Anomaly> kept = new ArrayList<>();
        double sum = 0;

        for (Anomaly a : all) {
            int y = a.pos.getY();
            if (y < m.filterMinY.get() || y > m.filterMaxY.get()) continue;
            if (!HeightAnalyzer.enabled(a.zone, m)) continue;
            if (!originShown(a.origin, m)) continue;

            double zoneMult = switch (a.zone) {
                case ABOVE_Y100 -> m.aboveY100Mult.get();
                case UNDERGROUND -> cfg.multiplier("underground", 1.0);
                case SURFACE -> cfg.multiplier("surface", 1.0);
            };
            double originMult = switch (a.origin) {
                case PLAYER_PLACED, SUSPECTED -> m.suspectedMult.get();
                case NATURAL -> m.naturalMult.get();
                case UNKNOWN -> cfg.multiplier("originUnknown", 1.0);
            };
            a.contribution = cfg.weight(a.type) * zoneMult * originMult;
            sum += a.contribution;
            kept.add(a);
        }
        kept.sort(Comparator.comparingDouble((Anomaly a) -> -a.contribution));

        ChunkResult r = new ChunkResult(d.pos);
        r.score = (int) Math.round(Math.max(0, Math.min(100, sum)));
        int[] lv = cfg.levelMin;
        r.level = r.score >= lv[3] ? Level.VERY_SUSPICIOUS : r.score >= lv[2] ? Level.SUSPICIOUS : r.score >= lv[1] ? Level.MODERATE : Level.LOW;
        r.anomalies = kept;
        r.complete = d.complete();
        r.progress = d.progressPercent();
        r.verticalLimited = d.verticalLimited;
        r.minY = Integer.MAX_VALUE; r.maxY = Integer.MIN_VALUE;
        for (Anomaly a : kept) {
            r.zoneCounts.merge(a.zone, 1, Integer::sum);
            r.originCounts.merge(a.origin, 1, Integer::sum);
            r.minY = Math.min(r.minY, a.pos.getY());
            r.maxY = Math.max(r.maxY, a.pos.getY());
        }
        if (kept.isEmpty()) { r.minY = 0; r.maxY = 0; }
        r.blockCounts.putAll(d.counts);
        return r;
    }

    private static boolean originShown(Origin o, Finder m) {
        return switch (o) {
            case UNKNOWN -> m.showUnknown.get();
            case SUSPECTED, PLAYER_PLACED -> m.showSuspected.get();
            case NATURAL -> m.showNatural.get();
        };
    }
}
