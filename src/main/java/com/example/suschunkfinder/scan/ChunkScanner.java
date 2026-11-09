package com.example.suschunkfinder.scan;

import com.example.suschunkfinder.analysis.BlockAnalyzer;
import com.example.suschunkfinder.analysis.ScoreCalculator;
import com.example.suschunkfinder.data.Model.ChunkData;
import com.example.suschunkfinder.Finder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.suschunkfinder.Mc.mc;

/**
 * Kolejka skanow: tylko dane, ktore klient JUZ ma (zaladowane chunki). Najblizsze chunki pierwsze.
 * Limity chunks-per-tick / sections-per-tick chronia FPS.
 */
public final class ChunkScanner {
    private static final class Job {
        final ChunkPos pos;
        final WorldChunk chunk;
        final ChunkData data;
        final int[] indices;
        int next;

        Job(ChunkPos pos, WorldChunk chunk, ChunkData data, int[] indices) {
            this.pos = pos; this.chunk = chunk; this.data = data; this.indices = indices;
        }
    }

    private final Finder m;
    private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    private final Map<ChunkPos, Job> jobs = new LinkedHashMap<>();
    private final Set<ChunkPos> dirty = ConcurrentHashMap.newKeySet();
    private int tickCounter, lastScanSig = Integer.MIN_VALUE, lastScoreSig = Integer.MIN_VALUE;
    private boolean manual;

    public ChunkScanner(Finder m) { this.m = m; }

    public void reset() {
        queue.clear(); jobs.clear(); dirty.clear();
        lastScanSig = Integer.MIN_VALUE; lastScoreSig = Integer.MIN_VALUE;
    }

    public void manualScan() { m.cache.clear(); queue.clear(); jobs.clear(); manual = true; }

    public void markDirty(ChunkPos p) { dirty.add(p); }

    public int pending() { return queue.size() + jobs.size(); }

    public void tick() {
        int scanSig = m.scanSignature();
        if (scanSig != lastScanSig) {
            if (lastScanSig != Integer.MIN_VALUE) { m.cache.clear(); queue.clear(); jobs.clear(); manual = true; }
            lastScanSig = scanSig;
        }
        int scoreSig = m.scoreSignature();
        if (scoreSig != lastScoreSig) { lastScoreSig = scoreSig; rescoreAll(); }

        tickCounter++;
        if (manual || (m.autoScan.get() && tickCounter % 20 == 0)) {
            refreshQueue();
            manual = false;
        }
        processJobs();
    }

    public void rescoreAll() {
        for (ResultCache.Entry e : m.cache.all()) e.result = ScoreCalculator.compute(e.data, m, m.cfg, m.observed);
    }

    // ------------------------------------------------------------------ kolejka

    private static int versionKey(WorldChunk c) {
        int h = System.identityHashCode(c);
        for (ChunkSection s : c.getSectionArray()) h = h * 31 + s.getNonEmptyBlockCount();
        return h;
    }

    private void refreshQueue() {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) return;
        ChunkPos pc = mc.player.getChunkPos();
        int r = m.scanDistance.get();

        List<ChunkPos> todo = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkPos cp = new ChunkPos(pc.x + dx, pc.z + dz);
                if (jobs.containsKey(cp)) continue;
                WorldChunk c = w.getChunkManager().getWorldChunk(cp.x, cp.z);
                if (c == null) continue;
                ResultCache.Entry e = m.cache.get(cp);
                boolean isDirty = dirty.remove(cp);
                boolean needs = e == null || isDirty || !m.rescanOnChange.get() || e.version != versionKey(c);
                if (needs) todo.add(cp);
            }
        }
        todo.sort(Comparator.comparingInt(p -> (p.x - pc.x) * (p.x - pc.x) + (p.z - pc.z) * (p.z - pc.z)));
        queue.clear();
        queue.addAll(todo);
    }

    private void processJobs() {
        ClientWorld w = mc.world;
        if (w == null) return;

        while (jobs.size() < m.chunksPerTick.get() && !queue.isEmpty()) startJob(queue.poll(), w);

        Iterator<Job> it = jobs.values().iterator();
        while (it.hasNext()) {
            if (step(it.next(), w)) it.remove();
        }
    }

    private void startJob(ChunkPos p, ClientWorld w) {
        WorldChunk c = w.getChunkManager().getWorldChunk(p.x, p.z);
        if (c == null) return;

        ChunkSection[] arr = c.getSectionArray();
        ChunkData d = new ChunkData(p);
        d.sectionsExpected = w.countVerticalSections();
        d.missingData = arr.length != d.sectionsExpected;   // brak sekcji = INCOMPLETE, nie "czysto"
        d.verticalLimited = !m.fullVertical.get();
        d.version = versionKey(c);
        d.chunkId = System.identityHashCode(c);

        int bottom = w.getBottomY();
        int fMin = m.filterMinY.get(), fMax = m.filterMaxY.get();
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < arr.length; i++) {          // od najnizszej sekcji do najwyzszej, bez pomijania
            int baseY = bottom + i * 16;
            if (d.verticalLimited && (baseY + 15 < fMin || baseY > fMax)) continue;
            idx.add(i);
        }
        d.sectionsTotal = idx.size();
        jobs.put(p, new Job(p, c, d, idx.stream().mapToInt(Integer::intValue).toArray()));
    }

    /** @return true gdy job skonczony. */
    private boolean step(Job j, ClientWorld w) {
        WorldChunk live = w.getChunkManager().getWorldChunk(j.pos.x, j.pos.z);
        if (live != j.chunk) { j.data.aborted = true; finish(j, w); return true; }

        ChunkSection[] arr = j.chunk.getSectionArray();
        int bottom = w.getBottomY();
        int n = 0;
        while (n < m.sectionsPerTick.get() && j.next < j.indices.length) {
            int i = j.indices[j.next++];
            if (i < arr.length) {
                BlockAnalyzer.scanSection(j.data, arr[i], bottom + i * 16, m, m.cfg);
                j.data.sectionsScanned++;
            }
            n++;
        }
        if (j.next >= j.indices.length) { finish(j, w); return true; }
        return false;
    }

    private void finish(Job j, ClientWorld w) {
        // "Player Placed": ten sam obiekt chunku skanowany ponownie i pojawil sie nowy sledzony blok = zmiana zaobserwowana w tej sesji
        ResultCache.Entry old = m.cache.get(j.pos);
        if (old != null && m.playerModDetection.get() && old.data.chunkId == j.data.chunkId
            && old.data.complete() && j.data.complete() && old.data.verticalLimited == j.data.verticalLimited) {
            for (var e : j.data.positions.entrySet()) {
                if (e.getValue().size() >= 256) continue;
                List<BlockPos> before = old.data.positions.get(e.getKey());
                if (before != null && before.size() >= 256) continue;
                Set<Long> had = new HashSet<>();
                if (before != null) for (BlockPos b : before) had.add(b.asLong());
                for (BlockPos p : e.getValue()) if (!had.contains(p.asLong())) m.observed.add(p.asLong());
            }
        }
        var res = ScoreCalculator.compute(j.data, m, m.cfg, m.observed);
        m.cache.put(j.pos, new ResultCache.Entry(j.data, res, j.data.version));
    }
}
