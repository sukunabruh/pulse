package com.example.pulse;

import com.example.pulse.command.WaypointCommands;
import com.example.pulse.config.ConfigManager;
import com.example.pulse.event.EventBus;
import com.example.pulse.event.HudEvent;
import com.example.pulse.event.TickEvent;
import com.example.pulse.gui.ClickGuiScreen;
import com.example.pulse.gui.HudEditorScreen;
import com.example.pulse.module.ModuleManager;
import com.example.pulse.module.impl.WaypointsModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PulseClient implements ClientModInitializer {
    public static final String MOD_ID = "pulse";
    /** Key bind category shown in Options > Controls (name comes from the lang file). */
    public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "modules"));
    public static final EventBus EVENTS = new EventBus();

    @Override
    public void onInitializeClient() {
        ModuleManager modules = new ModuleManager(EVENTS);
        ConfigManager config = new ConfigManager();
        config.load(modules);

        KeyBinding guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pulse.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        // Right Ctrl (not T, which vanilla uses for chat). Rebindable in Controls.
        KeyBinding hudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pulse.hud_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (guiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ClickGuiScreen(modules, config));
            }
            while (hudKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new HudEditorScreen(null, modules, config));
            }
            if (client.player != null && client.world != null) {
                EVENTS.post(TickEvent.INSTANCE);
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(MOD_ID, "hud"),
                (context, tickCounter) -> EVENTS.post(new HudEvent(context, tickCounter)));

        WaypointsModule waypoints = modules.get(WaypointsModule.class);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                WaypointCommands.register(dispatcher, waypoints));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            config.save(modules);
            modules.shutdown();
        });
    }
}
