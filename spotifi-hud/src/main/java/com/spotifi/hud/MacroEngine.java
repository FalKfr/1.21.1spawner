package com.spotifi.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/** Anchor macros: Single, Safe, Double, Anchor Pearl. Steps run on a ms timer, checked every tick and every frame. */
public class MacroEngine {
    public enum Kind { SINGLE, SAFE, DOUBLE, PEARL }

    private record Step(long due, Runnable run) {}
    private static final ArrayDeque<Step> queue = new ArrayDeque<>();
    private static final boolean[] wasDown = new boolean[4];

    public static boolean busy() { return !queue.isEmpty(); }

    /** Runs any steps that are due. Called from the tick and from the HUD frame for sub-tick timing. */
    public static void run(Minecraft mc) {
        if (mc.player == null || mc.level == null) { queue.clear(); return; }
        long now = System.nanoTime();
        while (!queue.isEmpty() && queue.peek().due() <= now) queue.poll().run().run();
    }

    public static void tick(Minecraft mc) {
        run(mc);
        Config c = Config.INSTANCE;
        Config.MacroCfg[] all = {c.singleAnchor, c.safeAnchor, c.doubleAnchor, c.anchorPearl};
        for (int i = 0; i < all.length; i++) {
            Config.MacroCfg m = all[i];
            boolean down = mc.player != null && mc.screen == null && m.enabled
                    && m.key != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(mc.getWindow(), m.key);
            if (down && !wasDown[i] && queue.isEmpty()) start(mc, Kind.values()[i], m);
            wasDown[i] = down;
        }
    }

    // ---- sequence builder ----
    private static class Seq {
        long t = System.nanoTime();
        final long delay;
        Seq(int delayMs) { delay = delayMs * 1_000_000L; }
        void add(Runnable r) { queue.add(new Step(t, r)); t += delay; }
        void add(Runnable r, long gapMs) { queue.add(new Step(t, r)); t += gapMs * 1_000_000L; }
    }

    private static void start(Minecraft mc, Kind kind, Config.MacroCfg m) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            msg(mc, "aim at the block you want to place the anchor on"); return;
        }
        Inventory inv = mc.player.getInventory();
        if (!has(inv, m.anchorSlot, Items.RESPAWN_ANCHOR)) { msg(mc, "no respawn anchor in hotbar slot " + m.anchorSlot); return; }
        if (!has(inv, m.glowstoneSlot, Items.GLOWSTONE)) { msg(mc, "no glowstone in hotbar slot " + m.glowstoneSlot); return; }
        if (kind == Kind.PEARL && !has(inv, m.pearlSlot, Items.ENDER_PEARL)) { msg(mc, "no ender pearl in hotbar slot " + m.pearlSlot); return; }

        Direction face = hit.getDirection();
        BlockPos a1 = hit.getBlockPos().relative(face);               // first anchor
        BlockHitResult on1 = new BlockHitResult(Vec3.atCenterOf(a1).relative(face, 0.5), face, a1, false);
        BlockHitResult on1Top = new BlockHitResult(Vec3.atCenterOf(a1).add(0, 0.5, 0), Direction.UP, a1, false);
        BlockPos a2 = a1.above();                                      // second anchor (double)
        BlockHitResult on2 = new BlockHitResult(Vec3.atCenterOf(a2).add(0, 0.5, 0), Direction.UP, a2, false);

        Seq s = new Seq(m.delayMs);
        Runnable place = () -> { select(mc, m.anchorSlot); useOn(mc, hit); };
        Runnable charge1 = () -> { select(mc, m.glowstoneSlot); useOn(mc, on1); };
        Runnable explode1 = () -> { select(mc, m.totemSlot); useOn(mc, on1); };

        switch (kind) {
            case SINGLE -> { s.add(place); s.add(charge1); s.add(explode1); }
            case PEARL -> {
                s.add(place); s.add(charge1); s.add(explode1);
                s.add(() -> { select(mc, m.pearlSlot); mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); });
            }
            case DOUBLE -> {
                s.add(place);
                s.add(charge1);
                s.add(() -> { select(mc, m.anchorSlot); useOn(mc, on1Top); });                 // second anchor on top
                s.add(() -> { select(mc, m.glowstoneSlot); useOn(mc, on2Top(a2)); });           // charge it
                s.add(() -> { select(mc, m.totemSlot); useOn(mc, on2Top(a2)); });               // explode second
                s.add(explode1);                                                                // explode first
            }
            case SAFE -> {
                s.add(place);
                s.add(charge1);
                int steps = Math.max(1, m.smoothness);
                float per = Mth.clamp(m.flickDeg, 10, 90) / (float) steps;
                for (int i = 0; i < steps; i++) {
                    s.add(() -> mc.player.setXRot(Mth.clamp(mc.player.getXRot() + per, -90f, 90f)), 15);
                }
                s.add(() -> {                                                                   // glowstone shield where you now look
                    select(mc, m.glowstoneSlot);
                    if (mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) useOn(mc, b);
                }, Math.max(m.delayMs, 20));
                s.add(() -> select(mc, m.totemSlot));
            }
        }
    }

    private static BlockHitResult on2Top(BlockPos a2) {
        return new BlockHitResult(Vec3.atCenterOf(a2).add(0, 0.5, 0), Direction.UP, a2, false);
    }

    private static boolean has(Inventory inv, int slot1to9, Item item) {
        return inv.getItem(Mth.clamp(slot1to9, 1, 9) - 1).is(item);
    }

    private static void select(Minecraft mc, int slot1to9) {
        mc.player.getInventory().setSelectedSlot(Mth.clamp(slot1to9, 1, 9) - 1);
    }

    private static void useOn(Minecraft mc, BlockHitResult hit) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void msg(Minecraft mc, String s) {
        mc.player.displayClientMessage(Component.literal("Spotifi HUD: " + s), true);
    }
}
