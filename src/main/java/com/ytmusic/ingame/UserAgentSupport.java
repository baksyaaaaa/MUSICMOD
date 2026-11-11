package com.ytmusic.ingame;

import de.keksuccino.rinku.Rinku;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Google often refuses sign-in inside embedded browsers. Presenting the embedded Chromium as a normal
 * desktop Chrome usually avoids that. Rinku reads its user agent when Chromium starts, so we set it
 * at runtime and (only if the user has not set their own) also write it into rinku.properties.
 */
final class UserAgentSupport {
    private UserAgentSupport() {}

    static String defaultUserAgent() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String platform;
        if (os.contains("win")) platform = "Windows NT 10.0; Win64; x64";
        else if (os.contains("mac")) platform = "Macintosh; Intel Mac OS X 10_15_7";
        else platform = "X11; Linux x86_64";
        return "Mozilla/5.0 (" + platform + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    }

    static void apply(YtConfig cfg) {
        if (!cfg.spoofUserAgent) return;
        String ua = cfg.userAgent.isBlank() ? defaultUserAgent() : cfg.userAgent.trim();

        try {
            Rinku.getSettings().setUserAgent(ua);
        } catch (Throwable t) {
            YtMusicClient.LOG.warn("Could not set the user agent at runtime: {}", t.toString());
        }

        try {
            Path file = FabricLoader.getInstance().getConfigDir().resolve("rinku").resolve("rinku.properties");
            if (!Files.exists(file)) return;
            Properties props = new Properties();
            try (BufferedReader in = Files.newBufferedReader(file)) {
                props.load(in);
            }
            if (props.getProperty("user-agent", "").isBlank()) {
                props.setProperty("user-agent", ua);
                try (BufferedWriter out = Files.newBufferedWriter(file)) {
                    props.store(out, "Rinku settings");
                }
            }
        } catch (Throwable t) {
            YtMusicClient.LOG.debug("Could not update rinku.properties: {}", t.toString());
        }
    }
}
