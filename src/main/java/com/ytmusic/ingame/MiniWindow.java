package com.ytmusic.ingame;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * The two small overlays: a thin "now playing" bar and a card with the cover art on the side.
 * Drawn on the HUD (not interactive) and on top of the chat (interactive: drag to move, buttons, seek,
 * corner grip or Ctrl + wheel to change the size, wheel for volume).
 */
final class MiniWindow {
    private enum Drag { NONE, MOVE, SEEK, SCALE }

    private Drag drag = Drag.NONE;
    private int grabX, grabY;
    private double seekPreview = -1;

    // resize drag: the outer edge stays where it is
    private boolean scaleRightAnchored;
    private int scaleAnchorX;
    private double scaleGrab;

    // ------------------------------------------------------------------ drawing

    void render(DrawContext ctx, TextRenderer tr, YtPlayer p, YtConfig cfg, int sw, int sh,
                boolean interactive, int mx, int my) {
        MiniLayout l = MiniLayout.of(cfg, sw, sh);
        Ui.Xform xf = l.xform();
        double lmx = interactive ? l.lx(mx) : -1e6, lmy = interactive ? l.ly(my) : -1e6;
        xf.enter(ctx);
        try {
            if (l.card) drawCard(ctx, tr, p, l, xf, interactive, lmx, lmy);
            else drawBar(ctx, tr, p, l, xf, interactive, lmx, lmy);
        } finally {
            xf.exit(ctx);
        }
    }

    private double fraction(YtPlayer p) {
        if (seekPreview >= 0) return seekPreview;
        return p.duration() > 0 ? Math.min(1.0, p.position() / (double) p.duration()) : 0.0;
    }

    /** Classic style only: thin accent stripe on the inner side. */
    private void stripe(DrawContext ctx, MiniLayout l) {
        ctx.fill(l.stripeX(), 0, l.stripeX() + 2, l.h, PlayerRenderer.ACCENT);
    }

    private void drawBar(DrawContext ctx, TextRenderer tr, YtPlayer p, MiniLayout l, Ui.Xform xf,
                         boolean interactive, double mx, double my) {
        Ui.panel(ctx, 0, 0, l.w, l.h, 0xC0101010, 6);
        if (!l.modern) stripe(ctx, l);

        // state icon (while the chat is open the play/pause button shows it and this corner holds the size grip)
        if (!interactive) {
            int ix = l.barIconX();
            if (p.playing()) Ui.iconPlay(ctx, ix, 4, Ui.white());
            else Ui.iconPause(ctx, ix, 4, Ui.white());
        }

        int left = l.barTextL(interactive), right = l.barTextR(interactive);
        Ui.marquee(ctx, tr, p.titleLine(), left, l.modern ? 5 : 4, right - left, Ui.white(), l.mirror, xf, "mini-bar");

        Ui.progress(ctx, l.progressX(), l.progressY(), l.progressW(), l.progressH(), fraction(p), 0xFF333333);
        if (interactive) {
            buttons(ctx, p, l, mx, my);
            grip(ctx, l, mx, my);
        }
    }

