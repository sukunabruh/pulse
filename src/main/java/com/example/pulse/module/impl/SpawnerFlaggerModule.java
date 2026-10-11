package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds mob spawners in loaded chunks and flags them: boxed through walls, announced in chat,
 * optionally saved as a waypoint, and remembered after the chunk unloads (until you change
 * dimension/server). Several spawners close together are flagged as a cluster.
 */
public class SpawnerFlaggerModule extends Module {
    private final NumberSetting range = add(new NumberSetting("Range (chunks)", 8, 1, 16, 1));
    private final BoolSetting alert = add(new BoolSetting("Chat alert", true));
    private final BoolSetting flagWaypoint = add(new BoolSetting("Save waypoint", true));
    private final BoolSetting clusterAlert = add(new BoolSetting("Cluster alert", true));
    private final BoolSetting labels = add(new BoolSetting("Labels", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", true));
    private final NumberSetting fill = add(new NumberSetting("Fill %", 25, 0, 100, 5));
    private final BoolSetting summary = add(new BoolSetting("Show summary", true));

    private final WaypointsModule waypoints;
    private final Set<BlockPos> known = new LinkedHashSet<>();
    private String worldKey = "";
    private int cooldown;

    public SpawnerFlaggerModule(WaypointsModule waypoints) {
        super("Spawner Flagger", "Flags mob spawners: box, chat alert, waypoint and cluster warning.", Category.ESP);
        this.waypoints = waypoints;
    }

    @Override
    public void onDisable() {
        known.clear();
        worldKey = "";
    }

    private String currentKey() {
        var s = mc.getCurrentServerEntry();
        String server = s != null ? s.address : "singleplayer";
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        String key = currentKey();
        if (!key.equals(worldKey)) {
            worldKey = key;
            known.clear();
        }
        if (--cooldown > 0) return;
        cooldown = 20;

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        int cx = cam.getBlockX() >> 4, cz = cam.getBlockZ() >> 4, r = range.intValue();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof MobSpawnerBlockEntity) flag(be.getPos().toImmutable());
                }
            }
        }
    }

    private int nearbyKnown(BlockPos p, int radius) {
        int n = 0;
        for (BlockPos k : known) {
            if (k.getSquaredDistance(p) <= (double) radius * radius) n++;
        }
        return n;
    }

    private void flag(BlockPos p) {
        if (known.contains(p)) return;
        boolean firstInArea = nearbyKnown(p, 24) == 0;
        known.add(p);
        int near = nearbyKnown(p, 24);

        if (alert.get()) {
            mc.player.sendMessage(Text.literal("[Pulse] Spawner found at " + p.getX() + ", " + p.getY() + ", " + p.getZ()), false);
        }
        if (clusterAlert.get() && near >= 2) {
            mc.player.sendMessage(Text.literal("[Pulse] " + near + " spawners clustered near "
                    + p.getX() + ", " + p.getZ() + " - likely a spawner room or farm"), false);
        }
        if (flagWaypoint.get() && firstInArea) {
            waypoints.add("Spawner-" + p.getX() + "_" + p.getY() + "_" + p.getZ(), p.getX(), p.getY(), p.getZ());
        }
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (known.isEmpty() || mc.options.hudHidden) return;

        if (summary.get() && mc.player != null) {
            double best = Double.MAX_VALUE;
            for (BlockPos p : known) best = Math.min(best, mc.player.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
            String s = "Spawners: " + known.size() + " (nearest " + (int) Math.sqrt(best) + "m)";
            ctx.drawTextWithShadow(mc.textRenderer, s, mc.getWindow().getScaledWidth() - 4 - mc.textRenderer.getWidth(s), 240, 0xFFFF3030);
        }

        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;
        int alpha = (int) Math.round(fill.get() / 100.0 * 255.0);

        for (BlockPos p : known) {
            int[] rect = esp.project(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            if (rect == null) continue;
            EspRender.drawBox(ctx, rect, 0xFF3030, alpha);
            if (tracers.get()) {
                EspRender.drawLine(ctx, esp.width() / 2, esp.height(), (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFFFF3030);
            }
            if (labels.get()) {
                double d = Math.sqrt(esp.eye().squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Spawner " + (int) d + "m"),
                        (rect[0] + rect[2]) / 2, rect[1] - 10, 0xFFFF3030);
            }
        }
    }
}
