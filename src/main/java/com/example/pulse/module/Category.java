package com.example.pulse.module;

public enum Category {
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    ESP("ESP");

    public final String displayName;

    Category(String displayName) { this.displayName = displayName; }
}
