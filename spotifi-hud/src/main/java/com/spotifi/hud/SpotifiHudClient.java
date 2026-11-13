package com.spotifi.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class SpotifiHudClient implements ClientModInitializer {
    public static KeyMapping OPEN_MENU;
    public static KeyMapping SPEAR_SWAP;

    @Override
    public void onInitializeClient() {
        // Default: Left Shift. Rebind in Options > Controls > Key Binds.
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.spotifi_hud.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, KeyMapping.Category.MISC));

        // Default: Right Click. Rebind in Controls to any key or mouse button.
        SPEAR_SWAP = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.spotifi_hud.spear_swap", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_MENU.consumeClick()) {
                if (mc.screen == null && mc.player != null) mc.setScreen(new MenuScreen());
            }
            Modules.tick(mc);
            MacroEngine.tick(mc);
        });

        HudRenderCallback.EVENT.register((graphics, delta) -> {
            MacroEngine.run(net.minecraft.client.Minecraft.getInstance()); // sub-tick timing for macro delays
            EspRenderer.render(graphics, delta.getGameTimeDeltaPartialTick(false));
        });
    }
}
