package com.spotifi.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class Modules {
    private enum Phase { IDLE, WAIT_SWAP, WAIT_BACK }

    private static Phase phase = Phase.IDLE;
    private static int timer = 0;
    private static int returnSlot = 0;

    public static void tick(Minecraft mc) {
        // always drain the key so presses don't pile up while the module is off
        boolean swapPressed = false;
        while (SpotifiHudClient.SPEAR_SWAP.consumeClick()) swapPressed = true;

        if (mc.player == null || mc.level == null) { phase = Phase.IDLE; return; }
        Config c = Config.INSTANCE;

        switch (phase) {
            case WAIT_SWAP -> {
                if (--timer <= 0) { attackClick(mc); phase = Phase.WAIT_BACK; timer = 3; }
            }
            case WAIT_BACK -> {
                if (--timer <= 0) {
                    mc.player.getInventory().setSelectedSlot(returnSlot);
                    phase = Phase.IDLE;
                }
            }
            case IDLE -> {
                if (mc.screen != null || MacroEngine.busy()) return;
                if (swapPressed && c.spearSwapEnabled && (!c.spearOnlyOnPlayer || aimingAtPlayer(mc))) {
                    startSpearSwap(mc, c);
                } else {
                    triggerbot(mc, c);
                }
            }
        }
    }

    private static void startSpearSwap(Minecraft mc, Config c) {
        Inventory inv = mc.player.getInventory();
        int held = inv.getSelectedSlot();
        int spear = isSpear(inv.getItem(held)) ? held : findSpearSlot(inv);
        if (spear == -1) {
            mc.player.displayClientMessage(Component.literal("Spotifi HUD: no spear in your hotbar"), true);
            return;
        }
        returnSlot = c.swapBackSlot == 0 ? held : c.swapBackSlot - 1;
        if (spear == held) {
            attackClick(mc);
            phase = Phase.WAIT_BACK;
            timer = 3;
        } else {
            inv.setSelectedSlot(spear);
            phase = Phase.WAIT_SWAP;
            timer = 1; // let the server register the slot change before attacking
        }
    }

    private static void triggerbot(Minecraft mc, Config c) {
        if (!c.triggerbotEnabled) return;
        if (!(mc.hitResult instanceof EntityHitResult hit)) return;
        Entity e = hit.getEntity();
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator()) return;
        if (c.triggerPlayersOnly && !(e instanceof Player)) return;
        if (mc.player.isUsingItem()) return;
        if (mc.player.getAttackStrengthScale(0.5f) < 0.95f) return; // wait for cooldown
        attackClick(mc);
    }

    private static boolean aimingAtPlayer(Minecraft mc) {
        return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player;
    }

    /** Hotbar only (slots 0-8). */
    private static int findSpearSlot(Inventory inv) {
        for (int i = 0; i < 9; i++) if (isSpear(inv.getItem(i))) return i;
        return -1;
    }

    private static boolean isSpear(ItemStack s) {
        return !s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("_spear");
    }

    /** Queues a normal attack click (uses whatever key your Attack bind is set to), so vanilla handles the attack/lunge. */
    private static void attackClick(Minecraft mc) {
        InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(mc.options.keyAttack);
        if (k == InputConstants.UNKNOWN) k = InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        KeyMapping.click(k);
    }
}
