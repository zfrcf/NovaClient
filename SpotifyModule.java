package com.novaclient.module.modules.misc;

import com.novaclient.NovaClient;
import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ModeSetting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spotify Module — lit la chanson en cours via le titre de la fenêtre Spotify.
 * Cross-platform : Linux (wmctrl), Windows (tasklist), macOS (AppleScript).
 * Aucune dépendance externe, aucune API key.
 */
public class SpotifyModule extends Module {

    private final BooleanSetting showInHUD = addSetting(new BooleanSetting("Show in HUD", true));
    private final ModeSetting displayMode = addSetting(new ModeSetting("Display Mode", "Watermark", "Watermark", "ArrayList", "Both"));

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NovaClient-Spotify");
        t.setDaemon(true);
        return t;
    });

    private String currentTrack = "";
    private String currentArtist = "";
    private boolean spotifyDetected = false;
    private final String osName = System.getProperty("os.name").toLowerCase();

    public SpotifyModule() {
        super("Spotify", "Affiche la chanson Spotify en cours dans le HUD", Category.MISC);
    }

    @Override
    public void onEnable() {
        NovaClient.log("Spotify module activé — détection de la fenêtre Spotify...");
        scheduler.scheduleAtFixedRate(this::updateMetadata, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        scheduler.shutdown();
        NovaClient.log("Spotify module désactivé");
    }

    /**
     * Met à jour les métadonnées en lisant le titre de la fenêtre Spotify.
     * Cross-platform : Linux, Windows, macOS.
     */
    private void updateMetadata() {
        try {
            String title = getSpotifyWindowTitle();
            if (title != null && !title.isEmpty()) {
                spotifyDetected = true;
                // Format Spotify : "Artiste - Titre" ou "Titre"
                if (title.contains(" - ")) {
                    String[] parts = title.split(" - ", 2);
                    currentArtist = parts[0].trim();
                    currentTrack = parts[1].trim();
                } else {
                    currentTrack = title.trim();
                    currentArtist = "";
                }
            } else {
                spotifyDetected = false;
                currentTrack = "";
                currentArtist = "";
            }
        } catch (Exception e) {
            spotifyDetected = false;
        }
    }

    /**
     * Récupère le titre de la fenêtre Spotify selon l'OS.
     */
    private String getSpotifyWindowTitle() {
        if (osName.contains("linux")) {
            return getLinuxWindowTitle();
        } else if (osName.contains("windows")) {
            return getWindowsWindowTitle();
        } else if (osName.contains("mac")) {
            return getMacWindowTitle();
        }
        return null;
    }

    /**
     * Linux : utilise wmctrl ou xdotool pour lister les fenêtres.
     */
    private String getLinuxWindowTitle() {
        // Tentative 1 : wmctrl
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"wmctrl", "-l"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("spotify")) {
                    // Format : "0x... 0 hostname Titre"
                    String[] parts = line.split(" ", 4);
                    if (parts.length >= 4) {
                        return parts[3];
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {}

        // Tentative 2 : xdotool
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"xdotool", "search", "--name", "Spotify", "getwindowname"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            if (line != null && !line.isEmpty()) {
                return line;
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Windows : utilise tasklist + PowerShell pour le titre.
     */
    private String getWindowsWindowTitle() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "powershell", "-Command",
                "(Get-Process | Where-Object {$_.MainWindowTitle -like '*Spotify*'}).MainWindowTitle"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * macOS : utilise AppleScript.
     */
    private String getMacWindowTitle() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "osascript", "-e",
                "tell application \"System Events\" to get name of first window of (first process whose name contains \"Spotify\")"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Renvoie le texte à afficher dans le HUD.
     */
    public String getDisplayText() {
        if (!spotifyDetected) return "♪ Spotify: Non détecté";
        if (currentTrack.isEmpty()) return "♪ Spotify: Aucune lecture";
        return "♪ " + currentTrack + (currentArtist.isEmpty() ? "" : " — " + currentArtist);
    }

    public boolean isSpotifyDetected() {
        return spotifyDetected;
    }

    public String getCurrentTrack() {
        return currentTrack;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }
}
