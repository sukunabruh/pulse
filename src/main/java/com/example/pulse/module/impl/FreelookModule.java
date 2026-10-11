package com.example.pulse.module.impl;

import com.example.pulse.PulseClient;
import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Hold the Freelook key (default Left Alt) and move the mouse to look around while your player
 * keeps facing - and walking - the same way. Release to snap the camera back.
 *
 * Implemented without mixins: a throwaway armor stand follows your eyes as the camera entity and
 * takes the mouse movement, while your real yaw/pitch is frozen. With "Third person" on (default)
 * the camera orbits your player so you can see yourself turn your head.
 * Not usable while Freecam is on.
 */
public class FreelookModule extends Module {
    private final KeyBinding holdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pulse.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, PulseClient.CATEGORY));
    private final BoolSetting thirdPerson = add(new BoolSetting("Third person", true));

    private boolean active;
    private ArmorStandEntity cam;
    private ClientPlayerEntity owner;
    private float frozenYaw, frozenPitch, camYaw, camPitch;
    private double eyeOffset;
    private Perspective savedPerspective;

    public FreelookModule() {
        super("Freelook", "Hold the Freelook key to look around without turning your player.", Category.MOVEMENT);
    }

    @Override
    public void onPreTick() {
        if (active && owner != null && mc.player == owner) syncLook();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (active && mc.player != owner) { // world change / respawn
            active = false;
            cam = null;
            owner = null;
        }
        boolean held = holdKey.isPressed() && mc.currentScreen == null;
        if (held && !active) start();
        else if (!held && active) stop();
        else if (active) {
            syncLook();
            follow(owner.getEyePos());
        }
    }

    private void start() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.getCameraEntity() != p) return; // not while Freecam is on
        owner = p;
        frozenYaw = p.getYaw();
        frozenPitch = p.getPitch();
        camYaw = frozenYaw;
        camPitch = frozenPitch;

        cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc.world);
        eyeOffset = cam.getEyeY() - cam.getY();
        follow(p.getEyePos());
        mc.setCameraEntity(cam);

        savedPerspective = mc.options.getPerspective();
        if (thirdPerson.get()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        active = true;
    }

    private void stop() {
        if (!active) return;
        active = false;
        if (owner != null && mc.player == owner) {
            owner.setYaw(frozenYaw);
            owner.setPitch(frozenPitch);
            mc.setCameraEntity(owner);
        } else if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (savedPerspective != null) mc.options.setPerspective(savedPerspective);
        savedPerspective = null;
        cam = null;
        owner = null;
    }

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

    private void follow(Vec3d eye) {
        cam.refreshPositionAndAngles(eye.x, eye.y - eyeOffset, eye.z, camYaw, camPitch);
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (!active || cam == null || owner == null) return;
        syncLook();
        follow(owner.getLerpedPos(tickProgress).add(0, owner.getEyeY() - owner.getY(), 0));
    }

    @Override
    public void onDisable() {
        stop();
    }
}
