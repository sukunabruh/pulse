package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.slot.Slot;

/**
 * Hover a totem in any inventory/container screen and it is swapped into your offhand
 * (or, if the offhand already has one, into the chosen hotbar slot).
 */
public class HoverTotemModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay ticks", 1, 0, 20, 1));
    private final NumberSetting hotbarSlot = add(new NumberSetting("Hotbar slot (0=off)", 0, 0, 9, 1));
    private int cooldown;

    public HoverTotemModule() {
        super("Hover Totem", "Hover a totem in an inventory to send it to your offhand.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        Slot slot = screen.focusedSlot;
        if (slot == null || !slot.hasStack() || !TotemUtil.isTotem(slot.getStack())) return;
        if (slot.getStack() == p.getOffHandStack()) return; // already the offhand totem

        int syncId = screen.getScreenHandler().syncId;
        if (!TotemUtil.offhandHasTotem(p)) {
            TotemUtil.swapWithOffhand(mc, syncId, slot.id);
            cooldown = delay.intValue();
            return;
        }
        int hb = hotbarSlot.intValue();
        if (hb > 0 && !TotemUtil.isTotem(p.getInventory().getStack(hb - 1))) {
            TotemUtil.swapWithHotbar(mc, syncId, slot.id, hb - 1);
            cooldown = delay.intValue();
        }
    }
}
