package com.ytmusic.ingame;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Owns the embedded Chromium page (music.youtube.com) and controls it with small JavaScript snippets.
 * Playback state comes back through console messages that our injected script prints.
 */
public final class YtPlayer {
    private static final String PREFIX = "YTMSTATE|";

    /**
     * Idempotent: safe to inject again and again (page reloads wipe it, so we re-inject often and on every load).
     *
     * Volume: the page itself sets video.volume (new track, loudness normalisation, reload - usually to its own
     * stored value, often 100%). We replace the volume setter on HTMLMediaElement so every write from the page
     * is turned into OUR target volume, re-apply it on all media events (capture phase, before the page reacts)
     * and also write our volume into the page's saved volume, so a reload starts at the right level too.
     */
    private static final String BOOTSTRAP =
            "(function(){"
          + "try{if(!document.getElementById('ytm-purple')){var st=document.createElement('style');st.id='ytm-purple';st.textContent=':root,html,body,ytmusic-app{--yt-spec-static-brand-red:#8b5cf6!important;--yt-spec-brand-button-background:#8b5cf6!important;--yt-spec-themed-blue:#a78bfa!important;--ytmusic-brand-red:#8b5cf6!important}ytmusic-player-bar,ytmusic-player-page,ytmusic-app-layout,tp-yt-paper-slider,tp-yt-paper-progress,#progress-bar{--paper-slider-active-color:#8b5cf6!important;--paper-slider-knob-color:#8b5cf6!important;--paper-slider-pin-color:#8b5cf6!important;--paper-slider-secondary-color:#a78bfa!important;--paper-progress-active-color:#8b5cf6!important}';(document.head||document.documentElement).appendChild(st);}}catch(e){}"
          + "if(window.__ytm){return;}"
          + "var S=window.__ytm={vol:50,muted:false};"
          + "function T(){return S.muted?0:Math.max(0,Math.min(1,S.vol/100));}"
          + "var P=HTMLMediaElement.prototype;"
          + "var D=Object.getOwnPropertyDescriptor(P,'volume');"
          + "function raw(v,x){try{D.set.call(v,x);}catch(e){}}"
          + "S.apply=function(v){try{if(v&&Math.abs(D.get.call(v)-T())>0.0005){raw(v,T());}}catch(e){}};"
          + "S.all=function(){var l=document.querySelectorAll('video,audio');for(var i=0;i<l.length;i++){S.apply(l[i]);}};"
          + "S.store=function(){try{var n=Date.now();localStorage.setItem('yt-player-volume',JSON.stringify("
          +   "{data:JSON.stringify({volume:Math.round(T()*100),muted:false}),expiration:n+2592000000,creation:n}));}catch(e){}};"
          + "S.set=function(vol,muted){S.vol=vol;S.muted=muted;S.all();S.store();};"
          + "try{Object.defineProperty(P,'volume',{configurable:true,enumerable:D.enumerable,"
          +   "get:function(){return D.get.call(this);},"
          +   "set:function(x){raw(this,T());}});}catch(e){}"
          + "['loadstart','emptied','loadedmetadata','loadeddata','canplay','play','playing','seeking','ratechange','volumechange','durationchange']"
          +   ".forEach(function(n){document.addEventListener(n,function(e){var t=e.target;if(t instanceof HTMLMediaElement){S.apply(t);}},true);});"
          + "setInterval(S.all,250);"
          + "S.all();"
          + "var last='';"
          + "setInterval(function(){"
          + "var v=document.querySelector('video');"
          + "var te=document.querySelector('ytmusic-player-bar .title');"
          + "var ae=document.querySelector('ytmusic-player-bar .byline');"
          + "var ie=document.querySelector('ytmusic-player-bar img.image')||document.querySelector('ytmusic-player-bar img');"
          + "var o={p:!!(v&&!v.paused&&!v.ended),"
          + "t:te?te.textContent.trim():'',"
          + "a:ae?(ae.getAttribute('title')||ae.textContent.trim()):'',"
          + "i:ie?(ie.src||''):'',"
          + "c:v?Math.floor(v.currentTime):0,"
          + "f:v?Math.round(v.currentTime*1000):0,"
          + "d:(v&&isFinite(v.duration))?Math.floor(v.duration):0};"
          + "var j=JSON.stringify(o);"
          + "if(j!==last){last=j;console.error('" + PREFIX + "'+j);}"
          + "},250);"
          + "})();";

