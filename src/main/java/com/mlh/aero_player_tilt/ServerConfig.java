package com.mlh.aero_player_tilt;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    private ServerConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TILT_MOBS =
            BUILDER.define("tiltMobs", true);

    public static final ModConfigSpec.BooleanValue TILT_ITEMS =
            BUILDER.define("tiltItems", true);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.DeckGravity> DECK_GRAVITY =
            BUILDER.defineEnum("deckGravity", com.mlh.aero_player_tilt.tilt.DeckGravity.DECK);

    public static final ModConfigSpec.BooleanValue ENFORCE_MIN_NORMAL_Y =
            BUILDER.define("enforceMaxTilt", false);
    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y =
            BUILDER.defineInRange("maxTilt", 0.8, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
