package com.example.suschunkfinder.render;

import com.example.suschunkfinder.Finder;
import com.example.suschunkfinder.data.Model.ChunkResult;
import com.example.suschunkfinder.scan.ResultCache;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.example.suschunkfinder.Mc.mc;

/** Lista najbardziej podejrzanych chunkow na ekranie (dziala przez sciany, w przeciwienstwie do ramek 3D). */
public final class SusHud {
    private SusHud() {}

    public static void render(DrawContext ctx, Finder f) {
        if (!f.enabled || !f.showHud.get() || mc.player == null || mc.options.hudHidden) return;

        List<ChunkResult> rs = new ArrayList<>();
        for (ResultCache.Entry e : f.cache.all()) if (f.visible(e.result)) rs.add(e.result);
        rs.sort(Comparator.comparingInt((ChunkResult r) -> -r.score));

        int x = 6, y = 6, lh = 10;
        int pend = f.scanner.pending();
        ctx.drawTextWithShadow(mc.textRenderer, "Sus Chunk Finder  flagged: " + f.flaggedCount() + (pend > 0 ? "  (queue " + pend + ")" : ""), x, y, 0xFFFFFFFF);
        y += lh + 2;

        double px = mc.player.getX(), pz = mc.player.getZ();
        int n = 0;
        for (ChunkResult r : rs) {
            if (n++ >= f.hudLines.get()) break;
            StringBuilder sb = new StringBuilder();
            if (f.showScore.get()) sb.append('[').append(r.score).append("] ").append(r.level.label).append("  ");
            if (f.showCoords.get()) {
                int cx = r.pos.getStartX() + 8, cz = r.pos.getStartZ() + 8;
                int dist = (int) Math.sqrt((cx - px) * (cx - px) + (cz - pz) * (cz - pz));
                sb.append("X ").append(cx).append(" Z ").append(cz).append(" (").append(dist).append("m)  ");
            }
            if (f.showOrigins.get() && !r.anomalies.isEmpty()) {
                sb.append("Y ").append(r.minY).append("..").append(r.maxY).append("  ")
                  .append(r.dominantOrigin() == null ? "?" : r.dominantOrigin().label);
            }
            if (!r.complete) sb.append("  INCOMPLETE ").append(r.progress).append('%');
            ctx.drawTextWithShadow(mc.textRenderer, sb.toString(), x, y, 0xFF000000 | f.rgb(r));
            y += lh;
        }
    }
}
