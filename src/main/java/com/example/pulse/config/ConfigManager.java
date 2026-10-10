package com.example.pulse.config;

import com.example.pulse.gui.Theme;
import com.example.pulse.module.Module;
import com.example.pulse.module.ModuleManager;
import com.example.pulse.module.impl.HudModule;
import com.example.pulse.setting.Setting;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves/loads module state, settings, HUD layout and GUI theme to config/pulse.json. */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("pulse");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String HUD_KEY = "_hudLayout";
    private static final String GUI_KEY = "_gui";
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("pulse.json");

    public void load(ModuleManager manager) {
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Module m : manager.getModules()) {
                JsonObject o = root.has(m.name) ? root.getAsJsonObject(m.name) : null;
                if (o == null) continue;
                JsonObject s = o.has("settings") ? o.getAsJsonObject("settings") : new JsonObject();
                for (Setting<?> setting : m.getSettings()) {
                    try {
                        if (s.has(setting.name)) setting.fromJson(s.get(setting.name));
                    } catch (RuntimeException ex) {
                        LOG.warn("Bad value for setting {}.{}", m.name, setting.name);
                    }
                }
                if (m.persistsEnabled() && o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
            }

            if (root.has(HUD_KEY)) {
                JsonObject hud = root.getAsJsonObject(HUD_KEY);
                for (HudModule.Element e : HudModule.ELEMENTS) {
                    try {
                        if (hud.has(e.id)) {
                            JsonArray a = hud.getAsJsonArray(e.id);
                            e.x = a.get(0).getAsInt();
                            e.y = a.get(1).getAsInt();
                        }
                    } catch (RuntimeException ex) {
                        LOG.warn("Bad HUD position for {}", e.id);
                    }
                }
            }
            if (root.has(GUI_KEY)) {
                JsonObject gui = root.getAsJsonObject(GUI_KEY);
                if (gui.has("theme")) Theme.index = Math.max(0, Math.min(Theme.count() - 1, gui.get("theme").getAsInt()));
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("Failed to load config", e);
        }
    }

    public void save(ModuleManager manager) {
        JsonObject root = new JsonObject();
        for (Module m : manager.getModules()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.persistsEnabled() && m.isEnabled());
            JsonObject s = new JsonObject();
            for (Setting<?> setting : m.getSettings()) s.add(setting.name, setting.toJson());
            o.add("settings", s);
            root.add(m.name, o);
        }

        JsonObject hud = new JsonObject();
        for (HudModule.Element e : HudModule.ELEMENTS) {
            JsonArray a = new JsonArray();
            a.add(e.x);
            a.add(e.y);
            hud.add(e.id, a);
        }
        root.add(HUD_KEY, hud);

        JsonObject gui = new JsonObject();
        gui.addProperty("theme", Theme.index);
        root.add(GUI_KEY, gui);

        try (Writer w = Files.newBufferedWriter(path)) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            LOG.error("Failed to save config", e);
        }
    }
}
