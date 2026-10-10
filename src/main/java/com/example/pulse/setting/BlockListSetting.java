package com.example.pulse.setting;

/** A comma-separated list of block ids, edited with the searchable block picker in the click GUI. */
public class BlockListSetting extends StringSetting {
    public BlockListSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    public int count() {
        int n = 0;
        for (String s : value.split(",")) if (!s.isBlank()) n++;
        return n;
    }
}
