package com.mlh.aero_player_tilt.tilt;

import com.mlh.aero_player_tilt.ServerConfig;
import com.mlh.aero_player_tilt.client.config.Config;

public final class TiltPolicy {
    private TiltPolicy() {}

    public static final double ENTITY_MIN_NORMAL_Y = 0.8;

    public static boolean serverRules() {
        return ServerConfig.SPEC.isLoaded();
    }

    public static boolean tiltMobs() {
        return serverRules()
                ? ServerConfig.TILT_MOBS.get()
                : Config.flag(Config.TILT_MOBS, false);
    }

    public static boolean tiltItems() {
        return serverRules()
                ? ServerConfig.TILT_ITEMS.get()
                : Config.flag(Config.TILT_ITEMS, false);
    }

    public static double entityMinNormalY() {
        return ENTITY_MIN_NORMAL_Y;
    }

    public static DeckGravity deckGravity() {
        return serverRules() ? ServerConfig.DECK_GRAVITY.get() : Config.deckGravity();
    }

    public static boolean playerTilt() {
        return Config.flag(Config.PLAYER_TILT, true);
    }

    public static boolean enforcesMinNormalY() {
        return serverRules() && ServerConfig.ENFORCE_MIN_NORMAL_Y.get();
    }

    public static double minNormalY() {
        return enforcesMinNormalY()
                ? ServerConfig.MIN_NORMAL_Y.get()
                : Config.value(Config.MIN_NORMAL_Y, 0.8);
    }

    public static double tiltMultiplier() {
        return Config.flag(Config.USE_TILT_MULTIPLIER, false)
                ? Config.value(Config.TILT_MULTIPLIER, 0.5)
                : 1.0;
    }

    public static boolean stickToDeck() {
        return Config.flag(Config.STICK_TO_DECK, true);
    }
}
