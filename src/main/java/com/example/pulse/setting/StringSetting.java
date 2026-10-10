package com.example.pulse.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {
    public StringSetting(String name, String defaultValue) {
        super(name, defaultValue);
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement element) { value = element.getAsString(); }
}
