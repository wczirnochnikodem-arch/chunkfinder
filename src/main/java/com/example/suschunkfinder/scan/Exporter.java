package com.example.suschunkfinder.scan;

import com.example.suschunkfinder.data.Model.Anomaly;
import com.example.suschunkfinder.data.Model.ChunkResult;
import com.example.suschunkfinder.Finder.ExportFormat;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Eksport do config/sus-chunk-finder/exports (TXT lub JSON). */
public final class Exporter {
    private Exporter() {}

    public static Path export(ResultCache cache, ExportFormat fmt, int minScore) throws Exception {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("config").resolve("sus-chunk-finder").resolve("exports");
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("sus-chunks-" + stamp + (fmt == ExportFormat.JSON ? ".json" : ".txt"));

        List<ChunkResult> list = new ArrayList<>();
        for (ResultCache.Entry e : cache.all()) {
            ChunkResult r = e.result;
            if (r.score >= minScore || !r.complete) list.add(r);
        }
        list.sort(Comparator.comparingInt((ChunkResult r) -> -r.score));

        if (fmt == ExportFormat.JSON) {
            JsonArray arr = new JsonArray();
            for (ChunkResult r : list) arr.add(toJson(r));
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(arr));
        } else {
            StringBuilder sb = new StringBuilder();
            for (ChunkResult r : list) {
                sb.append(String.format("Chunk (%d, %d)  blocks X %d..%d  Z %d..%d%n", r.pos.x, r.pos.z,
                    r.pos.getStartX(), r.pos.getEndX(), r.pos.getStartZ(), r.pos.getEndZ()));
                sb.append(String.format("  Score %d (%s)  scan %d%%%s%s%n", r.score, r.level.label, r.progress,
                    r.complete ? "" : "  INCOMPLETE", r.verticalLimited ? "  [limited vertical range]" : ""));
                sb.append("  Zones: ").append(r.zoneCounts).append("  Origins: ").append(r.originCounts).append('\n');
                sb.append("  Blocks: ").append(r.blockCounts).append('\n');
                for (Anomaly a : r.anomalies) {
                    sb.append(String.format("  - [+%.1f] %s @ %d %d %d | %s | %s | %s (%s)%n", a.contribution, a.type.label,
                        a.pos.getX(), a.pos.getY(), a.pos.getZ(), a.zone.label, a.origin.label, a.detail, a.originReason));
                }
                sb.append('\n');
            }
            Files.writeString(file, sb.toString());
        }
        return file;
    }

    private static JsonObject toJson(ChunkResult r) {
        JsonObject o = new JsonObject();
        o.addProperty("chunkX", r.pos.x);
        o.addProperty("chunkZ", r.pos.z);
        o.addProperty("blockX", r.pos.getStartX());
        o.addProperty("blockZ", r.pos.getStartZ());
        o.addProperty("score", r.score);
        o.addProperty("level", r.level.label);
        o.addProperty("complete", r.complete);
        o.addProperty("scanProgressPercent", r.progress);
        o.addProperty("verticalLimited", r.verticalLimited);
        JsonObject counts = new JsonObject();
        r.blockCounts.forEach(counts::addProperty);
        o.add("blockCounts", counts);
        JsonObject zones = new JsonObject();
        r.zoneCounts.forEach((k, v) -> zones.addProperty(k.label, v));
        o.add("zones", zones);
        JsonObject origins = new JsonObject();
        r.originCounts.forEach((k, v) -> origins.addProperty(k.label, v));
        o.add("origins", origins);
        JsonArray an = new JsonArray();
        for (Anomaly a : r.anomalies) {
            JsonObject j = new JsonObject();
            j.addProperty("type", a.type.name());
            j.addProperty("id", a.id);
            j.addProperty("x", a.pos.getX());
            j.addProperty("y", a.pos.getY());
            j.addProperty("z", a.pos.getZ());
            j.addProperty("count", a.count);
            j.addProperty("zone", a.zone.label);
            j.addProperty("origin", a.origin.label);
            j.addProperty("originReason", a.originReason);
            j.addProperty("detail", a.detail);
            j.addProperty("contribution", Math.round(a.contribution * 10) / 10.0);
            an.add(j);
        }
        o.add("anomalies", an);
        return o;
    }
}
