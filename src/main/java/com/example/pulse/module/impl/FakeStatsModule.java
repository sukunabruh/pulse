package com.example.pulse.module.impl;

import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import com.example.pulse.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a stats panel with values YOU choose. It is a local overlay only: it never changes
 * the real FPS/ping and nothing is sent to any server.
 */
public class FakeStatsModule extends Module {
    private final BoolSetting showFps = add(new BoolSetting("Show FPS", true));
    private final NumberSetting fps = add(new NumberSetting("FPS", 240, 0, 1000, 5));
    private final BoolSetting showPing = add(new BoolSetting("Show ping", true));
    private final NumberSetting ping = add(new NumberSetting("Ping", 12, 0, 500, 1));
    private final BoolSetting showCps = add(new BoolSetting("Show CPS", true));
    private final NumberSetting cps = add(new NumberSetting("CPS", 8, 0, 30, 1));
    private final BoolSetting showKd = add(new BoolSetting("Show K/D", true));
    private final NumberSetting kills = add(new NumberSetting("Kills", 25, 0, 999, 1));
    private final NumberSetting deaths = add(new NumberSetting("Deaths", 5, 0, 999, 1));
    private final NumberSetting posX = add(new NumberSetting("X position %", 100, 0, 100, 1));
    private final NumberSetting posY = add(new NumberSetting("Y position %", 5, 0, 100, 1));
    private final StringSetting customLine = add(new StringSetting("Custom line", ""));

    public FakeStatsModule() {
        super("Fake Stats", "Custom stats panel with values you choose (local display only).", Category.RENDER);
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (mc.player == null || mc.options.hudHidden) return;

        List<String> lines = new ArrayList<>();
        if (showFps.get()) lines.add("FPS: " + fps.intValue());
        if (showPing.get()) lines.add("Ping: " + ping.intValue() + "ms");
        if (showCps.get()) lines.add("CPS: " + cps.intValue());
        if (showKd.get()) {
            double kd = deaths.intValue() == 0 ? kills.intValue() : kills.intValue() / (double) deaths.intValue();
            lines.add("Kills: " + kills.intValue());
            lines.add("Deaths: " + deaths.intValue());
            lines.add(String.format("K/D: %.2f", kd));
        }

        if (!customLine.get().isBlank()) lines.add(customLine.get());
        if (lines.isEmpty()) return;

        int blockW = 0;
        for (String line : lines) blockW = Math.max(blockW, mc.textRenderer.getWidth(line));
        int blockH = lines.size() * 10;
        int screenW = mc.getWindow().getScaledWidth(), screenH = mc.getWindow().getScaledHeight();
        int x = 4 + (int) ((screenW - blockW - 8) * posX.get() / 100.0);
        int y = 4 + (int) ((screenH - blockH - 8) * posY.get() / 100.0);
        boolean rightAligned = posX.get() >= 50;
        for (String line : lines) {
            int lx = rightAligned ? x + blockW - mc.textRenderer.getWidth(line) : x;
            ctx.drawTextWithShadow(mc.textRenderer, line, lx, y, 0xFFFFFFFF);
            y += 10;
        }
    }
}
