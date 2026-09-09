package com.mlh.aero_player_tilt.tilt;

public enum DeckGravity {
    WORLD,

    DECK;

    public String translationKey() {
        return "aero_player_tilt.configuration.deckGravity." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
