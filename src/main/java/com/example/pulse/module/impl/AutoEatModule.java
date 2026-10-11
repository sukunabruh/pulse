package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;

/**
 * Eats from your hotbar when your hunger drops to the chosen level: it selects the food, holds
 * the use key until the item is eaten, then switches back to the slot you were on.
 * Note: if you are looking at something you can interact with (a chest, a door) the held use key
 * will interact with it instead, so don't enable this while standing at one.
 */
public class AutoEatModule extends Module {
    /** Preferred foods, best first. Anything else with a food component is used as a fallback. */
    private static final List<Item> PREFERRED = List.of(
            Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_SALMON,
            Items.GOLDEN_CARROT, Items.BAKED_POTATO, Items.COOKED_CHICKEN, Items.COOKED_COD,
            Items.COOKED_RABBIT, Items.BREAD, Items.PUMPKIN_PIE, Items.CARROT, Items.APPLE,
            Items.MELON_SLICE, Items.SWEET_BERRIES, Items.COOKIE, Items.DRIED_KELP);
    private static final List<Item> NEVER = List.of(
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.PUFFERFISH, Items.POISONOUS_POTATO,
            Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.CHICKEN);

    private final NumberSetting hungerLevel = add(new NumberSetting("Eat at hunger", 14, 1, 19, 1));
    private final BoolSetting goldenApples = add(new BoolSetting("Allow golden apples", false));

    private boolean eating;
    private int prevSlot = -1;
    private int eatTicks;

    public AutoEatModule() {
        super("Auto Eat", "Eats food from your hotbar when hungry, then switches back.", Category.PLAYER);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        if (mc.currentScreen != null) {
            stop();
            return;
        }
        PlayerInventory inv = p.getInventory();
        int food = p.getHungerManager().getFoodLevel();

        if (eating) {
            eatTicks++;
            if (!isFood(inv.getStack(inv.getSelectedSlot()))) { // ate the last one / slot changed
                stop();
                return;
            }
            if (eatTicks > 5 && !p.isUsingItem()) { // one item finished
                if (food > hungerLevel.intValue()) {
                    stop();
                    return;
                }
                eatTicks = 0;
            }
            mc.options.useKey.setPressed(true);
            return;
        }

        if (food > hungerLevel.intValue() || p.isUsingItem()) return;
        int slot = findFood(inv);
        if (slot < 0) return;
        prevSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        eating = true;
        eatTicks = 0;
        mc.options.useKey.setPressed(true);
    }

    @Override
    public void onDisable() {
        stop();
    }

    private void stop() {
        if (eating) {
            mc.options.useKey.setPressed(false);
            if (mc.player != null && prevSlot >= 0) mc.player.getInventory().setSelectedSlot(prevSlot);
        }
        eating = false;
        prevSlot = -1;
        eatTicks = 0;
    }

    private boolean isFood(ItemStack stack) {
        if (stack.isEmpty() || !stack.contains(DataComponentTypes.FOOD)) return false;
        Item item = stack.getItem();
        if (NEVER.contains(item)) return false;
        if (!goldenApples.get() && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) return false;
        return true;
    }

    private int findFood(PlayerInventory inv) {
        int best = -1, bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (!isFood(s)) continue;
            int rank = PREFERRED.indexOf(s.getItem());
            if (rank < 0) rank = PREFERRED.size();
            if (rank < bestRank) {
                bestRank = rank;
                best = i;
            }
        }
        return best;
    }
}
