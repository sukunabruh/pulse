package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;

public class AutoWalkModule extends Module {
    public AutoWalkModule() {
        super("Auto Walk", "Holds the forward key for you.", Category.MOVEMENT);
    }

    @Override public boolean persistsEnabled() { return false; }

    @Override
    public void onTick() {
        mc.options.forwardKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        mc.options.forwardKey.setPressed(false);
    }
}
