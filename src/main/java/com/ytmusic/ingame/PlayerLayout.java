package com.ytmusic.ingame;

/**
 * Geometry of the player window (all values in GUI units).
 * <pre>
 *  +-------------------------------+  title bar   (TITLE_H)
 *  |        web page (Chromium)    |  content
 *  +-------------------------------+  progress strip (PROGRESS_H) + controls row
 * </pre>
 */
final class PlayerLayout {
    static final int TITLE_H = 14;
    static final int BAR_H = 22;
    static final int PROGRESS_H = 4;
    static final int HANDLE = 10;
    static final int MIN_W = 240;
    static final int MIN_H = 150;
    static final int BTN = 14;

    final int x, y, w, h;

    // controls row
    final int rowY;
    final int prevX, playX, nextX, muteX, sliderX, pctX;
    final int sliderW = 50;
    final boolean showTime, showZoom;
    final int timeX, zoomOutX, zoomInX, homeX;

    private PlayerLayout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.rowY = y + h - BAR_H + PROGRESS_H + 1;

        int bx = x + 4;
        prevX = bx;
        playX = bx + 16;
        nextX = bx + 32;
        muteX = nextX + 22;
        sliderX = muteX + 18;
        pctX = sliderX + sliderW + 5;
        int cursor = pctX + 26;

        int rightEdge = x + w - HANDLE - 3;
        homeX = rightEdge - 28;
        zoomInX = homeX - 16;
        zoomOutX = zoomInX - 16;
        showZoom = cursor + 4 <= zoomOutX;
        int limit = showZoom ? zoomOutX : rightEdge;
        showTime = limit - cursor >= 56;
        timeX = cursor + 2;
    }

    /** Clamps the saved rectangle to the current screen size and returns the layout. */
    static PlayerLayout of(YtConfig c, int sw, int sh) {
        c.w = clamp(c.w, MIN_W, Math.max(MIN_W, sw - 4));
        c.h = clamp(c.h, MIN_H, Math.max(MIN_H, sh - 4));
        if (c.x == Integer.MIN_VALUE) c.x = sw - c.w - 6;
        c.x = clamp(c.x, 0, Math.max(0, sw - c.w));
        c.y = clamp(c.y, 0, Math.max(0, sh - c.h));
        return new PlayerLayout(c.x, c.y, c.w, c.h);
    }

    int contentX() { return x; }
    int contentY() { return y + TITLE_H; }
    int contentW() { return w; }
    int contentH() { return h - TITLE_H - BAR_H; }
    int barY() { return y + h - BAR_H; }

    boolean contains(double mx, double my) {
        return in(mx, my, x - 1, y - 1, w + 2, h + 2);
    }

    static boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
