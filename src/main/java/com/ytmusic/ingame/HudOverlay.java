package com.ytmusic.ingame;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

/**
 * What stays on screen while the player window is closed: nothing / a thin bar / a card with cover art /
 * the page itself. While the chat is open, {@link ChatOverlay} draws the (interactive) version instead.
 */
final class HudOverlay {
    private static final MiniWindow MINI = new MiniWindow();

    private HudOverlay() {}

    static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof PlayerScreen || mc.currentScreen instanceof ChatScreen) return;
        if (mc.options.hudHidden) return;

        YtConfig cfg = YtMusicClient.CONFIG;
        YtPlayer player = YtMusicClient.PLAYER;
        if (cfg.overlay == YtConfig.Overlay.HIDDEN || player.browser() == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        TextRenderer tr = mc.textRenderer;

        if (cfg.overlay == YtConfig.Overlay.CARD) {
            MINI.render(ctx, tr, player, cfg, sw, sh, false, 0, 0);
            return;
        }

        // FULL: the page itself, at the same place it has inside the player window
        PlayerLayout l = PlayerLayout.of(cfg, sw, sh);
        int scale = guiScale(mc);
        player.syncSize(l.contentW() * scale, l.contentH() * scale);
        PlayerRenderer.rectBorder(ctx, l.contentX() - 1, l.contentY() - 1, l.contentW() + 2, l.contentH() + 2, 0xFF000000);
        PlayerRenderer.drawBrowser(ctx, tr, player, l.contentX(), l.contentY(), l.contentW(), l.contentH());
    }

    static int guiScale(MinecraftClient mc) {
        return (int) Math.max(1L, Math.round((double) mc.getWindow().getScaleFactor()));
    }
}
