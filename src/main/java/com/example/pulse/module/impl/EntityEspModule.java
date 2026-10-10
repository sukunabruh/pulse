package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Boxes (with name, health and distance) around players, mobs and dropped items, through walls. */
public class EntityEspModule extends Module {
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting hostile = add(new BoolSetting("Hostile mobs", true));
    private final BoolSetting passive = add(new BoolSetting("Other mobs", false));
    private final BoolSetting items = add(new BoolSetting("Items", false));
    private final BoolSetting baseEntities = add(new BoolSetting("Frames/stands", true));
    private final NumberSetting range = add(new NumberSetting("Range", 64, 16, 256, 16));
    private final NumberSetting fill = add(new NumberSetting("Fill %", 15, 0, 100, 5));
    private final BoolSetting labels = add(new BoolSetting("Labels", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));

    public EntityEspModule() {
        super("Entity ESP", "Boxes around players, mobs and items through walls.", Category.ESP);
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        EspRender esp = EspRender.begin(mc, tickProgress);
        if (esp == null) return;
        Entity cam = mc.getCameraEntity();
        double maxSq = range.get() * range.get();
        int alpha = (int) Math.round(fill.get() / 100.0 * 255.0);

        for (Entity e : mc.world.getEntities()) {
            if (e == cam) continue;
            int rgb;
            if (e instanceof PlayerEntity) {
                if (!players.get()) continue;
                rgb = 0xFF5555;
            } else if (e instanceof HostileEntity) {
                if (!hostile.get()) continue;
                rgb = 0xFFA500;
            } else if (e instanceof ItemFrameEntity || e instanceof ArmorStandEntity) {
                if (!baseEntities.get()) continue;
                rgb = 0xFFD700;
            } else if (e instanceof MobEntity) {
                if (!passive.get()) continue;
                rgb = 0x55FF55;
            } else if (e instanceof ItemEntity) {
                if (!items.get()) continue;
                rgb = 0x55FFFF;
            } else {
                continue;
            }

            Vec3d pos = e.getLerpedPos(tickProgress);
            double dSq = esp.eye().squaredDistanceTo(pos);
            if (dSq > maxSq) continue;

            Box b = e.getDimensions(e.getPose()).getBoxAt(pos);
            int[] rect = esp.project(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
            if (rect == null) continue;
            EspRender.drawBox(ctx, rect, rgb, alpha);
            if (tracers.get()) {
                EspRender.drawLine(ctx, esp.width() / 2, esp.height(), (rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2, 0xFF000000 | rgb);
            }

            if (labels.get()) {
                String text = e.getName().getString();
                if (e instanceof LivingEntity le) text += " " + (int) Math.ceil(le.getHealth()) + "hp";
                text += " " + (int) Math.sqrt(dSq) + "m";
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(text), (rect[0] + rect[2]) / 2, rect[1] - 10, 0xFF000000 | rgb);
            }
        }
    }
}
