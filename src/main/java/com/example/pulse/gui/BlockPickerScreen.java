package com.example.pulse.gui;

import com.example.pulse.setting.StringSetting;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Searchable list of every block in the game. Click a row to add/remove it from the ESP list.
 * The result is stored in the setting as comma-separated ids (e.g. "diamond_block,bedrock").
 * Mouse clicks are polled from GLFW (like the click GUI), so no click-method overrides are needed.
 */
public class BlockPickerScreen extends Screen {
    private record Entry(Block block, String id, String name, String lower, ItemStack icon) {}

    private static final int ROW = 20, LIST_W = 320;
    private static List<Entry> CACHE;

    private final Screen parent;
    private final StringSetting setting;
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();

    private TextFieldWidget search;
    private ButtonWidget selectedOnlyButton;
    private List<Entry> filtered = List.of();
    private double scroll;
    private boolean selectedOnly, inited, lPrev;

    public BlockPickerScreen(Screen parent, StringSetting setting) {
        super(Text.literal("Choose blocks"));
        this.parent = parent;
        this.setting = setting;
        for (String raw : setting.get().split(",")) {
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if (t.startsWith("minecraft:")) t = t.substring("minecraft:".length());
            if (!t.isEmpty()) selected.add(t);
        }
    }

    private static List<Entry> entries() {
        if (CACHE == null) {
            List<Entry> list = new ArrayList<>();
            for (Block b : Registries.BLOCK) {
                if (b.getDefaultState().isAir()) continue;
                Identifier id = Registries.BLOCK.getId(b);
                String idStr = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
                String name = b.getName().getString();
                ItemStack icon = b.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(b.asItem());
                list.add(new Entry(b, idStr, name, (name + " " + idStr).toLowerCase(Locale.ROOT), icon));
            }
            list.sort(Comparator.comparing(Entry::id));
            CACHE = list;
        }
        return CACHE;
    }

    private void refilter() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        filtered = entries().stream()
                .filter(e -> (!selectedOnly || selected.contains(e.id())) && (q.isEmpty() || e.lower().contains(q)))
                .toList();
        scroll = 0;
    }

    private int listTop() { return 52; }
    private int listBottom() { return height - 34; }

    @Override
    protected void init() {
        if (!inited) {
            lPrev = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            inited = true;
        }

        String oldText = search == null ? "" : search.getText();
        search = new TextFieldWidget(textRenderer, width / 2 - 120, 24, 240, 20, Text.literal("Search"));
        search.setMaxLength(64);
        search.setPlaceholder(Text.literal("Search blocks..."));
        search.setText(oldText);
        search.setChangedListener(s -> refilter());
        addDrawableChild(search);
        setInitialFocus(search);

        selectedOnlyButton = ButtonWidget.builder(Text.literal("Selected only: " + (selectedOnly ? "ON" : "OFF")), b -> {
            selectedOnly = !selectedOnly;
            b.setMessage(Text.literal("Selected only: " + (selectedOnly ? "ON" : "OFF")));
            refilter();
        }).dimensions(width / 2 - 160, height - 26, 110, 20).build();
        addDrawableChild(selectedOnlyButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear all"), b -> {
            selected.clear();
            save();
            refilter();
        }).dimensions(width / 2 - 50, height - 26, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 + 56, height - 26, 104, 20).build());

        refilter();
    }

    private void save() {
        setting.set(String.join(",", selected));
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= verticalAmount * ROW * 2;
        return true;
    }

    private void handleInput(int mx, int my) {
        boolean l = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean down = l && !lPrev;
        lPrev = l;

        int x1 = width / 2 - LIST_W / 2, x2 = x1 + LIST_W;
        if (down && mx >= x1 && mx < x2 && my >= listTop() && my < listBottom()) {
            int idx = (int) ((my - listTop() + scroll) / ROW);
            if (idx >= 0 && idx < filtered.size()) {
                String id = filtered.get(idx).id();
                if (!selected.remove(id)) selected.add(id);
                save();
                if (selectedOnly) refilter();
            }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        handleInput(mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);

        int top = listTop(), bottom = listBottom();
        double maxScroll = Math.max(0, filtered.size() * ROW - (bottom - top));
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Choose blocks for the ESP"), width / 2, 8, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Selected: " + selected.size() + "   Showing: " + filtered.size(),
                width / 2 - LIST_W / 2, top - 11, 0xFF9A9AAA);

        int x1 = width / 2 - LIST_W / 2, x2 = x1 + LIST_W;
        ctx.fill(x1 - 1, top - 1, x2 + 1, bottom + 1, 0xAA000000);
        ctx.fill(x1, top, x2, bottom, 0xE60E0E14);

        ctx.enableScissor(x1, top, x2, bottom);
        int first = (int) (scroll / ROW);
        for (int i = first; i < filtered.size(); i++) {
            int y = top + i * ROW - (int) scroll;
            if (y >= bottom) break;
            Entry e = filtered.get(i);
            boolean on = selected.contains(e.id());
            boolean hover = mouseX >= x1 && mouseX < x2 && mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW, bottom);

            if (on) ctx.fill(x1, y, x2, y + ROW, Theme.alpha(0x40));
            else if (hover) ctx.fill(x1, y, x2, y + ROW, 0x22FFFFFF);

            // checkbox
            ctx.fill(x1 + 5, y + 5, x1 + 15, y + 15, 0xFF000000);
            ctx.fill(x1 + 6, y + 6, x1 + 14, y + 14, on ? Theme.accent() : 0xFF2A2A36);

            if (!e.icon().isEmpty()) ctx.drawItem(e.icon(), x1 + 22, y + 2);

            String name = textRenderer.trimToWidth(e.name(), 150);
            ctx.drawTextWithShadow(textRenderer, name, x1 + 44, y + 6, on ? 0xFFFFFFFF : 0xFFD0D0DA);
            String id = textRenderer.trimToWidth(e.id(), 100);
            ctx.drawTextWithShadow(textRenderer, id, x2 - 6 - textRenderer.getWidth(id), y + 6, 0xFF7A7A88);
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int barH = Math.max(12, (int) ((double) (bottom - top) * (bottom - top) / (filtered.size() * ROW)));
            int by = top + (int) ((bottom - top - barH) * (scroll / maxScroll));
            ctx.fill(x2 - 3, by, x2 - 1, by + barH, 0x88FFFFFF);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
