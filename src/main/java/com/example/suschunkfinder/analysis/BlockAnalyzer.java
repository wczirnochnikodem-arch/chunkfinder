package com.example.suschunkfinder.analysis;

import com.example.suschunkfinder.config.ConfigManager;
import com.example.suschunkfinder.data.Model.Category;
import com.example.suschunkfinder.data.Model.ChunkData;
import com.example.suschunkfinder.Finder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.List;

/** Zlicza bloki z tabel progow + zbiera markery struktur + (opcjonalnie) siatke powietrza do analizy terenu. */
public final class BlockAnalyzer {
    private BlockAnalyzer() {}

    public static void scanSection(ChunkData d, ChunkSection sec, int baseY, Finder m, ConfigManager cfg) {
        if (sec == null || sec.isEmpty()) return; // sekcja samego powietrza - nic do znalezienia

        boolean terrain = m.terrainAnomaly.get();
        if (!terrain && !sec.hasAny(s -> cfg.tracked.contains(s.getBlock()))) return;

        boolean[] on = new boolean[Category.values().length];
        on[Category.VALUABLE.ordinal()] = m.blockDetection.get();
        on[Category.STORAGE.ordinal()] = m.storageDetection.get();
        on[Category.REDSTONE.ordinal()] = m.redstoneDetection.get();
        on[Category.ARTIFICIAL.ordinal()] = terrain;

        int bx = d.pos.getStartX(), bz = d.pos.getStartZ();
        boolean[] air = terrain ? new boolean[4096] : null;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState st = sec.getBlockState(x, y, z);
                    if (st.isAir()) {
                        if (air != null) air[(y << 8) | (z << 4) | x] = true;
                        continue;
                    }
                    Block b = st.getBlock();

                    String id = cfg.hitIds.get(b);
                    if (id != null && on[cfg.hitCats.get(b).ordinal()]) {
                        d.counts.merge(id, 1, Integer::sum);
                        List<BlockPos> l = d.positions.computeIfAbsent(id, k -> new ArrayList<>());
                        if (l.size() < 256) l.add(new BlockPos(bx + x, baseY + y, bz + z));
                    }

                    String mk = cfg.markerBlocks.get(b);
                    if (mk != null) {
                        d.markerCounts.merge(mk, 1, Integer::sum);
                        List<BlockPos> l = d.markers.computeIfAbsent(mk, k -> new ArrayList<>());
                        if (l.size() < 128) l.add(new BlockPos(bx + x, baseY + y, bz + z));
                    }
                }
            }
        }

        if (air != null) AnomalyDetector.detectTerrain(d, air, baseY);
    }
}
