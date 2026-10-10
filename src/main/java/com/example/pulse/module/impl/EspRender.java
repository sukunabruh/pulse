package com.example.pulse.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Shared world-to-screen projection for the 2D ESP overlays (entities, spawners).
 * Call begin() once per frame; it returns null when drawing isn't possible
 * (no world, F1 hidden HUD, or third-person view).
 */
public final class EspRender {
    private final Vec3d eye, f, r, u;
    private final int w, h;
    private final double sx, sy;

    private EspRender(Vec3d eye, Vec3d f, Vec3d r, Vec3d u, int w, int h, double sx, double sy) {
        this.eye = eye; this.f = f; this.r = r; this.u = u;
        this.w = w; this.h = h; this.sx = sx; this.sy = sy;
    }

    public static EspRender begin(MinecraftClient mc, float tickProgress) {
        if (mc.world == null || mc.options.hudHidden) return null;
        if (!mc.options.getPerspective().isFirstPerson()) return null;
        Entity cam = mc.getCameraEntity();
        if (cam == null) return null;

        Vec3d eye = cam.getLerpedPos(tickProgress).add(0, cam.getEyeY() - cam.getY(), 0);
        double yr = Math.toRadians(cam.getYaw(tickProgress));
        double pr = Math.toRadians(cam.getPitch(tickProgress));

        // Minecraft: yaw 0 faces +Z, and "right" is -X at yaw 0
        Vec3d f = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3d r = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
        Vec3d u = new Vec3d(r.y * f.z - r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y - r.y * f.x);

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double sx = (w / 2.0) / (tanHalf * ((double) w / h));
        double sy = (h / 2.0) / tanHalf;
        return new EspRender(eye, f, r, u, w, h, sx, sy);
    }

    public Vec3d eye() { return eye; }
    public int width() { return w; }
    public int height() { return h; }

    /** Screen rectangle {x1, y1, x2, y2} of an axis-aligned box, or null if off-screen / behind. */
    public int[] project(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double sMinX = Double.MAX_VALUE, sMinY = Double.MAX_VALUE, sMaxX = -Double.MAX_VALUE, sMaxY = -Double.MAX_VALUE;
        int behind = 0;
        for (int c = 0; c < 8; c++) {
            double dx = ((c & 1) == 0 ? minX : maxX) - eye.x;
            double dy = (((c >> 1) & 1) == 0 ? minY : maxY) - eye.y;
            double dz = (((c >> 2) & 1) == 0 ? minZ : maxZ) - eye.z;
            double zc = dx * f.x + dy * f.y + dz * f.z;
            double xc = dx * r.x + dy * r.y + dz * r.z;
            double yc = dx * u.x + dy * u.y + dz * u.z;
            if (zc < 0.05) { behind++; zc = 0.05; }
            double px = Math.max(-3000, Math.min(3000, w / 2.0 + xc / zc * sx));
            double py = Math.max(-3000, Math.min(3000, h / 2.0 - yc / zc * sy));
            sMinX = Math.min(sMinX, px); sMaxX = Math.max(sMaxX, px);
            sMinY = Math.min(sMinY, py); sMaxY = Math.max(sMaxY, py);
        }
        if (behind == 8 || sMaxX < 0 || sMinX > w || sMaxY < 0 || sMinY > h) return null;

        int x1 = (int) Math.floor(sMinX), y1 = (int) Math.floor(sMinY);
        int x2 = (int) Math.ceil(sMaxX), y2 = (int) Math.ceil(sMaxY);
        if (x2 - x1 < 3) { int cx = (x1 + x2) / 2; x1 = cx - 1; x2 = cx + 2; }
        if (y2 - y1 < 3) { int cy = (y1 + y2) / 2; y1 = cy - 1; y2 = cy + 2; }
        return new int[]{x1, y1, x2, y2};
    }

    public static void drawBox(DrawContext ctx, int[] rect, int rgb, int fillAlpha) {
        int x1 = rect[0], y1 = rect[1], x2 = rect[2], y2 = rect[3];
        rgb &= 0xFFFFFF;
        if (fillAlpha > 0) ctx.fill(x1, y1, x2, y2, (fillAlpha << 24) | rgb);
        int line = 0xFF000000 | rgb;
        ctx.fill(x1, y1, x2, y1 + 1, line);
        ctx.fill(x1, y2 - 1, x2, y2, line);
        ctx.fill(x1, y1, x1 + 1, y2, line);
        ctx.fill(x2 - 1, y1, x2, y2, line);
    }

    /** Draws a 1px line (used for tracers). Cheap enough for a few dozen lines per frame. */
    public static void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int argb) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        for (int steps = 0; steps < 4000; steps++) {
            ctx.fill(x1, y1, x1 + 1, y1 + 1, argb);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }
}
