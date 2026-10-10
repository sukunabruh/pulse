package com.example.pulse.gui;

import com.example.pulse.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small vanilla-widget screen for editing a text setting (e.g. the Block ESP custom block list). */
public class StringEditScreen extends Screen {
    private final Screen parent;
    private final StringSetting setting;

    public StringEditScreen(Screen parent, StringSetting setting) {
        super(Text.literal(setting.name));
        this.parent = parent;
        this.setting = setting;
    }

    @Override
    protected void init() {
        TextFieldWidget field = new TextFieldWidget(textRenderer, width / 2 - 150, height / 2 - 10, 300, 20, Text.literal(setting.name));
        field.setMaxLength(512);
        field.setText(setting.get());
        field.setChangedListener(setting::set);
        addDrawableChild(field);
        setInitialFocus(field);

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 50, height / 2 + 16, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(setting.name + " (comma-separated block ids)"),
                width / 2, height / 2 - 28, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
