package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import com.example.pulse.setting.StringSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.Locale;

/**
 * Auto-accepts teleport requests: when a server message says someone wants to teleport to you
 * (or you to them), it sends the accept command (default "tpaccept").
 * By default only names on the whitelist are accepted. Names are comma-separated, no spaces.
 */
public class AutoTpaModule extends Module {
    private static final String[] PHRASES = {
            "teleport to you", "teleport you to", "wants to teleport", "has requested to teleport",
            "requests to teleport", "tpa request", "teleport request"};

    private final BoolSetting whitelistOnly = add(new BoolSetting("Whitelist only", true));
    private final StringSetting names = add(new StringSetting("Names", ""));
    private final StringSetting command = add(new StringSetting("Accept command", "tpaccept"));
    private final NumberSetting delay = add(new NumberSetting("Delay ticks", 10, 0, 100, 1));

    private int pending = -1;

    public AutoTpaModule() {
        super("Auto TPA", "Auto-accepts teleport requests from players on your whitelist.", Category.PLAYER);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!isEnabled() || overlay) return;
            if (matches(message.getString())) pending = delay.intValue();
        });
    }

    private boolean matches(String raw) {
        String msg = raw.toLowerCase(Locale.ROOT);
        boolean isRequest = false;
        for (String phrase : PHRASES) {
            if (msg.contains(phrase)) {
                isRequest = true;
                break;
            }
        }
        if (!isRequest) return false;
        if (!whitelistOnly.get()) return true;
        for (String n : names.get().split(",")) {
            String name = n.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty() && msg.contains(name)) return true;
        }
        return false;
    }

    @Override
    public void onTick() {
        if (pending < 0 || mc.player == null) return;
        if (pending-- > 0) return;
        pending = -1;
        String cmd = command.get().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.isEmpty()) mc.player.networkHandler.sendChatCommand(cmd);
    }

    @Override
    public void onDisable() {
        pending = -1;
    }
}
