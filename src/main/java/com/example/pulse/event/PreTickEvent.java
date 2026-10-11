package com.example.pulse.event;

/** Fired at the very start of each client tick while in a world (before the player ticks). */
public record PreTickEvent() {
    public static final PreTickEvent INSTANCE = new PreTickEvent();
}
