package com.mlh.aero_player_tilt.tilt;

public enum ZoneTilt {
    OFF,

    NO_FLOOR,

    ALWAYS;

    public String translationKey() {
        return "aero_player_tilt.configuration.zoneTilt." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
