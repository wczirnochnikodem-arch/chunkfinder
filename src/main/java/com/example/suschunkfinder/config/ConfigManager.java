package com.example.suschunkfinder.config;

import com.example.suschunkfinder.SusChunkFinderMod;
import com.example.suschunkfinder.data.Model.AnomalyType;
import com.example.suschunkfinder.data.Model.Category;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Wczytuje config.json (progi, wagi, filtry) i buduje mapy Block -> id / kategoria / marker struktury. */
public final class ConfigManager {
    public final Map<Category, Map<String, Integer>> thresholds = new EnumMap<>(Category.class);
    public final Map<String, Double> weights = new HashMap<>();
    public final Map<String, Double> multipliers = new HashMap<>();
    public int[] levelMin = {0, 20, 40, 70};
    public boolean ignoreCaves = true, ignoreGeodes = true;
    public final Set<String> ignoredStructures = new HashSet<>();

    public final Map<Block, String> hitIds = new HashMap<>();
    public final Map<Block, Category> hitCats = new HashMap<>();
    public final Map<Block, String> markerBlocks = new HashMap<>();
    public final Set<Block> tracked = new HashSet<>();

    /** Heurystyczne bloki-markery naturalnych struktur (klient nie zna danych struktur, wiec zgadujemy po blokach). */
    private static final Map<String, String> MARKERS = new HashMap<>();
    private static void mk(String structure, String... blocks) { for (String b : blocks) MARKERS.put(b, structure); }
    static {
        mk("village", "bell", "hay_block", "composter", "lectern", "loom", "cartography_table", "fletching_table", "smithing_table", "grindstone", "dirt_path");
        mk("mineshaft", "rail", "cobweb");
        mk("stronghold", "end_portal_frame", "mossy_stone_bricks");
        mk("ancient_city", "sculk", "sculk_catalyst", "sculk_shrieker", "sculk_sensor", "reinforced_deepslate");
        mk("bastion", "gilded_blackstone", "polished_blackstone_bricks", "chiseled_polished_blackstone");
        mk("fortress", "nether_brick_fence", "nether_bricks");
        mk("end_city", "purpur_block", "purpur_pillar", "end_rod");
        mk("trial_chamber", "trial_spawner", "vault", "copper_bulb", "tuff_bricks", "chiseled_tuff");
        mk("ruined_portal", "magma_block", "crying_obsidian");
        mk("desert_pyramid", "chiseled_sandstone");
        mk("jungle_temple", "tripwire_hook");
        mk("ocean_monument", "prismarine_bricks", "sea_lantern", "dark_prismarine", "wet_sponge");
        mk("woodland_mansion", "dark_oak_planks");
        mk("trail_ruins", "suspicious_gravel", "suspicious_sand");
        mk("geode", "budding_amethyst", "amethyst_block");
        mk("dungeon", "mossy_cobblestone");
    }

