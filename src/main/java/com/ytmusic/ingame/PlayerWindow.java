package com.ytmusic.ingame;

import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * The full player window (title bar, web page, control bar). Used by the player screen (key Y)
 * and, in "full" overlay mode, on top of the chat. Input is passed in by the host.
 */
final class PlayerWindow {
    private enum Drag { NONE, MOVE, RESIZE, VOLUME, SEEK, BROWSER }

    private static final int MODE_W = 54;
    private static final int STYLE_W = 44;

    private final YtConfig cfg;
    private final YtPlayer player;
    private final Runnable onClose;

    private Drag drag = Drag.NONE;
    private int grabX, grabY, dragButton;
    private double seekPreview = -1;

    PlayerWindow(YtConfig cfg, YtPlayer player, Runnable onClose) {
        this.cfg = cfg;
        this.player = player;
        this.onClose = onClose;
    }

    PlayerLayout layout(int sw, int sh) {
        return PlayerLayout.of(cfg, sw, sh);
    }

    boolean busy() { return drag != Drag.NONE; }

    boolean contains(double mx, double my, int sw, int sh) {
        return layout(sw, sh).contains(mx, my);
    }

    /** Ends any drag (the host screen is going away). */
    void cancel() {
        if (drag == Drag.BROWSER) {
            RinkuBrowser b = player.browser();
            if (b != null) b.sendMouseRelease(0, 0, dragButton);
        }
        if (drag == Drag.MOVE || drag == Drag.RESIZE || drag == Drag.VOLUME) cfg.save();
        drag = Drag.NONE;
        seekPreview = -1;
    }

    private static int closeX(PlayerLayout l) { return l.x + l.w - 14; }
    private static int modeX(PlayerLayout l) { return closeX(l) - 56; }
    private static int styleX(PlayerLayout l) { return closeX(l) - STYLE_W - 4; }

    /** Progress strip of the modern style is inset from the rounded corners. */
    private static int progX(PlayerLayout l) { return Ui.modern() ? l.x + 6 : l.x; }
    private static int progW(PlayerLayout l) { return Ui.modern() ? l.w - 12 : l.w; }

    private static double seekAt(double mx, PlayerLayout l) {
        return Math.max(0.0, Math.min(1.0, (mx - progX(l)) / (double) progW(l)));
    }

    private static String styleLabel(YtConfig.HudStyle s) {
        return s == YtConfig.HudStyle.MODERN ? "Modern" : "MC";
    }

    private int localX(double mx, PlayerLayout l, int scale) { return (int) ((mx - l.contentX()) * scale); }
    private int localY(double my, PlayerLayout l, int scale) { return (int) ((my - l.contentY()) * scale); }

    private void setVolumeFrom(double mx, PlayerLayout l) {
        int v = (int) Math.round((mx - l.sliderX) / (double) l.sliderW * 100.0);
        player.setVolume(PlayerLayout.clamp(v, 0, 100));
    }

    private static String modeLabel(YtConfig.Overlay o) {
        return "HUD: " + switch (o) {
            case HIDDEN -> "off";
            case CARD -> "card";
            case FULL -> "full";
        };
    }

    // ------------------------------------------------------------------ rendering

