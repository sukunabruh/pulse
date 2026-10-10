package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Saved locations (e.g. bases you found), kept per server and per dimension in
 * config/pulse-waypoints.json. Manage them with chat commands:
 *   /pulse wp add <name>            (at your position)
 *   /pulse wp add <name> <x> <y> <z>
 *   /pulse wp remove <name> | list | clear
 */
public class WaypointsModule extends Module {
    public record Waypoint(String name, int x, int y, int z, String dim) {}

    private static final Logger LOG = LoggerFactory.getLogger("pulse");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final BoolSetting labels = add(new BoolSetting("Labels", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", true));
    private final NumberSetting fill = add(new NumberSetting("Fill %", 15, 0, 100, 5));
    private final NumberSetting maxDist = add(new NumberSetting("Max dist (0=all)", 0, 0, 5000, 100));

    private final Map<String, List<Waypoint>> store = new LinkedHashMap<>();
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("pulse-waypoints.json");

    public WaypointsModule() {
        super("Waypoints", "Saved locations shown through walls. Use /pulse wp ...", Category.ESP);
        load();
        setEnabled(true);
    }

    // ------------------------------------------------------------ data

    private String serverKey() {
        var s = mc.getCurrentServerEntry();
        return s != null ? s.address : "singleplayer";
    }

    private String dim() {
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    private List<Waypoint> current() {
        return store.computeIfAbsent(serverKey(), k -> new ArrayList<>());
    }

    public List<Waypoint> list() {
        return List.copyOf(current());
    }

    public void add(String name, int x, int y, int z) {
        List<Waypoint> list = current();
        list.removeIf(w -> w.name().equalsIgnoreCase(name));
        list.add(new Waypoint(name, x, y, z, dim()));
        save();
    }

    public boolean remove(String name) {
        boolean removed = current().removeIf(w -> w.name().equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    public int clear() {
        int n = current().size();
        current().clear();
        save();
        return n;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                List<Waypoint> list = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    list.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                            o.get("z").getAsInt(), o.get("dim").getAsString()));
                }
                store.put(e.getKey(), list);
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to load waypoints", e);
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> e : store.entrySet()) {
            JsonArray arr = new JsonArray();
            for (Waypoint w : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name());
                o.addProperty("x", w.x());
                o.addProperty("y", w.y());
                o.addProperty("z", w.z());
                o.addProperty("dim", w.dim());
                arr.add(o);
            }
            root.add(e.getKey(), arr);
        }
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            LOG.error("Failed to save waypoints", e);
        }
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        List<Waypoint> list = current();
        if (list.isEmpty()) return;
        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;

        String dim = dim();
        int alpha = (int) Math.round(fill.get() / 100.0 * 255.0);
        double max = maxDist.get();

        for (Waypoint w : list) {
            if (!w.dim().equals(dim)) continue;
            double d = Math.sqrt(esp.eye().squaredDistanceTo(w.x() + 0.5, w.y() + 0.5, w.z() + 0.5));
            if (max > 0 && d > max) continue;

            int[] rect = esp.project(w.x(), w.y(), w.z(), w.x() + 1, w.y() + 1, w.z() + 1);
            if (rect == null) continue;
            EspRender.drawBox(ctx, rect, 0xFFD166, alpha);
            if (tracers.get()) {
                EspRender.drawLine(ctx, esp.width() / 2, esp.height(), (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFFFFD166);
            }
            if (labels.get()) {
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(w.name() + " " + (int) d + "m"),
                        (rect[0] + rect[2]) / 2, rect[1] - 10, 0xFFFFD166);
            }
        }
    }
}
