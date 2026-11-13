package com.spotifi.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Player ESP drawn in 2D by projecting each player's hitbox onto the screen. */
public class EspRenderer {

    public static void render(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Config cfg = Config.INSTANCE;
        if (!cfg.espEnabled || mc.player == null || mc.level == null || mc.options.hideGui) return;

        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 eye = mc.player.getEyePosition(pt);
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double pitch = Math.toRadians(mc.player.getViewXRot(pt));
        double fov = Math.toRadians(mc.options.fov().get());
        double focal = (h / 2.0) / Math.tan(fov / 2.0);

        // Camera basis (look, right, up)
        Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(look);

        int color = cfg.color();
        boolean tracers = cfg.style != Config.Style.BOX;
        boolean box = cfg.style != Config.Style.TRACERS;

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (p.distanceTo(mc.player) > cfg.range) continue;

            double x = Mth.lerp(pt, p.xo, p.getX());
            double y = Mth.lerp(pt, p.yo, p.getY());
            double z = Mth.lerp(pt, p.zo, p.getZ());
            double hw = p.getBbWidth() / 2.0, ht = p.getBbHeight();

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            boolean visible = true;
            for (int i = 0; i < 8; i++) {
                Vec3 c = new Vec3(x + ((i & 1) == 0 ? -hw : hw), y + ((i & 2) == 0 ? 0 : ht), z + ((i & 4) == 0 ? -hw : hw));
                double[] s = project(c.subtract(eye), look, right, up, w, h, focal);
                if (s == null) { visible = false; break; }
                minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
                minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
            }
            if (!visible) continue;

            if (box) outline(g, (int) minX, (int) minY, (int) maxX, (int) maxY, color);
            if (tracers) line(g, w / 2, h, (int) ((minX + maxX) / 2), (int) maxY, color);

            String label = p.getName().getString() + " " + Math.round(p.distanceTo(mc.player)) + "m";
            g.drawString(mc.font, label, (int) ((minX + maxX) / 2) - mc.font.width(label) / 2, (int) minY - 10, color);
        }
    }

    private static double[] project(Vec3 d, Vec3 look, Vec3 right, Vec3 up, int w, int h, double focal) {
        double dl = d.dot(look);
        if (dl < 0.05) return null; // behind camera
        double sx = w / 2.0 + (d.dot(right) / dl) * focal;
        double sy = h / 2.0 - (d.dot(up) / dl) * focal;
        return new double[]{sx, sy};
    }

    private static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1, y1, x2 + 1, y1 + 1, c);
        g.fill(x1, y2, x2 + 1, y2 + 1, c);
        g.fill(x1, y1, x1 + 1, y2 + 1, c);
        g.fill(x2, y1, x2 + 1, y2 + 1, c);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        int dx = x1 - x0, dy = y1 - y0;
        int n = Math.min(Math.max(Math.abs(dx), Math.abs(dy)), 4000);
        if (n == 0) return;
        for (int i = 0; i <= n; i++) {
            int x = x0 + dx * i / n, y = y0 + dy * i / n;
            g.fill(x, y, x + 1, y + 1, c);
        }
    }
}
