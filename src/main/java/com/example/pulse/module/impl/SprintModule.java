package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;

public class SprintModule extends Module {
    public SprintModule() {
        super("Sprint", "Always sprint while moving forward.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        if (mc.options.forwardKey.isPressed()
                && !p.isSneaking()
                && !p.isUsingItem()
                && !p.horizontalCollision
                && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
        }
    }
}