    private void drawCard(DrawContext ctx, TextRenderer tr, YtPlayer p, MiniLayout l, Ui.Xform xf,
                          boolean interactive, double mx, double my) {
        p.requestCover();
        Ui.panel(ctx, 0, 0, l.w, l.h, 0xD0101010, 8);
        if (!l.modern) stripe(ctx, l);

        // cover art
        int cx = l.coverX(), cy = l.coverY(), cs = MiniLayout.COVER;
        Identifier cover = p.coverTexture();
        if (l.modern) {
            if (cover != null) Ui.image(ctx, cover, cx, cy, cs, cs, 5);
            else {
                Ui.roundRect(ctx, cx, cy, cs, cs, 5, 0xFF2A2A31);
                Ui.iconPlay(ctx, cx + cs / 2 - 4, cy + cs / 2 - 4, Ui.gray());
            }
        } else {
            ctx.fill(cx - 1, cy - 1, cx + cs + 1, cy + cs + 1, 0xFF000000);
            if (cover != null) PlayerRenderer.drawImage(ctx, cover, cx, cy, cs, cs);
            else {
                ctx.fill(cx, cy, cx + cs, cy + cs, 0xFF2A2A2A);
                PlayerRenderer.iconPlay(ctx, cx + cs / 2 - 4, cy + cs / 2 - 4, PlayerRenderer.GRAY);
            }
        }

        // title (running text) + artist
        int tl = l.textL(), tr0 = l.textR(), room = Math.max(0, tr0 - tl);
        String title = p.title().isEmpty() ? p.titleLine() : p.title();
        Ui.marquee(ctx, tr, title, tl, 6, room, Ui.white(), l.mirror, xf, "mini-card");
        if (!p.artist().isEmpty()) {
            String a = Ui.trim(tr, p.artist(), room);
            if (l.mirror) Ui.drawTextRight(ctx, tr, a, tr0, 17, Ui.gray());
            else Ui.drawText(ctx, tr, a, tl, 17, Ui.gray());
        }

        // state icon + time
        int iconX = l.mirror ? tr0 - 8 : tl;
        if (p.playing()) Ui.iconPlay(ctx, iconX, 32, Ui.white());
        else Ui.iconPause(ctx, iconX, 32, Ui.white());
        if (p.duration() > 0 && (!interactive || l.w >= 190)) {
            String time = PlayerRenderer.fmt(p.position()) + " / " + PlayerRenderer.fmt(p.duration());
            if (l.mirror) Ui.drawTextRight(ctx, tr, time, iconX - 4, 31, Ui.gray());
            else Ui.drawText(ctx, tr, time, iconX + 12, 31, Ui.gray());
        }

        Ui.progress(ctx, l.progressX(), l.progressY(), l.progressW(), l.progressH(), fraction(p), 0xFF333333);
        if (interactive) {
            buttons(ctx, p, l, mx, my);
            grip(ctx, l, mx, my);
        }
    }

    private void buttons(DrawContext ctx, YtPlayer p, MiniLayout l, double mx, double my) {
        int by = l.btnY();
        for (int i = 0; i < 3; i++) {
            int bx = l.btnX(i);
            boolean hover = PlayerLayout.in(mx, my, bx, by, MiniLayout.BTN_W, MiniLayout.BTN_H);
            Ui.button(ctx, bx, by, MiniLayout.BTN_W, MiniLayout.BTN_H, hover);
            int iy = by + 2;
            if (i == 0) Ui.iconPrev(ctx, bx + 2, iy, Ui.white());
            else if (i == 1) {
                if (p.playing()) Ui.iconPause(ctx, bx + 3, iy, Ui.white());
                else Ui.iconPlay(ctx, bx + 3, iy, Ui.white());
            } else Ui.iconNext(ctx, bx + 2, iy, Ui.white());
        }
    }

    private void grip(DrawContext ctx, MiniLayout l, double mx, double my) {
        boolean hover = drag == Drag.SCALE || PlayerLayout.in(mx, my, l.gripX(), l.gripY(), MiniLayout.GRIP, MiniLayout.GRIP);
        int col = hover ? Ui.white() : Ui.alpha(Ui.gray(), 0.8f);
        int bottom = l.h - (l.modern ? 7 : 5);
        if (l.mirror) Ui.grip(ctx, l.w - 2, bottom, false, col);
        else Ui.grip(ctx, 2, bottom, true, col);
    }

    // ------------------------------------------------------------------ interaction (used while the chat is open)

