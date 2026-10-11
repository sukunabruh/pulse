package com.example.pulse.module.impl;

import com.example.pulse.gui.Theme;
import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Base Finder v2.
 *
 * Every loaded chunk is scored by the player-made blocks in it (redstone, storage, lights,
 * building blocks, crafting stations, signs...) plus item frames / armor stands. A chunk that
 * touches several different categories scores higher (bases are varied, natural structures
 * aren't), and chunks full of natural-structure blocks (strongholds, villages, monuments,
 * ancient cities, trial chambers...) are discounted.
 *
 * Flagged chunks are remembered after they unload and saved per server and dimension in
 * config/pulse-bases.json, so you can fly around and build a map of bases. Neighbouring flagged
 * chunks are merged into base clusters, which are listed, boxed, pointed at with edge arrows,
 * announced in chat and (optionally) saved as waypoints.
 *
 * It is a heuristic: it can still flag big generated structures, and it only sees chunks the
 * server has sent you (your render distance). Commands: /pulse base list | clear | ignore | wp
 */
public class BaseFinderModule extends Module {
    private static final Logger LOG = LoggerFactory.getLogger("pulse");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int STORE_MIN = 6;
    private static final int LINK = 2; // chunks within this Chebyshev distance join one cluster
    private static final int CAT_STORAGE = 1, CAT_REDSTONE = 2, CAT_LIGHT = 4, CAT_BUILD = 8, CAT_UTILITY = 16, CAT_DECOR = 32;
    private static final String[] COMPASS = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};

    private record Info(int weight, int cat) {}
    private record ChunkRec(int score, int minY, int maxY, int cats, int ent) {
        int eff() { return score + ent; }
    }
    public record Cluster(double x, double z, int score, int chunks, int minY, int maxY,
                          int minCx, int maxCx, int minCz, int maxCz, int bestCx, int bestCz, List<Long> members) {}
    private static final class Dim {
        final Map<Long, ChunkRec> chunks = new HashMap<>();
        final Set<Long> ignored = new HashSet<>();
    }

    // ---- settings
    private final NumberSetting range = add(new NumberSetting("Range (chunks)", 12, 2, 32, 1));
    private final NumberSetting minScore = add(new NumberSetting("Chunk min score", 15, 4, 200, 1));
    private final NumberSetting clusterMin = add(new NumberSetting("Base min score", 40, 5, 500, 5));
    private final NumberSetting listSize = add(new NumberSetting("List size", 5, 1, 10, 1));
    private final BoolSetting sortByDistance = add(new BoolSetting("Sort by distance", false));
    private final BoolSetting boxes = add(new BoolSetting("Boxes", true));
    private final BoolSetting arrows = add(new BoolSetting("Edge arrows", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));
    private final BoolSetting alert = add(new BoolSetting("Chat alert", true));
    private final BoolSetting autoWaypoint = add(new BoolSetting("Auto waypoint", true));
    private final BoolSetting saveToDisk = add(new BoolSetting("Remember bases", true));

    private final WaypointsModule waypoints;
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("pulse-bases.json");

    private final Map<Block, Info> weights = new HashMap<>();
    private final Set<Block> natural = new HashSet<>();
    private final Map<String, Dim> all = new HashMap<>();
    private final Map<Long, Integer> lastScan = new HashMap<>();
    private final ArrayDeque<int[]> queue = new ArrayDeque<>();
    private final List<int[]> alertedCenters = new ArrayList<>();

    private Dim cur = new Dim();
    private String storeKey = "";
    private List<Cluster> clusters = List.of();
    private boolean seed, dirty;
    private int age;

    public BaseFinderModule(WaypointsModule waypoints) {
        super("Base Finder", "Scores chunks for player-made blocks, remembers them and points at bases.", Category.ESP);
        this.waypoints = waypoints;
        loadFile();
    }

    // ------------------------------------------------------------------ weights

    private void buildWeights() {
        // storage
        put(2, CAT_STORAGE, "chest", "trapped_chest", "barrel");
        put(4, CAT_STORAGE, "ender_chest");
        put(3, CAT_STORAGE, BlockSets.coloredWithBase("shulker_box"));
        put(3, CAT_STORAGE, "hopper");
        put(3, CAT_STORAGE | CAT_REDSTONE, "dropper", "dispenser");
        // redstone / contraptions
        put(3, CAT_REDSTONE, "observer", "piston", "sticky_piston", "repeater", "comparator", "redstone_lamp",
                "tnt", "target", "slime_block", "honey_block", "crafter");
        put(2, CAT_REDSTONE, "daylight_detector", "note_block", "redstone_block", "tripwire_hook", "powered_rail",
                "detector_rail", "activator_rail", "lightning_rod", "iron_door", "iron_trapdoor");
        put(1, CAT_REDSTONE, "redstone_wire", "lever");
        // lighting
        put(1, CAT_LIGHT, "torch", "wall_torch", "soul_torch", "soul_wall_torch", "lantern", "soul_lantern",
                "glowstone", "campfire", "soul_campfire");
        put(2, CAT_LIGHT, "redstone_torch", "redstone_wall_torch", "jack_o_lantern", "candle");
        put(2, CAT_LIGHT, BlockSets.colored("candle"));
        put(3, CAT_LIGHT, "end_rod", "ochre_froglight", "verdant_froglight", "pearlescent_froglight");
        // building blocks
        put(3, CAT_BUILD, BlockSets.colored("concrete"));
        put(3, CAT_BUILD, BlockSets.colored("concrete_powder"));
        put(3, CAT_BUILD, BlockSets.colored("glazed_terracotta"));
        put(3, CAT_BUILD, BlockSets.colored("stained_glass"));
        put(2, CAT_BUILD, BlockSets.colored("stained_glass_pane"));
        put(2, CAT_BUILD, BlockSets.colored("wool"));
        put(2, CAT_BUILD, "glass", "smooth_stone", "bricks", "coal_block", "copper_block", "cut_copper",
                "polished_andesite", "polished_diorite", "polished_granite", "ladder", "bone_block", "crying_obsidian");
        put(1, CAT_BUILD, "glass_pane", "obsidian");
        put(1, CAT_BUILD, BlockSets.woodsWith("planks"));
        for (String w : BlockSets.WOODS) {
            put(3, CAT_BUILD, "stripped_" + w + "_log", "stripped_" + w + "_wood", "stripped_" + w + "_stem",
                    "stripped_" + w + "_hyphae", "stripped_" + w + "_block");
        }
        put(3, CAT_BUILD, "quartz_block", "quartz_bricks", "quartz_pillar", "smooth_quartz", "chiseled_quartz_block",
                "iron_block", "gold_block", "lapis_block", "scaffolding");
        put(4, CAT_BUILD, "diamond_block", "emerald_block");
        put(5, CAT_BUILD, "netherite_block");
        // crafting stations / utility
        put(1, CAT_UTILITY, "crafting_table", "furnace", "cauldron", "grindstone", "loom", "cartography_table",
                "stonecutter", "fletching_table", "lectern", "bookshelf", "composter");
        put(2, CAT_UTILITY, "blast_furnace", "smoker", "anvil", "chipped_anvil", "damaged_anvil", "smithing_table",
                "chiseled_bookshelf", "jukebox");
        put(2, CAT_UTILITY, BlockSets.colored("bed"));
        put(3, CAT_UTILITY, "enchanting_table", "brewing_stand");
        put(4, CAT_UTILITY, "conduit", "respawn_anchor");
        put(5, CAT_UTILITY, "beacon");
        // decoration
        put(2, CAT_DECOR, BlockSets.signs());
        put(2, CAT_DECOR, BlockSets.colored("banner"));
        put(2, CAT_DECOR, BlockSets.colored("wall_banner"));
        put(1, CAT_DECOR, "flower_pot");
        put(2, CAT_DECOR, "decorated_pot");

        // blocks that mark generated structures (used to discount a chunk, not to score it)
        for (String id : new String[]{"end_portal_frame", "bell", "hay_block", "sculk", "sculk_catalyst",
                "sculk_shrieker", "sculk_sensor", "reinforced_deepslate", "prismarine", "prismarine_bricks",
                "dark_prismarine", "sea_lantern", "nether_bricks", "nether_brick_fence", "cobweb", "spawner",
                "trial_spawner", "vault", "suspicious_sand", "suspicious_gravel", "purpur_block", "purpur_pillar",
                "end_stone_bricks", "gilded_blackstone", "chiseled_polished_blackstone", "polished_blackstone_bricks",
                "mossy_cobblestone", "mossy_stone_bricks", "amethyst_block", "budding_amethyst", "tuff_bricks",
                "chiseled_tuff", "chiseled_tuff_bricks", "polished_tuff", "copper_grate", "waxed_copper_grate",
                "waxed_oxidized_copper_grate", "waxed_weathered_copper_grate", "waxed_exposed_copper_grate",
                "heavy_core"}) {
            Block b = block(id);
            if (b != null && !weights.containsKey(b)) natural.add(b);
        }
    }

    private static Block block(String id) {
        Identifier ident = Identifier.tryParse("minecraft:" + id);
        return (ident != null && Registries.BLOCK.containsId(ident)) ? Registries.BLOCK.get(ident) : null;
    }

    private void put(int weight, int cat, String... ids) {
        for (String id : ids) {
            Block b = block(id);
            if (b != null) weights.put(b, new Info(weight, cat));
        }
    }

    private boolean interesting(BlockState state) {
        Block b = state.getBlock();
        return weights.containsKey(b) || natural.contains(b);
    }

    // ------------------------------------------------------------------ helpers

    private static long key(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((z & 0xFFFFFFFFL) << 32);
    }
    private static int kx(long k) { return (int) k; }
    private static int kz(long k) { return (int) (k >>> 32); }

    private String currentKey() {
        var s = mc.getCurrentServerEntry();
        String server = s != null ? s.address : "singleplayer";
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    private void ensureStore() {
        String k = currentKey();
        if (k.equals(storeKey)) return;
        if (!storeKey.isEmpty()) save();
        storeKey = k;
        cur = all.computeIfAbsent(k, x -> new Dim());
        lastScan.clear();
        queue.clear();
        alertedCenters.clear();
        clusters = List.of();
        seed = true; // clusters already on file are not announced again
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void onDisable() {
        queue.clear();
        save();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        age++;
        if (weights.isEmpty()) buildWeights();
        ensureStore();

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        int pcx = cam.getBlockX() >> 4, pcz = cam.getBlockZ() >> 4, r = range.intValue();

        if (seed) {
            rebuildClusters();
            for (Cluster c : clusters) alertedCenters.add(new int[]{c.bestCx(), c.bestCz()});
            seed = false;
        }

        if (age % 10 == 0) refreshQueue(pcx, pcz, r);
        long end = System.nanoTime() + 2_000_000L; // at most ~2 ms of scanning per tick
        while (!queue.isEmpty() && System.nanoTime() < end) {
            int[] p = queue.poll();
            scanChunk(p[0], p[1]);
        }
        if (age % 40 == 0) scanEntities();
        if (age % 20 == 0) {
            rebuildClusters();
            announce();
        }
        if (dirty && age % 600 == 0) save();
    }

    private void refreshQueue(int pcx, int pcz, int r) {
        if (!queue.isEmpty()) return;
        List<int[]> list = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx, cz = pcz + dz;
                Integer last = lastScan.get(key(cx, cz));
                int staleAfter = (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) ? 200 : 2400;
                if (last != null && age - last < staleAfter) continue;
                if (mc.world.getChunkManager().getWorldChunk(cx, cz) == null) continue;
                list.add(new int[]{cx, cz});
            }
        }
        list.sort(Comparator.comparingInt(p -> (p[0] - pcx) * (p[0] - pcx) + (p[1] - pcz) * (p[1] - pcz)));
        queue.addAll(list);
    }

    private void scanChunk(int cx, int cz) {
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) return;
        long k = key(cx, cz);
        lastScan.put(k, age);
        if (cur.ignored.contains(k)) return;

        int raw = 0, nat = 0, cats = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = mc.world.getBottomSectionCoord();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(this::interesting)) continue;
            int baseY = (bottom + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block b = sec.getBlockState(x, y, z).getBlock();
                        Info inf = weights.get(b);
                        if (inf != null) {
                            raw += inf.weight();
                            cats |= inf.cat();
                            minY = Math.min(minY, baseY + y);
                            maxY = Math.max(maxY, baseY + y);
                        } else if (natural.contains(b)) {
                            nat++;
                        }
                    }
                }
            }
        }

        double mult = 1.0 + 0.15 * Math.max(0, Integer.bitCount(cats) - 1); // variety bonus
        double structure = nat >= 6 ? 0.35 : 1.0;                            // generated structure discount
        int score = (int) Math.round(raw * mult * structure);

        ChunkRec old = cur.chunks.get(k);
        if (score >= STORE_MIN && minY != Integer.MAX_VALUE) {
            cur.chunks.put(k, new ChunkRec(score, minY, maxY, cats, old == null ? 0 : old.ent()));
            dirty = true;
        } else if (old != null) {
            cur.chunks.remove(k);
            dirty = true;
        }
    }

    private void scanEntities() {
        Map<Long, int[]> bonus = new HashMap<>(); // chunk -> {score, minY, maxY}
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemFrameEntity) && !(e instanceof ArmorStandEntity)) continue;
            long k = key(e.getBlockX() >> 4, e.getBlockZ() >> 4);
            int[] b = bonus.computeIfAbsent(k, x -> new int[]{0, Integer.MAX_VALUE, Integer.MIN_VALUE});
            b[0] += 2;
            b[1] = Math.min(b[1], e.getBlockY());
            b[2] = Math.max(b[2], e.getBlockY());
        }
        for (Map.Entry<Long, int[]> en : bonus.entrySet()) {
            long k = en.getKey();
            if (cur.ignored.contains(k)) continue;
            int[] b = en.getValue();
            ChunkRec old = cur.chunks.get(k);
            if (old != null) {
                int ent = Math.max(old.ent(), b[0]);
                if (ent != old.ent()) {
                    cur.chunks.put(k, new ChunkRec(old.score(), Math.min(old.minY(), b[1]), Math.max(old.maxY(), b[2]),
                            old.cats() | CAT_DECOR, ent));
                    dirty = true;
                }
            } else if (b[0] >= STORE_MIN) {
                cur.chunks.put(k, new ChunkRec(0, b[1], b[2], CAT_DECOR, b[0]));
                dirty = true;
            }
        }
    }

    // ------------------------------------------------------------------ clusters

    private void rebuildClusters() {
        int minS = minScore.intValue();
        List<Long> flagged = new ArrayList<>();
        for (Map.Entry<Long, ChunkRec> e : cur.chunks.entrySet()) {
            if (!cur.ignored.contains(e.getKey()) && e.getValue().eff() >= minS) flagged.add(e.getKey());
        }
        int n = flagged.size();
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(flagged.get(i), i);
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            int cx = kx(flagged.get(i)), cz = kz(flagged.get(i));
            for (int dx = -LINK; dx <= LINK; dx++) {
                for (int dz = -LINK; dz <= LINK; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Integer j = index.get(key(cx + dx, cz + dz));
                    if (j != null) union(parent, i, j);
                }
            }
        }

        Map<Integer, List<Long>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent, i), x -> new ArrayList<>()).add(flagged.get(i));

        List<Cluster> out = new ArrayList<>();
        int need = clusterMin.intValue();
        for (List<Long> members : groups.values()) {
            int sum = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            int minCx = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE, minCz = Integer.MAX_VALUE, maxCz = Integer.MIN_VALUE;
            int bestEff = -1, bestCx = 0, bestCz = 0;
            double wx = 0, wz = 0;
            for (long k : members) {
                ChunkRec rec = cur.chunks.get(k);
                int cx = kx(k), cz = kz(k), eff = rec.eff();
                sum += eff;
                wx += eff * (cx * 16 + 8.0);
                wz += eff * (cz * 16 + 8.0);
                minY = Math.min(minY, rec.minY());
                maxY = Math.max(maxY, rec.maxY());
                minCx = Math.min(minCx, cx);
                maxCx = Math.max(maxCx, cx);
                minCz = Math.min(minCz, cz);
                maxCz = Math.max(maxCz, cz);
                if (eff > bestEff) {
                    bestEff = eff;
                    bestCx = cx;
                    bestCz = cz;
                }
            }
            if (sum < need) continue;
            out.add(new Cluster(wx / sum, wz / sum, sum, members.size(), minY, maxY,
                    minCx, maxCx, minCz, maxCz, bestCx, bestCz, members));
        }

        Entity cam = mc.getCameraEntity();
        if (sortByDistance.get() && cam != null) {
            out.sort(Comparator.comparingDouble(c -> dist2d(cam, c)));
        } else {
            out.sort(Comparator.comparingInt(Cluster::score).reversed());
        }
        clusters = List.copyOf(out);
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) {
            p[i] = p[p[i]];
            i = p[i];
        }
        return i;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[ra] = rb;
    }

    private static double dist2d(Entity e, Cluster c) {
        double dx = c.x() - e.getX(), dz = c.z() - e.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String compass(Entity e, Cluster c) {
        double deg = Math.toDegrees(Math.atan2(c.z() - e.getZ(), c.x() - e.getX()));
        int idx = (int) Math.round(((deg + 360.0) % 360.0) / 45.0) % 8;
        return COMPASS[idx];
    }

    private void announce() {
        if (clusters.isEmpty()) return;
        for (Cluster c : clusters) {
            boolean seen = false;
            for (int[] a : alertedCenters) {
                if (Math.abs(a[0] - c.bestCx()) <= 5 && Math.abs(a[1] - c.bestCz()) <= 5) {
                    seen = true;
                    break;
                }
            }
            if (seen) continue;
            alertedCenters.add(new int[]{c.bestCx(), c.bestCz()});
            if (alert.get()) {
                mc.player.sendMessage(Text.literal("[Pulse] Possible base near " + (int) c.x() + ", " + (int) c.z()
                        + " (score " + c.score() + ", " + c.chunks() + " chunks)"), false);
            }
            if (autoWaypoint.get()) {
                waypoints.add("Base-" + c.bestCx() + "_" + c.bestCz(), (int) c.x(), (c.minY() + c.maxY()) / 2, (int) c.z());
            }
        }
    }

    // ------------------------------------------------------------------ commands API

    public List<String> describeClusters() {
        List<String> lines = new ArrayList<>();
        Entity cam = mc.getCameraEntity();
        for (Cluster c : clusters) {
            String s = "[Pulse] " + (int) c.x() + ", " + (int) c.z() + "  score " + c.score() + "  " + c.chunks() + " chunks";
            if (cam != null) s += "  " + (int) dist2d(cam, c) + "m " + compass(cam, c);
            lines.add(s);
        }
        return lines;
    }

    public int clearAll() {
        int n = cur.chunks.size();
        cur.chunks.clear();
        cur.ignored.clear();
        clusters = List.of();
        alertedCenters.clear();
        lastScan.clear();
        dirty = true;
        save();
        return n;
    }

    /** Hides the cluster nearest to you (and keeps it hidden). Returns false if there is none. */
    public boolean ignoreNearest() {
        Entity cam = mc.getCameraEntity();
        if (cam == null || clusters.isEmpty()) return false;
        Cluster best = null;
        double bd = Double.MAX_VALUE;
        for (Cluster c : clusters) {
            double d = dist2d(cam, c);
            if (d < bd) {
                bd = d;
                best = c;
            }
        }
        cur.ignored.addAll(best.members());
        for (long k : best.members()) cur.chunks.remove(k);
        dirty = true;
        rebuildClusters();
        save();
        return true;
    }

    public int saveClusterWaypoints() {
        int n = 0;
        for (Cluster c : clusters) {
            waypoints.add("Base-" + c.bestCx() + "_" + c.bestCz(), (int) c.x(), (c.minY() + c.maxY()) / 2, (int) c.z());
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ persistence

    private void loadFile() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                Dim d = new Dim();
                JsonObject o = e.getValue().getAsJsonObject();
                if (o.has("c")) {
                    for (JsonElement el : o.getAsJsonArray("c")) {
                        JsonArray a = el.getAsJsonArray();
                        d.chunks.put(key(a.get(0).getAsInt(), a.get(1).getAsInt()),
                                new ChunkRec(a.get(2).getAsInt(), a.get(3).getAsInt(), a.get(4).getAsInt(),
                                        a.get(5).getAsInt(), a.get(6).getAsInt()));
                    }
                }
                if (o.has("i")) {
                    for (JsonElement el : o.getAsJsonArray("i")) {
                        JsonArray a = el.getAsJsonArray();
                        d.ignored.add(key(a.get(0).getAsInt(), a.get(1).getAsInt()));
                    }
                }
                all.put(e.getKey(), d);
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to load bases", e);
        }
    }

    private void save() {
        if (!saveToDisk.get()) return;
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Dim> e : all.entrySet()) {
            Dim d = e.getValue();
            if (d.chunks.isEmpty() && d.ignored.isEmpty()) continue;
            JsonObject o = new JsonObject();
            JsonArray c = new JsonArray();
            for (Map.Entry<Long, ChunkRec> ce : d.chunks.entrySet()) {
                ChunkRec r = ce.getValue();
                JsonArray a = new JsonArray();
                a.add(kx(ce.getKey()));
                a.add(kz(ce.getKey()));
                a.add(r.score());
                a.add(r.minY());
                a.add(r.maxY());
                a.add(r.cats());
                a.add(r.ent());
                c.add(a);
            }
            o.add("c", c);
            JsonArray ig = new JsonArray();
            for (long k : d.ignored) {
                JsonArray a = new JsonArray();
                a.add(kx(k));
                a.add(kz(k));
                ig.add(a);
            }
            o.add("i", ig);
            root.add(e.getKey(), o);
        }
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(root, w);
            dirty = false;
        } catch (IOException e) {
            LOG.error("Failed to save bases", e);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (clusters.isEmpty() || mc.options.hudHidden) return;
        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        int screenW = mc.getWindow().getScaledWidth();

        int y = 150;
        String title = "Possible bases (" + clusters.size() + ")";
        ctx.drawTextWithShadow(mc.textRenderer, title, screenW - 4 - mc.textRenderer.getWidth(title), y, Theme.accent());
        y += 10;
        for (int i = 0; i < Math.min(listSize.intValue(), clusters.size()); i++) {
            Cluster c = clusters.get(i);
            String line = (int) c.x() + ", " + (int) c.z() + "  [" + c.score() + "]  " + (int) dist2d(cam, c) + "m " + compass(cam, c);
            ctx.drawTextWithShadow(mc.textRenderer, line, screenW - 4 - mc.textRenderer.getWidth(line), y, 0xFFFFFFFF);
            y += 10;
        }

        if (!boxes.get() && !tracers.get() && !arrows.get()) return;
        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;
        int w = esp.width(), h = esp.height();

        for (int i = 0; i < Math.min(8, clusters.size()); i++) {
            Cluster c = clusters.get(i);
            double d = dist2d(cam, c);
            String label = "Base " + c.score() + "  " + (int) d + "m";
            int[] rect = esp.project(c.minCx() * 16, c.minY(), c.minCz() * 16,
                    (c.maxCx() + 1) * 16, c.maxY() + 1, (c.maxCz() + 1) * 16);
            if (rect != null) {
                if (boxes.get()) {
                    EspRender.drawBox(ctx, rect, 0xFFD166, 0);
                    ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(label),
                            (rect[0] + rect[2]) / 2, Math.max(2, rect[1] - 10), 0xFFFFD166);
                }
                if (tracers.get()) {
                    EspRender.drawLine(ctx, w / 2, h, (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFFFFD166);
                }
            } else if (arrows.get() && i < 3) {
                drawEdgeArrow(ctx, esp, c, label, w, h);
            }
        }
    }

    /** Draws "< Base 420m" pinned to the screen edge, pointing toward an off-screen cluster. */
    private void drawEdgeArrow(DrawContext ctx, EspRender esp, Cluster c, String label, int w, int h) {
        double midY = (c.minY() + c.maxY()) / 2.0;
        double[] cs = esp.toCamera(c.x(), midY, c.z()); // {right, up, forward}
        double dirX = cs[0], dirY = -cs[1];
        if (cs[2] > 0.05) { // in front: use the real projection direction
            dirX = cs[0] / cs[2] * esp.scaleX();
            dirY = -cs[1] / cs[2] * esp.scaleY();
        }
        if (Math.abs(dirX) < 1e-6 && Math.abs(dirY) < 1e-6) dirX = 1;
        int margin = 26;
        double scale = Math.min((w / 2.0 - margin) / Math.max(1e-6, Math.abs(dirX)),
                (h / 2.0 - margin) / Math.max(1e-6, Math.abs(dirY)));
        int px = (int) (w / 2.0 + dirX * scale);
        int py = (int) (h / 2.0 + dirY * scale);

        String arrow = Math.abs(dirX) > Math.abs(dirY) ? (dirX < 0 ? "<" : ">") : (dirY < 0 ? "^" : "v");
        String text = arrow.equals(">") ? label + " " + arrow : arrow + " " + label;
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(text), px, py, 0xFFFFD166);
    }
}
