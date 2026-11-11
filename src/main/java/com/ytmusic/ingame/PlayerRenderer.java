package com.ytmusic.ingame;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Drawing helpers shared by the player screen and the in-game overlay. All colors are ARGB. */
final class PlayerRenderer {
    private PlayerRenderer() {}

    static final int ACCENT = 0xFF8B5CF6;
    static final int WHITE = 0xFFFFFFFF;
    static final int GRAY = 0xFFAAAAAA;
    static final int BTN = 0xFF343434;
    static final int BTN_HOVER = 0xFF4E4E4E;

    // Opaque page texture: same pipeline Rinku's own example uses for non-transparent browsers.
    static final RenderPipeline PIPELINE = RenderPipelines.GUI_TEXTURED;

    /** Draws the web page, or a status message while Chromium/the page is not ready. */
    static void drawBrowser(DrawContext ctx, TextRenderer tr, YtPlayer p, int x, int y, int w, int h) {
        RinkuBrowser b = p.browser();
        if (b != null && b.isTextureReady()) {
            Identifier id = b.getTextureIdentifier();
            if (id != null) {
                ctx.drawTexture(PIPELINE, id, x, y, 0f, 0f, w, h, w, h);
                return;
            }
        }
        ctx.fill(x, y, x + w, y + h, 0xFF0F0F0F);
        String msg = p.chromiumReady() ? "Loading YouTube Music..." : "Chromium is still downloading / starting...";
        int tw = tr.getWidth(msg);
        ctx.drawTextWithShadow(tr, Text.literal(msg), x + (w - tw) / 2, y + h / 2 - 4, GRAY);
    }

    /** Draws a whole texture scaled into the given rectangle. */
    static void drawImage(DrawContext ctx, Identifier id, int x, int y, int w, int h) {
        ctx.drawTexture(PIPELINE, id, x, y, 0f, 0f, w, h, w, h);
    }

    static void button(DrawContext ctx, int x, int y, int w, int h, boolean hover) {
        ctx.fill(x, y, x + w, y + h, hover ? BTN_HOVER : BTN);
    }

    static void rectBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ------------------------------------------------------------------ pixel icons (no font glyphs needed)

    static void iconPlay(DrawContext c, int x, int y, int col) {
        for (int i = 0; i < 4; i++) c.fill(x + 2 * i, y + i, x + 2 * i + 2, y + 8 - i, col);
    }

    static void iconPause(DrawContext c, int x, int y, int col) {
        c.fill(x + 1, y, x + 3, y + 8, col);
        c.fill(x + 5, y, x + 7, y + 8, col);
    }

    static void iconNext(DrawContext c, int x, int y, int col) {
        iconPlay(c, x, y, col);
        c.fill(x + 8, y, x + 10, y + 8, col);
    }

    static void iconPrev(DrawContext c, int x, int y, int col) {
        c.fill(x, y, x + 2, y + 8, col);
        for (int i = 0; i < 4; i++) {
            int cx = x + 2 + 2 * (3 - i);
            c.fill(cx, y + i, cx + 2, y + 8 - i, col);
        }
    }

    static void iconSpeaker(DrawContext c, int x, int y, boolean muted, int volume, int col) {
        c.fill(x, y + 2, x + 3, y + 6, col);
        c.fill(x + 3, y + 1, x + 4, y + 7, col);
        c.fill(x + 4, y, x + 5, y + 8, col);
        if (muted || volume == 0) {
            for (int i = 0; i < 5; i++) {
                c.fill(x + 7 + i, y + 1 + i, x + 8 + i, y + 2 + i, 0xFFFF5555);
                c.fill(x + 11 - i, y + 1 + i, x + 12 - i, y + 2 + i, 0xFFFF5555);
            }
        } else {
            c.fill(x + 6, y + 2, x + 7, y + 6, col);
            if (volume > 40) c.fill(x + 8, y + 1, x + 9, y + 7, col);
            if (volume > 75) c.fill(x + 10, y, x + 11, y + 8, col);
        }
    }

    static String fmt(int seconds) {
        seconds = Math.max(0, seconds);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
