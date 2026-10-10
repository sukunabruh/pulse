package com.example.pulse.event;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Fired every frame when the in-game HUD is drawn. */
public record HudEvent(DrawContext context, RenderTickCounter tickCounter) {
}
