package com.example.suschunkfinder.analysis;

import com.example.suschunkfinder.config.ConfigManager;
import com.example.suschunkfinder.data.Model.*;
import com.example.suschunkfinder.Finder;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public final class AnomalyDetector {
    private AnomalyDetector() {}

    private static int idx(int x, int y, int z) { return (y << 8) | (z << 4) | x; }

    // ---------------------------------------------------------------- teren (w obrebie jednej sekcji 16^3)

    /** Szuka prostych korytarzy 1x2 (>=12 dlugosci) i prostokatnych pomieszczen (>=4x4). Jaskinie maja nierowne dno/sciany -> odpadaja. */
    public static void detectTerrain(ChunkData d, boolean[] air, int baseY) {
        int bx = d.pos.getStartX(), bz = d.pos.getStartZ();

        // korytarze wzdluz X
        for (int y = 1; y <= 13; y++) {
            for (int z = 1; z <= 14; z++) {
                int run = 0, start = 0;
                for (int x = 0; x <= 16; x++) {
                    boolean ok = x < 16 && tunnel(air, x, y, z, true);
                    if (ok) { if (run == 0) start = x; run++; }
                    else {
                        if (run >= 12) addTerrain(d, AnomalyType.TERRAIN_ANOMALY, bx + start, baseY + y, bz + z, "prosty korytarz X, dlugosc " + run);
                        run = 0;
                    }
                }
            }
        }
        // korytarze wzdluz Z
        for (int y = 1; y <= 13; y++) {
            for (int x = 1; x <= 14; x++) {
                int run = 0, start = 0;
                for (int z = 0; z <= 16; z++) {
                    boolean ok = z < 16 && tunnel(air, x, y, z, false);
                    if (ok) { if (run == 0) start = z; run++; }
                    else {
                        if (run >= 12) addTerrain(d, AnomalyType.TERRAIN_ANOMALY, bx + x, baseY + y, bz + start, "prosty korytarz Z, dlugosc " + run);
                        run = 0;
                    }
                }
            }
        }
        // prostokatne pomieszczenia
        for (int y = 1; y <= 11; y++) {
            for (int z0 = 1; z0 <= 14; z0++) {
                for (int x0 = 1; x0 <= 14; x0++) {
                    if (!flat(air, x0, y, z0)) continue;
                    if (flat(air, x0 - 1, y, z0) || flat(air, x0, y, z0 - 1)) continue; // tylko lewy gorny rog
                    int w = 0;
                    while (x0 + w <= 14 && flat(air, x0 + w, y, z0)) w++;
                    if (w < 4) continue;
                    int dd = 0;
                    outer:
                    while (z0 + dd <= 14) {
                        for (int i = 0; i < w; i++) if (!flat(air, x0 + i, y, z0 + dd)) break outer;
                        dd++;
                    }
                    if (dd < 4) continue;
                    if (!ring(air, x0, y, z0, w, dd)) continue;
                    addTerrain(d, AnomalyType.ARTIFICIAL_ROOM, bx + x0, baseY + y, bz + z0, "prostokatne pomieszczenie " + w + "x" + dd);
                }
            }
        }
    }

    private static void addTerrain(ChunkData d, AnomalyType t, int x, int y, int z, String detail) {
        if (d.terrain.size() < 24) d.terrain.add(new TerrainFinding(t, new BlockPos(x, y, z), detail));
    }

    private static boolean tunnel(boolean[] a, int x, int y, int z, boolean alongX) {
        if (!a[idx(x, y, z)] || !a[idx(x, y + 1, z)]) return false;
        if (a[idx(x, y - 1, z)] || a[idx(x, y + 2, z)]) return false;
        if (alongX) return !a[idx(x, y, z - 1)] && !a[idx(x, y, z + 1)] && !a[idx(x, y + 1, z - 1)] && !a[idx(x, y + 1, z + 1)];
        return !a[idx(x - 1, y, z)] && !a[idx(x + 1, y, z)] && !a[idx(x - 1, y + 1, z)] && !a[idx(x + 1, y + 1, z)];
    }

    /** 2 bloki powietrza, stala podloga, sufit w ciagu 2-4 blokow. */
    private static boolean flat(boolean[] a, int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < 1 || y > 11) return false;
        if (!a[idx(x, y, z)] || !a[idx(x, y + 1, z)] || a[idx(x, y - 1, z)]) return false;
        return !a[idx(x, y + 2, z)] || !a[idx(x, y + 3, z)] || !a[idx(x, y + 4, z)];
    }

    private static boolean ring(boolean[] a, int x0, int y, int z0, int w, int dd) {
        for (int z = z0; z < z0 + dd; z++) {
            if (a[idx(x0 - 1, y, z)] || a[idx(x0 - 1, y + 1, z)] || a[idx(x0 + w, y, z)] || a[idx(x0 + w, y + 1, z)]) return false;
        }
        for (int x = x0; x < x0 + w; x++) {
            if (a[idx(x, y, z0 - 1)] || a[idx(x, y + 1, z0 - 1)] || a[idx(x, y, z0 + dd)] || a[idx(x, y + 1, z0 + dd)]) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- pelna detekcja dla chunku

    public static List<Anomaly> detect(ChunkData d, Finder m, ConfigManager cfg, Set<Long> observed) {
        Map<String, Anomaly> out = new LinkedHashMap<>();
        Set<String> groupIds = new HashSet<>();

        // 1) grupy blokow przekraczajace prog
        for (var e : d.counts.entrySet()) {
            String id = e.getKey();
            int n = e.getValue();
            Category cat = cfg.categoryOf(id);
            if (cat == null) continue;
            int thr = cfg.threshold(id);
            if (thr <= 0 || n < thr) continue;
            AnomalyType t = switch (cat) {
                case VALUABLE -> AnomalyType.VALUABLE_GROUP;
                case STORAGE -> AnomalyType.STORAGE_GROUP;
                case REDSTONE -> AnomalyType.REDSTONE_GROUP;
                case ARTIFICIAL -> AnomalyType.ARTIFICIAL_ROOM;
            };
            List<BlockPos> ps = d.positions.getOrDefault(id, List.of());
            if (ps.isEmpty()) continue;
            Anomaly a = new Anomaly(t, id, centroid(ps), ps, n, n + " x " + id + " (prog " + thr + ")");
            out.putIfAbsent(a.key(), a);
            groupIds.add(id);

            if (id.equals("obsidian") && compact(ps)) {
                Anomaly o = new Anomaly(AnomalyType.OBSIDIAN_STRUCTURE, id, centroid(ps), ps, n, "zwarte skupisko obsydianu (" + n + ")");
                out.putIfAbsent(o.key(), o);
            }
        }

        // 2) zaobserwowane zmiany blokow (tylko gdy heurystyki gracza wlaczone)
        if (m.playerModDetection.get()) {
            for (var e : d.positions.entrySet()) {
                if (groupIds.contains(e.getKey())) continue;
                List<BlockPos> seen = new ArrayList<>();
                for (BlockPos p : e.getValue()) if (observed.contains(p.asLong())) seen.add(p);
                if (seen.isEmpty()) continue;
                Anomaly a = new Anomaly(AnomalyType.PLAYER_MODIFICATION, e.getKey(), centroid(seen), seen, seen.size(),
                    seen.size() + " x " + e.getKey() + " zaobserwowane w tej sesji");
                out.putIfAbsent(a.key(), a);
            }
        }

        // 3) teren
        for (TerrainFinding f : d.terrain) {
            Anomaly a = new Anomaly(f.type(), "terrain", f.pos(), List.of(f.pos()), 1, f.detail());
            out.putIfAbsent(a.key() + ":" + f.pos().asLong(), a);
        }

        List<Anomaly> list = new ArrayList<>(out.values());
        for (Anomaly a : list) {
            a.zone = HeightAnalyzer.zone(a.pos.getY(), m);
            OriginAnalyzer.classify(a, d, m, cfg, observed);
        }

        // 4) bonus za skupisko: >=3 nienaturalne anomalie w promieniu 32 bloków
        List<Anomaly> core = new ArrayList<>();
        for (Anomaly a : list) if (a.origin != Origin.NATURAL) core.add(a);
        for (Anomaly a : core) {
            int near = 0;
            for (Anomaly b : core) if (b != a && a.pos.getSquaredDistance(b.pos) <= 1024) near++;
            if (near >= 2) {
                Anomaly c = new Anomaly(AnomalyType.CLUSTER_BONUS, "cluster", a.pos, List.of(a.pos), near + 1,
                    "skupisko " + (near + 1) + " anomalii w promieniu 32 blokow");
                c.zone = HeightAnalyzer.zone(c.pos.getY(), m);
                c.origin = m.playerModDetection.get() ? Origin.SUSPECTED : Origin.UNKNOWN;
                c.originReason = "wiele anomalii obok siebie";
                list.add(c);
                break;
            }
        }
        return list;
    }

    private static BlockPos centroid(List<BlockPos> ps) {
        long x = 0, y = 0, z = 0;
        for (BlockPos p : ps) { x += p.getX(); y += p.getY(); z += p.getZ(); }
        int n = Math.max(1, ps.size());
        return new BlockPos((int) (x / n), (int) (y / n), (int) (z / n));
    }

    /** Obsydian w zwartej bryle (a nie rozrzucony po jaskiniach): wypelnienie bounding boxa >= 25%. */
    private static boolean compact(List<BlockPos> ps) {
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (BlockPos p : ps) {
            x1 = Math.min(x1, p.getX()); x2 = Math.max(x2, p.getX());
            y1 = Math.min(y1, p.getY()); y2 = Math.max(y2, p.getY());
            z1 = Math.min(z1, p.getZ()); z2 = Math.max(z2, p.getZ());
        }
        long vol = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        return ps.size() / (double) vol >= 0.25;
    }
}
