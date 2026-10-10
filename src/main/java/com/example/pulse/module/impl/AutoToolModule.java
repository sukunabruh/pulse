package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoToolModule extends Module {
    public AutoToolModule() {
        super("Auto Tool", "Switches to the fastest hotbar tool when mining.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.world == null || p.isCreative()) return;
        if (!mc.options.attackKey.isPressed()) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        var inv = p.getInventory();
        int current = inv.getSelectedSlot();
        float bestSpeed = inv.getStack(current).getMiningSpeedMultiplier(state);
        int best = current;

        for (int i = 0; i < 9; i++) {
            float speed = inv.getStack(i).getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best != current) inv.setSelectedSlot(best);
    }
}