    void render(DrawContext ctx, TextRenderer tr, int sw, int sh, int scale, int mouseX, int mouseY) {
        PlayerLayout l = layout(sw, sh);
        boolean modern = Ui.modern();

        if (player.browser() == null) player.ensureBrowser(l.contentW() * scale, l.contentH() * scale);
        if (drag != Drag.RESIZE) player.syncSize(l.contentW() * scale, l.contentH() * scale);

        // frame + background
        if (modern) {
            Ui.roundRect(ctx, l.x - 1, l.y - 1, l.w + 2, l.h + 2, 8, 0x66000000);
            Ui.roundRect(ctx, l.x, l.y, l.w, l.h, 7, 0xF5141419);
        } else {
            ctx.fill(l.x - 1, l.y - 1, l.x + l.w + 1, l.y + l.h + 1, 0xFF000000);
            ctx.fill(l.x, l.y, l.x + l.w, l.y + l.h, 0xFF1A1A1A);
        }

        // ---- title bar
        if (modern) {
            ctx.fill(l.x + 6, l.y + PlayerLayout.TITLE_H - 1, l.x + l.w - 6, l.y + PlayerLayout.TITLE_H, 0x22FFFFFF);
        } else {
            ctx.fill(l.x, l.y, l.x + l.w, l.y + PlayerLayout.TITLE_H, 0xFF262626);
            ctx.fill(l.x, l.y + PlayerLayout.TITLE_H - 1, l.x + l.w, l.y + PlayerLayout.TITLE_H, PlayerRenderer.ACCENT);
        }

        int cx = closeX(l);
        int sx0 = styleX(l);
        int textY = l.y + 3;
        Ui.drawText(ctx, tr, "YT Music", l.x + (modern ? 7 : 4), textY, Ui.accent());
        int textStart = l.x + (modern ? 7 : 4) + Ui.width(tr, "YT Music") + 8;
        int room = sx0 - 6 - textStart;
        if (room > 12) {
            Ui.marquee(ctx, tr, player.titleLine(), textStart, textY, room, Ui.gray(), false, null, "window-title");
        }

        boolean hoverStyle = PlayerLayout.in(mouseX, mouseY, sx0, l.y + 1, STYLE_W, 12);
        Ui.button(ctx, sx0, l.y + 1, STYLE_W, 12, hoverStyle);
        Ui.drawTextCentered(ctx, tr, styleLabel(cfg.style), sx0, STYLE_W, textY, Ui.white());

        boolean hoverClose = PlayerLayout.in(mouseX, mouseY, cx, l.y + 1, 13, 12);
        if (modern) {
            Ui.roundRect(ctx, cx, l.y + 1, 13, 12, 6, hoverClose ? 0xFFE5484D : 0x1FFFFFFF);
            Ui.drawTextCentered(ctx, tr, "\u00D7", cx, 13, textY, Ui.white());
        } else {
            ctx.fill(cx, l.y + 1, cx + 13, l.y + 13, hoverClose ? 0xFFB02020 : PlayerRenderer.BTN);
            ctx.drawTextWithShadow(tr, Text.literal("x"), cx + 4, textY, PlayerRenderer.WHITE);
        }

        // ---- web page
        PlayerRenderer.drawBrowser(ctx, tr, player, l.contentX(), l.contentY(), l.contentW(), l.contentH());

        // ---- bottom bar
        if (!modern) ctx.fill(l.x, l.barY(), l.x + l.w, l.y + l.h, 0xFF202020);

        double frac = seekPreview >= 0 ? seekPreview
                : (player.duration() > 0 ? Math.min(1.0, player.position() / (double) player.duration()) : 0.0);
        if (modern) Ui.progress(ctx, progX(l), l.barY() + 1, progW(l), 2, frac, 0);
        else Ui.progress(ctx, l.x, l.barY(), l.w, PlayerLayout.PROGRESS_H, frac, 0xFF3A3A3A);

        int by = l.rowY;
        int B = PlayerLayout.BTN;
        int W = Ui.white();

        Ui.button(ctx, l.prevX, by, B, B, PlayerLayout.in(mouseX, mouseY, l.prevX, by, B, B));
        Ui.iconPrev(ctx, l.prevX + 2, by + 3, W);

        Ui.button(ctx, l.playX, by, B, B, PlayerLayout.in(mouseX, mouseY, l.playX, by, B, B));
        if (player.playing()) Ui.iconPause(ctx, l.playX + 3, by + 3, W);
        else Ui.iconPlay(ctx, l.playX + 3, by + 3, W);

        Ui.button(ctx, l.nextX, by, B, B, PlayerLayout.in(mouseX, mouseY, l.nextX, by, B, B));
        Ui.iconNext(ctx, l.nextX + 2, by + 3, W);

        Ui.button(ctx, l.muteX, by, B, B, PlayerLayout.in(mouseX, mouseY, l.muteX, by, B, B));
        PlayerRenderer.iconSpeaker(ctx, l.muteX + 1, by + 3, player.muted(), player.volume(), W);

        int ty = by + 6;
        int vw = player.muted() ? 0 : (int) (l.sliderW * player.volume() / 100.0);
        if (modern) {
            Ui.progress(ctx, l.sliderX, ty, l.sliderW, 3, player.muted() ? 0 : player.volume() / 100.0, 0);
            Ui.roundRect(ctx, l.sliderX + vw - 3, ty - 2, 7, 7, 3, W);
        } else {
            ctx.fill(l.sliderX, ty, l.sliderX + l.sliderW, ty + 3, 0xFF555555);
            ctx.fill(l.sliderX, ty, l.sliderX + vw, ty + 3, PlayerRenderer.ACCENT);
            ctx.fill(l.sliderX + vw - 1, ty - 3, l.sliderX + vw + 2, ty + 6, PlayerRenderer.WHITE);
        }
        String pct = player.muted() ? "mute" : player.volume() + "%";
        Ui.drawText(ctx, tr, pct, l.pctX, by + 3, Ui.gray());

        if (l.showTime) {
            String time = PlayerRenderer.fmt(player.position()) + "/" + PlayerRenderer.fmt(player.duration());
            Ui.drawText(ctx, tr, time, l.timeX, by + 3, Ui.gray());
        }
        if (l.showZoom) {
            Ui.button(ctx, l.zoomOutX, by, B, B, PlayerLayout.in(mouseX, mouseY, l.zoomOutX, by, B, B));
            Ui.drawTextCentered(ctx, tr, "-", l.zoomOutX, B, by + 3, W);
            Ui.button(ctx, l.zoomInX, by, B, B, PlayerLayout.in(mouseX, mouseY, l.zoomInX, by, B, B));
            Ui.drawTextCentered(ctx, tr, "+", l.zoomInX, B, by + 3, W);
            Ui.button(ctx, l.homeX, by, 28, B, PlayerLayout.in(mouseX, mouseY, l.homeX, by, 28, B));
            Ui.drawTextCentered(ctx, tr, "Home", l.homeX, 28, by + 3, W);
        }

        // resize grip (bottom-right)
        Ui.grip(ctx, l.x + l.w - 2, l.y + l.h - 1, false, Ui.gray());
    }

