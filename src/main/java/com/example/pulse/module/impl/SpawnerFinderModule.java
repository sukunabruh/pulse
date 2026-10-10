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

import java.util.HashSet;
import java.util.Set;

/** Finds mob spawners in loaded chunks, boxes them through walls and alerts you in chat once per spawner. */
public class SpawnerFinderModule extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 6, 1, 12, 1));
    private final BoolSetting alert = add(new BoolSetting("Chat alert", true));
    private final BoolSetting labels = add(new BoolSetting("Labels", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", true));
    private final NumberSetting fill = add(new NumberSetting("Fill %", 25, 0, 100, 5));

    private Set<BlockPos> current = new HashSet<>();
    private final Set<BlockPos> alerted = new HashSet<>();
    private int cooldown;

    public SpawnerFinderModule() {
        super("Spawner Finder", "Highlights mob spawners and alerts you when a new one is found.", Category.ESP);
    }

    @Override
    public void onDisable() {
        current = new HashSet<>();
        alerted.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (--cooldown > 0) return;
        cooldown = 20;

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        int cx = cam.getBlockX() >> 4, cz = cam.getBlockZ() >> 4, r = range.intValue();

        Set<BlockPos> found = new HashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof MobSpawnerBlockEntity) found.add(be.getPos().toImmutable());
                }
            }
        }
        for (BlockPos p : found) {
            if (alerted.add(p) && alert.get()) {
                mc.player.sendMessage(Text.literal("[Pulse] Spawner found at " + p.getX() + ", " + p.getY() + ", " + p.getZ()), false);
            }
        }
        current = found;
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (current.isEmpty()) return;
        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;
        int alpha = (int) Math.round(fill.get() / 100.0 * 255.0);

        for (BlockPos p : current) {
            int[] rect = esp.project(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            if (rect == null) continue;
            EspRender.drawBox(ctx, rect, 0xFF3030, alpha);
            if (tracers.get()) {
                EspRender.drawLine(ctx, esp.width() / 2, esp.height(), (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFFFF3030);
            }
            if (labels.get()) {
                double d = Math.sqrt(esp.eye().squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                String text = "Spawner " + (int) d + "m";
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(text), (rect[0] + rect[2]) / 2, rect[1] - 10, 0xFFFF3030);
            }
        }
    }
}