    private static final String JS_TOGGLE =
            "(function(){var b=document.querySelector('ytmusic-player-bar #play-pause-button');"
          + "if(b){b.click();return;}"
          + "var v=document.querySelector('video');if(v){if(v.paused){v.play();}else{v.pause();}}})();";
    private static final String JS_NEXT =
            "(function(){var b=document.querySelector('ytmusic-player-bar .next-button');if(b){b.click();}})();";
    private static final String JS_PREV =
            "(function(){var b=document.querySelector('ytmusic-player-bar .previous-button');if(b){b.click();}})();";

    /** Only plain https image URLs are ever put into the cover page. */
    private static final Pattern SAFE_URL = Pattern.compile("https://[A-Za-z0-9._~:/?#@!$&()*+,;=%\\-]+");

    private final YtConfig cfg;

    private volatile RinkuBrowser browser;
    private volatile boolean playing;
    private volatile String title = "";
    private volatile String artist = "";
    private volatile String pageTitle = "";
    private volatile int pos;
    private volatile long posMs = -1;      // the exact play position (ms) at the moment of the last report
    private volatile long posNanos = 0;    // when that report arrived
    private volatile int dur;
    private volatile String thumb = "";

    // tiny second browser that only shows the current cover image
    private RinkuBrowser cover;
    private String coverLoaded = "";
    private int coverRequestTick = -1000;

    private boolean handlerAdded;
    private boolean initScheduled;
    private boolean zoomPending = true;
    private int lastW = -1, lastH = -1;
    private int fpsApplied = -1;
    private int ticks;

    public YtPlayer(YtConfig cfg) {
        this.cfg = cfg;
    }

    // ------------------------------------------------------------------ state getters

    public RinkuBrowser browser() { return browser; }
    public boolean playing() { return playing; }
    public String title() { return title; }
    public String artist() { return artist; }
    public int position() { return pos; }

    /** the exact play position in seconds, counting on from the last report (used for lyrics) */
    public double positionPrecise() {
        long ms = posMs;
        if (ms < 0) return pos;
        return ms / 1000.0 + (playing ? (System.nanoTime() - posNanos) / 1e9 : 0.0);
    }
    public int duration() { return dur; }

    /** Called by the card overlay every frame it is drawn; keeps the cover browser alive. */
    public void requestCover() {
        coverRequestTick = ticks;
    }

