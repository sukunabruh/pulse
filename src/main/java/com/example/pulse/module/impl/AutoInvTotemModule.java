package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * While your inventory screen is open, fills the offhand with a totem (and optionally one hotbar
 * slot) from your main inventory, with a configurable delay between clicks.
 */
public class AutoInvTotemModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay ticks", 1, 0, 20, 1));
    private final NumberSetting hotbarSlot = add(new NumberSetting("Hotbar slot (0=off)", 0, 0, 9, 1));
    private int wait;

    public AutoInvTotemModule() {
        super("Auto Inv Totem", "Fills offhand (and a hotbar slot) with totems while your inventory is open.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            wait = delay.intValue();
            return;
        }
        if (wait-- > 0) return;
        int syncId = p.playerScreenHandler.syncId;

        if (!TotemUtil.offhandHasTotem(p)) {
            int slot = TotemUtil.findTotemSlotId(p);
            if (slot >= 0) {
                TotemUtil.swapWithOffhand(mc, syncId, slot);
                wait = delay.intValue();
            }
            return;
        }

        int hb = hotbarSlot.intValue();
        if (hb > 0 && !TotemUtil.isTotem(p.getInventory().getStack(hb - 1))) {
            int slot = TotemUtil.findMainTotemSlotId(p);
            if (slot >= 0) {
                TotemUtil.swapWithHotbar(mc, syncId, slot, hb - 1);
                wait = delay.intValue();
            }
        }
    }
}
