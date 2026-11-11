package com.ytmusic.ingame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to .minecraft/config/ytmusic.json */
public final class YtConfig {

    public enum Overlay {
        HIDDEN, CARD, FULL;

        public Overlay next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Look of the overlays and the player window: blocky Minecraft style or smooth "modern" style. */
    public enum HudStyle {
        CLASSIC, MODERN;

        public HudStyle next() {
            return this == CLASSIC ? MODERN : CLASSIC;
        }
    }

    // Window rectangle in GUI units. x = MIN_VALUE means "not placed yet" (top-right corner on first start).
    public int x = Integer.MIN_VALUE;
    public int y = 6;
    public int w = 320;
    public int h = 210;

    // Position / width of the small overlays (bar and card) in GUI units. miniX = MIN_VALUE -> top-right on first start.
    public int miniX = Integer.MIN_VALUE;
    public int miniY = 6;
    public int miniW = 210;
    /** Size multiplier of the bar / card (Ctrl + wheel or the corner grip while the chat is open). */
    public float miniScale = 1.0f;

    /** CLASSIC = Minecraft font and square boxes, MODERN = smooth font, rounded windows and icons. */
    public HudStyle style = HudStyle.CLASSIC;

    public int volume = 50;
    public boolean muted = false;

    /** What stays on screen while the player window is closed. */
    public Overlay overlay = Overlay.CARD;

    /** Chromium zoom level (0 = 100%). */
    public double zoom = 0.0;

    public String startUrl = "https://music.youtube.com";

    /** Present the embedded browser as a regular desktop Chrome (helps with Google sign-in). */
    public boolean spoofUserAgent = true;
    /** Leave empty to use an automatic Chrome-like value. */
    public String userAgent = "";

    public int activeFps = 30;
    public int idleFps = 5;

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("ytmusic.json");
    }

    public static YtConfig load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                YtConfig c = GSON.fromJson(Files.readString(p), YtConfig.class);
                if (c != null) {
                    c.sanitize();
                    return c;
                }
            }
        } catch (Exception e) {
            YtMusicClient.LOG.warn("Could not read ytmusic.json, using defaults", e);
        }
        YtConfig c = new YtConfig();
        c.save();
        return c;
    }

    public void sanitize() {
        volume = Math.max(0, Math.min(100, volume));
        zoom = Math.max(-3.0, Math.min(5.0, zoom));
        activeFps = Math.max(5, Math.min(60, activeFps));
        idleFps = Math.max(1, Math.min(30, idleFps));
        miniW = Math.max(150, Math.min(400, miniW));
        if (Float.isNaN(miniScale)) miniScale = 1.0f;
        miniScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, miniScale));
        overlay = Overlay.HIDDEN;   // no HUD modes any more: nothing stays on screen when the window is closed
        if (style == null) style = HudStyle.CLASSIC;
        if (startUrl == null || startUrl.isBlank()) startUrl = "https://music.youtube.com";
        if (userAgent == null) userAgent = "";
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this));
        } catch (IOException e) {
            YtMusicClient.LOG.warn("Could not save ytmusic.json", e);
        }
    }
}
