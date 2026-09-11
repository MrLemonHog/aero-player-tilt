package com.mlh.aero_player_tilt.client.debug;

import com.mlh.aero_player_tilt.AcsBridge;
import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.tilt.BodyTiltController;
import com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt;
import com.mlh.aero_player_tilt.client.tilt.TiltPrediction;
import com.mlh.aero_player_tilt.client.utils.StandingDeck;
import com.mlh.aero_player_tilt.client.utils.SurfaceRaycaster;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.playsi.aero_cam_sync.api.AcsClientState;
import com.playsi.aero_cam_sync.api.AcsState;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

@EventBusSubscriber(modid = AeroPlayerTilt.MODID, value = Dist.CLIENT)
public final class FrameTrace {
    private FrameTrace() {}

    private static final int RING = 400;

    private static final int LEAD = 30;
    private static final int TAIL = 150;

    private static final long QUIET_MS = 4000;

    private static final String[] LINES = new String[RING];

    private static int head;
    private static int filled;

    private static long frame;

    private static boolean announced;

    private static boolean warnedFormat;

    private static int pendingTail = -1;
    private static String pendingWhy = "";
    private static long lastDumpMs;

    private static long bodyStamp = Long.MIN_VALUE;
    private static double floorTarget;
    private static int floorHits;
    private static double floorShare;
    private static boolean hold;
    private static boolean airborneOverDeck;
    @Nullable private static UUID trackedDeck;
    @Nullable private static UUID standingDeck;

    private static long handStamp = Long.MIN_VALUE;
    private static double handLean;
    private static double handOver;
    private static float handLeft;

    public static boolean enabled() {
        return Config.isLoaded() && Config.flag(Config.DEBUG_FRAME_TRACE, false);
    }

    public static void body(SurfaceRaycaster.Floor floor,
                            @Nullable Vector3f surfaceNormal,
                            boolean holding,
                            boolean airborne,
                            @Nullable ClientSubLevel tracked,
                            @Nullable ClientSubLevel standing) {
        if (!enabled()) return;

        bodyStamp = frame;
        floorTarget = surfaceNormal == null
                ? -1.0
                : Math.toDegrees(Math.acos(Math.min(1.0f, Math.max(-1.0f, surfaceNormal.y))));
        floorHits = floor.hits();
        floorShare = floor.strongest() == null ? 0.0 : floor.share(floor.strongest().weight());
        hold = holding;
        airborneOverDeck = airborne;
        trackedDeck = tracked == null ? null : tracked.getUniqueId();
        standingDeck = standing == null ? null : standing.getUniqueId();
    }

    public static void handed(Quaternionf lean, double takeOverDegrees, float takeOverLeft) {
        if (!enabled()) return;

        handStamp = frame;
        handLean = degrees(lean.w());
        handOver = takeOverDegrees;
        handLeft = takeOverLeft;
    }

    private static double lastStageStep;
    private static final Quaterniond lastCamRot = new Quaterniond();
    private static Vec3 lastCamPos;
    private static Vec3 lastDrawnPos;
    private static boolean lastOnGround = true;
    private static double lastY;

    private static final Quaterniond lastInterpolated = new Quaterniond();
    private static final Quaterniond lastRaw = new Quaterniond();
    private static final Quaterniond lastAcs = new Quaterniond();
    private static boolean hasLastStages;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!enabled()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (!announced) {
            announced = true;
            AeroPlayerTilt.LOGGER.info("[trace] recording - a window is printed on every landing,"
                    + " and whenever a tilt stage turns more than {} deg in one frame",
                    fmt(jumpDegrees()));
        }

        Camera camera = mc.gameRenderer.getMainCamera();

        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float deltaTicks = mc.getTimer().getRealtimeDeltaTicks();

        Quaterniond camRot = new Quaterniond(camera.rotation());
        double camStep = lastCamPos == null
                ? 0.0
                : degrees(new Quaterniond(lastCamRot).conjugate().mul(camRot).normalize().w());

