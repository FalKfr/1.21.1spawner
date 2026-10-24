package com.spawnerprotect;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Spawner Protect (Minecraft 1.21.11, official Mojang mappings)
 *
 * When another player is rendered (present in your client world, within RENDER_RANGE) and a
 * skeleton spawner is within SCAN_RADIUS, and you have a SILK TOUCH pickaxe in your hotbar:
 * hold sneak, walk to / aim at / break every skeleton spawner in range, then disconnect.
 * Without a silk touch pickaxe it does nothing. Press P to toggle on/off.
 */
public class SpawnerProtectClient implements ClientModInitializer {

    // ---- settings (edit these) ----
    private static final int SCAN_RADIUS = 24;               // blocks around you to look for spawners
    private static final double RENDER_RANGE = 128.0;        // another player this close (and loaded) counts as "rendered"
    private static final double REACH = 4.5;                 // survival block reach
    private static final int TIMEOUT_TICKS = 20 * 60 * 3;    // give up + log off after 3 minutes
    private static final Set<String> WHITELIST = Set.of();   // names that DON'T trigger it, e.g. Set.of("MyAlt")
    private static final String TARGET_MOB = "minecraft:skeleton";

    private boolean enabled = true;   // toggled with the P key
    private boolean active = false;
    private boolean warnedNoPickaxe = false;
    private int ticks = 0;
    private KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.spawnerprotect.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("spawnerprotect", "main"))));

        ClientTickEvents.START_CLIENT_TICK.register(this::tick);

        // always-visible ON/OFF indicator, top-left of the screen
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("spawnerprotect", "status"),
                (graphics, deltaTracker) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return;
                    graphics.drawString(mc.font, statusText(), 4, 4, 0xFFFFFFFF, true);
                });
    }

    private Component statusText() {
        Component state = enabled
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        return Component.literal("Spawner Protect: ").append(state);
    }

    private void chat(Minecraft mc, String text) {
        mc.gui.getChat().addMessage(Component.literal("[Spawner Protect] " + text));
    }

    private void tick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            enabled = !enabled;
            if (!enabled) {
                active = false;
                releaseKeys(mc);
            }
            if (mc.player != null) {
                mc.gui.setOverlayMessage(statusText(), false);
            }
        }
        if (!enabled) return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.getConnection() == null) {
            active = false;
            return;
        }

        if (!active) {
            if (level.getGameTime() % 10 != 0) return;
            boolean trigger = otherPlayerRendered(mc) && !findSpawners(mc).isEmpty();
            if (!trigger) {
                warnedNoPickaxe = false;
                return;
            }
            if (findSilkPickaxe(mc) == -1) {
                if (!warnedNoPickaxe) {
                    warnedNoPickaxe = true;
                    chat(mc, "Player + spawner detected, but you have no silk touch pickaxe in your hotbar.");
                }
                return;
            }
            active = true;
            ticks = 0;
            chat(mc, "Player detected, breaking skeleton spawners...");
        }

        // silk touch pickaxe is required the whole time
        int slot = findSilkPickaxe(mc);
        if (slot == -1) {
            active = false;
            warnedNoPickaxe = true;
            releaseKeys(mc);
            chat(mc, "Silk touch pickaxe missing, stopped.");
            return;
        }
        player.getInventory().setSelectedSlot(slot);

        ticks++;
        mc.options.keyShift.setDown(true);

        List<BlockPos> spawners = findSpawners(mc);
        if (spawners.isEmpty() || ticks > TIMEOUT_TICKS) {
            finish(mc);
            return;
        }

        Vec3 eye = player.getEyePosition();
        BlockPos target = spawners.stream()
                .min(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(eye)))
                .get();

        aimAt(player, target);

        double dist = Vec3.atCenterOf(target).distanceTo(eye);
        boolean lookingAtIt = mc.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(target);

        if (dist <= REACH) {
            // only mine when the crosshair is actually on the spawner (never digs random blocks)
            mc.options.keyAttack.setDown(lookingAtIt);
            mc.options.keyUp.setDown(!lookingAtIt && dist > 2.0);
        } else {
            mc.options.keyAttack.setDown(false);
            mc.options.keyUp.setDown(true);
        }
        mc.options.keyJump.setDown(player.horizontalCollision && dist > REACH);
    }

    private void finish(Minecraft mc) {
        active = false;
        releaseKeys(mc);
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection()
                    .disconnect(Component.literal("Spawner Protect: spawners cleared, logged off."));
        }
    }

    private void releaseKeys(Minecraft mc) {
        mc.options.keyShift.setDown(false);
        mc.options.keyAttack.setDown(false);
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    /** Another (non-spectator, non-whitelisted) player exists in your client world within RENDER_RANGE. */
    private boolean otherPlayerRendered(Minecraft mc) {
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == mc.player) continue;
            if (other.isSpectator()) continue;
            if (WHITELIST.contains(other.getName().getString())) continue;
            if (other.distanceTo(mc.player) > RENDER_RANGE) continue;
            return true;
        }
        return false;
    }

    private List<BlockPos> findSpawners(Minecraft mc) {
        List<BlockPos> found = new ArrayList<>();
        ClientLevel level = mc.level;
        BlockPos center = mc.player.blockPosition();
        int chunkRange = (SCAN_RADIUS >> 4) + 1;
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        double maxSq = (double) SCAN_RADIUS * SCAN_RADIUS;

        for (int dx = -chunkRange; dx <= chunkRange; dx++) {
            for (int dz = -chunkRange; dz <= chunkRange; dz++) {
                LevelChunk chunk = level.getChunk(ccx + dx, ccz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof SpawnerBlockEntity)) continue;
                    if (be.getBlockPos().distSqr(center) > maxSq) continue;
                    if (isTargetSpawner(be, level)) found.add(be.getBlockPos());
                }
            }
        }
        return found;
    }

    private boolean isTargetSpawner(BlockEntity be, ClientLevel level) {
        CompoundTag tag = be.getUpdateTag(level.registryAccess());
        String id = tag.getCompoundOrEmpty("SpawnData")
                .getCompoundOrEmpty("entity")
                .getStringOr("id", "");
        return TARGET_MOB.equals(id);
    }

    private void aimAt(LocalPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 t = Vec3.atCenterOf(pos);
        double dx = t.x - eye.x;
        double dy = t.y - eye.y;
        double dz = t.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        player.setYRot((float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f);
        player.setXRot((float) -(Mth.atan2(dy, horiz) * 180.0 / Math.PI));
    }

    /** Hotbar slot (0-8) of a pickaxe with Silk Touch, or -1 if you don't have one. */
    private int findSilkPickaxe(Minecraft mc) {
        var inv = mc.player.getInventory();
        var silk = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(ItemTags.PICKAXES)) continue;
            if (EnchantmentHelper.getItemEnchantmentLevel(silk, stack) > 0) return i;
        }
        return -1;
    }
}
