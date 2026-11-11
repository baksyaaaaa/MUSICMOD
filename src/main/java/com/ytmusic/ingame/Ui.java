package com.ytmusic.ingame;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Style-aware drawing: every helper draws either the classic Minecraft look (blocky, font with shadow)
 * or the modern look (smooth Inter font, rounded panels, round buttons, smooth icons).
 * Shapes in the modern style are drawn at 1/K of a GUI unit, so corners and icons stay smooth.
 */
final class Ui {
    private Ui() {}

    private static final int K = 4; // sub-units per GUI unit for smooth shapes

    // ------------------------------------------------------------------ style & palette

    static boolean modern() {
        YtConfig c = YtMusicClient.CONFIG;
        return c != null && c.style == YtConfig.HudStyle.MODERN;
    }

    static int accent() { return modern() ? 0xFFB794FF : PlayerRenderer.ACCENT; }
    static int white() { return modern() ? 0xFFF4F4F6 : PlayerRenderer.WHITE; }
    static int gray() { return modern() ? 0xFFA3A3AD : PlayerRenderer.GRAY; }

    // ------------------------------------------------------------------ text

    private static Style modernStyle;
    private static boolean modernStyleTried;

    /** Style with the bundled smooth font (assets/ytmusic/font/modern.json). Falls back to the vanilla font. */
    private static Style modernFont() {
        if (!modernStyleTried) {
            modernStyleTried = true;
            try {
                // built through the text codec, so it does not depend on the font-description class names
                JsonObject o = new JsonObject();
                o.addProperty("text", "");
                o.addProperty("font", YtMusicClient.MOD_ID + ":modern");
                Text t = TextCodecs.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(null);
                modernStyle = t != null ? t.getStyle() : Style.EMPTY;
            } catch (Throwable e) {
                YtMusicClient.LOG.warn("Modern font style unavailable, using the default font: {}", e.toString());
                modernStyle = Style.EMPTY;
            }
        }
        return modernStyle;
    }

    static Text text(String s) {
        return modern() ? Text.literal(s).setStyle(modernFont()) : Text.literal(s);
    }

    static int width(TextRenderer tr, String s) {
        return tr.getWidth(text(s));
    }

    /** Cuts the string to the width and adds an ellipsis when something was cut. */
    static String trim(TextRenderer tr, String s, int w) {
        if (w <= 0 || s.isEmpty()) return "";
        if (width(tr, s) <= w) return s;
        String ell = modern() ? "…" : "...";
        StringVisitable v = tr.trimToWidth(text(s), Math.max(0, w - width(tr, ell)));
        return v.getString() + ell;
    }

    static void drawText(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
        if (modern()) ctx.drawText(tr, text(s), x, y, color, false);
        else ctx.drawTextWithShadow(tr, Text.literal(s), x, y, color);
    }

    static void drawTextRight(DrawContext ctx, TextRenderer tr, String s, int right, int y, int color) {
        drawText(ctx, tr, s, right - width(tr, s), y, color);
    }

    static void drawTextCentered(DrawContext ctx, TextRenderer tr, String s, int x, int w, int y, int color) {
        drawText(ctx, tr, s, x + (w - width(tr, s)) / 2, y, color);
    }

    // ------------------------------------------------------------------ marquee (running text)

    private static final float MARQUEE_SPEED = 22f;   // GUI units per second
    private static final long MARQUEE_PAUSE = 1800L; // ms the start of the text stays still
    private static final int MARQUEE_GAP = 30;        // space between the end and the next copy

    private static final class Marquee {
        String text = "";
        long start;
    }

    private static final Map<String, Marquee> MARQUEES = new HashMap<>();