    boolean mouseClicked(double mx, double my, YtConfig cfg, YtPlayer p, int sw, int sh) {
        MiniLayout l = MiniLayout.of(cfg, sw, sh);
        if (!l.contains(mx, my)) return false;
        double x = l.lx(mx), y = l.ly(my);

        if (PlayerLayout.in(x, y, l.gripX(), l.gripY(), MiniLayout.GRIP, MiniLayout.GRIP)) {
            drag = Drag.SCALE;
            scaleRightAnchored = !l.mirror;
            scaleAnchorX = scaleRightAnchored ? l.ox + l.pw() : l.ox;
            // distance between the mouse and the moving edge, so the box does not jump
            scaleGrab = scaleRightAnchored ? mx - l.ox : (l.ox + l.pw()) - mx;
            return true;
        }

        int by = l.btnY();
        for (int i = 0; i < 3; i++) {
            if (PlayerLayout.in(x, y, l.btnX(i), by, MiniLayout.BTN_W, MiniLayout.BTN_H)) {
                if (i == 0) p.prev();
                else if (i == 1) p.togglePlay();
                else p.next();
                return true;
            }
        }
        if (PlayerLayout.in(x, y, 0, l.progressY() - 2, l.w, l.h - l.progressY() + 2)) {
            drag = Drag.SEEK;
            seekPreview = seekAt(l, x);
            return true;
        }
        drag = Drag.MOVE;
        grabX = (int) mx - l.ox;
        grabY = (int) my - l.oy;
        return true;
    }

    private static double seekAt(MiniLayout l, double localX) {
        return Math.max(0.0, Math.min(1.0, (localX - l.progressX()) / (double) l.progressW()));
    }

    void mouseDragged(double mx, double my, YtConfig cfg, int sw, int sh) {
        MiniLayout l = MiniLayout.of(cfg, sw, sh);
        switch (drag) {
            case MOVE -> {
                cfg.miniX = (int) mx - grabX;
                cfg.miniY = (int) my - grabY;
            }
            case SEEK -> seekPreview = seekAt(l, l.lx(mx));
            case SCALE -> {
                double width = scaleRightAnchored ? scaleAnchorX - mx + scaleGrab : mx - scaleAnchorX + scaleGrab;
                setScale(cfg, l, (float) (width / l.w), scaleRightAnchored, scaleAnchorX, sw, sh);
            }
            default -> {
            }
        }
    }

    void mouseReleased(YtConfig cfg, YtPlayer p) {
        if (drag == Drag.MOVE || drag == Drag.SCALE) cfg.save();
        if (drag == Drag.SEEK && seekPreview >= 0) p.seekFraction(seekPreview);
        cancel();
    }

    /** Wheel over the overlay changes the volume; with Ctrl held it changes the size. */
    boolean mouseScrolled(double mx, double my, double vertical, boolean ctrl, YtConfig cfg, YtPlayer p, int sw, int sh) {
        MiniLayout l = MiniLayout.of(cfg, sw, sh);
        if (!l.contains(mx, my)) return false;
        if (ctrl) {
            float next = Math.round((cfg.miniScale + (float) Math.signum(vertical) * 0.1f) * 10f) / 10f;
            boolean right = !l.mirror;
            setScale(cfg, l, next, right, right ? l.ox + l.pw() : l.ox, sw, sh);
            cfg.save();
        } else {
            p.adjustVolume((int) Math.signum(vertical) * 5);
        }
        return true;
    }

    /** Changes the size and keeps the outer edge (the one at the screen border) in place. */
    private static void setScale(YtConfig cfg, MiniLayout l, float scale, boolean rightAnchored, int anchorX, int sw, int sh) {
        cfg.miniScale = Math.max(YtConfig.MIN_SCALE, Math.min(YtConfig.MAX_SCALE, scale));
        int pw = Math.round(l.w * cfg.miniScale);
        cfg.miniX = rightAnchored ? anchorX - pw : anchorX;
        MiniLayout.of(cfg, sw, sh); // clamp to the screen
    }

    boolean busy() { return drag != Drag.NONE; }

    void cancel() {
        drag = Drag.NONE;
        seekPreview = -1;
    }
}
