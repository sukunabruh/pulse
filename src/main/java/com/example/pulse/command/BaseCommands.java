package com.example.pulse.command;

import com.example.pulse.module.impl.BaseFinderModule;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/** Client-side chat commands (never sent to the server): /pulse base list|clear|ignore|wp */
public final class BaseCommands {
    private BaseCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, BaseFinderModule bf) {
        dispatcher.register(ClientCommandManager.literal("pulse")
                .then(ClientCommandManager.literal("base")
                        .then(ClientCommandManager.literal("list").executes(ctx -> {
                            var lines = bf.describeClusters();
                            if (lines.isEmpty()) {
                                ctx.getSource().sendFeedback(Text.literal("[Pulse] No bases found yet. Is Base Finder on?"));
                            }
                            for (String l : lines) ctx.getSource().sendFeedback(Text.literal(l));
                            return lines.size();
                        }))
                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                            int n = bf.clearAll();
                            ctx.getSource().sendFeedback(Text.literal("[Pulse] Cleared " + n + " remembered chunk(s) in this dimension."));
                            return n;
                        }))
                        .then(ClientCommandManager.literal("ignore").executes(ctx -> {
                            boolean ok = bf.ignoreNearest();
                            ctx.getSource().sendFeedback(Text.literal(ok
                                    ? "[Pulse] Ignoring the nearest base (e.g. a village or your own)."
                                    : "[Pulse] No base to ignore."));
                            return ok ? 1 : 0;
                        }))
                        .then(ClientCommandManager.literal("wp").executes(ctx -> {
                            int n = bf.saveClusterWaypoints();
                            ctx.getSource().sendFeedback(Text.literal("[Pulse] Saved " + n + " base waypoint(s)."));
                            return n;
                        }))));
    }
}
