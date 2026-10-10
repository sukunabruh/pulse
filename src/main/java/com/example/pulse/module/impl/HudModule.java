package com.example.pulse.module.impl;

import com.example.pulse.gui.Theme;
import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.setting.BoolSetting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * On-screen info overlay made of movable elements. Open the HUD editor (Right Ctrl by default,
 * or the button in the click GUI) to drag them around; positions are saved in the config.
 */
public class HudModule extends Module {

    /** One draggable piece of the HUD (watermark, coordinates, FPS, active modules). */
    public static final class Element {
        public final String id;
        public int x, y;
        private final BoolSetting toggle;
        private final Supplier<List<String>> live;
        private final List<String> preview;
        private final boolean accentColored;

        Element(String id, int x, int y, BoolSetting toggle, Supplier<List<String>> live,
                List<String> preview, boolean accentColored) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.toggle = toggle;
            this.live = live;
            this.preview = preview;
            this.accentColored = accentColored;
        }

        public boolean isEnabled() { return toggle.get(); }

        /** Lines to draw; in the editor an empty element shows sample text so it can be grabbed. */
        public List<String> lines(boolean editor) {
            List<String> l = live.get();
            return (editor && l.isEmpty()) ? preview : l;
        }

        public int width(TextRenderer tr, List<String> lines) {
            int w = 24;
            for (String s : lines) w = Math.max(w, tr.getWidth(s));
            return w;
        }

        public int height(List<String> lines) { return Math.max(10, lines.size() * 10); }

        public boolean accentColored() { return accentColored; }
    }

    public static final List<Element> ELEMENTS = new ArrayList<>();

    private final Supplier<List<Module>> allModules;

    public HudModule(Supplier<List<Module>> allModules) {
        super("HUD", "On-screen info overlay (movable in the HUD editor).", Category.RENDER);
        this.allModules = allModules;

        ELEMENTS.clear();
        ELEMENTS.add(new Element("watermark", 4, 4, add(new BoolSetting("Watermark", true)),
                () -> List.of("Pulse"), List.of("Pulse"), true));
        ELEMENTS.add(new Element("coords", 4, 16, add(new BoolSetting("Coordinates", true)),
                () -> mc.player == null ? List.of()
                        : List.of(String.format("XYZ: %d / %d / %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())),
                List.of("XYZ: 0 / 64 / 0"), false));
        ELEMENTS.add(new Element("fps", 4, 28, add(new BoolSetting("FPS", true)),
                () -> List.of("FPS: " + mc.getCurrentFps()), List.of("FPS: 60"), false));
        ELEMENTS.add(new Element("modules", 4, 40, add(new BoolSetting("Active modules", true)),
                () -> allModules.get().stream().filter(m -> m.isEnabled() && m != this).map(m -> m.name).toList(),
                List.of("Sprint", "Fullbright"), false));

        setEnabled(true);
    }

    @Override
    public void onHud(DrawContext ctx, float tickProgress) {
        if (mc.player == null || mc.options.hudHidden) return;
        for (Element e : ELEMENTS) {
            if (!e.isEnabled()) continue;
            int y = e.y;
            for (String line : e.lines(false)) {
                ctx.drawTextWithShadow(mc.textRenderer, line, e.x, y, e.accentColored() ? Theme.accent() : 0xFFFFFFFF);
                y += 10;
            }
        }
    }
}
