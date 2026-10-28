package com.spawnerprotect;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings, saved to .minecraft/config/spawnerprotect.properties */
public final class SpawnerProtectConfig {
    public static final int MIN_BREAKS = 1;
    public static final int MAX_BREAKS = 999;
    public static final double MIN_DELAY = 0.01;   // seconds
    public static final double MAX_DELAY = 2.00;   // seconds

    /** true = keep breaking until no players are detected; false = manual (break exactly breakCount times). */
    public static boolean autoDetect = true;
    /** Manual mode: how many times to break (e.g. 4 stacked spawners = 4). */
    public static int breakCount = 4;
    /** Seconds to wait after each break before breaking again. */
    public static double breakDelay = 0.50;
    /** Show the ON/OFF text (and the debug line) on the HUD. */
    public static boolean showHud = true;

    private SpawnerProtectConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("spawnerprotect.properties");
    }

    public static int clamp(int value) {
        return Math.max(MIN_BREAKS, Math.min(MAX_BREAKS, value));
    }

    public static double clampDelay(double seconds) {
        double v = Math.max(MIN_DELAY, Math.min(MAX_DELAY, seconds));
        return Math.round(v * 100.0) / 100.0;
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (InputStream in = Files.newInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            autoDetect = Boolean.parseBoolean(p.getProperty("autoDetect", "true").trim());
            breakCount = clamp(Integer.parseInt(p.getProperty("breakCount", "4").trim()));
            breakDelay = clampDelay(Double.parseDouble(p.getProperty("breakDelay", "0.5").trim()));
            showHud = Boolean.parseBoolean(p.getProperty("showHud", "true").trim());
        } catch (IOException | NumberFormatException e) {
            // keep defaults
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("autoDetect", Boolean.toString(autoDetect));
        p.setProperty("breakCount", Integer.toString(breakCount));
        p.setProperty("breakDelay", Double.toString(breakDelay));
        p.setProperty("showHud", Boolean.toString(showHud));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Spawner Protect settings");
        } catch (IOException e) {
            // ignore
        }
    }
}
