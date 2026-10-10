package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam: detaches the camera from your player so you can fly through walls.
 *
 * How it works (no mixins): a throwaway client-side armor stand becomes the camera entity,
 * and the real player's Input is swapped for an empty one so WASD no longer moves the player.
 * The camera is moved every rendered frame, so it stays smooth at any FPS.
 *
 * Controls: WASD = move, Jump = up, Sneak = down, Sprint = speed boost. Mouse looks around
 * (this also turns your real player, as in most simple freecams).
 * Limitations: hidden while F1 is on (movement is driven by the HUD callback); block/entity
 * interaction while in freecam is unreliable because the crosshair ray starts at the camera.
 */
public class FreecamModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", 10, 1, 60, 0.5));
    private final NumberSetting boost = add(new NumberSetting("Sprint boost", 3, 1, 10, 0.5));

    private boolean active;
    private ArmorStandEntity cam;
    private ClientPlayerEntity owner;
    private Input savedInput;
    private Vec3d eye = Vec3d.ZERO;
    private double eyeOffset;
    private long lastNanos;

    public FreecamModule() {
        super("Freecam", "Fly the camera through walls. Speed is adjustable.", Category.MOVEMENT, GLFW.GLFW_KEY_H);
    }

    @Override public boolean persistsEnabled() { return false; }

    @Override
    public void onTick() {
        if (!active) {
            start();
        } else if (mc.player != owner) {
            // Joined a different world / respawned: our references are stale.
            active = false;
            cam = null;
            owner = null;
            savedInput = null;
            setEnabled(false);
        }
    }

    private void start() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        owner = p;
        savedInput = p.input;
        p.input = new Input(); // all-zero input: the real player stands still

        cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc.world);
        eyeOffset = cam.getEyeY() - cam.getY();
        eye = p.getEyePos();
        place(p.getYaw(), p.getPitch());
        mc.setCameraEntity(cam);

        lastNanos = System.nanoTime();
        active = true;
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (!active || cam == null || owner == null) return;

        long now = System.nanoTime();
        double dt = Math.min((now - lastNanos) / 1_000_000_000.0, 0.1);
        lastNanos = now;

        float yaw = owner.getYaw();
        float pitch = owner.getPitch();

        if (mc.currentScreen == null) {
            double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
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
        place(yaw, pitch);
    }

    private void place(float yaw, float pitch) {
        // refreshPositionAndAngles also resets the "previous" position, so there is no tick lerp.
        cam.refreshPositionAndAngles(eye.x, eye.y - eyeOffset, eye.z, yaw, pitch);
    }

    @Override
    public void onDisable() {
        if (!active) return;
        active = false;
        if (owner != null && mc.player == owner) {
            owner.input = savedInput;
            mc.setCameraEntity(owner);
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        cam = null;
        owner = null;
        savedInput = null;
    }
}
