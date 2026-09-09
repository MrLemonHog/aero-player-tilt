package com.mlh.aero_player_tilt.client.debug;

import com.mlh.aero_player_tilt.AcsBridge;
import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.tilt.BodyTiltSource;
import com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt;
import com.mlh.aero_player_tilt.client.utils.MathUtils;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.playsi.aero_cam_sync.api.AcsClientState;
import com.playsi.aero_cam_sync.api.AcsState;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class TiltSyncOverlay {
    private TiltSyncOverlay() {}

    private static final int LABEL = 0xFFAAAAAA;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int GOOD  = 0xFF55FF55;
    private static final int WARN  = 0xFFFFFF55;
    private static final int BAD   = 0xFFFF5555;

    private static final double GOOD_DEGREES = 0.5;

    private static final double WARN_DEGREES = 2.0;

    private static final long PEAK_HOLD_MS = 3000;

    private static double peak;
    private static long peakAt;

    private static long loggedTick = Long.MIN_VALUE;

    public static void render(GuiGraphics gfx, float partialTick) {
        if (!Config.flag(Config.DEBUG_TILT_SYNC, false)) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        if (mc.getDebugOverlay().showDebugScreen()) return;

        AcsState state = AcsBridge.ACS.state(player, partialTick);
        AcsClientState client = state.client();

        Quaterniond body = PlayerTilt.getOrientation(player, partialTick);
        Quaternionf cam = state.posTilt();

        Quaterniond bodyQ = body == null ? new Quaterniond() : new Quaterniond(body);
        Quaterniond camQ = cam == null ? new Quaterniond() : new Quaterniond(cam);

        Quaterniond leanQ = body == null
                ? new Quaterniond()
                : new Quaterniond(BodyTiltSource.levelOff(MathUtils.toQuaternionf(body)));

        double bodyAngle = degrees(bodyQ);
        double leanAngle = degrees(leanQ);
        double camAngle = degrees(camQ);
        double delta = degrees(new Quaterniond(camQ).conjugate().mul(leanQ).normalize());
        double axis = axisDegrees(leanQ, camQ);

        trackPeak(delta);

        trackCamStep(camQ);

        UUID ourDeck = ClientPlayerTilt.currentDeckId();
        ClientSubLevel acsSubLevel = client == null ? null : client.tiltSubLevel();
        UUID acsDeck = acsSubLevel == null ? null : acsSubLevel.getUniqueId();
        boolean sameDeck = java.util.Objects.equals(ourDeck, acsDeck);

        double multiplier = PlayerTilt.tiltMultiplier();
        float scale = client == null ? Float.NaN : client.tiltScale();

        int right = gfx.guiWidth() - 4;
        int y = 4;

        y = row(gfx, right, y, "tilt sync", state.tiltApplied() ? "on" : "off",
                state.tiltApplied() ? VALUE : LABEL) + 1;

        y = row(gfx, right, y, "body",
                fmt(leanAngle) + "/" + fmt(bodyAngle) + "deg  x" + fmt(multiplier), VALUE);
        y = row(gfx, right, y, "cam", fmt(camAngle) + "deg  x" + (Float.isNaN(scale) ? "?" : fmt(scale)), VALUE);

        double takeOver = BodyTiltSource.takeOverDegrees();
        if (takeOver > 0.0) {
            y = row(gfx, right, y, "take", fmt(takeOver) + "deg", WARN);
        }

        y = row(gfx, right, y, "diff", fmt(delta) + "deg",
                takeOver > 0.0 ? LABEL : colorOf(delta));
        y = row(gfx, right, y, "axis", axis < 0.0 ? "-" : fmt(axis) + "deg",
                axis < 0.0 ? LABEL : colorOf(axis));
        y = row(gfx, right, y, "peak 3s", fmt(peak) + "deg", colorOf(peak));

        y = row(gfx, right, y, "step 3s", fmt(camStepPeak) + "deg/f",
                camStepPeak > JUMP_DEGREES ? BAD : VALUE) + 1;

        y = row(gfx, right, y, "deck", shortId(ourDeck) + " / " + shortId(acsDeck),
                sameDeck ? VALUE : BAD);

        String footing = com.mlh.aero_player_tilt.client.utils.StandingDeck.debug();
        y = row(gfx, right, y, "foot", footing,
                footing.indexOf('<') < 0 ? VALUE : WARN);

        String source = client == null ? null : client.tiltSource();
        boolean ours = AeroPlayerTilt.MODID.equals(source);
        y = row(gfx, right, y, "src",
                source == null ? "acs" : (ours ? "us" : source),
                source == null ? LABEL : (ours ? GOOD : WARN));

        String flags = view(client)
                + (state.modEnabled() ? "" : " acs-off")
                + (state.suppressed() ? " held:" + join(state.suppressedBy()) : "");
        row(gfx, right, y, "view", flags, state.suppressed() ? WARN : LABEL);

        log(player, bodyAngle, camAngle, delta, axis, ourDeck, acsDeck, state);
    }

    private static String view(@Nullable AcsClientState client) {
        if (client == null) return "?";

        return client.firstPerson() ? "1st" : "3rd";
    }

    private static final double JUMP_DEGREES = 4.0;

    private static final Quaterniond lastCam = new Quaterniond();
    private static boolean hasLastCam;

    private static double camStepPeak;
    private static long camStepAt;

    private static void trackCamStep(Quaterniond camQ) {
        double step = hasLastCam
                ? degrees(new Quaterniond(lastCam).conjugate().mul(camQ).normalize())
                : 0.0;

        lastCam.set(camQ);
        hasLastCam = true;

        long now = System.currentTimeMillis();
        if (step >= camStepPeak || now - camStepAt > PEAK_HOLD_MS) {
            camStepPeak = step;
            camStepAt = now;
        }
    }

    private static void trackPeak(double delta) {
        long now = System.currentTimeMillis();

        if (delta >= peak || now - peakAt > PEAK_HOLD_MS) {
            peak = delta;
            peakAt = now;
        }
    }

    private static void log(LocalPlayer player, double bodyAngle, double camAngle,
                            double delta, double axis,
                            @Nullable UUID ourDeck, @Nullable UUID acsDeck, AcsState state) {
        if (!Config.flag(Config.DEBUG_MESSAGES, false)) return;

        long tick = player.level().getGameTime();
        if (tick == loggedTick || tick % 10 != 0) return;
        loggedTick = tick;

        AeroPlayerTilt.LOGGER.info(
                "[tilt/sync] body={} cam={} diff={} axis={} peak={} deck={}/{} applied={} suppressed={}",
                fmt(bodyAngle), fmt(camAngle), fmt(delta), axis < 0.0 ? "-" : fmt(axis), fmt(peak),
                shortId(ourDeck), shortId(acsDeck), state.tiltApplied(), state.suppressed());
    }

    private static int row(GuiGraphics gfx, int right, int y, String label, String value, int color) {
        Minecraft mc = Minecraft.getInstance();

        int valueWidth = mc.font.width(value);
        gfx.drawString(mc.font, value, right - valueWidth, y, color, true);
        gfx.drawString(mc.font, label, right - valueWidth - 4 - mc.font.width(label), y, LABEL, true);

        return y + mc.font.lineHeight + 1;
    }

    private static int colorOf(double degrees) {
        if (degrees <= GOOD_DEGREES) return GOOD;
        return degrees <= WARN_DEGREES ? WARN : BAD;
    }

    private static double degrees(Quaterniond q) {
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(q.w()))));
    }

    private static double axisDegrees(Quaterniond a, Quaterniond b) {
        Vector3d axisA = axisOf(a);
        Vector3d axisB = axisOf(b);
        if (axisA == null || axisB == null) return -1.0;

        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, axisA.dot(axisB)))));
    }

    @Nullable
    private static Vector3d axisOf(Quaterniond q) {
        Vector3d axis = new Vector3d(q.x(), q.y(), q.z());
        if (q.w() < 0.0) axis.negate();

        return axis.length() < 1.0e-4 ? null : axis.normalize();
    }

    private static String shortId(@Nullable UUID id) {
        return id == null ? "-" : id.toString().substring(0, 4);
    }

    private static String join(List<String> names) {
        return names.isEmpty() ? "?" : String.join(",", names);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
