package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam: detaches the camera from your player so you can fly through walls.
 *
 * How it works (no mixins): a throwaway client-side armor stand becomes the camera entity and
 * the real player's Input is swapped for an empty one, so WASD no longer moves the player.
 *
 * v0.2 fixes:
 *  - Mouse look no longer turns your real player. The player's yaw/pitch is frozen and the mouse
 *    movement is applied to the camera only (the server never sees the camera rotation).
 *  - Attack / use / pick-block clicks are swallowed while in freecam, so you can't mine, place or
 *    hit things from the camera's crosshair (optional setting).
 *  - Movement keeps working with F1 (hidden HUD) via a per-tick fallback.
 *
 * Controls: WASD = move, Jump = up, Sneak = down, Sprint = speed boost.
 */
public class FreecamModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 10, 1, 60, 0.5));
    private final NumberSetting boost = add(new NumberSetting("Sprint boost", 3, 1, 10, 0.5));
    private final BoolSetting blockClicks = add(new BoolSetting("Block interaction", true));

    private boolean active;
    private ArmorStandEntity cam;
    private ClientPlayerEntity owner;
    private Input savedInput;
    private Vec3d eye = Vec3d.ZERO;
    private double eyeOffset;
    private float frozenYaw, frozenPitch, camYaw, camPitch;
    private long lastNanos, lastHudNanos;

    public FreecamModule() {
        super("Freecam", "Fly the camera through walls. Your player stays put and can't be turned.", Category.MOVEMENT, GLFW.GLFW_KEY_H);
    }

    @Override public boolean persistsEnabled() { return false; }

    @Override
    public void onPreTick() {
        if (!active || owner == null || mc.player != owner) return;
        // Runs before the player ticks, so the server never sees a turned head.
        syncLook();
        if (blockClicks.get()) swallowClicks();
    }

    @Override
    public void onTick() {
        if (!active) {
            start();
            return;
        }
        if (mc.player != owner) {
            // Joined a different world / respawned: our references are stale.
            active = false;
            cam = null;
            owner = null;
            savedInput = null;
            setEnabled(false);
            return;
        }
        // HUD callback not running (F1): keep the camera moving from the tick instead.
        if (System.nanoTime() - lastHudNanos > 100_000_000L) step();
    }

    private void start() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        owner = p;
        savedInput = p.input;
        p.input = new Input(); // all-zero input: the real player stands still

        frozenYaw = p.getYaw();
        frozenPitch = p.getPitch();
        camYaw = frozenYaw;
        camPitch = frozenPitch;

        cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc.world);
        eyeOffset = cam.getEyeY() - cam.getY();
        eye = p.getEyePos();
        place();
        mc.setCameraEntity(cam);

        lastNanos = System.nanoTime();
        lastHudNanos = lastNanos;
        active = true;
    }

    /** Moves whatever the mouse did to the real player onto the camera, then puts the player back. */
    private void syncLook() {
        float dy = owner.getYaw() - frozenYaw;
        float dp = owner.getPitch() - frozenPitch;
        if (dy != 0f || dp != 0f) {
            camYaw += dy;
            camPitch = MathHelper.clamp(camPitch + dp, -90f, 90f);
            owner.setYaw(frozenYaw);
            owner.setPitch(frozenPitch);
        }
    }

    private void swallowClicks() {
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.pickItemKey.setPressed(false);
        while (mc.options.attackKey.wasPressed()) { /* discard */ }
        while (mc.options.useKey.wasPressed()) { /* discard */ }
        while (mc.options.pickItemKey.wasPressed()) { /* discard */ }
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (!active || cam == null || owner == null) return;
        lastHudNanos = System.nanoTime();
        syncLook();
        step();
    }

    private void step() {
        if (!active || cam == null || owner == null) return;
        long now = System.nanoTime();
        double dt = Math.min((now - lastNanos) / 1_000_000_000.0, 0.1);
        lastNanos = now;

        if (mc.currentScreen == null) {
            double yr = Math.toRadians(camYaw), pr = Math.toRadians(camPitch);
            Vec3d forward = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
            Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));

            double fw = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
            double st = (mc.options.rightKey.isPressed() ? 1 : 0) - (mc.options.leftKey.isPressed() ? 1 : 0);
            double up = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

            Vec3d move = forward.multiply(fw).add(right.multiply(st)).add(0, up, 0);
            if (move.lengthSquared() > 1e-6) {
                double v = speed.get() * (mc.options.sprintKey.isPressed() ? boost.get() : 1.0);
                eye = eye.add(move.normalize().multiply(v * dt));
            }
        }
        place();
    }

    private void place() {
        // refreshPositionAndAngles also resets the "previous" position, so there is no tick lerp.
        cam.refreshPositionAndAngles(eye.x, eye.y - eyeOffset, eye.z, camYaw, camPitch);
    }

    @Override
    public void onDisable() {
        if (!active) return;
        active = false;
        if (owner != null && mc.player == owner) {
            owner.input = savedInput;
            owner.setYaw(frozenYaw);
            owner.setPitch(frozenPitch);
            mc.setCameraEntity(owner);
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        cam = null;
        owner = null;
        savedInput = null;
    }
}
