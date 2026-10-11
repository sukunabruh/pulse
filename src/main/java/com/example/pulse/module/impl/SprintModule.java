package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;

public class SprintModule extends Module {
    private final BoolSetting omni = add(new BoolSetting("All directions", false));

    public SprintModule() {
        super("Auto Sprint", "Sprints for you while you move (forward only, or any direction).", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        boolean moving = mc.options.forwardKey.isPressed()
                || (omni.get() && (mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()));
        if (moving
                && !p.isSneaking()
                && !p.isUsingItem()
                && !p.horizontalCollision
                && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
        }
    }
}
