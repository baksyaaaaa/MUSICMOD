package com.ytmusic.ingame;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YtMusicClient implements ClientModInitializer {
    public static final String MOD_ID = "ytmusic";
    public static final Logger LOG = LoggerFactory.getLogger("YT Music");

    public static YtConfig CONFIG;
    public static YtPlayer PLAYER;

    private static KeyBinding openKey, overlayKey, cornerKey, playKey, nextKey, prevKey, volUpKey, volDownKey, muteKey, styleKey;
    private static int cornerIndex;
    private int tick;

    @Override
    public void onInitializeClient() {
        CONFIG = YtConfig.load();
        UserAgentSupport.apply(CONFIG);
        PLAYER = new YtPlayer(CONFIG);

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = register("open", GLFW.GLFW_KEY_Y, category);
        playKey = register("playpause", GLFW.GLFW_KEY_BACKSLASH, category);
        prevKey = register("prev", GLFW.GLFW_KEY_LEFT_BRACKET, category);
        nextKey = register("next", GLFW.GLFW_KEY_RIGHT_BRACKET, category);
        volUpKey = register("vol_up", GLFW.GLFW_KEY_EQUAL, category);
        volDownKey = register("vol_down", GLFW.GLFW_KEY_MINUS, category);
        muteKey = register("mute", GLFW.GLFW_KEY_M, category);
        styleKey = register("style", GLFW.GLFW_KEY_UNKNOWN, category); // unbound by default; also a button in the window

        ChatOverlay.init();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "overlay"), (context, tickCounter) -> HudOverlay.render(context));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            CONFIG.save();
            PLAYER.shutdown();
        });
    }

    private static KeyBinding register(String name, int key, KeyBinding.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.ytmusic." + name, key, category));
    }

    private void onTick(MinecraftClient mc) {
        tick++;
        PLAYER.tick(mc);

        // Key binds only react in the game itself; inside the player window all keys go to the web page.
        if (mc.currentScreen != null || mc.player == null) return;

        while (openKey.wasPressed()) {
            mc.setScreen(new PlayerScreen());
        }
        while (styleKey.wasPressed()) {
            CONFIG.style = CONFIG.style.next();
            CONFIG.save();
            toast(mc, "YT Music style: " + (CONFIG.style == YtConfig.HudStyle.MODERN ? "modern" : "Minecraft"));
        }
        while (playKey.wasPressed()) {
            if (ensureBrowser(mc)) PLAYER.togglePlay();
        }
        while (nextKey.wasPressed()) {
            if (ensureBrowser(mc)) PLAYER.next();
        }
        while (prevKey.wasPressed()) {
            if (ensureBrowser(mc)) PLAYER.prev();
        }
        while (muteKey.wasPressed()) {
            if (ensureBrowser(mc)) {
                PLAYER.toggleMute();
                CONFIG.save();
                toast(mc, PLAYER.muted() ? "YT Music: muted" : "YT Music volume: " + PLAYER.volume() + "%");
            }
        }
        if (tick % 3 == 0) {
            if (volUpKey.isPressed() && ensureBrowser(mc)) {
                PLAYER.adjustVolume(2);
                toast(mc, "YT Music volume: " + PLAYER.volume() + "%");
            }
            if (volDownKey.isPressed() && ensureBrowser(mc)) {
                PLAYER.adjustVolume(-2);
                toast(mc, "YT Music volume: " + PLAYER.volume() + "%");
            }
        }
    }

    /** Moves the current overlay to the next screen corner: top-right, bottom-right, bottom-left, top-left. */
    private static void snapToNextCorner(MinecraftClient mc) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        cornerIndex = (cornerIndex + 1) % 4;
        boolean right = cornerIndex == 0 || cornerIndex == 1;
        boolean top = cornerIndex == 0 || cornerIndex == 3;
        int m = 6;
        if (CONFIG.overlay == YtConfig.Overlay.FULL) {
            PlayerLayout l = PlayerLayout.of(CONFIG, sw, sh);
            CONFIG.x = right ? sw - l.w - m : m;
            CONFIG.y = top ? m : sh - l.h - m;
        } else {
            MiniLayout l = MiniLayout.of(CONFIG, sw, sh);
            CONFIG.miniX = right ? sw - l.pw() - m : m;
            CONFIG.miniY = top ? m : sh - l.ph() - m;
        }
        CONFIG.save();
    }

    /** Starts the browser in the background if needed (so the hotkeys work before the window was ever opened). */
    private static boolean ensureBrowser(MinecraftClient mc) {
        if (PLAYER.browser() != null) return true;
        int scale = HudOverlay.guiScale(mc);
        PlayerLayout l = PlayerLayout.of(CONFIG, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        boolean ok = PLAYER.ensureBrowser(l.contentW() * scale, l.contentH() * scale);
        if (!ok) toast(mc, "YT Music: Chromium is not ready yet");
        return ok;
    }

    static void toast(MinecraftClient mc, String message) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(message), true);
    }
}