        Vec3 camPos = camera.getPosition();
        double camMoved = lastCamPos == null ? 0.0 : camPos.distanceTo(lastCamPos);

        Vec3 drawnPos = player.getPosition(partialTick);
        double bodyMoved = lastDrawnPos == null ? 0.0 : drawnPos.distanceTo(lastDrawnPos);

        AcsState state = AcsBridge.ACS.state(player, partialTick);
        AcsClientState client = state.client();

        Quaternionf acsTilt = state.posTilt();

        Quaterniond interpolated = PlayerTilt.getOrientation(player, partialTick);
        Quaterniond snapNow = ClientPlayerTilt.currentLocal();
        Quaterniond snapWas = ClientPlayerTilt.previousLocal();

        boolean claimed = handStamp == frame;
        boolean measured = bodyStamp == frame;

        Quaterniond rawNow = new Quaterniond(BodyTiltController.getRawTilt());
        Quaterniond acsNow = acsTilt == null ? new Quaterniond() : new Quaterniond(acsTilt);
        Quaterniond intNow = interpolated == null ? new Quaterniond() : new Quaterniond(interpolated);

        double dInt = hasLastStages ? turn(lastInterpolated, intNow) : 0.0;
        double dRaw = hasLastStages ? turn(lastRaw, rawNow) : 0.0;
        double dAcs = hasLastStages ? turn(lastAcs, acsNow) : 0.0;

        lastInterpolated.set(intNow);
        lastRaw.set(rawNow);
        lastAcs.set(acsNow);
        hasLastStages = true;

        Vec3 motion = player.getDeltaMovement();

        try {
        LINES[head] = String.format(Locale.ROOT,
                "f=%d t=%d+%.2f dt=%.3f | STEP int=%s raw=%s acs=%s cam=%s"
                        + " | cam pos=%s body pos=%s yaw=%.2f pitch=%.2f view=%s"
                        + " | acs tilt=%s scale=%s src=%s applied=%d supp=%d"
                        + " | hand lean=%s over=%s left=%.1f claim=%d"
                        + " | body int=%s raw=%s snap=%s>%s"
                        + " | floor tgt=%s hits=%d share=%.2f hold=%d ht=%.1f air=%d land=%s"
                        + " | boots %s"
                        + " | deck trk=%s std=%s foot=%s"
                        + " | plr g=%d y=%.4f dy=%s dmy=%s",
                frame,
                mc.level.getGameTime(), partialTick, deltaTicks,
                fmt(dInt), fmt(dRaw), fmt(dAcs), fmt(camStep),
                fmt(camMoved), fmt(bodyMoved), camera.getYRot(), camera.getXRot(),
                camera.isDetached() ? "3rd" : "1st",
                fmt(acsTilt == null ? 0.0 : degrees(acsTilt.w())),
                client == null ? "?" : fmt(client.tiltScale()),
                client == null || client.tiltSource() == null ? "acs" : client.tiltSource(),
                state.tiltApplied() ? 1 : 0, state.suppressed() ? 1 : 0,
                claimed ? fmt(handLean) : "-", claimed ? fmt(handOver) : "-",
                claimed ? handLeft : 0f, claimed ? 1 : 0,
                interpolated == null ? "-" : fmt(degrees(interpolated.w())),
                fmt(degrees(BodyTiltController.getRawTiltW())),
                snapWas == null ? "-" : fmt(degrees(snapWas.w())),
                snapNow == null ? "-" : fmt(degrees(snapNow.w())),
                measured ? (floorTarget < 0.0 ? "-" : fmt(floorTarget)) : "?",
                measured ? floorHits : -1, measured ? floorShare : 0.0,
                measured && hold ? 1 : 0, BodyTiltController.holdTicksSpent(),
                measured && airborneOverDeck ? 1 : 0, landing(),
                com.mlh.aero_player_tilt.client.tilt.BootsController.trace(),
                measured ? shortId(trackedDeck) : "?", measured ? shortId(standingDeck) : "?",
                StandingDeck.debug().replace(' ', '_'),
                player.onGround() ? 1 : 0, player.getY(),
                fmt(player.getY() - lastY), fmt(motion.y));
        } catch (RuntimeException broken) {
            if (!warnedFormat) {
                warnedFormat = true;
                AeroPlayerTilt.LOGGER.warn("[trace] could not write a line", broken);
            }
            LINES[head] = "f=" + frame + " | trace line failed: " + broken;
        }

