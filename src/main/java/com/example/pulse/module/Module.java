package com.example.pulse.module;

import com.example.pulse.PulseClient;
import com.example.pulse.setting.Setting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public final String name;
    public final String description;
    public final Category category;
    public final String id;

    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final KeyBinding toggleKey;

    protected Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    /** defaultKey is only the default; the user can rebind it in Options > Controls > Pulse. */
    protected Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.id = name.toLowerCase(Locale.ROOT).replace(" ", "");
        this.toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pulse.toggle." + id, InputUtil.Type.KEYSYM, defaultKey, PulseClient.CATEGORY));
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting<?>> getSettings() { return settings; }

    public boolean isEnabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }

    /** Modules that return false always start disabled (e.g. Freecam, Auto Walk). */
    public boolean persistsEnabled() { return true; }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
    }

    public KeyBinding getToggleKey() { return toggleKey; }

    public final void pollToggleKey() {
        while (toggleKey.wasPressed()) toggle();
    }

    // Hooks. mc.player / mc.world may be null in onEnable/onDisable (e.g. when loading config).
    public void onEnable() {}
    public void onDisable() {}
    public void onPreTick() {}
    public void onTick() {}
    public void onHud(DrawContext context, float tickProgress) {}
}
