package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;

/** Keeps a totem of undying in your offhand, refilling it from your inventory the moment it is empty. */
public class AutoTotemModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay ticks", 0, 0, 10, 1));
    private int wait;

    public AutoTotemModule() {
        super("Auto Totem", "Moves a totem into your offhand whenever it is empty.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return; // Inv Totem / Hover Totem handle open screens
        if (TotemUtil.offhandHasTotem(p)) {
            wait = delay.intValue();
            return;
        }
        int slot = TotemUtil.findTotemSlotId(p);
        if (slot < 0) return;
        if (wait-- > 0) return;
        TotemUtil.swapWithOffhand(mc, p.playerScreenHandler.syncId, slot);
        wait = delay.intValue();
    }
}
