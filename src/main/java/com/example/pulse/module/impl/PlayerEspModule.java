package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated player ESP: box, name, health, distance, held item, totem marker and a health bar,
 * through walls. Optional chat alert the first time a player comes within range.
 */
public class PlayerEspModule extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", 256, 16, 512, 16));
    private final NumberSetting fill = add(new NumberSetting("Fill %", 10, 0, 100, 5));
    private final BoolSetting names = add(new BoolSetting("Names", true));
    private final BoolSetting health = add(new BoolSetting("Health", true));
    private final BoolSetting healthBar = add(new BoolSetting("Health bar", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", true));
    private final BoolSetting heldItem = add(new BoolSetting("Held item", true));
    private final BoolSetting totemMark = add(new BoolSetting("Totem marker", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));
    private final BoolSetting showSelf = add(new BoolSetting("Self (in freecam)", true));
    private final BoolSetting alert = add(new BoolSetting("Chat alert in range", false));
    private final NumberSetting alertRange = add(new NumberSetting("Alert range", 128, 16, 512, 16));

    private final Set<UUID> inRange = new HashSet<>();
    private int cooldown;

    public PlayerEspModule() {
        super("Player ESP", "Boxes, names, health and gear for players through walls.", Category.ESP);
    }

    @Override
    public void onDisable() {
        inRange.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (--cooldown > 0) return;
        cooldown = 10;
        if (!alert.get()) {
            inRange.clear();
            return;
        }
        double alertSq = alertRange.get() * alertRange.get();
        Set<UUID> nowIn = new HashSet<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            double dSq = mc.player.squaredDistanceTo(p);
            if (dSq <= alertSq) {
                nowIn.add(p.getUuid());
                if (!inRange.contains(p.getUuid())) {
                    mc.player.sendMessage(Text.literal("[Pulse] " + p.getName().getString() + " is in range at "
                            + p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ()
                            + " (" + (int) Math.sqrt(dSq) + "m)"), false);
                }
            }
        }
        inRange.clear();
        inRange.addAll(nowIn);
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (mc.world == null) return;
        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;
        Entity cam = mc.getCameraEntity();
        double maxSq = range.get() * range.get();
        int alpha = (int) Math.round(fill.get() / 100.0 * 255.0);

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == cam) continue;
            if (p == mc.player && !(showSelf.get() && cam != mc.player)) continue;
            if (!p.isAlive()) continue;

            Vec3d pos = p.getLerpedPos(tickProgress);
            double dSq = esp.eye().squaredDistanceTo(pos);
            if (dSq > maxSq) continue;

            Box b = p.getDimensions(p.getPose()).getBoxAt(pos);
            int[] rect = esp.project(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
            if (rect == null) continue;

            int rgb = p.isInvisible() ? 0xAAAAAA : p.isSneaking() ? 0xFFAA00 : 0xFF5555;
            if (p == mc.player) rgb = 0x55FF55;
            EspRender.drawBox(ctx, rect, rgb, alpha);
            if (tracers.get()) {
                EspRender.drawLine(ctx, esp.width() / 2, esp.height(), (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFF000000 | rgb);
            }

            if (healthBar.get()) {
                float frac = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
                int bx = rect[0] - 4, top = rect[1], bottom = rect[3];
                int filled = (int) ((bottom - top) * frac);
                int col = frac > 0.6f ? 0xFF55FF55 : frac > 0.3f ? 0xFFFFAA00 : 0xFFFF3333;
                ctx.fill(bx - 1, top - 1, bx + 3, bottom + 1, 0xAA000000);
                ctx.fill(bx, bottom - filled, bx + 2, bottom, col);
            }

            StringBuilder line = new StringBuilder();
            if (names.get()) line.append(p.getName().getString());
            if (health.get()) {
                int hp = (int) Math.ceil(p.getHealth() + p.getAbsorptionAmount());
                line.append(line.length() > 0 ? " " : "").append(hp).append("hp");
            }
            if (distance.get()) line.append(line.length() > 0 ? " " : "").append((int) Math.sqrt(dSq)).append("m");
            if (totemMark.get() && TotemUtil.isTotem(p.getOffHandStack())) line.append(" [T]");
            int cx = (rect[0] + rect[2]) / 2;
            int ty = rect[1] - 10;
            if (heldItem.get()) {
                ItemStack held = p.getMainHandStack();
                if (!held.isEmpty()) {
                    ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(held.getName().getString()), cx, ty, 0xFFCCCCCC);
                    ty -= 10;
                }
            }
            if (line.length() > 0) {
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(line.toString()), cx, ty, 0xFF000000 | rgb);
            }
        }
    }
}
