package com.ytmusic.ingame;

import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The interactive player window (key Y): everything you type goes to the web page, the mouse works on the
 * page and on the window chrome. ESC or the x button closes it; what stays on screen is set with the HUD button.
 */
public final class PlayerScreen extends Screen {
    private final YtConfig cfg = YtMusicClient.CONFIG;
    private final YtPlayer player = YtMusicClient.PLAYER;
    private final PlayerWindow window;

    public PlayerScreen() {
        super(Text.literal("YouTube Music"));
        this.window = new PlayerWindow(cfg, player, this::close);
    }

    private int scale() {
        return HudOverlay.guiScale(this.client);
    }

    @Override
    protected void init() {
        PlayerLayout l = window.layout(this.width, this.height);
        int s = scale();
        player.ensureBrowser(l.contentW() * s, l.contentH() * s);
        player.syncSize(l.contentW() * s, l.contentH() * s);
        RinkuBrowser b = player.browser();
        if (b != null) b.setFocus(true);
    }

    @Override
    public boolean shouldPause() {
        return false; // the music window must not pause single-player
    }

    @Override
    public void removed() {
        window.cancel();
        RinkuBrowser b = player.browser();
        if (b != null) b.setFocus(false);
        cfg.save();
        super.removed();
    }

    // ------------------------------------------------------------------ rendering & mouse (delegated)

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        window.render(ctx, this.textRenderer, this.width, this.height, scale(), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        window.mouseClicked(click.x(), click.y(), click.button(), this.width, this.height, scale());
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (!window.busy()) return false;
        window.mouseDragged(click.x(), click.y(), this.width, this.height, scale());
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        boolean was = window.busy();
        window.mouseReleased(click.x(), click.y(), this.width, this.height, scale());
        return was;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        window.mouseMoved(mx, my, this.width, this.height, scale());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        return window.mouseScrolled(mx, my, verticalAmount, this.width, this.height, scale());
    }

    // ------------------------------------------------------------------ keyboard (everything goes to the web page)

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        RinkuBrowser b = player.browser();
        if (b != null) b.sendKeyPress(input.key(), input.scancode(), input.modifiers());
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput input) {
        RinkuBrowser b = player.browser();
        if (b != null) b.sendKeyRelease(input.key(), input.scancode(), input.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        RinkuBrowser b = player.browser();
        if (b != null) {
            for (char ch : Character.toChars(input.codepoint())) {
                b.sendKeyTyped(ch, input.modifiers());
            }
        }
        return true;
    }
}