    // ------------------------------------------------------------------ mouse

    /** Returns true when the click was inside the window and has been handled. */
    boolean mouseClicked(double mx, double my, int button, int sw, int sh, int scale) {
        PlayerLayout l = layout(sw, sh);
        if (!l.contains(mx, my)) return false;

        // web page
        if (PlayerLayout.in(mx, my, l.contentX(), l.contentY(), l.contentW(), l.contentH())) {
            RinkuBrowser b = player.browser();
            if (b != null) {
                b.setFocus(true);
                b.sendMousePress(localX(mx, l, scale), localY(my, l, scale), button);
                drag = Drag.BROWSER;
                dragButton = button;
            }
            return true;
        }
        if (button != 0) return true;

        int B = PlayerLayout.BTN;

        // title bar
        if (PlayerLayout.in(mx, my, l.x, l.y, l.w, PlayerLayout.TITLE_H)) {
            if (PlayerLayout.in(mx, my, closeX(l), l.y + 1, 13, 12)) {
                onClose.run();
                return true;
            }
            if (PlayerLayout.in(mx, my, styleX(l), l.y + 1, STYLE_W, 12)) {
                cfg.style = cfg.style.next();
                cfg.save();
                return true;
            }
            drag = Drag.MOVE;
            grabX = (int) mx - l.x;
            grabY = (int) my - l.y;
            return true;
        }

        // resize grip
        if (PlayerLayout.in(mx, my, l.x + l.w - PlayerLayout.HANDLE, l.y + l.h - PlayerLayout.HANDLE, PlayerLayout.HANDLE, PlayerLayout.HANDLE)) {
            drag = Drag.RESIZE;
            return true;
        }

        // progress strip
        if (PlayerLayout.in(mx, my, l.x, l.barY(), l.w, PlayerLayout.PROGRESS_H + 1)) {
            drag = Drag.SEEK;
            seekPreview = seekAt(mx, l);
            return true;
        }

        // controls row
        int by = l.rowY;
        if (PlayerLayout.in(mx, my, l.prevX, by, B, B)) { player.prev(); return true; }
        if (PlayerLayout.in(mx, my, l.playX, by, B, B)) { player.togglePlay(); return true; }
        if (PlayerLayout.in(mx, my, l.nextX, by, B, B)) { player.next(); return true; }
        if (PlayerLayout.in(mx, my, l.muteX, by, B, B)) { player.toggleMute(); return true; }
        if (PlayerLayout.in(mx, my, l.sliderX - 3, by, l.sliderW + 6, B)) {
            drag = Drag.VOLUME;
            setVolumeFrom(mx, l);
            return true;
        }
        if (l.showZoom) {
            if (PlayerLayout.in(mx, my, l.zoomOutX, by, B, B)) { player.changeZoom(-0.5); cfg.save(); return true; }
            if (PlayerLayout.in(mx, my, l.zoomInX, by, B, B)) { player.changeZoom(0.5); cfg.save(); return true; }
            if (PlayerLayout.in(mx, my, l.homeX, by, 28, B)) { player.home(); return true; }
        }
        return true;
    }