    /**
     * Draws the text inside [x, x + w). If it is too long it scrolls smoothly to the left and the start
     * comes back in from the right, then holds still for a moment. Short text is not moved
     * (aligned to the right when alignRight is set, used by the mirrored layout).
     *
     * @param xf the transform the caller is currently drawing in (null = plain screen coordinates)
     */
    static void marquee(DrawContext ctx, TextRenderer tr, String s, int x, int y, int w, int color,
                        boolean alignRight, Xform xf, String key) {
        if (w <= 4 || s.isEmpty()) return;
        int tw = width(tr, s);
        if (tw <= w) {
            drawText(ctx, tr, s, alignRight ? x + w - tw : x, y, color);
            return;
        }
        long now = Util.getMeasuringTimeMs();
        Marquee m = MARQUEES.computeIfAbsent(key, k -> new Marquee());
        if (!s.equals(m.text)) {
            m.text = s;
            m.start = now; // new song -> start from the beginning
        }
        int cycle = tw + MARQUEE_GAP;
        long moveMs = (long) (cycle * 1000f / MARQUEE_SPEED);
        long e = (now - m.start) % (MARQUEE_PAUSE + moveMs);
        float off = e < MARQUEE_PAUSE ? 0f : (e - MARQUEE_PAUSE) * MARQUEE_SPEED / 1000f;

        int top = y - 1, bottom = y + 10;
        // the scissor rectangle is set in screen space, outside of the caller's transform
        if (xf != null) {
            xf.exit(ctx);
            ctx.enableScissor(xf.sx(x), xf.sy(top), xf.sx2(x + w), xf.sy2(bottom));
            xf.enter(ctx);
        } else {
            ctx.enableScissor(x, top, x + w, bottom);
        }
        var mat = ctx.getMatrices();
        mat.pushMatrix();
        mat.translate(x - off, y);
        drawText(ctx, tr, s, 0, 0, color);
        drawText(ctx, tr, s, cycle, 0, color);
        mat.popMatrix();
        if (xf != null) {
            xf.exit(ctx);
            ctx.disableScissor();
            xf.enter(ctx);
        } else {
            ctx.disableScissor();
        }
    }

    /** Translate + uniform scale used by the small overlays. */
    record Xform(float ox, float oy, float s) {
        void enter(DrawContext ctx) {
            var m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(ox, oy);
            m.scale(s, s);
        }

        void exit(DrawContext ctx) {
            ctx.getMatrices().popMatrix();
        }

        int sx(float lx) { return (int) Math.floor(ox + lx * s); }
        int sy(float ly) { return (int) Math.floor(oy + ly * s); }
        int sx2(float lx) { return (int) Math.ceil(ox + lx * s); }
        int sy2(float ly) { return (int) Math.ceil(oy + ly * s); }
    }

    // ------------------------------------------------------------------ shapes

