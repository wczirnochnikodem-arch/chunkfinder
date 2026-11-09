/*
 * This file is part of the Sus Chunk Finder addon.
 * Copyright (c) SusChunkFinder.
 */

package com.suschunkfinder.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;

/**
 * Sus Chunk Finder module for Meteor Client.
 * Evaluates chunks for hidden bases using Suspicion Score (0-100).
 *
 * Configuration is stored automatically by Meteor in modules.nbt.
 * Default thresholds are in assets/suschunkfinder/susfinder-defaults.json.
 */
public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral   = settings.getDefaultGroup();
    private final SettingGroup sgScan      = settings.createGroup("Scan");
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgHeight    = settings.createGroup("Height Zones");
    private final SettingGroup sgScore     = settings.createGroup("Scoring");
    private final SettingGroup sgRender    = settings.createGroup("Render");
    private final SettingGroup sgFilter    = settings.createGroup("Filters");

    // --- General ---
    public final Setting<Boolean> autoScan = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-scan").description("Scans chunks automatically when loaded.").defaultValue(true).build());
    public final Setting<Integer> minScore = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-suspicion-score").description("Chunks below this threshold are not shown.")
        .defaultValue(20).range(0, 100).sliderRange(0, 100).build());

    // --- Scan ---
    public final Setting<Boolean> fullVertical = sgScan.add(new BoolSetting.Builder()
        .name("full-vertical-scan").description("Scan from minBuildHeight to maxBuildHeight-1. Default ON.").defaultValue(true).build());
    public final Setting<Integer> scanDistance = sgScan.add(new IntSetting.Builder()
        .name("scan-distance").description("Radius in chunks.").defaultValue(8).range(1, 32).sliderRange(1, 32).build());
    public final Setting<Integer> chunksPerTick = sgScan.add(new IntSetting.Builder()
        .name("chunks-per-tick").description("Limit of chunks analyzed per tick (anti-lag).").defaultValue(1).range(1, 8).build());
    public final Setting<Integer> sectionsPerTick = sgScan.add(new IntSetting.Builder()
        .name("sections-per-tick").description("How many 16x16x16 sections per tick in one chunk.").defaultValue(4).range(1, 32).build());
    public final Setting<Boolean> rescanOnChange = sgScan.add(new BoolSetting.Builder()
        .name("rescan-only-on-change").description("Skips chunks whose data has not changed.").defaultValue(true).build());

    // --- Detection (each category independent) ---
    public final Setting<Boolean> blockDetection = sgDetection.add(new BoolSetting.Builder()
        .name("block-detection").description("Valuable/unusual blocks.").defaultValue(true).build());
    public final Setting<Boolean> storageDetection = sgDetection.add(new BoolSetting.Builder()
        .name("storage-block-detection").description("Chests, furnaces, anvils etc.").defaultValue(true).build());
    public final Setting<Boolean> redstoneDetection = sgDetection.add(new BoolSetting.Builder()
        .name("redstone-detection").description("Redstone (wire only from 20 pieces).").defaultValue(true).build());
    public final Setting<Boolean> terrainAnomaly = sgDetection.add(new BoolSetting.Builder()
        .name("terrain-anomaly-detection").description("Artificial rooms, corridors, empty spaces.").defaultValue(true).build());
    public final Setting<Boolean> playerModDetection = sgDetection.add(new BoolSetting.Builder()
        .name("player-modification-detection").description("Heuristics of player interference traces (SUSPICION, not proof).").defaultValue(true).build());

    // --- Height zones ---
    public final Setting<Boolean> scanUnderground = sgHeight.add(new BoolSetting.Builder()
        .name("scan-underground").description("Classify/report underground zone.").defaultValue(true).build());
    public final Setting<Boolean> scanSurface = sgHeight.add(new BoolSetting.Builder()
        .name("scan-surface").description("Classify/report surface zone.").defaultValue(true).build());
    public final Setting<Boolean> scanAbove100 = sgHeight.add(new BoolSetting.Builder()
        .name("scan-above-y100").description("Classify/report high areas.").defaultValue(true).build());
    public final Setting<Integer> undergroundMaxY = sgHeight.add(new IntSetting.Builder()
        .name("underground-max-y").defaultValue(50).range(-64, 320).sliderRange(-64, 320).build());
    public final Setting<Integer> surfaceMaxY = sgHeight.add(new IntSetting.Builder()
        .name("surface-max-y").defaultValue(100).range(-64, 320).sliderRange(-64, 320).build());

    // --- Scoring ---
    public final Setting<Double> aboveY100Mult = sgScore.add(new DoubleSetting.Builder()
        .name("above-y100-multiplier").defaultValue(1.3).range(0.1, 3).build());
    public final Setting<Double> naturalMult = sgScore.add(new DoubleSetting.Builder()
        .name("natural-origin-multiplier").description("Multiplier for blocks with Natural origin.").defaultValue(0.3).range(0, 1).build());
    public final Setting<Double> suspectedMult = sgScore.add(new DoubleSetting.Builder()
        .name("suspected-player-multiplier").defaultValue(1.25).range(1, 3).build());

    // --- Render ---
    public final Setting<Boolean> renderChunks = sgRender.add(new BoolSetting.Builder()
        .name("render-chunks").defaultValue(true).build());
    public final Setting<Boolean> showCoords = sgRender.add(new BoolSetting.Builder()
        .name("show-coordinates").defaultValue(true).build());
    public final Setting<Boolean> showScore = sgRender.add(new BoolSetting.Builder()
        .name("show-suspicion-score").defaultValue(true).build());
    public final Setting<Boolean> showOrigins = sgRender.add(new BoolSetting.Builder()
        .name("show-block-origins").defaultValue(true).build());

    // --- Filters ---
    public final Setting<Integer> filterMinY = sgFilter.add(new IntSetting.Builder()
        .name("filter-min-y").defaultValue(-64).range(-64, 320).sliderRange(-64, 320).build());
    public final Setting<Integer> filterMaxY = sgFilter.add(new IntSetting.Builder()
        .name("filter-max-y").defaultValue(320).range(-64, 320).sliderRange(-64, 320).build());
    public final Setting<Boolean> showUnknown = sgFilter.add(new BoolSetting.Builder()
        .name("show-origin-unknown").defaultValue(true).build());
    public final Setting<Boolean> showSuspected = sgFilter.add(new BoolSetting.Builder()
        .name("show-origin-suspected").defaultValue(true).build());
    public final Setting<Boolean> showNatural = sgFilter.add(new BoolSetting.Builder()
        .name("show-origin-natural").defaultValue(false).build());

    public SusChunkFinder() {
        super(Categories.World, "sus-chunk-finder",
            "Evaluates chunks for hidden bases (Suspicion Score 0-100).");
    }

    // TODO: onActivate/onDeactivate, @EventHandler TickEvent.Post (scan queue),
    // ChunkDataEvent / PacketEvent.Receive (new data), Render3DEvent (rendering),
    // reset cache on GameJoinedEvent / dimension change.
    // Buttons "Manual Scan", "Clear Results", "Export Results": getWidget(GuiTheme) + theme.button(...).
}
