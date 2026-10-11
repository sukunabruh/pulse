package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Storage ESP: boxes every chest, barrel, shulker box, hopper etc. in loaded chunks, coloured by
 * type. Uses the chunk's block-entity list (cheap), so it reaches your whole render distance.
 */
public class StorageEspModule extends Module {
    private record Entry(BlockPos pos, int type) {}

    private static final String[] NAMES = {"Chests", "Trapped chests", "Ender chests", "Barrels",
            "Shulkers", "Hoppers", "Dispensers/droppers", "Furnaces"};
    private static final int[] COLORS = {0xFFA500, 0xFF3030, 0xAA55FF, 0xA0713A,
            0xD66BFF, 0x8888AA, 0x66AAFF, 0xBBBBBB};
    private static final boolean[] DEFAULT_ON = {true, true, true, true, true, true, true, false};

    private final BoolSetting[] toggles = new BoolSetting[NAMES.length];
    private final NumberSetting range = add(new NumberSetting("Range (chunks)", 8, 1, 16, 1));
    private final NumberSetting fill = add(new NumberSetting("Fill %", 20, 0, 100, 5));
    private final NumberSetting maxBoxes = add(new NumberSetting("Max boxes", 400, 50, 2000, 50));
    private final BoolSetting labels = add(new BoolSetting("Labels", false));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));
    private final BoolSetting counts = add(new BoolSetting("Show counts", true));

    private List<Entry> entries = List.of();
    private final int[] total = new int[NAMES.length];
    private int cooldown;

    public StorageEspModule() {
        super("Storage ESP", "Highlights chests, barrels, shulkers, hoppers... through walls.", Category.ESP);
        for (int i = 0; i < NAMES.length; i++) toggles[i] = add(new BoolSetting(NAMES[i], DEFAULT_ON[i]));
    }

    @Override
    public void onDisable() {
        entries = List.of();
    }

    private static int classify(Block b) {
        if (b == Blocks.CHEST) return 0;
        if (b == Blocks.TRAPPED_CHEST) return 1;
        if (b == Blocks.ENDER_CHEST) return 2;
        if (b == Blocks.BARREL) return 3;
        if (b instanceof ShulkerBoxBlock) return 4;
        if (b == Blocks.HOPPER) return 5;
        if (b == Blocks.DISPENSER || b == Blocks.DROPPER) return 6;
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return 7;
        return -1;
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;
        if (--cooldown > 0) return;
        cooldown = 20;
        Entity cam = mc.getCameraEntity();
        if (cam == null) return;

        int cx = cam.getBlockX() >> 4, cz = cam.getBlockZ() >> 4, r = range.intValue();
        List<Entry> found = new ArrayList<>();
        int[] counted = new int[NAMES.length];
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    int type = classify(be.getCachedState().getBlock());
                    if (type < 0 || !toggles[type].get()) continue;
                    found.add(new Entry(be.getPos().toImmutable(), type));
                    counted[type]++;
                }
            }
        }
        entries = found;
        System.arraycopy(counted, 0, total, 0, total.length);
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (mc.options.hudHidden) return;

        if (counts.get()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < NAMES.length; i++) {
                if (total[i] > 0) sb.append(sb.length() > 0 ? "  " : "").append(NAMES[i]).append(' ').append(total[i]);
            }
            if (sb.length() > 0) {
                String s = sb.toString();
                ctx.drawTextWithShadow(mc.textRenderer, s, mc.getWindow().getScaledWidth() - 4 - mc.textRenderer.getWidth(s), 230, 0xFFFFA500);
            }
        }

        if (entries.isEmpty()) return;
        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;

        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingDouble(e -> distSq(esp, e.pos())));
        int limit = Math.min(sorted.size(), maxBoxes.intValue());
        int alpha = (int) Math.round(fill.get() / 100.0 * 255.0);

        for (int i = 0; i < limit; i++) {
            Entry e = sorted.get(i);
            BlockPos p = e.pos();
            int[] rect = esp.project(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            if (rect == null) continue;
            int rgb = COLORS[e.type()];
            EspRender.drawBox(ctx, rect, rgb, alpha);
            if (tracers.get() && i < 60) {
                EspRender.drawLine(ctx, esp.width() / 2, esp.height(), (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFF000000 | rgb);
            }
            if (labels.get() && i < 80) {
                String text = NAMES[e.type()] + " " + (int) Math.sqrt(distSq(esp, p)) + "m";
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(text), (rect[0] + rect[2]) / 2, rect[1] - 10, 0xFF000000 | rgb);
            }
        }
    }

    private static double distSq(EspRender esp, BlockPos p) {
        return esp.eye().squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }
}
