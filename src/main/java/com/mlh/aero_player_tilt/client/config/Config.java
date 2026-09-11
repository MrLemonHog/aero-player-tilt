package com.mlh.aero_player_tilt.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("What the tilt is, and when it is allowed to exist at all.").push("general");
    }

    public static final ModConfigSpec.BooleanValue MOD_ENABLED =
            BUILDER.comment("Enable Mod. Turns the mod on or off.")
                    .define("enabled", true);

    public static final ModConfigSpec.BooleanValue PLAYER_TILT =
            BUILDER.comment("Tilt Player Body. Tilts the player's body")
                    .define("playerTilt", true);

    public static final ModConfigSpec.BooleanValue ROTATE_CAMERA =
            BUILDER.comment("Rotate Camera. Tilts the camera with the body. Off keeps the camera\n" +
                            "aligned with the world.")
                    .define("rotateCamera", true);

    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y =
            BUILDER.comment("Max Tilt Threshold. The steepest floor you can still stand on.\n" +
                            "\n" +
                            "Past 37\u00b0 you slide off. Turn on \"Sticky tilt\" below.\n" +
                            "A rule of this world, sent to everyone who joins. Turned on with\n" +
                            "enforceMaxTilt in the world's config.\n" +
                            "Set by the server, so nobody can lean further than anyone else.")
                    .defineInRange("minNormalY", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue GRIP_SLOPES =
            BUILDER.comment("Sticky tilt. Don't slide off tilted decks.\n" +
                            "\n" +
                            "Magnetic boots are on \u2014 they already hold you to any face.")
                    .define("gripSlopes", false);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.DeckGravity> DECK_GRAVITY =
            BUILDER.comment("Gravity. Which way you fall above a tilted sub-level.\n" +
                            "\n" +
                            "WORLD - Down is the world's own. A jump lands downhill of where you\n" +
                            "took off.\n" +
                            "DECK - Down is the sub-level's. A jump lands where you took off, as on\n" +
                            "flat ground.\n" +
                            "\n" +
                            "Set by the server for everyone.")
                    .defineEnum("deckGravity", com.mlh.aero_player_tilt.tilt.DeckGravity.DECK);

    public static final ModConfigSpec.BooleanValue USE_TILT_MULTIPLIER =
            BUILDER.comment("Tilt multiplier. How much of the tilt the body takes. 0 = stay\n" +
                            "upright.")
                    .define("useTiltMultiplier", false);

    public static final ModConfigSpec.DoubleValue TILT_MULTIPLIER =
            BUILDER.comment("Tilt multiplier. How much of the tilt the body takes. 0 = stay\n" +
                            "upright.")
                    .defineInRange("tiltMultiplier", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<String> TOGGLE_KEY =
            BUILDER.comment("On/off key bind. Bind a key.")
                    .define("toggleKey", "key.keyboard.o");

    public static final ModConfigSpec.ConfigValue<String> OPEN_CONFIG_KEY =
            BUILDER.comment("Open this window. Bind a key.")
                    .define("openConfigKey", "");

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("How the tilt travels: how fast it follows the floor, and how long it is held\n" +
                        "on to when the floor goes missing.").push("motion");
    }

    public static final ModConfigSpec.DoubleValue SMOOTH_SPEED =
            BUILDER.comment("Tilt Speed. How fast the body leans onto the floor under it. 0 = snap,\n" +
                            "higher = lazier.")
                    .defineInRange("smoothSpeed", 1.7, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue SMOOTH_SPEED_EXIT =
            BUILDER.comment("Straighten Speed. How fast the body straightens up again once it is\n" +
                            "off the sub-level.")
                    .defineInRange("smoothSpeedExit", 4.0, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue TAKEOVER_TICKS =
            BUILDER.comment("Camera take-over. How long the camera takes to move onto the body. 0 =\n" +
                            "at once, with a jolt.")
                    .defineInRange("takeoverTicks", 4.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue REMOTE_SMOOTH_SPEED =
            BUILDER.comment("Other Players' Smoothing. Smoothing of other players' tilt. 0 = off.")
                    .defineInRange("remoteSmoothSpeed", 1.5, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue HOLD_TICKS =
            BUILDER.comment("Hold after losing the floor. How long the tilt survives with no floor\n" +
                            "found.")
                    .defineInRange("holdTicks", 5.0, 0.0, 200.0);

    public static final ModConfigSpec.DoubleValue AIRBORNE_HOLD_TICKS =
            BUILDER.comment("Hold through a jump. Keeps the tilt while you are in the air over your\n" +
                            "sub-level, so a jump does not straighten you. Needs landing\n" +
                            "prediction.")
                    .defineInRange("airborneHoldTicks", 60.0, 0.0, 400.0);

    public static final ModConfigSpec.BooleanValue STICK_TO_DECK =
            BUILDER.comment("Stop sliding on slopes. Count as standing while there is floor within\n" +
                            "a step below. Stops the sliding on slopes.")
                    .define("stickToDeck", true);

    public static final ModConfigSpec.BooleanValue TURN_WITH_DECK =
            BUILDER.comment("Turn with the sub-level. On a spinning sub-level, keep facing the same\n" +
                            "way relative to it, not to the world.")
                    .define("turnWithDeck", true);

    public static final ModConfigSpec.DoubleValue DECK_MOMENTUM =
            BUILDER.comment("Carry momentum sub-level. Makes a fast-spinning sub-level comfortable\n" +
                            "to walk on. 1.0 = with the spin, 0 = off.\n" +
                            "\n" +
                            "Sable: Sure Footing does the same (rotate_ground_velocity). Leave one\n" +
                            "of the two on.")
                    .defineInRange("deckMomentum", 1.0, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue DECK_CARRY_TICKS =
            BUILDER.comment("Sub-level stickiness. How long a sub-level carries you while it only\n" +
                            "touches you from the side.")
                    .defineInRange("deckCarryTicks", 20.0, 0.0, 200.0);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("Which sub-levels are worth tilting onto at all, and when a tilt is dropped.")
                .push("activation");
    }

    public static final ModConfigSpec.BooleanValue GATE_MASS_ENABLED =
            BUILDER.comment("Mass. Tilt only on sub-levels at least this heavy.")
                    .define("gateMassEnabled", false);
    public static final ModConfigSpec.DoubleValue GATE_MASS_MIN =
            BUILDER.comment("Mass. Tilt only on sub-levels at least this heavy.")
                    .defineInRange("gateMassMin", 10.0, 0.0, 100_000_000.0);

    public static final ModConfigSpec.BooleanValue GATE_BLOCKS_ENABLED =
            BUILDER.comment("Block Count. Tilt only on sub-levels with at least this many blocks.")
                    .define("gateBlocksEnabled", false);
    public static final ModConfigSpec.IntValue GATE_BLOCKS_MIN =
            BUILDER.comment("Block Count. Tilt only on sub-levels with at least this many blocks.")
                    .defineInRange("gateBlocksMin", 10, 0, 100_000_000);

    public static final ModConfigSpec.BooleanValue GATE_LENGTH_ENABLED =
            BUILDER.comment("Length (X). Tilt only on sub-levels at least this long.")
                    .define("gateLengthEnabled", false);
    public static final ModConfigSpec.IntValue GATE_LENGTH_MIN =
            BUILDER.comment("Length (X). Tilt only on sub-levels at least this long.")
                    .defineInRange("gateLengthMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue GATE_HEIGHT_ENABLED =
            BUILDER.comment("Height (Y). Tilt only on sub-levels at least this tall.")
                    .define("gateHeightEnabled", false);
    public static final ModConfigSpec.IntValue GATE_HEIGHT_MIN =
            BUILDER.comment("Height (Y). Tilt only on sub-levels at least this tall.")
                    .defineInRange("gateHeightMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue GATE_WIDTH_ENABLED =
            BUILDER.comment("Width (Z). Tilt only on sub-levels at least this wide.")
                    .define("gateWidthEnabled", false);
    public static final ModConfigSpec.IntValue GATE_WIDTH_MIN =
            BUILDER.comment("Width (Z). Tilt only on sub-levels at least this wide.")
                    .defineInRange("gateWidthMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue DROP_CACHE_ON_ALL_MISS =
            BUILDER.comment("Instantly forget Sublevel. Forget the sub-level as soon as every ray\n" +
                            "misses.")
                    .define("dropOnAllMiss", true);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.ZoneTilt> ZONE_TILT =
            BUILDER.comment("Tilt inside the sub-level. How much a sub-level you are INSIDE decides\n" +
                            "which way is up.\n" +
                            "\n" +
                            "OFF - Only the rays underfoot decide.\n" +
                            "NO_FLOOR - The sub-level decides only where the rays find nothing.\n" +
                            "ALWAYS - The sub-level decides as long as you are inside it. Best\n" +
                            "while building.\n" +
                            "\n" +
                            "\"Disable on flying\" below switches this off while you fly.")
                    .defineEnum("zoneMode", com.mlh.aero_player_tilt.tilt.ZoneTilt.OFF);

    public static final ModConfigSpec.DoubleValue ZONE_REACH =
            BUILDER.comment("Zone reach. How far outside the hull still counts as inside, in\n" +
                            "blocks.")
                    .defineInRange("zoneReach", 2.0, 0.0, 32.0);

    public static final ModConfigSpec.DoubleValue ZONE_LINGER_TICKS =
            BUILDER.comment("Keep after leaving. How long the vessel keeps you after you leave\n" +
                            "sub-level hitbox.")
                    .defineInRange("zoneLingerTicks", 10.0, 0.0, 200.0);

    public static final ModConfigSpec.BooleanValue DISABLE_ON_FLYING =
            BUILDER.comment("Disable on flying. Do not tilt while flying in Creative or Spectator\n" +
                            "\n" +
                            "This switches the tilt inside a sub-level off while you fly.")
                    .define("disableOnFlying", true);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("The rays that find the floor under the feet. More of them, and longer ones,\n" +
                        "cost more per frame; the defaults are cheap and land on ordinary decks.")
                .push("raycast");
    }

    public static final ModConfigSpec.IntValue RAYCAST_COUNT =
            BUILDER.comment("Raycast Count. How many rays sample the floor.")
                    .defineInRange("count", 10, 1, 10000);

    public static final ModConfigSpec.DoubleValue RAYCAST_DOWN_LENGTH =
            BUILDER.comment("Distance to Floor. How far down to look for a surface.")
                    .defineInRange("downLength", 7.0, 0.1, 12.0);

    public static final ModConfigSpec.DoubleValue RAYCAST_UP_LENGTH =
            BUILDER.comment("Foot Offset. Where the rays start, measured from the feet.")
                    .defineInRange("upLength", 0.2, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue RAYCAST_RADIUS =
            BUILDER.comment("Ring radius. How wide the ring of rays spreads, in blocks.")
                    .defineInRange("radius", 0.58, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue BLEND_FOOTING =
            BUILDER.comment("Blend underfoot. Read the floor from everything underfoot, not just\n" +
                            "one sub-level. Stops flickering.")
                    .define("blendFooting", true);

    public static final ModConfigSpec.DoubleValue FOOTING_GRIP =
            BUILDER.comment("Footing depth. How far below the boots a surface still counts as your\n" +
                            "floor.")
                    .defineInRange("footingGrip", 0.6, 0.05, 4.0);

    public static final ModConfigSpec.DoubleValue FOOTING_MARGIN =
            BUILDER.comment("Swap margin. Standing across two sub-levels, how much more of your\n" +
                            "weight the other one has to take before it becomes the one holding\n" +
                            "you.")
                    .defineInRange("footingMargin", 0.2, 0.0, 0.9);

    public static final ModConfigSpec.DoubleValue FOOTING_DWELL_TICKS =
            BUILDER.comment("Swap delay. How many ticks it has to keep that lead. Together the two\n" +
                            "stop a seam throwing you between the sub-levels.")
                    .defineInRange("footingDwellTicks", 4.0, 0.0, 60.0);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("Unfinished work: may look wrong in cases nobody has walked into yet, and\n" +
                        "may change shape between builds. Each entry says whether it is on.").push("experimental");
    }

    public static final ModConfigSpec.BooleanValue PREDICT_LANDING =
            BUILDER.comment("Aim at the landing. Turn towards the floor you are going to land on\n" +
                            "during the flight.")
                    .define("predictLanding", true);

    public static final ModConfigSpec.IntValue PREDICT_HORIZON_TICKS =
            BUILDER.comment("Look ahead. How far ahead a jump is simulated.")
                    .defineInRange("predictHorizonTicks", 40, 1, 200);

    public static final ModConfigSpec.DoubleValue LANDING_LEAD_TICKS =
            BUILDER.comment("Settle before touchdown. Finish the turn this many ticks before\n" +
                            "landing.")
                    .defineInRange("landingLeadTicks", 2.0, 0.0, 20.0);

    public static final ModConfigSpec.BooleanValue MAGNETIC_BOOTS =
            BUILDER.comment("Magnetic boots. Any face becomes your floor \u2014 walls, ceilings. Jump\n" +
                            "again in the air to let go.")
                    .define("test", false);

    public static final ModConfigSpec.DoubleValue MAGNETIC_REACH =
            BUILDER.comment("Reach. How far below your feet to look for a surface.")
                    .defineInRange("testReach", 0.75, 0.1, 4.0);

    public static final ModConfigSpec.DoubleValue MAGNETIC_LEAN =
            BUILDER.comment("Lean start. How close an edge has to be before the body leans over it.\n" +
                            "0 \u2014 no lean.")
                    .defineInRange("testLeanStart", 0.30, 0.0, 0.5);

    public static final ModConfigSpec.DoubleValue MAGNETIC_PULL =
            BUILDER.comment("Grip. How hard the boots press into the face. Doesn't affect jumps.")
                    .defineInRange("testPull", 1.0, 0.25, 6.0);

    public static final ModConfigSpec.DoubleValue MAGNETIC_SMOOTH =
            BUILDER.comment("Turn speed. How long the body takes to turn onto a new face. 0 \u2014\n" +
                            "instant.")
                    .defineInRange("testSmooth", 1.0, 0.0, 20.0);

    public static final ModConfigSpec.BooleanValue TILT_MOBS =
            BUILDER.comment("Mob tilting\n" +
                            "\n" +
                            "Yours only: this server has no mod.\n" +
                            "Set by the server: nothing is sent for a mob's tilt, so the host\n" +
                            "decides.")
                    .define("tiltMobs", false);

    public static final ModConfigSpec.BooleanValue TILT_ITEMS =
            BUILDER.comment("Item tilting\n" +
                            "\n" +
                            "Yours only: this server has no mod.\n" +
                            "Set by the server: nothing is sent for a mob's tilt, so the host\n" +
                            "decides.")
                    .define("tiltItems", false);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("Only useful when something is wrong.").push("debug");
    }

    public static final ModConfigSpec.BooleanValue DEBUG_RAYS =
            BUILDER.comment("Show Debug Rays. Draw the floor rays and what they hit.")
                    .define("rays", false);

    public static final ModConfigSpec.BooleanValue DEBUG_MESSAGES =
            BUILDER.comment("Show debug messages in console. Write the tilt's log lines to the\n" +
                            "console.").define("debugMessages", false);

    public static final ModConfigSpec.BooleanValue DEBUG_TILT_SYNC =
            BUILDER.comment("Tilt vs camera panel. Panel comparing body tilt with Camera Sync's\n" +
                            "camera tilt.")
                    .define("tiltSyncPanel", false);

    public static final ModConfigSpec.BooleanValue DEBUG_FRAME_TRACE =
            BUILDER.comment("Frame trace. Log every frame around a landing or a camera jump.")
                    .define("frameTrace", false);

    public static final ModConfigSpec.DoubleValue DEBUG_FRAME_TRACE_JUMP =
            BUILDER.comment("Jump threshold")
                    .defineInRange("frameTraceJump", 0.75, 0.05, 45.0);

    static { BUILDER.pop(); }

    public static final int CURRENT_CONFIG_SCHEMA = 7;

    public static final ModConfigSpec.IntValue CONFIG_SCHEMA_VERSION =
            BUILDER.comment("Config schema version. Do not edit manually.\n" +
                            "Used to detect when a config reset prompt should be shown.")
                                    .defineInRange("configSchemaVersion", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean isLoaded() {
        try {
            MOD_ENABLED.get();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static boolean flag(ModConfigSpec.BooleanValue value, boolean fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static double value(ModConfigSpec.DoubleValue value, double fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static int value(ModConfigSpec.IntValue value, int fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static com.mlh.aero_player_tilt.tilt.DeckGravity deckGravity() {
        return SPEC.isLoaded() ? DECK_GRAVITY.get() : com.mlh.aero_player_tilt.tilt.DeckGravity.DECK;
    }
}
