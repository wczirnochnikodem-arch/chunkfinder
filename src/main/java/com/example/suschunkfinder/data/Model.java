package com.example.suschunkfinder.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.*;

/** Wszystkie typy danych w jednym miejscu. */
public final class Model {
    private Model() {}

    public enum Category { VALUABLE, STORAGE, REDSTONE, ARTIFICIAL }

    public enum Zone {
        UNDERGROUND("Underground"), SURFACE("Surface"), ABOVE_Y100("Above Y100");
        public final String label;
        Zone(String label) { this.label = label; }
    }

    public enum Origin {
        PLAYER_PLACED("Player Placed"),
        SUSPECTED("Suspected Player Modification"),
        NATURAL("Natural Generation"),
        UNKNOWN("Unknown");
        public final String label;
        Origin(String label) { this.label = label; }
    }

    public enum Level {
        LOW("Niski"), MODERATE("Umiarkowany"), SUSPICIOUS("Podejrzany"), VERY_SUSPICIOUS("Bardzo podejrzany");
        public final String label;
        Level(String label) { this.label = label; }
    }

    public enum AnomalyType {
        VALUABLE_GROUP("valuableBlockGroup", 12, "Cenne bloki"),
        STORAGE_GROUP("storageBlockGroup", 10, "Bloki magazynowe"),
        REDSTONE_GROUP("redstoneGroup", 8, "Redstone"),
        PLAYER_MODIFICATION("playerModification", 15, "Zaobserwowana zmiana bloku"),
        TERRAIN_ANOMALY("terrainAnomaly", 10, "Sztuczny korytarz"),
        ARTIFICIAL_ROOM("artificialRoom", 14, "Sztuczne pomieszczenie / budulec"),
        OBSIDIAN_STRUCTURE("obsidianStructure", 12, "Struktura z obsydianu"),
        CLUSTER_BONUS("clusterBonus", 8, "Skupisko anomalii");

        public final String weightKey;
        public final double defaultWeight;
        public final String label;
        AnomalyType(String weightKey, double defaultWeight, String label) {
            this.weightKey = weightKey; this.defaultWeight = defaultWeight; this.label = label;
        }
    }

    /** Wynik heurystyki terenu (przed zamiana na Anomaly). */
    public record TerrainFinding(AnomalyType type, BlockPos pos, String detail) {}

    /** Surowe dane skanu jednego chunku (punktacja liczona osobno, mozna ja przeliczyc bez ponownego skanu). */
    public static final class ChunkData {
        public final ChunkPos pos;
        public final Map<String, Integer> counts = new HashMap<>();
        public final Map<String, List<BlockPos>> positions = new HashMap<>();   // dokladne X/Y/Z (limit 256 / typ)
        public final Map<String, Integer> markerCounts = new HashMap<>();
        public final Map<String, List<BlockPos>> markers = new HashMap<>();     // struktura -> markery
        public final List<TerrainFinding> terrain = new ArrayList<>();
        public int sectionsTotal, sectionsScanned, sectionsExpected, version, chunkId;
        public boolean aborted, missingData, verticalLimited;

        public ChunkData(ChunkPos pos) { this.pos = pos; }

        public boolean complete() { return !aborted && !missingData && sectionsScanned >= sectionsTotal; }

        public int progressPercent() {
            int denom = verticalLimited ? sectionsTotal : Math.max(sectionsTotal, sectionsExpected);
            return denom <= 0 ? 0 : Math.min(100, sectionsScanned * 100 / denom);
        }
    }

    public static final class Anomaly {
        public final AnomalyType type;
        public final String id;
        public final BlockPos pos;
        public final List<BlockPos> positions;
        public final int count;
        public final String detail;
        public Zone zone = Zone.SURFACE;
        public Origin origin = Origin.UNKNOWN;
        public String originReason = "";
        public double contribution;

        public Anomaly(AnomalyType type, String id, BlockPos pos, List<BlockPos> positions, int count, String detail) {
            this.type = type; this.id = id; this.pos = pos; this.positions = positions; this.count = count; this.detail = detail;
        }

        /** Klucz: typ + id + skupisko (kostka 16^3) - kazda anomalia liczona raz. */
        public String key() {
            return type + ":" + id + ":" + (pos.getX() >> 4) + ":" + (pos.getY() >> 4) + ":" + (pos.getZ() >> 4);
        }
    }

    public static final class ChunkResult {
        public final ChunkPos pos;
        public int score;
        public Level level = Level.LOW;
        public List<Anomaly> anomalies = List.of();
        public final Map<String, Integer> blockCounts = new TreeMap<>();
        public final EnumMap<Zone, Integer> zoneCounts = new EnumMap<>(Zone.class);
        public final EnumMap<Origin, Integer> originCounts = new EnumMap<>(Origin.class);
        public int minY, maxY;
        public boolean complete, verticalLimited;
        public int progress;

        public ChunkResult(ChunkPos pos) { this.pos = pos; }

        public Origin dominantOrigin() {
            Origin best = null; int n = -1;
            for (var e : originCounts.entrySet()) if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
            return best;
        }
    }
}
