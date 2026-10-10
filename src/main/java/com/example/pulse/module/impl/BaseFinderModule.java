package com.example.pulse.module.impl;

import com.example.pulse.gui.Theme;
import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Scores loaded chunks by how many player-made blocks they contain (redstone parts, glass,
 * concrete, torches, crafting stations, signs, storage...). Chunks above "Min score" are listed
 * and boxed. It is a heuristic: villages, mineshafts and other generated structures can also score.
 * Scanning is incremental (2 chunks per tick) and cached - no per-frame world iteration.
 */
public class BaseFinderModule extends Module {
    private record Result(int cx, int cz, int score, int minY, int maxY) {}

    private final NumberSetting range = add(new NumberSetting("Range", 8, 2, 16, 1));
    private final NumberSetting minScore = add(new NumberSetting("Min score", 15, 4, 200, 1));
    private final NumberSetting listSize = add(new NumberSetting("List size", 5, 1, 10, 1));
    private final BoolSetting boxes = add(new BoolSetting("Chunk boxes", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));
    private final BoolSetting alert = add(new BoolSetting("Chat alert", true));

    private final Map<Block, Integer> weights = new HashMap<>();
    private final Map<Long, Result> results = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();
    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private List<Result> sorted = List.of();
    private int cooldown;
    private int lastMin = -1;

    public BaseFinderModule() {
        super("Base Finder", "Scores chunks by player-made blocks to point you at bases.", Category.ESP);
    }

    @Override
    public void onDisable() {
        results.clear();
        alerted.clear();
        queue.clear();
        sorted = List.of();
    }

    private void buildWeights() {
        // strong signals (3): things that almost only exist because a player built them
        for (String id : new String[]{"hopper", "dropper", "dispenser", "observer", "piston", "sticky_piston",
                "repeater", "comparator", "redstone_lamp", "tnt", "beacon", "end_rod", "scaffolding",
                "enchanting_table", "brewing_stand", "conduit"}) put(id, 3);
        for (String id : BlockSets.coloredWithBase("shulker_box")) put(id, 3);
        for (String id : BlockSets.colored("concrete")) put(id, 3);
        for (String id : BlockSets.colored("concrete_powder")) put(id, 3);
        for (String id : BlockSets.colored("glazed_terracotta")) put(id, 3);
        for (String id : BlockSets.colored("stained_glass")) put(id, 3);
        for (String id : BlockSets.colored("stained_glass_pane")) put(id, 2);
        for (String id : BlockSets.signs()) put(id, 2);
        // weak signals (1): common in builds but also in some generated structures
        for (String id : new String[]{"torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch",
                "redstone_wall_torch", "lantern", "soul_lantern", "crafting_table", "furnace", "blast_furnace",
                "smoker", "anvil", "chipped_anvil", "damaged_anvil", "glass", "glass_pane", "bookshelf", "cauldron"}) put(id, 1);
    }

    private void put(String id, int weight) {
        Identifier ident = Identifier.tryParse("minecraft:" + id);
        if (ident != null && Registries.BLOCK.containsId(ident)) weights.put(Registries.BLOCK.get(ident), weight);
    }

    private boolean weighted(BlockState state) {
        return weights.containsKey(state.getBlock());
    }

    private static long key(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (weights.isEmpty()) buildWeights();

        if (minScore.intValue() != lastMin) { // threshold changed: rescan everything
            lastMin = minScore.intValue();
            results.clear();
            queue.clear();
            cooldown = 0;
        }

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        int cx = cam.getBlockX() >> 4, cz = cam.getBlockZ() >> 4, r = range.intValue();

        if (queue.isEmpty() && --cooldown <= 0) {
            cooldown = 100;
            List<int[]> list = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) list.add(new int[]{cx + dx, cz + dz});
            }
            list.sort(Comparator.comparingInt(p -> (p[0] - cx) * (p[0] - cx) + (p[1] - cz) * (p[1] - cz)));
            queue.addAll(list);
        }
        for (int n = 0; n < 2 && !queue.isEmpty(); n++) {
            int[] p = queue.poll();
            scanChunk(p[0], p[1]);
        }

        results.keySet().removeIf(k -> Math.abs((int) (long) k - cx) > r + 1 || Math.abs((int) (k >>> 32) - cz) > r + 1);
        sorted = results.values().stream().sorted(Comparator.comparingInt(Result::score).reversed()).toList();
    }

    private void scanChunk(int cx, int cz) {
        long k = key(cx, cz);
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) {
            results.remove(k);
            return;
        }
        int score = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = mc.world.getBottomSectionCoord();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(this::weighted)) continue;
            int baseY = (bottom + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Integer w = weights.get(sec.getBlockState(x, y, z).getBlock());
                        if (w == null) continue;
                        score += w;
                        minY = Math.min(minY, baseY + y);
                        maxY = Math.max(maxY, baseY + y);
                    }
                }
            }
        }
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                    || be instanceof ShulkerBoxBlockEntity || be instanceof HopperBlockEntity) {
                score++;
                minY = Math.min(minY, be.getPos().getY());
                maxY = Math.max(maxY, be.getPos().getY());
            }
        }

        if (score >= minScore.intValue() && minY != Integer.MAX_VALUE) {
            results.put(k, new Result(cx, cz, score, minY, maxY));
            if (alerted.add(k) && alert.get()) {
                mc.player.sendMessage(Text.literal("[Pulse] Possible base near " + (cx * 16 + 8) + ", " + (cz * 16 + 8)
                        + " (score " + score + ")"), false);
            }
        } else {
            results.remove(k);
        }
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (sorted.isEmpty() || mc.options.hudHidden) return;
        int screenW = mc.getWindow().getScaledWidth();
        int y = 150;

        String title = "Possible bases";
        ctx.drawTextWithShadow(mc.textRenderer, title, screenW - 4 - mc.textRenderer.getWidth(title), y, Theme.accent());
        y += 10;
        for (int i = 0; i < Math.min(listSize.intValue(), sorted.size()); i++) {
            Result res = sorted.get(i);
            String line = (res.cx() * 16 + 8) + ", " + (res.cz() * 16 + 8) + "  [" + res.score() + "]";
            ctx.drawTextWithShadow(mc.textRenderer, line, screenW - 4 - mc.textRenderer.getWidth(line), y, 0xFFFFFFFF);
            y += 10;
        }

        if (!boxes.get() && !tracers.get()) return;
        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;
        for (int i = 0; i < Math.min(8, sorted.size()); i++) {
            Result res = sorted.get(i);
            int[] rect = esp.project(res.cx() * 16, res.minY(), res.cz() * 16,
                    res.cx() * 16 + 16, res.maxY() + 1, res.cz() * 16 + 16);
            if (rect == null) continue;
            if (boxes.get()) {
                EspRender.drawBox(ctx, rect, 0xFFD166, 0);
                double d = Math.sqrt(esp.eye().squaredDistanceTo(res.cx() * 16 + 8, (res.minY() + res.maxY()) / 2.0, res.cz() * 16 + 8));
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Base? " + res.score() + "  " + (int) d + "m"),
                        (rect[0] + rect[2]) / 2, Math.max(2, rect[1] - 10), 0xFFFFD166);
            }
            if (tracers.get()) {
                EspRender.drawLine(ctx, esp.width() / 2, esp.height(), (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFFFFD166);
            }
        }
    }
}
