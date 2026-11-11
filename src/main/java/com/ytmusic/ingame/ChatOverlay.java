package com.ytmusic.ingame;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;

/**
 * While the chat is open the mouse cursor is free, so the overlay becomes interactive:
 * drag it anywhere, click the buttons, seek, scroll to change the volume, Ctrl + scroll or the corner grip
 * to change the size - and in "full" mode use the
 * YouTube Music page itself with the mouse. Typing keeps going to the chat box.
 *
 * Mouse buttons are polled from GLFW every frame instead of hooking the click events (no mixins needed).
 */
final class ChatOverlay {
    private final PlayerWindow window;
    private final MiniWindow mini = new MiniWindow();

    private boolean first = true;
    private boolean prevDown;
    private boolean owning;

    private ChatOverlay() {
        this.window = new PlayerWindow(YtMusicClient.CONFIG, YtMusicClient.PLAYER, () -> {
            YtMusicClient.CONFIG.overlay = YtConfig.Overlay.HIDDEN;
            YtMusicClient.CONFIG.save();
        });
    }

    static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen)) return;
            ChatOverlay overlay = new ChatOverlay();
            ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, tickDelta) -> overlay.render(client, ctx, mouseX, mouseY));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, hAmount, vAmount) -> !overlay.scroll(client, mx, my, vAmount));
            ScreenEvents.remove(screen).register(s -> overlay.finish());
        });
    }

    private static boolean active() {
        YtConfig cfg = YtMusicClient.CONFIG;
        return cfg.overlay != YtConfig.Overlay.HIDDEN && YtMusicClient.PLAYER.browser() != null;
    }

    private void render(MinecraftClient mc, DrawContext ctx, int mx, int my) {
        if (!active()) return;
        YtConfig cfg = YtMusicClient.CONFIG;
        YtPlayer player = YtMusicClient.PLAYER;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int scale = HudOverlay.guiScale(mc);
        boolean full = cfg.overlay == YtConfig.Overlay.FULL;

        if (full) window.render(ctx, mc.textRenderer, sw, sh, scale, mx, my);
        else mini.render(ctx, mc.textRenderer, player, cfg, sw, sh, true, mx, my);

        // --- mouse input (left button), polled
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (first) {
            first = false;
            prevDown = down; // a button that was already held when the chat opened is not a click
        }
        if (down && !prevDown) {
            owning = full ? window.mouseClicked(mx, my, 0, sw, sh, scale)
                    : mini.mouseClicked(mx, my, cfg, player, sw, sh);
        } else if (down && owning) {
            if (full) window.mouseDragged(mx, my, sw, sh, scale);
            else mini.mouseDragged(mx, my, cfg, sw, sh);
        } else if (!down && prevDown && owning) {
            if (full) window.mouseReleased(mx, my, sw, sh, scale);
            else mini.mouseReleased(cfg, player);
            owning = false;
        }
        prevDown = down;
    }

    private boolean scroll(MinecraftClient mc, double mx, double my, double vertical) {
        if (!active()) return false;
        YtConfig cfg = YtMusicClient.CONFIG;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (cfg.overlay == YtConfig.Overlay.FULL) {
            return window.mouseScrolled(mx, my, vertical, sw, sh, HudOverlay.guiScale(mc));
        }
        long win = mc.getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        return mini.mouseScrolled(mx, my, vertical, ctrl, cfg, YtMusicClient.PLAYER, sw, sh);
    }

    private void finish() {
        window.cancel();
        mini.cancel();
        YtMusicClient.CONFIG.save();
    }
}
