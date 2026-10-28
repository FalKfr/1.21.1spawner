package com.spawnerprotect;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpawnerProtectConfigScreen extends Screen {

    private final Screen parent;
    private Button modeButton;
    private Button countLabel;
    private Button hudButton;
    private final List<Button> countButtons = new ArrayList<>();

    public SpawnerProtectConfigScreen(Screen parent) {
        super(Component.literal("Spawner Protect"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        countButtons.clear();
        int cx = this.width / 2;
        int y = this.height / 4 + 4;

        // row 1: auto detect / manual
        modeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            SpawnerProtectConfig.autoDetect = !SpawnerProtectConfig.autoDetect;
            refresh();
        }).bounds(cx - 130, y, 260, 20).build());

        // row 2: manual break count
        int ry = y + 26;
        countButtons.add(addCountButton("-10", cx - 130, ry, 36, -10));
        countButtons.add(addCountButton("-1", cx - 90, ry, 28, -1));
        countLabel = this.addRenderableWidget(Button.builder(Component.empty(), b -> { })
                .bounds(cx - 58, ry, 116, 20).build());
        countButtons.add(addCountButton("+1", cx + 62, ry, 28, 1));
        countButtons.add(addCountButton("+10", cx + 94, ry, 36, 10));

        // row 3: delay slider (0.01 s - 2.00 s)
        this.addRenderableWidget(new DelaySlider(cx - 130, y + 52, 260, 20));

        // row 4: show on HUD
        hudButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            SpawnerProtectConfig.showHud = !SpawnerProtectConfig.showHud;
            refresh();
        }).bounds(cx - 130, y + 78, 260, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(cx - 130, y + 108, 260, 20).build());

        refresh();
    }

    private Button addCountButton(String label, int x, int y, int w, int delta) {
        return this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
            SpawnerProtectConfig.breakCount = SpawnerProtectConfig.clamp(SpawnerProtectConfig.breakCount + delta);
            refresh();
        }).bounds(x, y, w, 20).build());
    }

    private void refresh() {
        boolean auto = SpawnerProtectConfig.autoDetect;
        modeButton.setMessage(Component.literal(auto
                ? "Auto detect: ON (breaks until no players)"
                : "Auto detect: OFF (manual)"));
        countLabel.setMessage(Component.literal("Manual breaks: " + SpawnerProtectConfig.breakCount));
        countLabel.active = !auto;
        for (Button b : countButtons) {
            b.active = !auto;
        }
        hudButton.setMessage(Component.literal("Show on HUD: " + (SpawnerProtectConfig.showHud ? "ON" : "OFF")));
    }

    @Override
    public void onClose() {
        SpawnerProtectConfig.save();
        this.minecraft.setScreen(parent);
    }

    /** Slider for the delay between breaks, 0.01 s to 2.00 s. */
    private static class DelaySlider extends AbstractSliderButton {
        DelaySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), toSlider(SpawnerProtectConfig.breakDelay));
            updateMessage();
        }

        private static double toSlider(double seconds) {
            return (seconds - SpawnerProtectConfig.MIN_DELAY)
                    / (SpawnerProtectConfig.MAX_DELAY - SpawnerProtectConfig.MIN_DELAY);
        }

        private double seconds() {
            return SpawnerProtectConfig.clampDelay(SpawnerProtectConfig.MIN_DELAY
                    + this.value * (SpawnerProtectConfig.MAX_DELAY - SpawnerProtectConfig.MIN_DELAY));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Delay between breaks: "
                    + String.format(Locale.ROOT, "%.2f", seconds()) + " s"));
        }

        @Override
        protected void applyValue() {
            SpawnerProtectConfig.breakDelay = seconds();
        }
    }
}
