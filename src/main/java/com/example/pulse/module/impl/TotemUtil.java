package com.example.pulse.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Shared helpers for the totem modules. Slot ids are PlayerScreenHandler ids (9-35 main, 36-44 hotbar). */
final class TotemUtil {
    private TotemUtil() {}

    static boolean isTotem(ItemStack stack) {
        return stack.isOf(Items.TOTEM_OF_UNDYING);
    }

    static boolean offhandHasTotem(ClientPlayerEntity p) {
        return isTotem(p.getOffHandStack());
    }

    /** Screen-handler slot id of a spare totem (main inventory first, then hotbar), or -1. */
    static int findTotemSlotId(ClientPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 9; i < 36; i++) if (isTotem(inv.getStack(i))) return i;
        for (int i = 0; i < 9; i++) if (isTotem(inv.getStack(i))) return 36 + i;
        return -1;
    }

    /** Spare totem in the main inventory only (not the hotbar), or -1. */
    static int findMainTotemSlotId(ClientPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 9; i < 36; i++) if (isTotem(inv.getStack(i))) return i;
        return -1;
    }

    /** Swaps the given slot with the offhand (the vanilla "F" swap, button 40). */
    static void swapWithOffhand(MinecraftClient mc, int syncId, int slotId) {
        mc.interactionManager.clickSlot(syncId, slotId, 40, SlotActionType.SWAP, mc.player);
    }

    /** Swaps the given slot with hotbar slot index 0-8. */
    static void swapWithHotbar(MinecraftClient mc, int syncId, int slotId, int hotbarIndex) {
        mc.interactionManager.clickSlot(syncId, slotId, hotbarIndex, SlotActionType.SWAP, mc.player);
    }
}