    void mouseDragged(double mx, double my, int sw, int sh, int scale) {
        PlayerLayout l = layout(sw, sh);
        switch (drag) {
            case MOVE -> {
                cfg.x = (int) mx - grabX;
                cfg.y = (int) my - grabY;
            }
            case RESIZE -> {
                cfg.w = Math.min((int) mx - l.x + PlayerLayout.HANDLE / 2, sw - l.x);
                cfg.h = Math.min((int) my - l.y + PlayerLayout.HANDLE / 2, sh - l.y);
            }
            case VOLUME -> setVolumeFrom(mx, l);
            case SEEK -> seekPreview = seekAt(mx, l);
            case BROWSER -> {
                RinkuBrowser b = player.browser();
                if (b != null) b.sendMouseMove(localX(mx, l, scale), localY(my, l, scale));
            }
            default -> {
            }
        }
    }

    void mouseReleased(double mx, double my, int sw, int sh, int scale) {
        PlayerLayout l = layout(sw, sh);
        Drag finished = drag;
        drag = Drag.NONE;
        switch (finished) {
            case BROWSER -> {
                RinkuBrowser b = player.browser();
                if (b != null) b.sendMouseRelease(localX(mx, l, scale), localY(my, l, scale), dragButton);
            }
            case RESIZE -> {
                PlayerLayout nl = layout(sw, sh);
                player.syncSize(nl.contentW() * scale, nl.contentH() * scale);
                cfg.save();
            }
            case MOVE, VOLUME -> cfg.save();
            case SEEK -> {
                if (seekPreview >= 0) player.seekFraction(seekPreview);
                seekPreview = -1;
            }
            default -> {
            }
        }
    }

    void mouseMoved(double mx, double my, int sw, int sh, int scale) {
        RinkuBrowser b = player.browser();
        if (b == null) return;
        PlayerLayout l = layout(sw, sh);
        if (PlayerLayout.in(mx, my, l.contentX(), l.contentY(), l.contentW(), l.contentH())) {
            b.sendMouseMove(localX(mx, l, scale), localY(my, l, scale));
        }
    }

    /** Returns true when the wheel event was over the window and has been handled. */
    boolean mouseScrolled(double mx, double my, double vertical, int sw, int sh, int scale) {
        PlayerLayout l = layout(sw, sh);
        if (PlayerLayout.in(mx, my, l.x, l.barY(), l.w, PlayerLayout.BAR_H)) {
            player.adjustVolume((int) Math.signum(vertical) * 5);
            return true;
        }
        if (PlayerLayout.in(mx, my, l.contentX(), l.contentY(), l.contentW(), l.contentH())) {
            RinkuBrowser b = player.browser();
            if (b != null) b.sendMouseWheel(localX(mx, l, scale), localY(my, l, scale), vertical, 0);
            return true;
        }
        return false;
    }
}
