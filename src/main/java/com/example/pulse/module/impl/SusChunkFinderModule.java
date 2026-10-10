package com.example.pulse.module.impl;

import com.example.pulse.gui.Theme;
import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Flags loaded chunks with unusually many storage block entities (chests, barrels, shulkers,
 * hoppers; spawners count triple). Shows the top results as a list with block coordinates.
 * Scans every ~2 seconds; no per-frame world iteration.
 */
public class SusChunkFinderModule extends Module {
    private record Result(int cx, int cz, int score) {}

    private final NumberSetting range = add(new NumberSetting("Range", 8, 2, 16, 1));
    private final NumberSetting minScore = add(new NumberSetting("Min score", 8, 2, 40, 1));
    private final NumberSetting listSize = add(new NumberSetting("List size", 5, 1, 10, 1));
    private final BoolSetting alert = add(new BoolSetting("Chat alert", true));

    private List<Result> results = List.of();
    private final Set<Long> alerted = new HashSet<>();
    private int cooldown;

    public SusChunkFinderModule() {
        super("Sus Chunk Finder", "Lists chunks packed with storage blocks / spawners.", Category.ESP);
    }

    @Override
    public void onDisable() {
        results = List.of();
        alerted.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (--cooldown > 0) return;
        cooldown = 40;

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        int cx = cam.getBlockX() >> 4, cz = cam.getBlockZ() >> 4, r = range.intValue();

        List<Result> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                int score = 0;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof MobSpawnerBlockEntity) score += 3;
                    else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                            || be instanceof ShulkerBoxBlockEntity || be instanceof HopperBlockEntity) score++;
                }
                if (score >= minScore.intValue()) found.add(new Result(cx + dx, cz + dz, score));
            }
        }
        found.sort(Comparator.comparingInt(Result::score).reversed());

        for (Result res : found) {
            long key = (res.cx() & 0xFFFFFFFFL) | ((res.cz() & 0xFFFFFFFFL) << 32);
            if (alerted.add(key) && alert.get()) {
                mc.player.sendMessage(Text.literal("[Pulse] Sus chunk at " + (res.cx() * 16 + 8) + ", " + (res.cz() * 16 + 8)
                        + " (score " + res.score() + ")"), false);
            }
        }
        results = found;
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (results.isEmpty() || mc.options.hudHidden) return;
        int screenW = mc.getWindow().getScaledWidth();
        int y = 80;

        String title = "Sus chunks";
        ctx.drawTextWithShadow(mc.textRenderer, title, screenW - 4 - mc.textRenderer.getWidth(title), y, Theme.accent());
        y += 10;
        for (int i = 0; i < Math.min(listSize.intValue(), results.size()); i++) {
            Result res = results.get(i);
            String line = (res.cx() * 16 + 8) + ", " + (res.cz() * 16 + 8) + "  [" + res.score() + "]";
            ctx.drawTextWithShadow(mc.textRenderer, line, screenW - 4 - mc.textRenderer.getWidth(line), y, 0xFFFFFFFF);
            y += 10;
        }
    }
}
