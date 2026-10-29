package com.mlh.aero_player_tilt;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    private ServerConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TILT_MOBS =
            BUILDER.define("tiltMobs", true);

    public static final ModConfigSpec.BooleanValue TILT_ITEMS =
            BUILDER.define("tiltItems", true);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.EntityFilter.Mode> MOB_LIST_MODE =
            BUILDER.comment("BLACKLIST: mobList is never tilted. WHITELIST: only mobList is tilted.")
                    .defineEnum("mobListMode", com.mlh.aero_player_tilt.tilt.EntityFilter.Mode.BLACKLIST);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MOB_LIST =
            BUILDER.comment("Mob ids, \"#\" tags or \"modid:*\", e.g. [\"minecraft:cow\", \"#minecraft:raiders\", \"alexsmobs:*\"]")
                    .defineListAllowEmpty("mobList", java.util.List.of(), () -> "", entry -> entry instanceof String);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.EntityFilter.Mode> ITEM_LIST_MODE =
            BUILDER.comment("BLACKLIST: itemList is never tilted. WHITELIST: only itemList is tilted.")
                    .defineEnum("itemListMode", com.mlh.aero_player_tilt.tilt.EntityFilter.Mode.BLACKLIST);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ITEM_LIST =
            BUILDER.comment("Dropped item ids, \"#\" tags or \"modid:*\", e.g. [\"minecraft:diamond\", \"#minecraft:swords\"]")
                    .defineListAllowEmpty("itemList", java.util.List.of(), () -> "", entry -> entry instanceof String);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.DeckGravity> DECK_GRAVITY =
            BUILDER.defineEnum("deckGravity", com.mlh.aero_player_tilt.tilt.DeckGravity.DECK);

    public static final ModConfigSpec.BooleanValue ENFORCE_MIN_NORMAL_Y =
            BUILDER.define("enforceMaxTilt", false);
    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y =
            BUILDER.defineInRange("maxTilt", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue ALLOW_MAGNETIC_BOOTS =
            BUILDER.define("allowMagneticBoots", true);

    public static final ModConfigSpec.BooleanValue ALLOW_STICKY_TILT =
            BUILDER.define("allowStickyTilt", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
