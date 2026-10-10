package com.example.pulse.event;

/** Fired once per client tick while in a world. */
public record TickEvent() {
    public static final TickEvent INSTANCE = new TickEvent();
}
