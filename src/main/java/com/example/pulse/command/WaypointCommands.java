package com.example.pulse.command;

import com.example.pulse.module.impl.WaypointsModule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Client-side chat commands (never sent to the server): /pulse wp add|remove|list|clear */
public final class WaypointCommands {
    private WaypointCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, WaypointsModule wp) {
        dispatcher.register(ClientCommandManager.literal("pulse")
                .then(ClientCommandManager.literal("wp")
                        .then(ClientCommandManager.literal("add")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            BlockPos p = ctx.getSource().getPlayer().getBlockPos();
                                            String name = StringArgumentType.getString(ctx, "name");
                                            wp.add(name, p.getX(), p.getY(), p.getZ());
                                            ctx.getSource().sendFeedback(Text.literal("[Pulse] Saved " + name + " at "
                                                    + p.getX() + ", " + p.getY() + ", " + p.getZ()));
                                            return 1;
                                        })
                                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                        .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> {
                                                                    String name = StringArgumentType.getString(ctx, "name");
                                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                                    int y = IntegerArgumentType.getInteger(ctx, "y");
                                                                    int z = IntegerArgumentType.getInteger(ctx, "z");
                                                                    wp.add(name, x, y, z);
                                                                    ctx.getSource().sendFeedback(Text.literal("[Pulse] Saved " + name
                                                                            + " at " + x + ", " + y + ", " + z));
                                                                    return 1;
                                                                })))))) 
                        .then(ClientCommandManager.literal("remove")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            boolean ok = wp.remove(name);
                                            ctx.getSource().sendFeedback(Text.literal(ok
                                                    ? "[Pulse] Removed " + name
                                                    : "[Pulse] No waypoint named " + name));
                                            return ok ? 1 : 0;
                                        })))
                        .then(ClientCommandManager.literal("list")
                                .executes(ctx -> {
                                    var list = wp.list();
                                    if (list.isEmpty()) {
                                        ctx.getSource().sendFeedback(Text.literal("[Pulse] No waypoints on this server."));
                                    }
                                    for (var w : list) {
                                        ctx.getSource().sendFeedback(Text.literal("[Pulse] " + w.name() + ": "
                                                + w.x() + ", " + w.y() + ", " + w.z() + " (" + w.dim() + ")"));
                                    }
                                    return list.size();
                                }))
                        .then(ClientCommandManager.literal("clear")
                                .executes(ctx -> {
                                    int n = wp.clear();
                                    ctx.getSource().sendFeedback(Text.literal("[Pulse] Cleared " + n + " waypoint(s)."));
                                    return n;
                                }))));
    }
}
