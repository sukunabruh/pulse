package com.example.pulse.gui;

import com.example.pulse.config.ConfigManager;
import com.example.pulse.module.Category;
import com.example.pulse.module.Module;
import com.example.pulse.module.ModuleManager;
import com.example.pulse.setting.BlockListSetting;
import com.example.pulse.setting.BoolSetting;
import com.example.pulse.setting.NumberSetting;
import com.example.pulse.setting.Setting;
import com.example.pulse.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Modern "floating panels" click GUI, drawn by hand.
 *
 *  - One draggable panel per category; click a header and drag to move it.
 *  - Left-click a module to toggle it, right-click to expand/collapse its settings (animated).
 *  - Drag sliders, click toggles, click "edit" on text settings, click "Bind" then press a key.
 *  - Mouse wheel scrolls the panel under the cursor. Esc or Right Shift closes.
 *
 * Mouse and key state are polled from GLFW inside render(), so this class does not override any
 * of the click/key methods whose signatures changed in 1.21.9 (less likely to break on updates).
 */
public class ClickGuiScreen extends Screen {
    private static final int W = 118, HEAD = 18, ROW = 16, SROW = 14;
    private static final int FB_W = 96, FB_H = 14;

    private enum Kind { MODULE, BOOL, NUMBER, STRING, BIND }

    private static final class Row {
        Kind kind;
        Module module;
        Setting<?> setting;
        int relY, y, h;
        int relClipTop, relClipBottom, clipTop, clipBottom;
        boolean interactive = true;
    }

    private static final class Panel {
        final Category cat;
        int x, y, bodyH, contentH;
        double scroll;
        final List<Row> rows = new ArrayList<>();

        Panel(Category cat, int x, int y) {
            this.cat = cat;
            this.x = x;
            this.y = y;
        }
    }

    // Static so panel positions / open modules survive closing and reopening the GUI.
    private static final List<Panel> ORDER = new ArrayList<>();
    private static final Map<Module, Float> ANIM = new HashMap<>();
    private static final Set<Module> OPEN = new HashSet<>();

    private final ModuleManager manager;
    private final ConfigManager config;

    private boolean inited, lPrev, rPrev, shiftPrev;
    private Panel dragPanel;
    private int dragDX, dragDY;
    private NumberSetting dragSlider;
    private int sliderX, sliderW;
    private Module binding;
    private boolean bindWait;

