package com.example.suschunkfinder.scan;

import com.example.suschunkfinder.data.Model.ChunkData;
import com.example.suschunkfinder.data.Model.ChunkResult;
import net.minecraft.util.math.ChunkPos;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Wynik chunku + wersja danych (hash) - bez zmian = bez ponownego skanu. */
public final class ResultCache {
    public static final class Entry {
        public final ChunkData data;
        public ChunkResult result;
        public final int version;

        public Entry(ChunkData data, ChunkResult result, int version) {
            this.data = data; this.result = result; this.version = version;
        }
    }

    private final Map<ChunkPos, Entry> map = new ConcurrentHashMap<>();

    public Entry get(ChunkPos p) { return map.get(p); }
    public void put(ChunkPos p, Entry e) { map.put(p, e); }
    public void clear() { map.clear(); }
    public Collection<Entry> all() { return map.values(); }
    public int size() { return map.size(); }
}
