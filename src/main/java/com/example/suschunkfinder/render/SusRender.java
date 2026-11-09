package com.example.suschunkfinder.render;

import com.example.suschunkfinder.Finder;
import com.example.suschunkfinder.data.Model.Anomaly;
import com.example.suschunkfinder.data.Model.ChunkResult;
import com.example.suschunkfinder.scan.ResultCache;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import static com.example.suschunkfinder.Mc.mc;

/** Ramki chunkow (linie) + male ramki trafien. Linie sa glebokosciowe - widac je tylko w linii wzroku; liste przez sciany daje HUD i /susfinder list. */
public final class SusRender {
    private SusRender() {}

    public static void render(WorldRenderContext ctx, Finder f) {
        if (!f.enabled || !f.renderChunks.get() || mc.player == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        Vec3d cam = ctx.camera().getPos();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry e = ms.peek();
        ChunkPos pc = mc.player.getChunkPos();
        int rd = f.renderDistance.get();
        boolean any = false;

        for (ResultCache.Entry en : f.cache.all()) {
            ChunkResult r = en.result;
            if (!f.visible(r)) continue;
            if (Math.abs(r.pos.x - pc.x) > rd || Math.abs(r.pos.z - pc.z) > rd) continue;

            int rgb = f.rgb(r);
            int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;

            double y1, y2;
            if (r.anomalies.isEmpty()) { y1 = mc.player.getY() - 4; y2 = mc.player.getY() + 8; }
            else { y1 = r.minY - 1; y2 = Math.max(r.maxY + 2, y1 + 4); }

            box(vc, e, cam, r.pos.getStartX(), y1, r.pos.getStartZ(), r.pos.getEndX() + 1, y2, r.pos.getEndZ() + 1, cr, cg, cb, 255);
            any = true;

            if (f.renderHits.get()) {
                int n = 0;
                outer:
                for (Anomaly a : r.anomalies) {
                    for (BlockPos p : a.positions) {
                        if (n++ > 48) break outer;
                        box(vc, e, cam, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, cr, cg, cb, 255);
                    }
                }
            }
        }
        if (any && vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
    }

    private static void box(VertexConsumer vc, MatrixStack.Entry e, Vec3d cam,
                            double ax, double ay, double az, double bx, double by, double bz, int r, int g, int b, int a) {
        float x1 = (float) (ax - cam.x), y1 = (float) (ay - cam.y), z1 = (float) (az - cam.z);
        float x2 = (float) (bx - cam.x), y2 = (float) (by - cam.y), z2 = (float) (bz - cam.z);
        // dol
        line(vc, e, x1, y1, z1, x2, y1, z1, r, g, b, a); line(vc, e, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(vc, e, x2, y1, z2, x1, y1, z2, r, g, b, a); line(vc, e, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // gora
        line(vc, e, x1, y2, z1, x2, y2, z1, r, g, b, a); line(vc, e, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(vc, e, x2, y2, z2, x1, y2, z2, r, g, b, a); line(vc, e, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // slupki
        line(vc, e, x1, y1, z1, x1, y2, z1, r, g, b, a); line(vc, e, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(vc, e, x2, y1, z2, x2, y2, z2, r, g, b, a); line(vc, e, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(VertexConsumer vc, MatrixStack.Entry e, float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len == 0) return;
        nx /= len; ny /= len; nz /= len;
        vc.vertex(e, x1, y1, z1).color(r, g, b, a).normal(e, nx, ny, nz);
        vc.vertex(e, x2, y2, z2).color(r, g, b, a).normal(e, nx, ny, nz);
    }
}