    static int alpha(int color, float f) {
        int a = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** Rounded rectangle with anti-aliased corners. */
    static void roundRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x, y + r, x + w, y + h - r, color);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(1f / K, 1f / K);
        int W = w * K, H = h * K, R = r * K;
        for (int i = 0; i < R; i++) {
            double dy = R - i - 0.5;
            double inset = R - Math.sqrt(Math.max(0.0, (double) R * R - dy * dy));
            int full = (int) Math.ceil(inset);
            float cov = (float) (full - inset);
            row(ctx, i, full, W, cov, color);
            row(ctx, H - 1 - i, full, W, cov, color);
        }
        m.popMatrix();
    }

    private static void row(DrawContext ctx, int yy, int full, int W, float cov, int color) {
        ctx.fill(full, yy, W - full, yy + 1, color);
        if (full > 0 && cov > 0.03f) {
            int c = alpha(color, cov);
            ctx.fill(full - 1, yy, full, yy + 1, c);
            ctx.fill(W - full, yy, W - full + 1, yy + 1, c);
        }
    }

    /** Smooth triangle pointing right (or left) inside the w x h box. */
    static void triangle(DrawContext ctx, float x, float y, float w, float h, boolean right, int color) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(1f / K, 1f / K);
        int W = Math.round(w * K), H = Math.round(h * K);
        double half = H / 2.0;
        for (int j = 0; j < H; j++) {
            double c = 1.0 - Math.abs((j + 0.5) - half) / half;
            double len = W * c;
            int full = (int) len;
            float cov = (float) (len - full);
            if (right) {
                ctx.fill(0, j, full, j + 1, color);
                if (cov > 0.03f) ctx.fill(full, j, full + 1, j + 1, alpha(color, cov));
            } else {
                ctx.fill(W - full, j, W, j + 1, color);
                if (cov > 0.03f) ctx.fill(W - full - 1, j, W - full, j + 1, alpha(color, cov));
            }
        }
        m.popMatrix();
    }

    /** Image with rounded corners (texture drawn in strips at sub-unit resolution). */
    static void image(DrawContext ctx, Identifier id, int x, int y, int w, int h, int r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            PlayerRenderer.drawImage(ctx, id, x, y, w, h);
            return;
        }
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(1f / K, 1f / K);
        int W = w * K, H = h * K, R = r * K;
        ctx.drawTexture(PlayerRenderer.PIPELINE, id, 0, R, 0f, R, W, H - 2 * R, W, H);
        for (int i = 0; i < R; i++) {
            double dy = R - i - 0.5;
            int inset = (int) Math.round(R - Math.sqrt(Math.max(0.0, (double) R * R - dy * dy)));
            int sw = W - 2 * inset;
            if (sw <= 0) continue;
            ctx.drawTexture(PlayerRenderer.PIPELINE, id, inset, i, inset, i, sw, 1, W, H);
            ctx.drawTexture(PlayerRenderer.PIPELINE, id, inset, H - 1 - i, inset, H - 1 - i, sw, 1, W, H);
        }
        m.popMatrix();
    }

    // ------------------------------------------------------------------ widgets

    /** Background of an overlay / window. */
    static void panel(DrawContext ctx, int x, int y, int w, int h, int classicColor, int radius) {
        if (modern()) roundRect(ctx, x, y, w, h, radius, 0xE6141419);
        else ctx.fill(x, y, x + w, y + h, classicColor);
    }

    static void button(DrawContext ctx, int x, int y, int w, int h, boolean hover) {
        if (modern()) roundRect(ctx, x, y, w, h, Math.min(w, h) / 2, hover ? 0x47FFFFFF : 0x1FFFFFFF);
        else PlayerRenderer.button(ctx, x, y, w, h, hover);
    }

    /** Progress / volume track. */
    static void progress(DrawContext ctx, int x, int y, int w, int h, double frac, int classicTrack) {
        frac = Math.max(0.0, Math.min(1.0, frac));
        if (modern()) {
            roundRect(ctx, x, y, w, h, h, 0x40FFFFFF);
            int fw = (int) Math.round(w * frac);
            if (fw > 0) roundRect(ctx, x, y, Math.max(h, fw), h, h, accent());
        } else {
            ctx.fill(x, y, x + w, y + h, classicTrack);
            ctx.fill(x, y, x + (int) (w * frac), y + h, PlayerRenderer.ACCENT);
        }
    }

    // ------------------------------------------------------------------ icons (8 units tall, drawn at x, y)

    static void iconPlay(DrawContext c, int x, int y, int col) {
        if (modern()) triangle(c, x + 1, y, 7, 8, true, col);
        else PlayerRenderer.iconPlay(c, x, y, col);
    }

    static void iconPause(DrawContext c, int x, int y, int col) {
        if (modern()) {
            roundRect(c, x + 1, y, 2, 8, 1, col);
            roundRect(c, x + 5, y, 2, 8, 1, col);
        } else PlayerRenderer.iconPause(c, x, y, col);
    }

    static void iconNext(DrawContext c, int x, int y, int col) {
        if (modern()) {
            triangle(c, x, y + 0.5f, 6.5f, 7, true, col);
            roundRect(c, x + 7, y, 2, 8, 1, col);
        } else PlayerRenderer.iconNext(c, x, y, col);
    }

    static void iconPrev(DrawContext c, int x, int y, int col) {
        if (modern()) {
            roundRect(c, x, y, 2, 8, 1, col);
            triangle(c, x + 2.5f, y + 0.5f, 6.5f, 7, false, col);
        } else PlayerRenderer.iconPrev(c, x, y, col);
    }

    /** Three dots in a corner: "drag me to resize". */
    static void grip(DrawContext c, int x, int y, boolean left, int col) {
        // x, y = the corner itself; dots go inwards
        int d = left ? 1 : -1;
        int bx = left ? x + 1 : x - 3;
        int by = y - 3;
        if (modern()) {
            roundRect(c, bx, by, 2, 2, 1, col);
            roundRect(c, bx + 4 * d, by, 2, 2, 1, col);
            roundRect(c, bx, by - 4, 2, 2, 1, col);
        } else {
            c.fill(bx, by, bx + 2, by + 2, col);
            c.fill(bx + 4 * d, by, bx + 4 * d + 2, by + 2, col);
            c.fill(bx, by - 4, bx + 2, by - 2, col);
        }
    }
}
