package com.example.pulse.gui;

import java.awt.Color;

/** GUI accent colour presets. Click "Theme" in the click GUI footer to cycle. */
public final class Theme {
    private record Preset(String name, int rgb) {}

    private static final Preset[] PRESETS = {
            new Preset("Purple", 0x8B5CF6),
            new Preset("Cyan", 0x22D3EE),
            new Preset("Rose", 0xF43F5E),
            new Preset("Green", 0x22C55E),
            new Preset("Amber", 0xF59E0B),
            new Preset("Rainbow", -1),
    };

    public static int index = 0;

    private Theme() {}

    public static String name() { return PRESETS[safe()].name(); }
    public static void next() { index = (safe() + 1) % PRESETS.length; }
    public static int count() { return PRESETS.length; }

    private static int safe() { return Math.max(0, Math.min(PRESETS.length - 1, index)); }

    private static int rainbow(float offset) {
        float hue = ((System.currentTimeMillis() % 6000L) / 6000f + offset) % 1f;
        return Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF;
    }

    public static int rgb() {
        int v = PRESETS[safe()].rgb();
        return v < 0 ? rainbow(0) : v;
    }

    /** Opaque accent colour (ARGB). */
    public static int accent() { return 0xFF000000 | rgb(); }

    /** Accent colour with the given alpha (0-255). */
    public static int alpha(int a) { return (a << 24) | (rgb() & 0xFFFFFF); }

    /** Colour for a left-to-right strip, t in [0,1]. */
    public static int gradient(float t) {
        if (PRESETS[safe()].rgb() < 0) return 0xFF000000 | rainbow(t * 0.25f);
        int c = rgb();
        float[] hsb = Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        float hue = (hsb[0] + t * 0.10f) % 1f;
        return Color.HSBtoRGB(hue, hsb[1], hsb[2]);
    }
}
