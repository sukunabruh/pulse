package com.example.pulse.module.impl;

import com.example.pulse.PulseClient;
import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.NumberSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Hold the zoom key (default C) to lower FOV. Vanilla FOV range is 30-110. */
public class ZoomModule extends Module {
    private final KeyBinding zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pulse.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, PulseClient.CATEGORY));
    private final NumberSetting zoomFov = add(new NumberSetting("Zoom FOV", 30, 30, 90, 1));
    private Integer savedFov = null;

    public ZoomModule() {
        super("Zoom", "Hold the zoom key to zoom in.", Category.RENDER);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        if (zoomKey.isPressed()) {
            if (savedFov == null) savedFov = mc.options.getFov().getValue();
            mc.options.getFov().setValue(zoomFov.intValue());
        } else {
            restore();
        }
    }

    @Override
    public void onDisable() {
        restore();
    }

    private void restore() {
        if (savedFov != null) {
            mc.options.getFov().setValue(savedFov);
            savedFov = null;
        }
    }
}
