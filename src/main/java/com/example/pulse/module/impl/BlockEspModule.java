package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BlockListSetting;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import com.example.pulse.setting.StringSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Block ESP drawn as a 2D screen-space overlay (boxes are projected from world to screen).
 * Because it is just a HUD overlay, it shows through walls and is independent of the
 * 1.21.x world-render pipeline changes. True 3D wireframes can be added later.
 *
 * Scanning is incremental (a few chunks per tick, rescanned every ~2 s) and results are cached,
 * so there is no per-frame world iteration.
 */
public class BlockEspModule extends Module {
    private record Group(String label, BoolSetting toggle, int color, String[] ids) {}
    private record Info(int color, String name) {}
    private record Hit(BlockPos pos, int color, String name) {}

    private static final int MAX_HITS_PER_CHUNK = 1500;

    private final List<Group> groups = new ArrayList<>();
    private final NumberSetting range;
    private final NumberSetting fillOpacity;
    private final NumberSetting maxBoxes;
    private final BoolSetting labels;
    private final BoolSetting tracers;
    private final StringSetting custom;
    private final NumberSetting customHue;

    private final Map<Block, Info> wanted = new HashMap<>();
    private final Map<Long, List<Hit>> results = new HashMap<>();
    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private String signature = "";
    private int cooldown;

    public BlockEspModule() {
        super("Block ESP", "Highlights chosen blocks through walls. Customize types, range and colors.", Category.ESP, GLFW.GLFW_KEY_J);

        custom = add(new BlockListSetting("Pick blocks", ""));
        customHue = add(new NumberSetting("Pick color", 120, 0, 360, 5));

        group("Diamond", true, 0x00E5FF, "diamond_ore", "deepslate_diamond_ore");
        group("Debris", true, 0xC8702A, "ancient_debris");
        group("Chests", true, 0xFFA500, "chest", "trapped_chest", "ender_chest", "barrel");
        group("Spawners", true, 0xFF3030, "spawner");
        group("Emerald", false, 0x00E64D, "emerald_ore", "deepslate_emerald_ore");
        group("Gold", false, 0xFFD700, "gold_ore", "deepslate_gold_ore", "nether_gold_ore");
        group("Iron", false, 0xD8AF93, "iron_ore", "deepslate_iron_ore");
        group("Redstone", false, 0xFF2020, "redstone_ore", "deepslate_redstone_ore");
        group("Lapis", false, 0x2040FF, "lapis_ore", "deepslate_lapis_ore");
        group("Copper", false, 0xE07B39, "copper_ore", "deepslate_copper_ore");
        group("Coal", false, 0x9E9E9E, "coal_ore", "deepslate_coal_ore");
        group("Quartz", false, 0xEEEEEE, "nether_quartz_ore");
        group("Shulkers", false, 0xD66BFF, BlockSets.coloredWithBase("shulker_box"));
        group("Hoppers", false, 0x8888AA, "hopper");
        group("Utility", false, 0xFFFFFF, "crafting_table", "furnace", "blast_furnace", "smoker", "anvil",
                "enchanting_table", "brewing_stand", "beacon", "jukebox", "lectern", "bell");
        group("Beds", false, 0xFF6EB4, BlockSets.colored("bed"));
        group("Signs", false, 0xC8A165, BlockSets.signs());

        range = add(new NumberSetting("Range", 4, 1, 10, 1));
        fillOpacity = add(new NumberSetting("Fill %", 25, 0, 100, 5));
        maxBoxes = add(new NumberSetting("Max boxes", 200, 20, 1000, 20));
        labels = add(new BoolSetting("Labels", true));
        tracers = add(new BoolSetting("Tracers", false));
    }

    private void group(String label, boolean on, int color, String... ids) {
        groups.add(new Group(label, add(new BoolSetting(label, on)), color, ids));
    }

    @Override
    public void onDisable() {
        results.clear();
        queue.clear();
    }

    // ---------------------------------------------------------------- scanning

