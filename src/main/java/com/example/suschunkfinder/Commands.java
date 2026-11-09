package com.example.suschunkfinder;

import com.example.suschunkfinder.data.Model.Anomaly;
import com.example.suschunkfinder.data.Model.ChunkResult;
import com.example.suschunkfinder.scan.ResultCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.example.suschunkfinder.Mc.mc;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** /susfinder toggle | scan | clear | export | reload | list | options | get <opt> | set <opt> <value> */
public final class Commands {
    private Commands() {}

    private static void say(CommandContext<FabricClientCommandSource> c, String s) {
        c.getSource().sendFeedback(Text.literal(s));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d, Finder f) {
        d.register(literal("susfinder")
            .executes(c -> { say(c, "/susfinder toggle | scan | clear | export | reload | list | options | get <opcja> | set <opcja> <wartosc>"); return 1; })
            .then(literal("toggle").executes(c -> { f.enabled = !f.enabled; say(c, "Sus Chunk Finder: " + (f.enabled ? "ON" : "OFF")); return 1; }))
            .then(literal("scan").executes(c -> { f.manualScan(); say(c, "Skan wymuszony dla chunkow w promieniu " + f.scanDistance.get()); return 1; }))
            .then(literal("clear").executes(c -> { f.clearAll(); say(c, "Wyniki wyczyszczone."); return 1; }))
            .then(literal("reload").executes(c -> { f.reloadConfig(); say(c, "Config (progi) przeladowany."); return 1; }))
            .then(literal("export").executes(c -> {
                try { say(c, "Zapisano: " + f.export()); } catch (Exception e) { say(c, "Eksport nie powiodl sie: " + e.getMessage()); }
                return 1;
            }))
            .then(literal("list").executes(c -> { list(c, f); return 1; }))
            .then(literal("options").executes(c -> {
                f.opts.forEach((k, o) -> say(c, k + " = " + o.show() + "  [" + o.hint() + "]  " + o.desc));
                return 1;
            }))
            .then(literal("get").then(argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> CommandSource.suggestMatching(f.opts.keySet(), b))
                .executes(c -> {
                    Finder.Opt<?> o = f.opts.get(StringArgumentType.getString(c, "name"));
                    say(c, o == null ? "Nieznana opcja." : o.name + " = " + o.show() + "  [" + o.hint() + "]");
                    return 1;
                })))
            .then(literal("set").then(argument("name", StringArgumentType.word())
                .suggests((ctx, b) -> CommandSource.suggestMatching(f.opts.keySet(), b))
                .then(argument("value", StringArgumentType.greedyString()).executes(c -> {
                    Finder.Opt<?> o = f.opts.get(StringArgumentType.getString(c, "name"));
                    if (o == null) { say(c, "Nieznana opcja. Uzyj /susfinder options"); return 0; }
                    if (!o.parse(StringArgumentType.getString(c, "value"))) { say(c, "Zla wartosc. Dozwolone: " + o.hint()); return 0; }
                    f.saveSettings();
                    say(c, o.name + " = " + o.show());
                    return 1;
                }))))
        );
    }

    private static void list(CommandContext<FabricClientCommandSource> c, Finder f) {
        List<ChunkResult> rs = new ArrayList<>();
        for (ResultCache.Entry e : f.cache.all()) if (f.visible(e.result)) rs.add(e.result);
        rs.sort(Comparator.comparingInt((ChunkResult r) -> -r.score));
        if (rs.isEmpty()) { say(c, "Brak chunkow powyzej progu " + f.minScore.get() + " (w kolejce: " + f.scanner.pending() + ")."); return; }
        int n = 0;
        for (ChunkResult r : rs) {
            if (n++ >= 10) break;
            say(c, String.format("[%d] %s  chunk %d,%d  X %d Z %d  Y %d..%d  %s%s", r.score, r.level.label, r.pos.x, r.pos.z,
                r.pos.getStartX() + 8, r.pos.getStartZ() + 8, r.minY, r.maxY,
                r.dominantOrigin() == null ? "-" : r.dominantOrigin().label, r.complete ? "" : "  INCOMPLETE " + r.progress + "%"));
            int k = 0;
            for (Anomaly a : r.anomalies) {
                if (k++ >= 3) break;
                say(c, String.format("    +%.0f %s @ %d %d %d (%s, %s)", a.contribution, a.detail, a.pos.getX(), a.pos.getY(), a.pos.getZ(), a.zone.label, a.origin.label));
            }
        }
    }
}
