package com.mlh.aero_player_tilt.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("What the tilt is, and when it is allowed to exist at all.").push("general");
    }

    public static final ModConfigSpec.BooleanValue MOD_ENABLED =
            BUILDER.comment("The whole mod. Off leaves plain Sable behaviour.")
                    .define("enabled", true);

    public static final ModConfigSpec.BooleanValue PLAYER_TILT =
            BUILDER.comment("Tilt the player's body along with the contraption.\n" +
                            "Rotates the model, the collision box and the movement/jump basis, so a tilted\n" +
                            "1-wide gap can actually be walked through. Other players see the tilt only when\n" +
                            "the mod is installed on the server.")
                    .define("playerTilt", true);

    public static final ModConfigSpec.DoubleValue MIN_NORMAL_Y =
            BUILDER.comment("Maximum tilt threshold (0.0 - 1.0): the minimum Y of a deck normal that still\n" +
                            "counts as walkable. Surfaces steeper than this are never tilted onto, and a tilt\n" +
                            "the ship rotates past this limit is dropped instead of held - so the player falls\n" +
                            "off a near-vertical surface the way they do in plain Sable.\n" +
                            "\n" +
                            "Yours only: it shapes the tilt YOUR client computes, and that finished tilt\n" +
                            "is what everyone else is sent, so nobody has to share the setting to agree\n" +
                            "about the result. Mobs and items are the other way round and take the\n" +
                            "world's entityMinNormalY instead; this value stands in for it only when\n" +
                            "the server does not have the mod.")
                    .defineInRange("minNormalY", 0.8, 0.0, 1.0);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.DeckGravity> DECK_GRAVITY =
            BUILDER.comment("Which way you fall while airborne over a leaning deck.\n" +
                            "\n" +
                            "WORLD - the world's own down, which is what you get without this mod. A jump\n" +
                            "leaves along the deck's normal and nothing ever brings it back, so you land\n" +
                            "downhill of where you took off: roughly\n" +
                            "4.4*sin*cos blocks, which is 0.75 at 10 degrees, 1.4 at 20, 1.9 at 30. That is\n" +
                            "geometry, not a bug - under world gravity, leaving along the deck and landing\n" +
                            "on the same spot cannot both hold.\n" +
                            "\n" +
                            "DECK - the deck's down. The whole flight happens in the deck's frame, so a\n" +
                            "jump looks vertical on a tilted screen AND puts you back where you started,\n" +
                            "the way it does on flat ground. Covers every flight over the deck, not only\n" +
                            "jumps: a step off a ledge and a block broken underfoot are the same flight,\n" +
                            "and having one of them behave differently is what reads as a bug. The\n" +
                            "default.")
                    .defineEnum("deckGravity", com.mlh.aero_player_tilt.tilt.DeckGravity.DECK);

    public static final ModConfigSpec.BooleanValue USE_TILT_MULTIPLIER =
            BUILDER.comment("Scale the finished tilt instead of matching the deck exactly. Applied at the\n" +
                            "very end, so the model, the collision box, the movement basis and everyone\n" +
                            "else's copy all see the same scaled tilt. Off means a plain multiplier of 1.")
                    .define("useTiltMultiplier", false);

    public static final ModConfigSpec.DoubleValue TILT_MULTIPLIER =
            BUILDER.comment("1.0 leans with the floor, 0.5 halfway, 0.0 stays upright.\n" +
                            "Ignored while useTiltMultiplier is off.")
                    .defineInRange("tiltMultiplier", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<String> TOGGLE_KEY =
            BUILDER.comment("Toggle mod keybind. Not I: Aeronautics Camera Sync's own toggle is\n" +
                            "bound there, and one press would flip both mods at once.")
                    .define("toggleKey", "key.keyboard.o");

    public static final ModConfigSpec.ConfigValue<String> OPEN_CONFIG_KEY =
            BUILDER.comment("Open config screen keybind")
                    .define("openConfigKey", "");

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("How the tilt travels: how fast it follows the floor, and how long it is held\n" +
                        "on to when the floor goes missing.").push("motion");
    }

    public static final ModConfigSpec.DoubleValue SMOOTH_SPEED =
            BUILDER.comment("Body tilt interpolation half-life, in ticks (0.0 = instant snap, 9999 = never moves).\n" +
                            "Smoothing is measured in the deck's own frame, so a rotating sub-level does not\n" +
                            "make the body lag behind it - only real changes (stepping on/off the deck,\n" +
                            "walking onto a ramp) are eased.")
                    .defineInRange("smoothSpeed", 1.7, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue SMOOTH_SPEED_EXIT =
            BUILDER.comment("Half-life, in ticks, of the tilt STRAIGHTENING back to vertical: the player left\n" +
                            "the deck, or the surface under his feet stopped being walkable. Same units as\n" +
                            "smoothSpeed (0.0 = instant snap), but separate, because the two are wanted at\n" +
                            "different speeds: settling onto a deck should be quick enough to feel attached,\n" +
                            "while getting off it looks better as a slow, deliberate straightening.\n" +
                            "Switching from one sub-level to another is NOT this - that eases at smoothSpeed,\n" +
                            "since the body goes straight from one deck's normal to the other's.")
                    .defineInRange("smoothSpeedExit", 4.0, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue TAKEOVER_TICKS =
            BUILDER.comment("How long, in ticks, the CAMERA takes to slide onto the body when this mod takes\n" +
                            "the camera over from Aeronautics Camera Sync's own tilt.\n" +
                            "\n" +
                            "Only one of the two is steering at a time. While we are, ACS is handed our\n" +
                            "rotation and keeps it, so letting go is seamless - it simply carries on from\n" +
                            "where we left it. Taking over is not: ACS works the deck out for itself on the\n" +
                            "frames we say nothing, and the moment we speak up its value is replaced by ours,\n" +
                            "whatever the two happen to be. Land on a steep deck after a fall and those are\n" +
                            "far apart - the body is still upright and only starting to turn, while the camera\n" +
                            "has been leaning with the floor it was falling towards - so the whole difference\n" +
                            "is spent in one frame. That is the jolt on touchdown, and why stepping off the\n" +
                            "same deck is smooth.\n" +
                            "\n" +
                            "So the hand-over is walked instead: the camera keeps the rotation it had and\n" +
                            "arrives on the body's over this many ticks, along the same smoothstep a predicted\n" +
                            "landing is flown on - it leaves gently and comes to rest, rather than trailing a\n" +
                            "fraction of a degree behind long after the body has settled. Costs those few\n" +
                            "ticks where the camera is not exactly the body's eye - which is also where aim\n" +
                            "comes from, so it is deliberately short.\n" +
                            "0.0 = take the camera the instant we claim it, jolt and all.")
                    .defineInRange("takeoverTicks", 4.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue REMOTE_SMOOTH_SPEED =
            BUILDER.comment("Half-life, in ticks, of OTHER players' tilt as this client draws it.\n" +
                            "\n" +
                            "A body works its own tilt out every frame, but what reaches everyone else is\n" +
                            "a 20 Hz sample of it, arriving on the network's schedule rather than on the\n" +
                            "viewer's ticks. Drawn as it arrives, a tick with no packet freezes the body\n" +
                            "and the next one moves it twice as far - which is unnoticeable standing\n" +
                            "still and is the entire motion when someone crosses from one sub-level to\n" +
                            "another, where the whole swing takes a handful of ticks.\n" +
                            "\n" +
                            "So the reported value is followed on the frame clock instead, the same way\n" +
                            "smoothSpeed follows the floor. Measured in the deck's own frame, so a\n" +
                            "rotating sub-level is still tracked exactly. Costs that half-life in extra\n" +
                            "lag behind what the sender is doing, which for a lean nobody can see.\n" +
                            "0.0 = off: draw each value the moment it arrives, steps and all.")
                    .defineInRange("remoteSmoothSpeed", 1.5, 0.0, 9999.0);

    public static final ModConfigSpec.DoubleValue HOLD_TICKS =
            BUILDER.comment("How long, in ticks, a tilt is kept after the ground under it stops answering -\n" +
                            "a seam between two plots, a step, a ray that missed. Long enough to bridge\n" +
                            "those, short enough that a body which really has left its floor does not stay\n" +
                            "leaning. 0.0 = straighten the moment the floor is lost.")
                    .defineInRange("holdTicks", 5.0, 0.0, 200.0);

    public static final ModConfigSpec.DoubleValue AIRBORNE_HOLD_TICKS =
            BUILDER.comment("The same budget, but for a jump that still has its deck underneath it. A\n" +
                            "flight is not a body that lost its floor - it is coming back down onto it -\n" +
                            "so straightening in mid-air is exactly what nobody wants to see. Only applies\n" +
                            "while predictLanding is on; without it a jump is held by holdTicks like\n" +
                            "anything else.")
                    .defineInRange("airborneHoldTicks", 60.0, 0.0, 400.0);

    public static final ModConfigSpec.BooleanValue STICK_TO_DECK =
            BUILDER.comment("Count a body walking along a deck as standing on it, even on the ticks its boots\n" +
                            "are a finger's width clear of the floor.\n" +
                            "\n" +
                            "A body can step UP what its step height allows and cannot step DOWN anything at\n" +
                            "all - it walks off the edge and falls. On flat ground that is invisible, because\n" +
                            "the next step lands at the same height. On a deck leaning thirty degrees every\n" +
                            "stride does it: the floor drops away under the boots, the body falls a tick,\n" +
                            "catches it, and leaves again - never two ticks on the ground together.\n" +
                            "\n" +
                            "Half the ticks airborne is half the ticks at air drag instead of ground friction,\n" +
                            "so the body accelerates like something thrown rather than something walking,\n" +
                            "footsteps stop and the controls go vague. That is what sliding across a deck after\n" +
                            "a landing actually is: not a body sliding on it, a body touching it every other\n" +
                            "tick.\n" +
                            "\n" +
                            "This changes nothing about where the body IS - it is not moved, pulled down or\n" +
                            "slowed - only whether the game counts it as standing. It rides the last tenth of a\n" +
                            "block above the deck, which nobody can see, and is moved at walking friction,\n" +
                            "which everybody can feel. A ray still has to find deck within one step below, so\n" +
                            "walking off a real edge falls the way it always did.\n")
                    .define("stickToDeck", true);

    public static final ModConfigSpec.BooleanValue TURN_WITH_DECK =
            BUILDER.comment("Turn with a sub-level that spins under your feet, so you keep facing the same way\n" +
                            "along the deck rather than the same way in the world.\n" +
                            "\n" +
                            "This is plain Sable behaviour, and it is what you get standing on a turning\n" +
                            "platform without this mod: Sable takes the deck's turn back out of your yaw every\n" +
                            "frame, so the mast you were looking at stays in front of you and walking towards\n" +
                            "it is one held key. Sable stands that down the moment an entity carries an\n" +
                            "orientation of its own, which a leaning body always does - so with the lean on,\n" +
                            "and only then, the deck used to rotate out from under the view and a spinning\n" +
                            "propeller could not be walked on at all.\n" +
                            "\n" +
                            "On, the turn is taken back out the same way Sable does it, measured about the\n" +
                            "body's own up rather than the world's, since that is the axis a leaning body\n" +
                            "turns about. Off leaves the view where it was - the deck spins beneath you and\n" +
                            "your heading is the world's.\n" +
                            "\n" +
                            "Only decides what happens while you are LEANING. With no lean Sable answers for\n" +
                            "this, and it always turns you.")
                    .define("turnWithDeck", true);

    public static final ModConfigSpec.DoubleValue DECK_MOMENTUM =
            BUILDER.comment("How much of the deck's turn your standing MOMENTUM is turned by, as a share of\n" +
                            "what your heading was turned by. Only while turnWithDeck is doing the turning.\n" +
                            "\n" +
                            "Turning the heading is only half of walking a spinner. A tick of walking keeps\n" +
                            "about half of last tick's velocity, and that half is a world direction: it still\n" +
                            "points where the deck was pointing a tick ago, while the stride you are adding\n" +
                            "points where it points now. The two are added, so you travel between them - hold\n" +
                            "W and you go diagonally, leaning towards the trailing side, by an angle that\n" +
                            "grows with how fast the deck turns. At a third of a turn a second that is most\n" +
                            "of twenty degrees, which is not a nuisance, it is unwalkable.\n" +
                            "\n" +
                            "So the leftover is turned by exactly what the heading was turned by, at the top\n" +
                            "of the tick that is about to spend it, and the stride adds to a momentum already\n" +
                            "pointing the same way.\n" +
                            "\n" +
                            "1.0 carries it exactly. Higher leads: a tick is spent travelling a straight line\n" +
                            "in the world while the deck keeps turning under it, so about half a tick of turn\n" +
                            "is always owed. Raise this if you still drift towards the trailing side, lower it\n" +
                            "if you curl into the spin. 0.0 = off, which is the drift described above.\n" +
                            "\n" +
                            "Sable: Sure Footing does the same thing from its own end (rotate_ground_velocity).\n" +
                            "Run one or the other - both at once over-rotates and curls you into the spin.")
                    .defineInRange("deckMomentum", 1.0, 0.0, 3.0);

    public static final ModConfigSpec.DoubleValue DECK_CARRY_TICKS =
            BUILDER.comment("How sticky a deck is, in ticks. A tilted body brushing a sub-level with its\n" +
                            "shoulder is not standing on it, so that touch is not allowed to carry him\n" +
                            "along - but a body that WAS standing on it a moment ago still is, even on the\n" +
                            "ticks the floor answers with a side contact instead of one from below: a step,\n" +
                            "a seam, the corner of a stair. This is how long that memory lasts.\n" +
                            "0.0 = a deck carries only on the ticks it is provably underfoot, which lets\n" +
                            "go of ledges and stairs; higher values hold on to a wall you are merely\n" +
                            "leaning against.")
                    .defineInRange("deckCarryTicks", 20.0, 0.0, 200.0);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("Which sub-levels are worth tilting onto at all, and when a tilt is dropped.")
                .push("activation");
    }

    public static final ModConfigSpec.BooleanValue GATE_MASS_ENABLED =
            BUILDER.comment("Only tilt when the sub-level mass is at least the threshold (primary factor)")
                    .define("gateMassEnabled", false);
    public static final ModConfigSpec.DoubleValue GATE_MASS_MIN =
            BUILDER.comment("Minimum sub-level mass to allow tilt")
                    .defineInRange("gateMassMin", 10.0, 0.0, 100_000_000.0);

    public static final ModConfigSpec.BooleanValue GATE_BLOCKS_ENABLED =
            BUILDER.comment("Only tilt when the sub-level block count is at least the threshold")
                    .define("gateBlocksEnabled", false);
    public static final ModConfigSpec.IntValue GATE_BLOCKS_MIN =
            BUILDER.comment("Minimum sub-level block count to allow tilt")
                    .defineInRange("gateBlocksMin", 10, 0, 100_000_000);

    public static final ModConfigSpec.BooleanValue GATE_LENGTH_ENABLED =
            BUILDER.comment("Only tilt when the sub-level length (X) is at least the threshold")
                    .define("gateLengthEnabled", false);
    public static final ModConfigSpec.IntValue GATE_LENGTH_MIN =
            BUILDER.comment("Minimum sub-level length in blocks (X) to allow tilt")
                    .defineInRange("gateLengthMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue GATE_HEIGHT_ENABLED =
            BUILDER.comment("Only tilt when the sub-level height (Y) is at least the threshold")
                    .define("gateHeightEnabled", false);
    public static final ModConfigSpec.IntValue GATE_HEIGHT_MIN =
            BUILDER.comment("Minimum sub-level height in blocks (Y) to allow tilt")
                    .defineInRange("gateHeightMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue GATE_WIDTH_ENABLED =
            BUILDER.comment("Only tilt when the sub-level width (Z) is at least the threshold")
                    .define("gateWidthEnabled", false);
    public static final ModConfigSpec.IntValue GATE_WIDTH_MIN =
            BUILDER.comment("Minimum sub-level width in blocks (Z) to allow tilt")
                    .defineInRange("gateWidthMin", 3, 1, 100_000);

    public static final ModConfigSpec.BooleanValue DROP_CACHE_ON_ALL_MISS =
            BUILDER.comment("Forget the tracked sub-level the moment every ray misses, instead of keeping\n" +
                            "it in case the floor comes back. Stops the camera picking the deck up again\n" +
                            "once the player is above and clear of it.")
                    .define("dropOnAllMiss", true);

    public static final ModConfigSpec.EnumValue<com.mlh.aero_player_tilt.tilt.ZoneTilt> ZONE_TILT =
            BUILDER.comment("How much a sub-level you are INSIDE gets to say about which way is up.\n" +
                            "\n" +
                            "The rays answer what is under the boots, which is the right question on a deck and\n" +
                            "the wrong one everywhere else aboard: halfway up a jump, on a ladder, out over an\n" +
                            "open hold, drifting through the hangar in creative. And while BUILDING one it is the\n" +
                            "wrong question altogether - a frame that holds still is worth more than the exact\n" +
                            "slope of whatever block you happen to be standing on.\n" +
                            "\n" +
                            "OFF - the rays are the only answer.\n" +
                            "NO_FLOOR - the vessel answers on the frames the rays cannot.\n" +
                            "ALWAYS - the vessel answers for as long as you are inside it, floor or no floor.\n" +
                            "\n" +
                            "Overlapping vessels are settled by how deep inside each one you are, and the same\n" +
                            "margin and delay that keep a seam from flip-flopping keep two hulls from trading\n" +
                            "you back and forth.\n")
                    .defineEnum("zoneMode", com.mlh.aero_player_tilt.tilt.ZoneTilt.OFF);

    public static final ModConfigSpec.DoubleValue ZONE_REACH =
            BUILDER.comment("How far past the hull the zone still counts, in blocks.\n" +
                            "\n" +
                            "Zero is the vessel itself and nothing more, which is too mean a line to build on:\n" +
                            "step off the edge to place a block and the frame lets go of you. A few blocks of\n" +
                            "reach puts the boundary out where the scaffolding is. Measured outward from the\n" +
                            "sub-level's own shape, not from the box the world draws round it - a rotated hull's\n" +
                            "world box is a sack several blocks bigger than the ship in every direction, and\n" +
                            "standing in the sack is not standing aboard.\n")
                    .defineInRange("zoneReach", 2.0, 0.0, 32.0);

    public static final ModConfigSpec.DoubleValue ZONE_LINGER_TICKS =
            BUILDER.comment("How long the vessel keeps you after you have left its zone, in ticks.\n" +
                            "\n" +
                            "A boundary crossed is not always a boundary left: a jump that carries you out and\n" +
                            "back, a step off the scaffold, a hull that drifts out from under you. Without this,\n" +
                            "the frame drops the moment you cross and picks you up again a tick later, which is\n" +
                            "the flicker this whole mod spends its time avoiding elsewhere. The tilt follows the\n" +
                            "vessel while it lasts, so a ship that turns during it turns you with it.\n" +
                            "0.0 = let go on the frame the boundary is crossed.\n")
                    .defineInRange("zoneLingerTicks", 10.0, 0.0, 200.0);

    public static final ModConfigSpec.BooleanValue DISABLE_ON_FLYING =
            BUILDER.comment("Stop body tilt when flying (Creative or Spectator)")
                    .define("disableOnFlying", true);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("The rays that find the floor under the feet. More of them, and longer ones,\n" +
                        "cost more per frame; the defaults are cheap and land on ordinary decks.")
                .push("raycast");
    }

    public static final ModConfigSpec.IntValue RAYCAST_COUNT =
            BUILDER.comment("Number of raycasts (10 is usually enough)")
                    .defineInRange("count", 10, 1, 10000);

    public static final ModConfigSpec.DoubleValue RAYCAST_DOWN_LENGTH =
            BUILDER.comment("Distance from player down to floor")
                    .defineInRange("downLength", 7.0, 0.1, 12.0);

    public static final ModConfigSpec.DoubleValue RAYCAST_UP_LENGTH =
            BUILDER.comment("Raycast start offset from foot")
                    .defineInRange("upLength", 0.2, -1.0, 1.0);

    public static final ModConfigSpec.DoubleValue RAYCAST_RADIUS =
            BUILDER.comment("How far out from the middle the ring of rays is spread, in blocks. About the\n" +
                            "player's own half-width by default, so the normal comes from the ground he is\n" +
                            "actually standing on rather than from a single point between his feet. Wider\n" +
                            "reads a slope earlier and catches the neighbouring block on a ledge.")
                    .defineInRange("radius", 0.58, 0.0, 2.0);

    public static final ModConfigSpec.BooleanValue BLEND_FOOTING =
            BUILDER.comment("Read the floor as one surface, whoever owns it.\n" +
                            "\n" +
                            "Off, the rays report only the sub-level Sable says you are tracking, and that\n" +
                            "answer is whatever your hitbox last touched from below. On a step between two\n" +
                            "decks a leaning body dips its lower corner into the deck beneath, the answer\n" +
                            "flips to that one, the body turns towards it, the corner lifts clear, the\n" +
                            "answer flips back - a swing that feeds itself, because the lean decides the\n" +
                            "input that decides the lean.\n" +
                            "\n" +
                            "On, every ray is instead asked what it actually landed on, and the floor is\n" +
                            "the weighted average of all of it - both decks at a seam, and the ground where\n" +
                            "there is no deck. Nothing about it depends on how far over you are already\n" +
                            "leaning, so there is nothing left to swing, and crossing a seam becomes one\n" +
                            "continuous slide from one floor to the other instead of a choice between them.")
                    .define("blendFooting", true);

    public static final ModConfigSpec.DoubleValue FOOTING_GRIP =
            BUILDER.comment("How far below your boots a surface still counts as the floor you are standing\n" +
                            "on, in blocks - at exactly this depth it is worth half a vote, and it fades\n" +
                            "smoothly from there.\n" +
                            "Small values ignore everything but the floor immediately underfoot, so a deck\n" +
                            "one step down - or a hole beside your boots - barely registers. Large ones let\n" +
                            "the floor further under you pull the lean towards itself. Applies to the rays\n" +
                            "of a single deck as well, so it is worth something with blendFooting off too.")
                    .defineInRange("footingGrip", 0.6, 0.05, 4.0);

    public static final ModConfigSpec.DoubleValue FOOTING_MARGIN =
            BUILDER.comment("How much more of your weight another deck must be taking before it is allowed\n" +
                            "to become the one carrying you, as a fraction (0.2 = a fifth more). The lean\n" +
                            "itself is always shared out honestly; this only decides which single deck the\n" +
                            "tilt is stamped against, and holding that still is what stops a hand-over\n" +
                            "every other tick while you straddle a seam. 0.0 = whoever is ahead this frame.\n" +
                            "Needs blendFooting.")
                    .defineInRange("footingMargin", 0.2, 0.0, 0.9);

    public static final ModConfigSpec.DoubleValue FOOTING_DWELL_TICKS =
            BUILDER.comment("And how long, in ticks, it has to keep that lead before the hand-over happens.\n" +
                            "A real walk from one deck to the next holds it easily; a seam never does.\n" +
                            "Needs blendFooting.")
                    .defineInRange("footingDwellTicks", 4.0, 0.0, 60.0);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("Unfinished work: may look wrong in cases nobody has walked into yet, and\n" +
                        "may change shape between builds. Each entry says whether it is on.").push("experimental");
    }

    public static final ModConfigSpec.BooleanValue PREDICT_LANDING =
            BUILDER.comment("Aim the body at the surface it is going to land on and spend the flight\n" +
                            "turning towards it, instead of straightening in mid-air and snapping back on\n" +
                            "touchdown. The flight is simulated forward to find where the feet arrive, and\n" +
                            "the turn is walked along a smoothstep, so it leaves gently and settles into\n" +
                            "the landing. Also what makes airborneHoldTicks apply.")
                    .define("predictLanding", true);

    public static final ModConfigSpec.IntValue PREDICT_HORIZON_TICKS =
            BUILDER.comment("How far ahead, in ticks, the flight is simulated when looking for the landing.\n" +
                            "Longer sees the end of a high jump; a flight still unfinished at the horizon\n" +
                            "is left to the ordinary hold.")
                    .defineInRange("predictHorizonTicks", 40, 1, 200);

    public static final ModConfigSpec.DoubleValue LANDING_LEAD_TICKS =
            BUILDER.comment("Finish the turn this many ticks before touchdown, so the last moments of a\n" +
                            "flight are still and the landing is not the frame the body is still rotating\n" +
                            "in.")
                    .defineInRange("landingLeadTicks", 2.0, 0.0, 20.0);

    public static final ModConfigSpec.BooleanValue TILT_MOBS =
            BUILDER.comment("Tilt mobs standing on a sub-level along with the deck.\n" +
                            "\n" +
                            "ONLY USED when the server does not have the mod. Nothing is sent for a\n" +
                            "mob's tilt - every side works it out for itself - so on a real server the\n" +
                            "answer has to come from one place, and that place is tiltMobs in the\n" +
                            "world's own serverconfig folder. Left to the client you would see a tilted\n" +
                            "cow where the next player sees an upright one, and the server would run\n" +
                            "the collision for a third version.")
                    .define("tiltMobs", false);

    public static final ModConfigSpec.BooleanValue TILT_ITEMS =
            BUILDER.comment("Tilt dropped items lying on a sub-level along with the deck.\n" +
                            "Same mechanism as tiltMobs, and the same rule about where it is read from.")
                    .define("tiltItems", false);

    static { BUILDER.pop(); }

    static {
        BUILDER.comment("Only useful when something is wrong.").push("debug");
    }

    public static final ModConfigSpec.BooleanValue DEBUG_RAYS =
            BUILDER.comment("Draw the floor rays and what they hit").define("rays", false);

    public static final ModConfigSpec.BooleanValue DEBUG_MESSAGES =
            BUILDER.comment("Write the tilt's own running commentary to the log").define("debugMessages", false);

    public static final ModConfigSpec.BooleanValue DEBUG_TILT_SYNC =
            BUILDER.comment("On-screen panel comparing this mod's body tilt with the camera tilt Aeronautics\n" +
                            "Camera Sync is applying: both angles, the rotation between them, and the deck\n" +
                            "each side is holding on to. The two are meant to be one rotation, and the\n" +
                            "moment they most easily are not is a step from one sub-level to the next.\n" +
                            "With debugMessages on, the same numbers go to the log twice a second.")
                    .define("tiltSyncPanel", false);

    public static final ModConfigSpec.BooleanValue DEBUG_FRAME_TRACE =
            BUILDER.comment("Write down every frame of a landing.\n" +
                            "\n" +
                            "The panel and the ordinary log lines answer what is wrong NOW, and a jolt lasts one\n" +
                            "frame - so neither can say what moved between two of them. With this on, the last few\n" +
                            "seconds of frames are kept in memory and a window of them is printed whenever the feet\n" +
                            "touch down, or the camera turns further in a single frame than an eased body ever\n" +
                            "could. Each line follows the same angle through every stage it passes: the floor the\n" +
                            "rays ask for, the controller's value, the two tick samples, the interpolation everyone\n" +
                            "reads, what we hand the camera, what the camera applied, and what the finished view\n" +
                            "did. A jump appears at one stage and is smooth at the one before it, which is the\n" +
                            "whole diagnosis.\n" +
                            "\n" +
                            "Costs a formatted line per frame while it is on. For hunting a specific jolt, not for\n" +
                            "playing with.\n")
                    .define("frameTrace", false);

    public static final ModConfigSpec.DoubleValue DEBUG_FRAME_TRACE_JUMP =
            BUILDER.comment("How far the camera has to turn in ONE frame, in degrees, before frameTrace calls it a\n" +
                            "jump and prints the window around it. Smoothing spreads a turn over many frames, so at\n" +
                            "any ordinary frame rate a real one is a few tenths; a value that was snapped somewhere\n" +
                            "is whole degrees. Lower to catch smaller ones, at the price of windows you did not\n" +
                            "want. Ignored while frameTrace is off.\n")
                    .defineInRange("frameTraceJump", 0.75, 0.05, 45.0);

    static { BUILDER.pop(); }

    public static final int CURRENT_CONFIG_SCHEMA = 7;

    public static final ModConfigSpec.IntValue CONFIG_SCHEMA_VERSION =
            BUILDER.comment(
                    "Config schema version. Do not edit manually.\n" +
                            "Used to detect when a config reset prompt should be shown."
            ).defineInRange("configSchemaVersion", 0, 0, Integer.MAX_VALUE);

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