        head = (head + 1) % RING;
        if (filled < RING) filled++;
        frame++;

        double stageStep = Math.max(dInt, Math.max(dRaw, dAcs));

        if (!lastOnGround && player.onGround()) {
            arm("landed");
        } else if (stageStep > jumpDegrees() && lastStageStep <= jumpDegrees()) {
            arm(String.format(Locale.ROOT, "tilt jumped %s deg in one frame (int=%s raw=%s acs=%s)",
                    fmt(stageStep), fmt(dInt), fmt(dRaw), fmt(dAcs)));
        }

        lastCamRot.set(camRot);
        lastCamPos = camPos;
        lastDrawnPos = drawnPos;
        lastStageStep = stageStep;
        lastOnGround = player.onGround();
        lastY = player.getY();

        if (pendingTail >= 0 && --pendingTail < 0) dump();
    }

    private static double jumpDegrees() {
        return Config.value(Config.DEBUG_FRAME_TRACE_JUMP, 0.75);
    }

    public static void mark(String why) {
        if (!enabled()) return;

        arm(why);
    }

    private static void arm(String why) {
        if (pendingTail >= 0) return;

        long now = System.currentTimeMillis();
        if (now - lastDumpMs < QUIET_MS) return;

        pendingWhy = why;
        pendingTail = TAIL;
    }

    private static void dump() {
        lastDumpMs = System.currentTimeMillis();
        pendingTail = -1;

        int count = Math.min(filled, LEAD + TAIL + 1);
        int start = (head - count + RING) % RING;

        AeroPlayerTilt.LOGGER.info("[trace] ==== {} ==== {} frames", pendingWhy, count);
        AeroPlayerTilt.LOGGER.info("[trace] legend: STEP is how far each stage turned since the"
                + " previous frame, in degrees - raw=controller's own value, int=the tick"
                + " interpolation everything reads, acs=what the camera applied, cam=the"
                + " finished view (mouse included). The stage that jumped has one line several"
                + " times its neighbours' and the stage BEFORE it does not, which is the answer."
                + " Values: hand=what we handed ACS (over=hand-over left to spend, left=ticks of"
                + " it), snap=the two tick samples int is drawn between, floor tgt=the angle the"
                + " rays are asking for before any smoothing.");

        for (int i = 0; i < count; i++) {
            String line = LINES[(start + i) % RING];
            if (line != null) AeroPlayerTilt.LOGGER.info("[trace] {}", line);
        }

        AeroPlayerTilt.LOGGER.info("[trace] ==== end ====");
    }

    public static void forget() {
        head = 0;
        filled = 0;
        frame = 0;
        pendingTail = -1;
        lastCamPos = null;
        lastDrawnPos = null;
        lastOnGround = true;
        lastStageStep = 0.0;
        hasLastStages = false;
        announced = false;
    }

    private static double turn(Quaterniond from, Quaterniond to) {
        return degrees(new Quaterniond(from).conjugate().mul(to).normalize().w());
    }

    private static String landing() {
        TiltPrediction.Landing landing = TiltPrediction.current();
        if (landing == null) return "-";

        return fmt(landing.ticks()) + "t/"
                + fmt(Math.toDegrees(Math.acos(Math.min(1.0f, landing.normal().y))));
    }

    private static double degrees(double w) {
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(w))));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String shortId(@Nullable UUID id) {
        return id == null ? "-" : id.toString().substring(0, 4);
    }
}
