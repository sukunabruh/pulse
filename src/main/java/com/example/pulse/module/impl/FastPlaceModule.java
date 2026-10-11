package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Removes (or shortens) the delay between item uses while you hold the use key. */
public class FastPlaceModule extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay ticks", 0, 0, 4, 1));
    private final BoolSetting blocks = add(new BoolSetting("Blocks", true));
    private final BoolSetting extras = add(new BoolSetting("XP bottles & crystals", true));

    public FastPlaceModule() {
        super("Fast Place", "Place blocks (and throw XP bottles) with no use delay.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || !mc.options.useKey.isPressed()) return;
        ItemStack held = p.getMainHandStack();
        boolean ok = (blocks.get() && held.getItem() instanceof BlockItem)
                || (extras.get() && (held.isOf(Items.EXPERIENCE_BOTTLE) || held.isOf(Items.END_CRYSTAL)));
        if (!ok) return;
        // itemUseCooldown is widened via pulse.accesswidener
        if (mc.itemUseCooldown > delay.intValue()) mc.itemUseCooldown = delay.intValue();
    }
}