    @Override
    public void onTick() {
        if (mc.world == null) return;
        Entity cam = mc.getCameraEntity();
        if (cam == null) return;

        String sig = buildSignature();
        if (!sig.equals(signature)) {
            signature = sig;
            rebuildWanted();
            results.clear();
            queue.clear();
            cooldown = 0;
        }
        if (wanted.isEmpty()) return;

        int cx = cam.getBlockX() >> 4, cz = cam.getBlockZ() >> 4;
        int r = range.intValue();

        if (queue.isEmpty() && --cooldown <= 0) {
            cooldown = 40;
            List<int[]> list = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) list.add(new int[]{cx + dx, cz + dz});
            }
            list.sort(Comparator.comparingInt(p -> (p[0] - cx) * (p[0] - cx) + (p[1] - cz) * (p[1] - cz)));
            queue.addAll(list);
        }

        for (int n = 0; n < 3 && !queue.isEmpty(); n++) {
            int[] p = queue.poll();
            scanChunk(p[0], p[1]);
        }

        results.keySet().removeIf(k -> Math.abs((int) (long) k - cx) > r + 1 || Math.abs((int) (k >>> 32) - cz) > r + 1);
    }

    private static long key(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
    }

    private boolean isWanted(BlockState state) {
        return wanted.containsKey(state.getBlock());
    }

    private void scanChunk(int cx, int cz) {
        long k = key(cx, cz);
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) {
            results.remove(k);
            return;
        }
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = mc.world.getBottomSectionCoord();
        List<Hit> hits = new ArrayList<>();

        outer:
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(this::isWanted)) continue;
            int baseY = (bottom + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Info info = wanted.get(sec.getBlockState(x, y, z).getBlock());
                        if (info == null) continue;
                        hits.add(new Hit(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z), info.color(), info.name()));
                        if (hits.size() >= MAX_HITS_PER_CHUNK) break outer;
                    }
                }
            }
        }
        if (hits.isEmpty()) results.remove(k); else results.put(k, hits);
    }

    private String buildSignature() {
        StringBuilder sb = new StringBuilder();
        for (Group g : groups) sb.append(g.toggle().get() ? '1' : '0');
        return sb.append('|').append(custom.get()).append('|').append(customHue.intValue()).toString();
    }

    private void rebuildWanted() {
        wanted.clear();
        for (Group g : groups) {
            if (!g.toggle().get()) continue;
            for (String id : g.ids()) addBlock(id, g.color());
        }
        int customColor = java.awt.Color.HSBtoRGB(customHue.intValue() / 360f, 0.9f, 1f) & 0xFFFFFF;
        for (String raw : custom.get().split(",")) {
            String id = raw.trim();
            if (!id.isEmpty()) addBlock(id, customColor);
        }
    }

    private void addBlock(String id, int color) {
        Identifier ident = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (ident == null || !Registries.BLOCK.containsId(ident)) return;
        Block block = Registries.BLOCK.get(ident);
        wanted.put(block, new Info(color, block.getName().getString()));
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (results.isEmpty() || mc.world == null || mc.options.hudHidden) return;
        if (!mc.options.getPerspective().isFirstPerson()) return; // projection assumes first person
        Entity cam = mc.getCameraEntity();
        if (cam == null) return;

        Vec3d eye = cam.getLerpedPos(tickProgress).add(0, cam.getEyeY() - cam.getY(), 0);
        double yr = Math.toRadians(cam.getYaw(tickProgress));
        double pr = Math.toRadians(cam.getPitch(tickProgress));

        // camera basis (Minecraft: yaw 0 faces +Z, right is -X at yaw 0)
        Vec3d f = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3d r = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
        Vec3d u = new Vec3d(r.y * f.z - r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y - r.y * f.x);

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double sx = (w / 2.0) / (tanHalf * ((double) w / h));
        double sy = (h / 2.0) / tanHalf;

        // collect nearest hits first
        List<Hit> all = new ArrayList<>();
        for (List<Hit> list : results.values()) all.addAll(list);
        all.sort(Comparator.comparingDouble(hit -> distSq(eye, hit.pos())));
        int limit = Math.min(all.size(), maxBoxes.intValue());

        int alpha = (int) Math.round(fillOpacity.get() / 100.0 * 255.0);
        for (int i = 0; i < limit; i++) {
            Hit hit = all.get(i);
            BlockPos bp = hit.pos();
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            int behind = 0;

            for (int c = 0; c < 8; c++) {
                double dx = bp.getX() + (c & 1) - eye.x;
                double dy = bp.getY() + ((c >> 1) & 1) - eye.y;
                double dz = bp.getZ() + ((c >> 2) & 1) - eye.z;
                double zc = dx * f.x + dy * f.y + dz * f.z;
                double xc = dx * r.x + dy * r.y + dz * r.z;
                double yc = dx * u.x + dy * u.y + dz * u.z;
                if (zc < 0.05) {
                    behind++;
                    zc = 0.05;
                }
                double px = Math.max(-3000, Math.min(3000, w / 2.0 + xc / zc * sx));
                double py = Math.max(-3000, Math.min(3000, h / 2.0 - yc / zc * sy));
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
            }
            if (behind == 8 || maxX < 0 || minX > w || maxY < 0 || minY > h) continue;

            int x1 = (int) Math.floor(minX), y1 = (int) Math.floor(minY);
            int x2 = (int) Math.ceil(maxX), y2 = (int) Math.ceil(maxY);
            if (x2 - x1 < 3) { int cx = (x1 + x2) / 2; x1 = cx - 1; x2 = cx + 2; }
            if (y2 - y1 < 3) { int cy = (y1 + y2) / 2; y1 = cy - 1; y2 = cy + 2; }

            int rgb = hit.color() & 0xFFFFFF;
            if (alpha > 0) ctx.fill(x1, y1, x2, y2, (alpha << 24) | rgb);
            int line = 0xFF000000 | rgb;
            ctx.fill(x1, y1, x2, y1 + 1, line);
            ctx.fill(x1, y2 - 1, x2, y2, line);
            ctx.fill(x1, y1, x1 + 1, y2, line);
            ctx.fill(x2 - 1, y1, x2, y2, line);

            if (tracers.get() && i < 60) {
                EspRender.drawLine(ctx, w / 2, h, (x1 + x2) / 2, (y1 + y2) / 2, line);
            }

            if (labels.get() && i < 80) {
                String text = hit.name() + " " + (int) Math.sqrt(distSq(eye, bp)) + "m";
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(text), (x1 + x2) / 2, y1 - 10, line);
            }
        }
    }

    private static double distSq(Vec3d eye, BlockPos p) {
        double dx = p.getX() + 0.5 - eye.x, dy = p.getY() + 0.5 - eye.y, dz = p.getZ() + 0.5 - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