    public ClickGuiScreen(ModuleManager manager, ConfigManager config) {
        super(Text.literal("Pulse"));
        this.manager = manager;
        this.config = config;
        if (ORDER.isEmpty()) {
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) ORDER.add(new Panel(cats[i], 14 + i * (W + 10), 16));
        }
    }

    @Override
    protected void init() {
        if (!inited) {
            long win = client.getWindow().getHandle();
            lPrev = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            rPrev = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            shiftPrev = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            inited = true;
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return binding == null; }

    // ------------------------------------------------------------------ layout

    private static Kind kindOf(Setting<?> s) {
        if (s instanceof BoolSetting) return Kind.BOOL;
        if (s instanceof NumberSetting) return Kind.NUMBER;
        if (s instanceof StringSetting) return Kind.STRING;
        return null;
    }

    private static Row row(Kind kind, Module m, Setting<?> s, int relY, int h) {
        Row r = new Row();
        r.kind = kind;
        r.module = m;
        r.setting = s;
        r.relY = relY;
        r.h = h;
        return r;
    }

    private float updateAnim(Module m) {
        float a = ANIM.getOrDefault(m, 0f);
        float target = OPEN.contains(m) ? 1f : 0f;
        a += (target - a) * 0.35f;
        if (Math.abs(target - a) < 0.01f) a = target;
        ANIM.put(m, a);
        return a;
    }

    private void layout(Panel p) {
        p.rows.clear();
        int y = 0;
        for (Module m : manager.getByCategory(p.cat)) {
            Row mr = row(Kind.MODULE, m, null, y, ROW);
            mr.relClipTop = y;
            mr.relClipBottom = y + ROW;
            p.rows.add(mr);
            y += ROW;

            float a = updateAnim(m);
            if (a > 0f) {
                int n = m.getSettings().size() + 1; // + bind row
                int shown = Math.round(n * SROW * a);
                int sy = y, k = 0;
                for (Setting<?> s : m.getSettings()) {
                    Kind kind = kindOf(s);
                    if (kind == null) continue;
                    Row r = row(kind, m, s, sy + k * SROW, SROW);
                    r.relClipTop = sy;
                    r.relClipBottom = sy + shown;
                    r.interactive = a > 0.9f;
                    p.rows.add(r);
                    k++;
                }
                Row br = row(Kind.BIND, m, null, sy + k * SROW, SROW);
                br.relClipTop = sy;
                br.relClipBottom = sy + shown;
                br.interactive = a > 0.9f;
                p.rows.add(br);
                y += shown;
            }
        }
        p.contentH = y;
        int maxBody = Math.max(ROW, height - p.y - HEAD - 8);
        p.bodyH = Math.max(ROW, Math.min(y, maxBody));
        p.scroll = Math.max(0, Math.min(Math.max(0, y - p.bodyH), p.scroll));

        int top = p.y + HEAD;
        for (Row r : p.rows) {
            r.y = top + r.relY - (int) p.scroll;
            r.clipTop = top + r.relClipTop - (int) p.scroll;
            r.clipBottom = top + r.relClipBottom - (int) p.scroll;
        }
    }

    private boolean inside(Panel p, int mx, int my) {
        return mx >= p.x && mx < p.x + W && my >= p.y && my < p.y + HEAD + p.bodyH;
    }

    private boolean hit(Panel p, Row r, int mx, int my) {
        if (!r.interactive) return false;
        int top = p.y + HEAD, bottom = top + p.bodyH;
        int y1 = Math.max(Math.max(r.y, top), r.clipTop);
        int y2 = Math.min(Math.min(r.y + r.h, bottom), r.clipBottom);
        return mx >= p.x && mx < p.x + W && my >= y1 && my < y2;
    }

    // ------------------------------------------------------------------ input (polled)

    private void handleInput(int mx, int my) {
        long win = client.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean lDown = l && !lPrev, rDown = r && !rPrev;
        lPrev = l;
        rPrev = r;

        boolean shift = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean shiftDown = shift && !shiftPrev;
        shiftPrev = shift;

        if (binding != null) {
            pollBind(win);
            return;
        }
        if (shiftDown) {
            close();
            return;
        }

        if (!l) {
            dragPanel = null;
            dragSlider = null;
        }
        if (dragPanel != null) {
            dragPanel.x = Math.max(0, Math.min(width - W, mx - dragDX));
            dragPanel.y = Math.max(0, Math.min(height - HEAD - ROW, my - dragDY));
        }
        if (dragSlider != null) applySlider(mx);

        if (lDown || rDown) handleClick(mx, my, lDown);
    }

    private void applySlider(int mx) {
        double frac = Math.max(0, Math.min(1, (mx - sliderX) / (double) sliderW));
        dragSlider.set(dragSlider.min + frac * (dragSlider.max - dragSlider.min));
    }

    private int[] themeBtn() { return new int[]{8, height - 28, FB_W, FB_H}; }
    private int[] hudBtn() { return new int[]{8 + FB_W + 6, height - 28, FB_W, FB_H}; }

    private static boolean in(int[] r, int mx, int my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private void drawButton(DrawContext ctx, int[] r, String label, int mx, int my) {
        boolean hover = in(r, mx, my);
        ctx.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, 0xAA000000);
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? 0xFF22222E : 0xFF16161F);
        ctx.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], Theme.accent());
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), r[0] + r[2] / 2, r[1] + 3, 0xFFFFFFFF);
    }

    private void handleClick(int mx, int my, boolean left) {
        if (left) {
            if (in(themeBtn(), mx, my)) {
                Theme.next();
                return;
            }
            if (in(hudBtn(), mx, my)) {
                client.setScreen(new HudEditorScreen(this, manager, config));
                return;
            }
        }
        for (int i = ORDER.size() - 1; i >= 0; i--) {
            Panel p = ORDER.get(i);
            if (!inside(p, mx, my)) continue;

            ORDER.remove(i); // bring to front
            ORDER.add(p);

            if (my < p.y + HEAD) {
                if (left) {
                    dragPanel = p;
                    dragDX = mx - p.x;
                    dragDY = my - p.y;
                }
                return;
            }
            for (Row r : p.rows) {
                if (hit(p, r, mx, my)) {
                    clickRow(p, r, mx, left);
                    return;
                }
            }
            return;
        }
    }

    private void clickRow(Panel p, Row r, int mx, boolean left) {
        switch (r.kind) {
            case MODULE -> {
                if (left) {
                    r.module.toggle();
                } else {
                    if (!OPEN.remove(r.module)) OPEN.add(r.module);
                }
            }
            case BOOL -> {
                if (left) ((BoolSetting) r.setting).toggle();
            }
            case NUMBER -> {
                if (left) {
                    dragSlider = (NumberSetting) r.setting;
                    sliderX = p.x + 4;
                    sliderW = W - 8;
                    applySlider(mx);
                }
            }
            case STRING -> {
                if (left) {
                    if (r.setting instanceof BlockListSetting bl) client.setScreen(new BlockPickerScreen(this, bl));
                    else client.setScreen(new StringEditScreen(this, (StringSetting) r.setting));
                }
            }
            case BIND -> {
                if (left) {
                    binding = r.module;
                    bindWait = true;
                }
            }
        }
    }

    private void pollBind(long win) {
        int pressed = -1, count = 0;
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
            if (GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS) {
                count++;
                pressed = key;
            }
        }
        if (bindWait) { // wait until every key (e.g. the one used to open the GUI) is released
            if (count == 0) bindWait = false;
            return;
        }
        if (count == 0) return;

        KeyBinding kb = binding.getToggleKey();
        kb.setBoundKey(pressed == GLFW.GLFW_KEY_ESCAPE
                ? InputUtil.UNKNOWN_KEY
                : InputUtil.Type.KEYSYM.createFromCode(pressed));
        KeyBinding.updateKeysByCode();
        client.options.write();
        binding = null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int i = ORDER.size() - 1; i >= 0; i--) {
            Panel p = ORDER.get(i);
            if (inside(p, (int) mouseX, (int) mouseY)) {
                p.scroll -= verticalAmount * ROW;
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        for (Panel p : ORDER) layout(p);
        handleInput(mouseX, mouseY);

        ctx.fill(0, 0, width, height, 0x88000000);
        for (Panel p : ORDER) drawPanel(ctx, p, mouseX, mouseY);
        drawTooltip(ctx, mouseX, mouseY);
        drawButton(ctx, themeBtn(), "Theme: " + Theme.name(), mouseX, mouseY);
        drawButton(ctx, hudBtn(), "HUD Editor", mouseX, mouseY);

        String hint = "Drag headers to move  |  Right-click a module for settings  |  Wheel scrolls  |  Right Shift / Esc closes";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint), width / 2, height - 11, 0xFF9A9AAA);
    }

    private static int gradient(float t) {
        return Theme.gradient(t);
    }

    private void drawPanel(DrawContext ctx, Panel p, int mx, int my) {
        int x = p.x, y = p.y;
        int bodyTop = y + HEAD, bodyBottom = bodyTop + p.bodyH;

        ctx.fill(x - 1, y - 1, x + W + 1, bodyBottom + 1, 0xAA000000);
        ctx.fill(x, y, x + W, y + HEAD, 0xFF16161F);
        ctx.fill(x, bodyTop, x + W, bodyBottom, 0xE60E0E14);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(p.cat.displayName), x + W / 2, y + 5, 0xFFFFFFFF);
        for (int i = 0; i < W; i += 2) {
            ctx.fill(x + i, y + HEAD - 2, x + i + 2, y + HEAD, gradient(i / (float) W));
        }

        ctx.enableScissor(x, bodyTop, x + W, bodyBottom);
        for (Row r : p.rows) drawRow(ctx, p, r, mx, my);
        ctx.disableScissor();

        if (p.contentH > p.bodyH) {
            int barH = Math.max(12, (int) ((double) p.bodyH * p.bodyH / p.contentH));
            double frac = p.scroll / Math.max(1, p.contentH - p.bodyH);
            int by = bodyTop + (int) ((p.bodyH - barH) * frac);
            ctx.fill(x + W - 2, by, x + W, by + barH, 0x88FFFFFF);
        }
    }

    private void drawRow(DrawContext ctx, Panel p, Row r, int mx, int my) {
        int top = p.y + HEAD, bottom = top + p.bodyH;
        int y1 = Math.max(Math.max(r.y, top), r.clipTop);
        int y2 = Math.min(Math.min(r.y + r.h, bottom), r.clipBottom);
        if (y1 >= y2) return;

        ctx.enableScissor(p.x, y1, p.x + W, y2);
        boolean hover = hit(p, r, mx, my);
        int x = p.x;
        int ty = r.y + (r.h - 8) / 2 + 1;

        switch (r.kind) {
            case MODULE -> {
                Module m = r.module;
                boolean on = m.isEnabled();
                if (on) ctx.fill(x, r.y, x + W, r.y + r.h, Theme.alpha(0x40));
                else if (hover) ctx.fill(x, r.y, x + W, r.y + r.h, 0x22FFFFFF);
                if (on) ctx.fill(x, r.y, x + 2, r.y + r.h, Theme.accent());
                ctx.drawTextWithShadow(textRenderer, m.name, x + 7, ty, on ? 0xFFFFFFFF : 0xFFB0B0BC);
                ctx.drawTextWithShadow(textRenderer, OPEN.contains(m) ? "-" : "+", x + W - 11, ty, 0xFF8A8A99);
            }
            case BOOL -> {
                BoolSetting s = (BoolSetting) r.setting;
                ctx.fill(x, r.y, x + W, r.y + r.h, hover ? 0x66000000 : 0x55000000);
                ctx.drawTextWithShadow(textRenderer, s.name, x + 9, ty, 0xFFD0D0DA);
                int bx = x + W - 18;
                ctx.fill(bx - 1, r.y + 2, bx + 11, r.y + r.h - 2, 0xFF000000);
                ctx.fill(bx, r.y + 3, bx + 10, r.y + r.h - 3, s.get() ? Theme.accent() : 0xFF2A2A36);
            }
            case NUMBER -> {
                NumberSetting s = (NumberSetting) r.setting;
                double frac = (s.get() - s.min) / (s.max - s.min);
                ctx.fill(x, r.y, x + W, r.y + r.h, 0x55000000);
                ctx.fill(x + 4, r.y + 1, x + W - 4, r.y + r.h - 1, 0x44000000);
                ctx.fill(x + 4, r.y + 1, x + 4 + (int) ((W - 8) * frac), r.y + r.h - 1, hover || dragSlider == s ? Theme.alpha(0xAA) : Theme.alpha(0x80));
                String v = s.step < 1 ? String.format("%.1f", s.get()) : String.valueOf(s.intValue());
                ctx.drawTextWithShadow(textRenderer, s.name, x + 8, ty, 0xFFE4E4EE);
                ctx.drawTextWithShadow(textRenderer, v, x + W - 8 - textRenderer.getWidth(v), ty, 0xFFE4E4EE);
            }
            case STRING -> {
                StringSetting s = (StringSetting) r.setting;
                ctx.fill(x, r.y, x + W, r.y + r.h, hover ? 0x66000000 : 0x55000000);
                ctx.drawTextWithShadow(textRenderer, s.name, x + 9, ty, 0xFFD0D0DA);
                String right = s instanceof BlockListSetting bl ? bl.count() + " blocks" : "edit";
                ctx.drawTextWithShadow(textRenderer, right, x + W - 8 - textRenderer.getWidth(right), ty, 0xFF8A8A99);
            }
            case BIND -> {
                ctx.fill(x, r.y, x + W, r.y + r.h, hover ? 0x66000000 : 0x55000000);
                ctx.drawTextWithShadow(textRenderer, "Bind", x + 9, ty, 0xFFD0D0DA);
                String key = binding == r.module
                        ? "press a key..."
                        : r.module.getToggleKey().getBoundKeyLocalizedText().getString();
                key = textRenderer.trimToWidth(key, W - 50);
                ctx.drawTextWithShadow(textRenderer, key, x + W - 8 - textRenderer.getWidth(key), ty,
                        binding == r.module ? 0xFFFFD166 : 0xFF8A8A99);
            }
        }
        ctx.disableScissor();
    }

    private void drawTooltip(DrawContext ctx, int mx, int my) {
        if (binding != null || dragPanel != null || dragSlider != null) return;
        for (int i = ORDER.size() - 1; i >= 0; i--) {
            Panel p = ORDER.get(i);
            if (!inside(p, mx, my)) continue;
            for (Row r : p.rows) {
                if (r.kind == Kind.MODULE && hit(p, r, mx, my)) {
                    String d = r.module.description;
                    int tw = textRenderer.getWidth(d);
                    int tx = Math.min(mx + 10, width - tw - 8), ty = my + 12;
                    ctx.fill(tx - 3, ty - 3, tx + tw + 3, ty + 11, 0xF0101018);
                    ctx.fill(tx - 3, ty - 3, tx + tw + 3, ty - 2, Theme.accent());
                    ctx.drawTextWithShadow(textRenderer, d, tx, ty, 0xFFFFFFFF);
                }
            }
            return; // only the topmost panel under the cursor
        }
    }

    @Override
    public void removed() {
        config.save(manager);
    }
}
