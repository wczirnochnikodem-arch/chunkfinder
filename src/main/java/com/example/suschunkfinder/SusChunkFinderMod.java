package com.example.suschunkfinder;

import com.example.suschunkfinder.render.SusHud;
import com.example.suschunkfinder.render.SusRender;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public class SusChunkFinderMod implements ClientModInitializer {
    public static final String ID = "sus-chunk-finder";
    public static final Logger LOG = LogUtils.getLogger();
    public static final Finder FINDER = new Finder();

    private static KeyBinding toggleKey, scanKey;

    @Override
    public void onInitializeClient() {
        LOG.info("Sus Chunk Finder init");
        FINDER.loadSettings();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sus-chunk-finder.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.sus-chunk-finder"));
        scanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sus-chunk-finder.scan", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.sus-chunk-finder"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                FINDER.enabled = !FINDER.enabled;
                if (client.player != null)
                    client.player.sendMessage(Text.literal("[SusFinder] " + (FINDER.enabled ? "ON" : "OFF")), true);
            }
            while (scanKey.wasPressed()) FINDER.manualScan();
            FINDER.tick();
        });

        WorldRenderEvents.LAST.register(ctx -> SusRender.render(ctx, FINDER));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> SusHud.render(ctx, FINDER));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Commands.register(dispatcher, FINDER));
    }
}
