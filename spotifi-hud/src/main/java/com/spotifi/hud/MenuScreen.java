package com.spotifi.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class MenuScreen extends Screen {
    private enum Page { MAIN, ESP, SPEAR, TRIGGER, MACROS, MACRO_EDIT }

    /** click receives the mouse button: 0 = left, 1 = right */
    private record Row(String label, int color, IntConsumer click) {}

    private static final int PW = 250, ROW = 22, GREEN = 0xFF1DB954, GREY = 0xFFAAAAAA, WHITE = 0xFFFFFFFF;
    private static final String[] MACRO_NAMES = {"Single Anchor", "Safe Anchor", "Double Anchor", "Anchor Pearl"};

    private Page page = Page.MAIN;
    private int editingIndex = 0;
    private boolean capturing = false;

    public MenuScreen() { super(Component.literal("Spotifi HUD")); }

    @Override public boolean isPauseScreen() { return false; }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }
    private static int dir(int btn) { return btn == 1 ? -1 : 1; }

    private Config.MacroCfg macro(int i) {
        Config c = Config.INSTANCE;
        return switch (i) { case 0 -> c.singleAnchor; case 1 -> c.safeAnchor; case 2 -> c.doubleAnchor; default -> c.anchorPearl; };
    }

    private static String keyName(int k) {
        return k == GLFW.GLFW_KEY_UNKNOWN ? "None" : InputConstants.Type.KEYSYM.getOrCreate(k).getDisplayName().getString();
    }

    private List<Row> rows() {
        Config c = Config.INSTANCE;
        List<Row> r = new ArrayList<>();
        switch (page) {
            case MAIN -> {
                r.add(new Row("Player ESP: " + onOff(c.espEnabled), c.espEnabled ? GREEN : GREY,
                        b -> { if (b == 0) c.espEnabled = !c.espEnabled; else page = Page.ESP; }));
                r.add(new Row("Spear Swap: " + onOff(c.spearSwapEnabled), c.spearSwapEnabled ? GREEN : GREY,
                        b -> { if (b == 0) c.spearSwapEnabled = !c.spearSwapEnabled; else page = Page.SPEAR; }));
                r.add(new Row("Triggerbot: " + onOff(c.triggerbotEnabled), c.triggerbotEnabled ? GREEN : GREY,
                        b -> { if (b == 0) c.triggerbotEnabled = !c.triggerbotEnabled; else page = Page.TRIGGER; }));
                r.add(new Row("Anchor Macros  >", WHITE, b -> page = Page.MACROS));
            }
            case ESP -> {
                r.add(new Row("Style: " + c.style.name(), WHITE,
                        b -> c.style = Config.Style.values()[Math.floorMod(c.style.ordinal() + dir(b), Config.Style.values().length)]));
                r.add(new Row("Color: " + Config.COLOR_NAMES[c.colorIndex], c.color(),
                        b -> c.colorIndex = Math.floorMod(c.colorIndex + dir(b), Config.COLORS.length)));
                r.add(new Row("Range: " + c.range + " blocks", WHITE,
                        b -> c.range = Math.max(16, Math.min(256, c.range + 16 * dir(b)))));
                r.add(back());
            }
            case SPEAR -> {
                r.add(new Row("Swap back to: " + (c.swapBackSlot == 0 ? "Previous item" : "Hotbar slot " + c.swapBackSlot), WHITE,
                        b -> c.swapBackSlot = Math.floorMod(c.swapBackSlot + dir(b), 10)));
                r.add(new Row("Only when aiming at player: " + onOff(c.spearOnlyOnPlayer), WHITE,
                        b -> c.spearOnlyOnPlayer = !c.spearOnlyOnPlayer));
                r.add(back());
            }
            case TRIGGER -> {
                r.add(new Row("Players only: " + onOff(c.triggerPlayersOnly), WHITE, b -> c.triggerPlayersOnly = !c.triggerPlayersOnly));
                r.add(back());
            }
            case MACROS -> {
                for (int i = 0; i < 4; i++) {
                    final int idx = i;
                    Config.MacroCfg m = macro(i);
                    r.add(new Row(MACRO_NAMES[i] + ": " + onOff(m.enabled) + "   [" + keyName(m.key) + "]", m.enabled ? GREEN : GREY,
                            b -> { if (b == 0) m.enabled = !m.enabled; else { editingIndex = idx; page = Page.MACRO_EDIT; } }));
                }
                r.add(new Row("Back", GREY, b -> page = Page.MAIN));
            }
            case MACRO_EDIT -> {
                Config.MacroCfg m = macro(editingIndex);
                r.add(new Row(capturing ? "Activate key: press a key... (Esc = cancel)" : "Activate key: " + keyName(m.key), capturing ? GREEN : WHITE,
                        b -> { if (b == 0) capturing = true; else m.key = GLFW.GLFW_KEY_UNKNOWN; }));
                r.add(new Row("Delay: " + m.delayMs + " ms", WHITE, b -> m.delayMs = Math.max(0, Math.min(500, m.delayMs + 5 * dir(b)))));
                r.add(new Row("Anchor key (slot): " + m.anchorSlot, WHITE, b -> m.anchorSlot = Math.floorMod(m.anchorSlot - 1 + dir(b), 9) + 1));
                r.add(new Row("Glowstone key (slot): " + m.glowstoneSlot, WHITE, b -> m.glowstoneSlot = Math.floorMod(m.glowstoneSlot - 1 + dir(b), 9) + 1));
                r.add(new Row("Totem key (slot): " + m.totemSlot, WHITE, b -> m.totemSlot = Math.floorMod(m.totemSlot - 1 + dir(b), 9) + 1));
                if (editingIndex == 3)
                    r.add(new Row("Pearl key (slot): " + m.pearlSlot, WHITE, b -> m.pearlSlot = Math.floorMod(m.pearlSlot - 1 + dir(b), 9) + 1));
                if (editingIndex == 1) {
                    r.add(new Row("Flick distance: " + m.flickDeg + " deg", WHITE, b -> m.flickDeg = Math.max(10, Math.min(90, m.flickDeg + 5 * dir(b)))));
                    r.add(new Row("Smoothness: " + m.smoothness, WHITE, b -> m.smoothness = Math.max(1, Math.min(15, m.smoothness + dir(b)))));
                }
                r.add(new Row("Back", GREY, b -> { capturing = false; page = Page.MACROS; }));
            }
        }
        return r;
    }

    private Row back() { return new Row("Back", GREY, b -> page = Page.MAIN); }

    private String hint() {
        return switch (page) {
            case MAIN -> "Left click: toggle   Right click: edit";
            case ESP -> "Left/right click cycles; range +16 / -16";
            case SPEAR -> "Swap key: set in Controls (default Right Click)";
            case TRIGGER -> "Attacks when your crosshair is on a target";
            case MACROS -> "Left click: toggle   Right click: edit";
            case MACRO_EDIT -> "Left +  /  Right -   |  Aim at a block, press the key";
        };
    }

    private int top(int rowCount) { return Math.max(12, (height - (rowCount * ROW + 60)) / 2); }
    private int left() { return (width - PW) / 2; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        List<Row> rows = rows();
        int x = left(), y = top(rows.size());
        g.fill(x - 6, y - 6, x + PW + 6, y + 24 + rows.size() * ROW + 20, 0xCC101010);
        g.fill(x - 6, y - 6, x + PW + 6, y - 4, GREEN);
        g.drawString(font, "Spotifi HUD", x, y + 4, GREEN);

        for (int i = 0; i < rows.size(); i++) {
            int ry = y + 24 + i * ROW;
            boolean hover = mx >= x && mx <= x + PW && my >= ry && my < ry + ROW - 2;
            g.fill(x, ry, x + PW, ry + ROW - 2, hover ? 0xFF303030 : 0xFF202020);
            g.drawString(font, rows.get(i).label(), x + 6, ry + 7, rows.get(i).color());
        }
        g.drawString(font, hint(), x, y + 24 + rows.size() * ROW + 2, 0xFF888888);
    }

    private int rowAt(double mx, double my, int count) {
        int x = left(), y = top(count) + 24;
        if (mx < x || mx > x + PW || my < y) return -1;
        int i = (int) ((my - y) / ROW);
        return ((my - y) % ROW < ROW - 2 && i < count) ? i : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        capturing = false;
        List<Row> rows = rows();
        int i = rowAt(event.x(), event.y(), rows.size());
        if (i >= 0) {
            rows.get(i).click().accept(event.button());
            Config.INSTANCE.save();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing) {
            if (event.key() != GLFW.GLFW_KEY_ESCAPE) macro(editingIndex).key = event.key();
            capturing = false;
            Config.INSTANCE.save();
            return true;
        }
        return super.keyPressed(event);
    }
}
