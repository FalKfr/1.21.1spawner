package com.spotifi.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public enum Style { TRACERS, BOX, BOTH }

    public static final int[] COLORS = {0xFF1DB954, 0xFFFF3B3B, 0xFFFF69B4, 0xFF3BC9FF, 0xFFFFE03B, 0xFFFFFFFF};
    public static final String[] COLOR_NAMES = {"Green", "Red", "Pink", "Cyan", "Yellow", "White"};

    public boolean espEnabled = true;
    public Style style = Style.TRACERS;
    public int colorIndex = 0;
    public int range = 128;

    // Spear swap
    public boolean spearSwapEnabled = false;
    public int swapBackSlot = 0;            // 0 = previous item, 1-9 = hotbar slot you chose
    public boolean spearOnlyOnPlayer = true; // only fire the swap key when aiming at a player

    // Triggerbot
    public boolean triggerbotEnabled = false;
    public boolean triggerPlayersOnly = true;

    // Anchor macros
    public static class MacroCfg {
        public boolean enabled = false;
        public int key = GLFW.GLFW_KEY_UNKNOWN; // activate key (GLFW code)
        public int delayMs = 27;                // delay between steps
        public int anchorSlot = 4, glowstoneSlot = 5, totemSlot = 9, pearlSlot = 3; // hotbar 1-9
        public int flickDeg = 60, smoothness = 5; // Safe Anchor only

        public MacroCfg() {}
        public MacroCfg(int key, int delayMs) { this.key = key; this.delayMs = delayMs; }
    }
    public MacroCfg singleAnchor = new MacroCfg(GLFW.GLFW_KEY_R, 27);
    public MacroCfg safeAnchor = new MacroCfg(GLFW.GLFW_KEY_T, 50);
    public MacroCfg doubleAnchor = new MacroCfg(GLFW.GLFW_KEY_G, 27);
    public MacroCfg anchorPearl = new MacroCfg(GLFW.GLFW_KEY_V, 27);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spotifi_hud.json");
    public static Config INSTANCE = load();

    public int color() { return COLORS[Math.floorMod(colorIndex, COLORS.length)]; }

    public static Config load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) return c;
            }
        } catch (Exception ignored) {}
        return new Config();
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception ignored) {}
    }
}
