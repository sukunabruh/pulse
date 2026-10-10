package com.example.pulse.gui;

import com.example.pulse.config.ConfigManager;
import com.example.pulse.module.ModuleManager;
import com.example.pulse.module.impl.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Drag HUD elements (watermark, coords, FPS, active modules) to where you want them. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private final ModuleManager manager;
    private final ConfigManager config;

    private HudModule.Element drag;
    private int dx, dy;
    private boolean inited, lPrev;

    public HudEditorScreen(Screen parent, ModuleManager manager, ConfigManager config) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
        this.manager = manager;
        this.config = config;
    }

    @Override
    protected void init() {
        if (!inited) {
            long win = client.getWindow().getHandle();
            lPrev = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            inited = true;
        }
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long win = client.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean down = l && !lPrev;
        lPrev = l;
        if (!l) drag = null;

        List<HudModule.Element> els = HudModule.ELEMENTS;

        if (down) {
            for (int i = els.size() - 1; i >= 0; i--) {
                HudModule.Element e = els.get(i);
                if (!e.isEnabled()) continue;
                List<String> lines = e.lines(true);
                int w = e.width(textRenderer, lines), h = e.height(lines);
                if (mouseX >= e.x - 2 && mouseX < e.x + w + 2 && mouseY >= e.y - 2 && mouseY < e.y + h + 2) {
                    drag = e;
                    dx = mouseX - e.x;
                    dy = mouseY - e.y;
                    break;
                }
            }
        }
        if (drag != null) {
            List<String> lines = drag.lines(true);
            drag.x = Math.max(0, Math.min(width - drag.width(textRenderer, lines), mouseX - dx));
            drag.y = Math.max(0, Math.min(height - drag.height(lines), mouseY - dy));
        }

        ctx.fill(0, 0, width, height, 0x88000000);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("HUD Editor  -  drag elements, Esc to save and close"),
                width / 2, height / 2 - 4, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Turn elements on/off in the HUD module settings"),
                width / 2, height / 2 + 8, 0xFF9A9AAA);

        for (HudModule.Element e : els) {
            if (!e.isEnabled()) continue;
            List<String> lines = e.lines(true);
            int w = e.width(textRenderer, lines), h = e.height(lines);
            boolean active = e == drag;
            int x1 = e.x - 2, y1 = e.y - 2, x2 = e.x + w + 2, y2 = e.y + h + 2;

            ctx.fill(x1, y1, x2, y2, active ? 0x66000000 : 0x44000000);
            int c = Theme.accent();
            ctx.fill(x1, y1, x2, y1 + 1, c);
            ctx.fill(x1, y2 - 1, x2, y2, c);
            ctx.fill(x1, y1, x1 + 1, y2, c);
            ctx.fill(x2 - 1, y1, x2, y2, c);

            int y = e.y;
            for (String s : lines) {
                ctx.drawTextWithShadow(textRenderer, s, e.x, y, e.accentColored() ? Theme.accent() : 0xFFFFFFFF);
                y += 10;
            }
        }
    }

    @Override
    public void removed() {
        config.save(manager);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
