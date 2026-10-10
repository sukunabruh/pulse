package com.example.pulse.module.impl;

/** Helpers that expand block id families (16 colours, wood types) into lists of ids. */
public final class BlockSets {
    public static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    public static final String[] WOODS = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "bamboo", "crimson", "warped", "pale_oak"};

    private BlockSets() {}

    /** e.g. colored("bed") -> white_bed, orange_bed, ... */
    public static String[] colored(String suffix) {
        String[] out = new String[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) out[i] = COLORS[i] + "_" + suffix;
        return out;
    }

    /** e.g. coloredWithBase("shulker_box") -> shulker_box, white_shulker_box, ... */
    public static String[] coloredWithBase(String base) {
        String[] out = new String[COLORS.length + 1];
        out[0] = base;
        for (int i = 0; i < COLORS.length; i++) out[i + 1] = COLORS[i] + "_" + base;
        return out;
    }

    public static String[] signs() {
        String[] out = new String[WOODS.length * 4];
        int i = 0;
        for (String w : WOODS) {
            out[i++] = w + "_sign";
            out[i++] = w + "_wall_sign";
            out[i++] = w + "_hanging_sign";
            out[i++] = w + "_wall_hanging_sign";
        }
        return out;
    }
}
