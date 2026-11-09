package com.example.suschunkfinder.analysis;

import com.example.suschunkfinder.config.ConfigManager;
import com.example.suschunkfinder.data.Model.*;
import com.example.suschunkfinder.Finder;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pochodzenie. Uczciwie: klient nie wie kto postawil blok.
 *  PLAYER_PLACED - tylko gdy ZOBACZYLISMY zmiane bloku (BlockUpdate) w tej sesji.
 *  NATURAL       - marker znanej struktury/geody w poblizu (heurystyka po blokach, nie dane serwera).
 *  SUSPECTED     - bloki/ksztalty, ktorych generacja swiata normalnie nie tworzy.
 *  UNKNOWN       - brak dowodow.
 */
public final class OriginAnalyzer {
    private OriginAnalyzer() {}

    private static final int RADIUS = 24;

    public static void classify(Anomaly a, ChunkData d, Finder m, ConfigManager cfg, Set<Long> observed) {
        if (a.type == AnomalyType.PLAYER_MODIFICATION || hasObserved(a, observed)) {
            a.origin = Origin.PLAYER_PLACED;
            a.originReason = "zaobserwowana zmiana bloku w tej sesji";
            return;
        }
        String st = nearbyStructure(a, d, cfg);
        if (st != null) {
            a.origin = Origin.NATURAL;
            a.originReason = "markery struktury/geody: " + st;
            return;
        }
        if ("ancient_debris".equals(a.id)) {
            a.origin = Origin.NATURAL;
            a.originReason = "ancient debris generuje sie naturalnie";
            return;
        }
        if (!m.playerModDetection.get()) {
            a.origin = Origin.UNKNOWN;
            a.originReason = "heurystyki gracza wylaczone";
            return;
        }
        switch (a.type) {
            case VALUABLE_GROUP -> {
                if (a.id.equals("obsidian") || a.id.equals("crying_obsidian") || a.id.equals("spawner")) {
                    a.origin = Origin.UNKNOWN; a.originReason = "moze byc naturalny, brak dowodu";
                } else {
                    a.origin = Origin.SUSPECTED; a.originReason = "blok poza strukturami nie generuje sie naturalnie";
                }
            }
            case OBSIDIAN_STRUCTURE -> { a.origin = Origin.UNKNOWN; a.originReason = "ksztalt zwarty, ale brak dowodu"; }
            case STORAGE_GROUP, REDSTONE_GROUP -> { a.origin = Origin.SUSPECTED; a.originReason = "poza znanymi strukturami"; }
            case TERRAIN_ANOMALY, ARTIFICIAL_ROOM -> { a.origin = Origin.SUSPECTED; a.originReason = "nienaturalna geometria / budulec"; }
            default -> { a.origin = Origin.UNKNOWN; a.originReason = "brak dowodow"; }
        }
    }

    private static boolean hasObserved(Anomaly a, Set<Long> observed) {
        if (observed.isEmpty()) return false;
        for (BlockPos p : a.positions) if (observed.contains(p.asLong())) return true;
        return false;
    }

    private static String nearbyStructure(Anomaly a, ChunkData d, ConfigManager cfg) {
        for (Map.Entry<String, List<BlockPos>> e : d.markers.entrySet()) {
            String s = e.getKey();
            if (!cfg.structureEnabled(s)) continue;
            if (d.markerCounts.getOrDefault(s, 0) < 2) continue;
            for (BlockPos p : e.getValue()) {
                if (Math.abs(p.getX() - a.pos.getX()) <= RADIUS && Math.abs(p.getY() - a.pos.getY()) <= RADIUS
                    && Math.abs(p.getZ() - a.pos.getZ()) <= RADIUS) return s;
            }
        }
        return null;
    }
}
