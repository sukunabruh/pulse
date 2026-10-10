package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Client-side night vision. Note: disabling also clears any real night vision you have. */
public class FullbrightModule extends Module {
    public FullbrightModule() {
        super("Fullbright", "Maximum brightness via client-side night vision.", Category.RENDER);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
        // Refresh before the last 200 ticks, when vanilla starts flickering the effect.
        if (cur == null || cur.getDuration() < 220) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}
