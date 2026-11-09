package com.example.suschunkfinder;

import com.example.suschunkfinder.config.ConfigManager;
import com.example.suschunkfinder.data.Model.ChunkResult;
import com.example.suschunkfinder.scan.ChunkScanner;
import com.example.suschunkfinder.scan.Exporter;
import com.example.suschunkfinder.scan.ResultCache;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.suschunkfinder.Mc.mc;

/** Stan moda + wszystkie ustawienia (zastepuje modul Meteora). Ustawienia: config/sus-chunk-finder/settings.json lub komenda /susfinder set. */
public final class Finder {
    public enum ExportFormat { TXT, JSON }

    /** Pojedyncze ustawienie z walidacja zakresu. */
    public static final class Opt<T> {
        public final String name, desc;
        private final Class<T> type;
        private final double min, max;
        private T value;

        @SuppressWarnings("unchecked")
        Opt(String name, T def, double min, double max, String desc) {
            this.name = name; this.desc = desc; this.value = def; this.min = min; this.max = max;
            this.type = (Class<T>) def.getClass();
        }

        public T get() { return value; }
        public String show() { return String.valueOf(value); }

        public String hint() {
            if (type == Boolean.class) return "true/false";
            if (type == Integer.class) return (int) min + ".." + (int) max;
            if (type == Double.class) return min + ".." + max;
            if (type.isEnum()) return Arrays.toString(type.getEnumConstants());
            return "";
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public boolean parse(String s) {
            try {
                Object o;
                String t = s.trim();
                if (type == Boolean.class) {
                    switch (t.toLowerCase()) {
                        case "true", "on", "1", "yes" -> o = true;
                        case "false", "off", "0", "no" -> o = false;
                        default -> { return false; }
                    }
                } else if (type == Integer.class) {
                    o = (int) Math.max(min, Math.min(max, Integer.parseInt(t)));
                } else if (type == Double.class) {
                    o = Math.max(min, Math.min(max, Double.parseDouble(t)));
                } else if (type.isEnum()) {
                    o = Enum.valueOf((Class) type, t.toUpperCase());
                } else return false;
                value = (T) o;
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public final Map<String, Opt<?>> opts = new TreeMap<>();

    private <T> Opt<T> opt(String name, T def, double min, double max, String desc) {
        Opt<T> o = new Opt<>(name, def, min, max, desc);
        opts.put(name, o);
        return o;
    }
    private Opt<Boolean> bool(String name, boolean def, String desc) { return opt(name, def, 0, 0, desc); }

    // --- General ---
    public final Opt<Boolean> autoScan = bool("auto-scan", true, "Skanuje chunki automatycznie po zaladowaniu.");
    public final Opt<Integer> minScore = opt("minimum-suspicion-score", 20, 0, 100, "Chunki ponizej progu nie sa pokazywane.");
    public final Opt<ExportFormat> exportFormat = opt("export-format", ExportFormat.JSON, 0, 0, "Format eksportu.");
    // --- Scan ---
    public final Opt<Boolean> fullVertical = bool("full-vertical-scan", true, "Skan od dolu do gory swiata. OFF = tylko zakres filter-min-y..filter-max-y.");
    public final Opt<Integer> scanDistance = opt("scan-distance", 8, 1, 32, "Promien skanu w chunkach.");
    public final Opt<Integer> chunksPerTick = opt("chunks-per-tick", 1, 1, 8, "Limit chunkow na tick (anty-lag).");
    public final Opt<Integer> sectionsPerTick = opt("sections-per-tick", 4, 1, 32, "Sekcji 16x16x16 na tick.");
    public final Opt<Boolean> rescanOnChange = bool("rescan-only-on-change", true, "Pomija chunki bez zmian.");
    // --- Detection ---
    public final Opt<Boolean> blockDetection = bool("block-detection", true, "Cenne/nietypowe bloki.");
    public final Opt<Boolean> storageDetection = bool("storage-block-detection", true, "Skrzynie, piece, kowadla itd.");
    public final Opt<Boolean> redstoneDetection = bool("redstone-detection", true, "Redstone.");
    public final Opt<Boolean> terrainAnomaly = bool("terrain-anomaly-detection", true, "Sztuczne pomieszczenia, korytarze, budulec.");
    public final Opt<Boolean> playerModDetection = bool("player-modification-detection", true, "Heurystyki sladow gracza (przypuszczenie, nie dowod).");
    // --- Height zones ---
    public final Opt<Boolean> scanUnderground = bool("scan-underground", true, "Raportuj strefe podziemi.");
    public final Opt<Boolean> scanSurface = bool("scan-surface", true, "Raportuj powierzchnie.");
    public final Opt<Boolean> scanAbove100 = bool("scan-above-y100", true, "Raportuj obszary wysokie.");
    public final Opt<Integer> undergroundMaxY = opt("underground-max-y", 50, -64, 320, "Granica Underground/Surface.");
    public final Opt<Integer> surfaceMaxY = opt("surface-max-y", 100, -64, 320, "Granica Surface/Above Y100.");
    // --- Scoring ---
    public final Opt<Double> aboveY100Mult = opt("above-y100-multiplier", 1.3, 0.1, 3, "Mnoznik dla Y>100.");
    public final Opt<Double> naturalMult = opt("natural-origin-multiplier", 0.3, 0, 1, "Mnoznik dla pochodzenia Natural.");
    public final Opt<Double> suspectedMult = opt("suspected-player-multiplier", 1.25, 1, 3, "Mnoznik dla Suspected/Player Placed.");
    // --- Render ---
    public final Opt<Boolean> renderChunks = bool("render-chunks", true, "Ramki chunkow w swiecie.");
    public final Opt<Boolean> renderHits = bool("render-block-hits", true, "Pojedyncze trafienia (maks. ~48 na chunk).");
    public final Opt<Integer> renderDistance = opt("render-distance", 16, 1, 64, "Promien rysowania w chunkach.");
    public final Opt<Boolean> showHud = bool("show-hud", true, "Lista podejrzanych chunkow na ekranie.");
    public final Opt<Integer> hudLines = opt("hud-lines", 8, 1, 20, "Ile chunkow na liscie HUD.");
    public final Opt<Boolean> showCoords = bool("show-coordinates", true, "Koordynaty na HUD.");
    public final Opt<Boolean> showScore = bool("show-suspicion-score", true, "Score na HUD.");
    public final Opt<Boolean> showOrigins = bool("show-block-origins", true, "Pochodzenie i zakres Y na HUD.");
    public final Opt<Boolean> showIncomplete = bool("show-incomplete", true, "Szara ramka dla niepelnego skanu (nie znaczy 'czysto').");
    // --- Filters ---
    public final Opt<Integer> filterMinY = opt("filter-min-y", -64, -64, 320, "Dolna granica Y.");
    public final Opt<Integer> filterMaxY = opt("filter-max-y", 320, -64, 320, "Gorna granica Y.");
    public final Opt<Boolean> showUnknown = bool("show-origin-unknown", true, "Pokazuj pochodzenie Unknown.");
    public final Opt<Boolean> showSuspected = bool("show-origin-suspected", true, "Pokazuj Suspected (i Player Placed).");
    public final Opt<Boolean> showNatural = bool("show-origin-natural", false, "Pokazuj Natural.");

    // --- stan ---
    public final ConfigManager cfg = new ConfigManager();
    public final ResultCache cache = new ResultCache();
    public final Set<Long> observed = ConcurrentHashMap.newKeySet();
    public final ChunkScanner scanner = new ChunkScanner(this);
    public boolean enabled = true;
    private boolean cfgLoaded;
    private ClientWorld lastWorld;

    public int scanSignature() {
        return Objects.hash(blockDetection.get(), storageDetection.get(), redstoneDetection.get(), terrainAnomaly.get(),
            fullVertical.get(), fullVertical.get() ? 0 : Objects.hash(filterMinY.get(), filterMaxY.get()));
    }

    public int scoreSignature() {
        return Objects.hash(minScore.get(), scanUnderground.get(), scanSurface.get(), scanAbove100.get(),
            undergroundMaxY.get(), surfaceMaxY.get(), aboveY100Mult.get(), naturalMult.get(), suspectedMult.get(),
            filterMinY.get(), filterMaxY.get(), showUnknown.get(), showSuspected.get(), showNatural.get(),
            playerModDetection.get());
    }

    public void tick() {
        if (mc.world == null || mc.player == null) return;
        if (!cfgLoaded) { cfg.load(); cfgLoaded = true; }
        if (mc.world != lastWorld) {              // zmiana swiata / wymiaru -> czysty start
            cache.clear(); observed.clear(); scanner.reset();
            lastWorld = mc.world;
        }
        if (enabled) scanner.tick();
    }

    public void clearAll() { cache.clear(); observed.clear(); scanner.reset(); }
    public void manualScan() { scanner.manualScan(); }
    public void reloadConfig() { cfg.load(); cfgLoaded = true; scanner.manualScan(); }
    public Path export() throws Exception { return Exporter.export(cache, exportFormat.get(), minScore.get()); }

    public boolean visible(ChunkResult r) {
        if (r.score >= minScore.get() && !r.anomalies.isEmpty()) return true;
        return !r.complete && showIncomplete.get();
    }

    /** Zielony / zolty / pomaranczowy / czerwony, szary = INCOMPLETE. */
    public int rgb(ChunkResult r) {
        if (r.anomalies.isEmpty() && !r.complete) return 0x969696;
        return switch (r.level) {
            case LOW -> 0x50DC50;
            case MODERATE -> 0xFFDC00;
            case SUSPICIOUS -> 0xFF8C00;
            case VERY_SUSPICIOUS -> 0xFF2828;
        };
    }

    public int flaggedCount() {
        int n = 0;
        for (ResultCache.Entry e : cache.all()) if (e.result.score >= minScore.get() && !e.result.anomalies.isEmpty()) n++;
        return n;
    }

    // --- zapis / odczyt ustawien ---
    private static Path settingsFile() {
        return FabricLoader.getInstance().getGameDir().resolve("config").resolve("sus-chunk-finder").resolve("settings.json");
    }

    public void loadSettings() {
        try {
            Path f = settingsFile();
            if (!Files.exists(f)) return;
            try (Reader r = Files.newBufferedReader(f)) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                for (var e : o.entrySet()) {
                    Opt<?> opt = opts.get(e.getKey());
                    if (opt != null) opt.parse(e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            SusChunkFinderMod.LOG.error("Sus Chunk Finder: nie udalo sie wczytac settings.json", e);
        }
    }

    public void saveSettings() {
        try {
            Path f = settingsFile();
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            opts.forEach((k, v) -> o.addProperty(k, v.show()));
            Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(o));
        } catch (Exception e) {
            SusChunkFinderMod.LOG.error("Sus Chunk Finder: nie udalo sie zapisac settings.json", e);
        }
    }
}
