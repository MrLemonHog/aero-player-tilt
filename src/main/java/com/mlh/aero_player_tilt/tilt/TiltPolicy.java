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

    public static EntityFilter.Mode mobListMode() {
        if (serverRules()) return ServerConfig.MOB_LIST_MODE.get();
        return Config.isLoaded() ? Config.MOB_LIST_MODE.get() : EntityFilter.Mode.BLACKLIST;
    }

    public static java.util.List<? extends String> mobList() {
        if (serverRules()) return ServerConfig.MOB_LIST.get();
        return Config.isLoaded() ? Config.MOB_LIST.get() : java.util.List.of();
    }

    public static EntityFilter.Mode itemListMode() {
        if (serverRules()) return ServerConfig.ITEM_LIST_MODE.get();
        return Config.isLoaded() ? Config.ITEM_LIST_MODE.get() : EntityFilter.Mode.BLACKLIST;
    }

    public static java.util.List<? extends String> itemList() {
        if (serverRules()) return ServerConfig.ITEM_LIST.get();
        return Config.isLoaded() ? Config.ITEM_LIST.get() : java.util.List.of();
    }

    public static double entityMinNormalY() {
        return ENTITY_MIN_NORMAL_Y;
    }

    public static DeckGravity deckGravity() {
        return serverRules() ? ServerConfig.DECK_GRAVITY.get() : Config.deckGravity();
    }

    public static boolean allowsMagneticBoots() {
        return !serverRules() || ServerConfig.ALLOW_MAGNETIC_BOOTS.get();
    }

    public static boolean allowsStickyTilt() {
        return !serverRules() || ServerConfig.ALLOW_STICKY_TILT.get();
    }

    public static boolean playerTilt() {
        return Config.flag(Config.PLAYER_TILT, true);
    }

    public static boolean enforcesMinNormalY() {
        return serverRules() && ServerConfig.ENFORCE_MIN_NORMAL_Y.get();
    }

    public static double minNormalY() {
        if (enforcesMinNormalY()) return ServerConfig.MIN_NORMAL_Y.get();

        var tuning = com.mlh.aero_player_tilt.apiimpl.ApiRegistry.tuning();
        if (tuning != null) return Math.max(0.0, Math.cos(Math.toRadians(tuning.maxAngle())));

        return Config.value(Config.MIN_NORMAL_Y, 0.8);
    }

    public static double configuredMinNormalY() {
        return enforcesMinNormalY()
                ? ServerConfig.MIN_NORMAL_Y.get()
                : Config.value(Config.MIN_NORMAL_Y, 0.8);
    }

    public static double tiltMultiplier() {
        var tuning = com.mlh.aero_player_tilt.apiimpl.ApiRegistry.tuning();
        return tuning != null ? tuning.multiplier() : configuredTiltMultiplier();
    }

    public static double configuredTiltMultiplier() {
        return Config.flag(Config.USE_TILT_MULTIPLIER, false)
                ? Config.value(Config.TILT_MULTIPLIER, 0.5)
                : 1.0;
    }

    public static boolean stickToDeck() {
        return Config.flag(Config.STICK_TO_DECK, true);
    }
}