    public void load() {
        thresholds.clear(); weights.clear(); multipliers.clear(); ignoredStructures.clear();
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("config").resolve("sus-chunk-finder");
            Files.createDirectories(dir);
            Path file = dir.resolve("config.json");
            if (!Files.exists(file)) {
                try (InputStream in = ConfigManager.class.getResourceAsStream("/susfinder-defaults.json")) {
                    if (in != null) Files.copy(in, file);
                }
            }
            try (Reader r = Files.newBufferedReader(file)) {
                parse(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (Exception e) {
            SusChunkFinderMod.LOG.error("Sus Chunk Finder: blad wczytywania config.json - brak progow!", e);
        }
        buildBlockMaps();
    }

    private void parse(JsonObject root) {
        JsonObject th = root.getAsJsonObject("thresholds");
        thresholds.put(Category.VALUABLE, ints(th, "valuable"));
        thresholds.put(Category.STORAGE, ints(th, "storage"));
        thresholds.put(Category.REDSTONE, ints(th, "redstone"));
        thresholds.put(Category.ARTIFICIAL, ints(th, "artificial"));

        JsonObject sc = root.getAsJsonObject("scoring");
        if (sc != null) {
            doubles(sc, "weights", weights);
            doubles(sc, "multipliers", multipliers);
            JsonObject lv = sc.getAsJsonObject("levels");
            if (lv != null) {
                levelMin = new int[]{
                    lv.getAsJsonArray("low").get(0).getAsInt(),
                    lv.getAsJsonArray("moderate").get(0).getAsInt(),
                    lv.getAsJsonArray("suspicious").get(0).getAsInt(),
                    lv.getAsJsonArray("verySuspicious").get(0).getAsInt()};
            }
        }
        JsonObject nf = root.getAsJsonObject("naturalFilters");
        if (nf != null) {
            if (nf.has("ignoreCaves")) ignoreCaves = nf.get("ignoreCaves").getAsBoolean();
            if (nf.has("ignoreGeodes")) ignoreGeodes = nf.get("ignoreGeodes").getAsBoolean();
            if (nf.has("ignoreVanillaStructures"))
                for (JsonElement e : nf.getAsJsonArray("ignoreVanillaStructures")) ignoredStructures.add(e.getAsString());
        }
    }

    private static Map<String, Integer> ints(JsonObject parent, String key) {
        Map<String, Integer> m = new HashMap<>();
        if (parent == null || !parent.has(key)) return m;
        for (var e : parent.getAsJsonObject(key).entrySet()) m.put(e.getKey(), e.getValue().getAsInt());
        return m;
    }

    private static void doubles(JsonObject parent, String key, Map<String, Double> out) {
        if (!parent.has(key)) return;
        for (var e : parent.getAsJsonObject(key).entrySet()) out.put(e.getKey(), e.getValue().getAsDouble());
    }

    /** "red_shulker_box" -> "shulker_box", "oak_button" -> "button" itd. - config uzywa nazw ogolnych. */
    static String normalize(String p) {
        if (p.endsWith("_button")) return "button";
        if (p.endsWith("_pressure_plate")) return "pressure_plate";
        if (p.endsWith("shulker_box")) return "shulker_box";
        if (p.endsWith("_planks")) return "planks";
        if (p.endsWith("_wool")) return "wool";
        if (p.endsWith("_concrete")) return "concrete";
        if (p.endsWith("_bed")) return "bed";
        if (p.endsWith("glass_pane")) return "glass_pane";
        if (p.equals("glass") || p.endsWith("_stained_glass")) return "glass";
        if (p.endsWith("anvil")) return "anvil";
        if (p.equals("wall_torch")) return "torch";
        return p;
    }

    private void buildBlockMaps() {
        hitIds.clear(); hitCats.clear(); markerBlocks.clear(); tracked.clear();
        for (Block b : Registries.BLOCK) {
            var id = Registries.BLOCK.getId(b);
            if (!"minecraft".equals(id.getNamespace())) continue;
            String path = id.getPath();
            String key = normalize(path);
            for (Category c : Category.values()) {
                Map<String, Integer> t = thresholds.get(c);
                if (t != null && t.containsKey(key)) { hitIds.put(b, key); hitCats.put(b, c); tracked.add(b); break; }
            }
            String s = MARKERS.get(path);
            if (s != null && structureEnabled(s)) { markerBlocks.put(b, s); tracked.add(b); }
        }
    }

    public boolean structureEnabled(String s) {
        if ("dungeon".equals(s)) return true;
        if ("geode".equals(s)) return ignoreGeodes;
        return ignoredStructures.contains(s);
    }

    public Category categoryOf(String id) {
        for (Category c : Category.values()) {
            Map<String, Integer> t = thresholds.get(c);
            if (t != null && t.containsKey(id)) return c;
        }
        return null;
    }

    public int threshold(String id) {
        Category c = categoryOf(id);
        return c == null ? 0 : thresholds.get(c).getOrDefault(id, 0);
    }

    public double weight(AnomalyType t) { return weights.getOrDefault(t.weightKey, t.defaultWeight); }

    public double multiplier(String key, double def) { return multipliers.getOrDefault(key, def); }
}
