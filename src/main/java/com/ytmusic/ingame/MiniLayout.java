package com.ytmusic.ingame;

/**
 * Geometry of the small overlays: the thin bar (COMPACT) and the card with cover art (CARD).
 *
 * The overlay is laid out in its own LOCAL units (origin 0,0, size w x h) and drawn scaled by {@link #s}
 * at the screen position (ox, oy). When the overlay sits on the left half of the screen the layout is
 * mirrored (accent stripe, cover, buttons and text block swap sides) - only positions are mirrored,
 * text and icons are drawn normally so they stay readable.
 */
final class MiniLayout {
    static final int BAR_H = 18;
    static final int CARD_H = 48;
    static final int BTN_W = 14;
    static final int BTN_H = 12;
    static final int COVER = 38;
    static final int GRIP = 8;
    static final int BUTTONS_W = 3 * (BTN_W + 2);

    /** Screen position (GUI units) of the top-left corner. */
    final int ox, oy;
    /** Scale of the local layout. */
    final float s;
    /** Local size. */
    final int w, h;
    final boolean card, mirror, modern;

    private MiniLayout(int ox, int oy, float s, int w, int h, boolean card, boolean mirror, boolean modern) {
        this.ox = ox;
        this.oy = oy;
        this.s = s;
        this.w = w;
        this.h = h;
        this.card = card;
        this.mirror = mirror;
        this.modern = modern;
    }

    static MiniLayout of(YtConfig c, int sw, int sh) {
        boolean card = c.overlay == YtConfig.Overlay.CARD;
        c.miniW = PlayerLayout.clamp(c.miniW, 150, 400);
        int w = card ? c.miniW : Math.min(c.miniW, 260);
        int h = card ? CARD_H : BAR_H;

        float s = Math.max(YtConfig.MIN_SCALE, Math.min(YtConfig.MAX_SCALE, c.miniScale));
        // never bigger than the screen
        if (w * s > sw - 4) s = Math.max(0.25f, (sw - 4) / (float) w);
        if (h * s > sh - 4) s = Math.max(0.25f, Math.min(s, (sh - 4) / (float) h));

        int pw = Math.round(w * s), ph = Math.round(h * s);
        if (c.miniX == Integer.MIN_VALUE) c.miniX = sw - pw - 6;
        c.miniX = PlayerLayout.clamp(c.miniX, 0, Math.max(0, sw - pw));
        c.miniY = PlayerLayout.clamp(c.miniY, 0, Math.max(0, sh - ph));
        boolean mirror = c.miniX + pw / 2 < sw / 2;
        return new MiniLayout(c.miniX, c.miniY, s, w, h, card, mirror, c.style == YtConfig.HudStyle.MODERN);
    }

    Ui.Xform xform() { return new Ui.Xform(ox, oy, s); }

    /** Size on screen (GUI units). */
    int pw() { return Math.round(w * s); }
    int ph() { return Math.round(h * s); }

    /** Screen -> local coordinates. */
    double lx(double mx) { return (mx - ox) / s; }
    double ly(double my) { return (my - oy) / s; }

    boolean contains(double mx, double my) {
        return PlayerLayout.in(mx, my, ox, oy, pw(), ph());
    }

    // ------------------------------------------------------------------ local geometry

    /** Accent stripe: on the inner side (towards the screen centre). */
    int stripeX() { return mirror ? w - 2 : 0; }

    /** i = 0 prev, 1 play/pause, 2 next (always in this order, left to right). Buttons sit on the outer side. */
    int btnX(int i) { return mirror ? 3 + i * (BTN_W + 2) : w - (3 - i) * (BTN_W + 2) - 1; }
    int btnY() { return card ? 30 : 3; }

    int progressX() { return modern ? 6 : 0; }
    int progressW() { return modern ? w - 12 : w; }
    int progressY() {
        if (modern) return card ? h - 5 : h - 4;
        return card ? h - 4 : h - 3;
    }
    int progressH() {
        if (modern) return 2;
        return card ? 4 : 3;
    }

    // card
    int coverX() { return mirror ? w - 5 - COVER : 5; }
    int coverY() { return 5; }
    int textL() { return mirror ? 5 : 5 + COVER + 5; }
    int textR() { return mirror ? w - 5 - COVER - 5 : w - 5; }

    // bar
    int barIconX() { return mirror ? w - 15 : 7; }
    int barTextL(boolean interactive) { return mirror ? (interactive ? 4 + BUTTONS_W + 2 : 5) : 20; }
    int barTextR(boolean interactive) { return mirror ? w - 20 : w - 4 - (interactive ? BUTTONS_W + 2 : 0); }

    /** Resize grip: bottom corner on the inner side (the outer edge stays in place while resizing). */
    int gripX() { return mirror ? w - GRIP : 0; }
    int gripY() { return h - GRIP; }
}