    /** Texture of the current cover art, or null while it is not available yet. */
    public Identifier coverTexture() {
        RinkuBrowser c = cover;
        if (c == null || thumb.isEmpty() || coverLoaded.isEmpty()) return null;
        try {
            return c.isTextureReady() ? c.getTextureIdentifier() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean chromiumReady() {
        try {
            return Rinku.isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }

    public String titleLine() {
        String t = title;
        if (t.isEmpty()) {
            String pt = pageTitle;
            if (pt != null && !pt.isBlank()) {
                if (pt.endsWith(" - YouTube Music")) pt = pt.substring(0, pt.length() - " - YouTube Music".length());
                return pt;
            }
            return "Nothing playing";
        }
        return artist.isEmpty() ? t : t + " - " + artist;
    }

    // ------------------------------------------------------------------ browser lifecycle

    /** Creates the browser once Chromium is ready. Returns true when a browser exists. */
    public boolean ensureBrowser(int pxW, int pxH) {
        if (browser != null) return true;
        if (!chromiumReady()) {
            if (!initScheduled) {
                initScheduled = true;
                try {
                    Rinku.scheduleForInit(ok -> YtMusicClient.LOG.info("Rinku initialization finished: {}", ok));
                } catch (Throwable t) {
                    YtMusicClient.LOG.debug("scheduleForInit failed: {}", t.toString());
                }
            }
            return false;
        }
        try {
            RinkuBrowser b = Rinku.createBrowser(cfg.startUrl, false, Math.max(1, pxW), Math.max(1, pxH));
            if (b == null) return false;
            browser = b;
            lastW = Math.max(1, pxW);
            lastH = Math.max(1, pxH);
            addHandler();
            zoomPending = true;
            applyFrameRate(true);
            YtMusicClient.LOG.info("YouTube Music browser created ({}x{})", lastW, lastH);
            return true;
        } catch (Throwable t) {
            YtMusicClient.LOG.error("Could not create the browser", t);
            return false;
        }
    }

    private void addHandler() {
        if (handlerAdded) return;
        handlerAdded = true;
        Rinku.getClient().addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public boolean onConsoleMessage(CefBrowser b, CefSettings.LogSeverity level, String message, String source, int line) {
                if (message != null && message.startsWith(PREFIX) && isMine(b)) {
                    parseState(message.substring(PREFIX.length()));
                    return true; // swallow, so it does not spam the game log
                }
                return false;
            }

            @Override
            public void onTitleChange(CefBrowser b, String t) {
                if (isMine(b) && t != null) pageTitle = t;
            }
        });
        try {
            CefLoadHandler loadHandler = new CefLoadHandlerAdapter() {
                @Override
                public void onLoadEnd(CefBrowser b, CefFrame frame, int httpStatusCode) {
                    if (isMine(b) && (frame == null || frame.isMain())) injectNow();
                }

                @Override
                public void onLoadingStateChange(CefBrowser b, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                    if (isMine(b) && !isLoading) injectNow();
                }
            };
            // via reflection, so this compiles whatever exact client type Rinku exposes
            Object client = Rinku.getClient();
            client.getClass().getMethod("addLoadHandler", CefLoadHandler.class).invoke(client, loadHandler);
        } catch (Throwable t) {
            // the periodic injection in tick() still covers this
            YtMusicClient.LOG.debug("Could not add a load handler: {}", t.toString());
        }
    }

    private boolean isMine(CefBrowser b) {
        RinkuBrowser cur = browser;
        return b != null && cur != null && b.getIdentifier() == cur.getIdentifier();
    }

    private void parseState(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            playing = o.has("p") && o.get("p").getAsBoolean();
            title = str(o, "t");
            String a = str(o, "a");
            int dot = a.indexOf('\u2022');
            artist = (dot >= 0 ? a.substring(0, dot) : a).trim();
            pos = o.has("c") ? o.get("c").getAsInt() : 0;
            if (o.has("f")) {
                posMs = o.get("f").getAsLong();
                posNanos = System.nanoTime();
            }
            dur = o.has("d") ? o.get("d").getAsInt() : 0;
            String img = str(o, "i");
            thumb = img.startsWith("https://") ? img.replaceFirst("=w\\d+-h\\d+", "=w226-h226") : "";
        } catch (Exception ignored) {
            // malformed message - ignore
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    public void shutdown() {
        RinkuBrowser b = browser;
        browser = null;
        if (b != null) {
            try {
                b.close();
            } catch (Throwable ignored) {
            }
        }
        RinkuBrowser c = cover;
        cover = null;
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ per-tick / per-frame

    public void tick(MinecraftClient mc) {
        RinkuBrowser b = browser;
        if (b == null) return;
        ticks++;
        if (ticks % 10 == 1) js(BOOTSTRAP);
        if (ticks % 40 == 1) {
            pushVolume();
            if (zoomPending) {
                zoomPending = false;
                applyZoom();
            }
        }
        boolean active = mc.currentScreen instanceof PlayerScreen || cfg.overlay == YtConfig.Overlay.FULL;
        applyFrameRate(active);
        if (ticks - coverRequestTick < 60) updateCover();
    }

    private void updateCover() {
        String url = thumb;
        if (url.isEmpty() || !SAFE_URL.matcher(url).matches()) return;
        if (cover == null) {
            if (!chromiumReady()) return;
            try {
                cover = Rinku.createBrowser("about:blank", false, 96, 96);
                if (cover != null) cover.setWindowlessFrameRate(5);
            } catch (Throwable t) {
                YtMusicClient.LOG.warn("Could not create the cover browser: {}", t.toString());
                cover = null;
                return;
            }
            coverLoaded = "";
        }
        RinkuBrowser c = cover;
        if (c != null && !url.equals(coverLoaded)) {
            coverLoaded = url;
            String html = "<html><body style='margin:0;background:#1a1a1a;overflow:hidden'>"
                    + "<img src='" + url + "' style='display:block;width:100vw;height:100vh;object-fit:cover'></body></html>";
            try {
                c.loadURL("data:text/html;charset=utf-8,"
                        + URLEncoder.encode(html, StandardCharsets.UTF_8).replace("+", "%20"));
            } catch (Throwable t) {
                YtMusicClient.LOG.debug("Cover load failed: {}", t.toString());
            }
        }
    }

    /** Resizes Chromium to the given pixel size when it changed. */
    public void syncSize(int pxW, int pxH) {
        RinkuBrowser b = browser;
        if (b == null) return;
        pxW = Math.max(1, pxW);
        pxH = Math.max(1, pxH);
        if (pxW == lastW && pxH == lastH) return;
        lastW = pxW;
        lastH = pxH;
        try {
            b.resize(pxW, pxH);
        } catch (Throwable t) {
            YtMusicClient.LOG.debug("resize failed: {}", t.toString());
        }
    }

    private void applyFrameRate(boolean active) {
        RinkuBrowser b = browser;
        if (b == null) return;
        int fps = active ? cfg.activeFps : cfg.idleFps;
        if (fps == fpsApplied) return;
        fpsApplied = fps;
        try {
            b.setWindowlessFrameRate(fps);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ commands

    public void togglePlay() {
        playing = !playing; // optimistic, corrected by the next state message
        js(JS_TOGGLE);
    }

    public void next() { js(JS_NEXT); }

    public void prev() { js(JS_PREV); }

    public void seekFraction(double f) {
        f = Math.max(0.0, Math.min(1.0, f));
        js("(function(){var v=document.querySelector('video');if(v&&isFinite(v.duration)){v.currentTime=v.duration*"
                + String.format(Locale.ROOT, "%.4f", f) + ";}})();");
    }

    public void setVolume(int v) {
        cfg.volume = Math.max(0, Math.min(100, v));
        if (cfg.volume > 0) cfg.muted = false;
        pushVolume();
    }

    public void adjustVolume(int delta) {
        setVolume(cfg.volume + delta);
    }

    public void toggleMute() {
        cfg.muted = !cfg.muted;
        pushVolume();
    }

    public int volume() { return cfg.volume; }
    public boolean muted() { return cfg.muted; }

    private void pushVolume() {
        double t = cfg.muted ? 0.0 : cfg.volume / 100.0;
        js("(function(){if(window.__ytm&&window.__ytm.set){window.__ytm.set(" + cfg.volume + "," + cfg.muted + ");return;}"
                + "var v=document.querySelector('video');if(v){v.volume=" + String.format(Locale.ROOT, "%.3f", t) + ";}})();");
    }

    /** Injects the bootstrap and the current volume right away (called when a page finished loading). */
    private void injectNow() {
        js(BOOTSTRAP);
        pushVolume();
    }

    public void changeZoom(double delta) {
        cfg.zoom = Math.max(-3.0, Math.min(5.0, cfg.zoom + delta));
        applyZoom();
    }

    private void applyZoom() {
        RinkuBrowser b = browser;
        if (b == null) return;
        try {
            b.setZoomLevel(cfg.zoom);
        } catch (Throwable ignored) {
        }
    }

    public void home() {
        RinkuBrowser b = browser;
        if (b != null) {
            try {
                b.loadURL(cfg.startUrl);
            } catch (Throwable ignored) {
            }
        }
    }

    private void js(String code) {
        RinkuBrowser b = browser;
        if (b == null) return;
        try {
            String url = b.getURL();
            b.executeJavaScript(code, url == null ? "" : url, 0);
        } catch (Throwable t) {
            YtMusicClient.LOG.debug("JS call failed: {}", t.toString());
        }
    }
}
