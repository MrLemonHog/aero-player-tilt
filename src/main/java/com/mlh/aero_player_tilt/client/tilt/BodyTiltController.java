package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BodyTiltController {
    private BodyTiltController() {}

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);

    private static final Quaternionf bodyTilt = new Quaternionf();
    private static boolean wasComputingTilt = false;

    private static float holdTicks = 0f;

    private static boolean wasPredicting = false;
    private static float predictTotal = 0f;

    private static final Quaternionf lastShipRotation = new Quaternionf();
    private static boolean hasLastShipRotation = false;

    @javax.annotation.Nullable
    private static java.util.UUID lastShipId = null;

    private static final Quaternionf carrierRotation = new Quaternionf();
    @javax.annotation.Nullable
    private static java.util.UUID carrierId = null;

    private static boolean releasing = false;

    public static boolean shouldComputeTilt(Player player) {
        if (player == null) return false;
        if (!Config.isLoaded() || !Config.MOD_ENABLED.get()) return false;
        if (com.mlh.aero_player_tilt.client.compat.ReplayCompat.inReplay()) return false;
        if (!com.mlh.aero_player_tilt.SideManager.isClientServer()) return false;
        return player.getVehicle() == null;
    }

    public static void tickApplyState() {
        Minecraft mc = Minecraft.getInstance();
        boolean computing = shouldComputeTilt(mc.player);
        if (computing && !wasComputingTilt) {
            bodyTilt.identity();
            hasLastShipRotation = false;
            lastShipId = null;
            carrierId = null;
            holdTicks = 0f;
            wasPredicting = false;
            releasing = false;
        }
        wasComputingTilt = computing;
    }

    public static void driveFromBoots(Quaternionf frame,
                                      Quaternionf deckOrientation,
                                      java.util.UUID deckId) {
        bodyTilt.set(frame);

        carrierRotation.set(deckOrientation);
        carrierId = deckId;

        lastShipRotation.set(deckOrientation);
        lastShipId = deckId;
        hasLastShipRotation = true;

        holdTicks = 0f;
        wasPredicting = false;
        releasing = false;
    }

    public static void bootsReleased() {
        payTwistIntoYaw(Minecraft.getInstance().player);

        releasing = true;
        holdTicks = 0f;
        wasPredicting = false;
        hasLastShipRotation = false;
        lastShipId = null;
    }

    private static void payTwistIntoYaw(@javax.annotation.Nullable Player player) {
        if (player == null) return;

        Quaterniond steady = com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.steadyTilt(
                new Quaterniond(bodyTilt));

        double length = Math.sqrt(steady.y * steady.y + steady.w * steady.w);
        if (!(length > 1.0e-6)) return;

        Quaterniond twist = new Quaterniond(0.0, steady.y / length, 0.0, steady.w / length);
        if (twist.w < 0.0) twist.set(0.0, -twist.y, 0.0, -twist.w);

        float degrees = (float) Math.toDegrees(2.0 * Math.atan2(twist.y, twist.w));
        if (!Float.isFinite(degrees) || degrees == 0f) return;

        bodyTilt.mul(new Quaternionf(twist).conjugate()).normalize();

        player.yBodyRot -= degrees;
        player.yBodyRotO -= degrees;
        player.yHeadRot -= degrees;
        player.yHeadRotO -= degrees;
        player.setYRot(player.getYRot() - degrees);
        player.yRotO -= degrees;
    }

    public static void updateBodyTilt(@javax.annotation.Nullable Vector3f surfaceNormal,
                                       float deltaTime,
                                       boolean hold,
                                       @javax.annotation.Nullable Quaternionf shipRotation,
                                       @javax.annotation.Nullable java.util.UUID shipId) {
        updateBodyTilt(surfaceNormal, deltaTime, hold, shipRotation, shipId, null, false);
    }

    public static void updateBodyTilt(@javax.annotation.Nullable Vector3f surfaceNormal,
                                       float deltaTime,
                                       boolean hold,
                                       @javax.annotation.Nullable Quaternionf shipRotation,
                                       @javax.annotation.Nullable java.util.UUID shipId,
                                       @javax.annotation.Nullable TiltPrediction.Landing landing,
                                       boolean airborneOverDeck) {
        if (hold) holdTicks += deltaTime;
        else holdTicks = 0f;

        float budget = (float) (airborneOverDeck
                ? ClientPlayerTilt.settings().airborneHoldTicks()
                : ClientPlayerTilt.settings().holdTicks());

        boolean holding = hold && holdTicks <= budget;

        boolean sameShip = hasLastShipRotation
                && shipId != null && shipId.equals(lastShipId);

        Quaternionf carried = new Quaternionf(bodyTilt);
        if (shipRotation != null && sameShip) {
            carried.premul(new Quaternionf(shipRotation)
                    .mul(new Quaternionf(lastShipRotation).conjugate()));
            carried.normalize();

            PlayerTilt.dropTwist(carried);
        }

        if (shipRotation != null) {
            lastShipRotation.set(shipRotation);
            lastShipId = shipId;
            hasLastShipRotation = true;
        } else {
            hasLastShipRotation = false;
            lastShipId = null;
        }

        float maxAngle = maxTiltAngle();
        boolean tooSteep = tiltAngle(carried) > maxAngle + (float) PlayerTilt.FLOOR_FADE;
        if (!tooSteep && !releasing) clampTilt(carried, maxAngle);

        boolean predicting = landing != null && !tooSteep;

        boolean live = !tooSteep && (holding || surfaceNormal != null || predicting);
        if (live) {
            bodyTilt.set(carried);
        }

        if (live && shipRotation != null && shipId != null) {
            carrierRotation.set(shipRotation);
            carrierId = shipId;
        } else {
            carrierId = null;
        }

        if (predicting) {
            aimAtLanding(landing, deltaTime);
            holdTicks = 0f;
            return;
        }

        wasPredicting = false;

        if (holding && !tooSteep) return;

        Quaternionf target = (surfaceNormal != null)
                ? new Quaternionf().rotationTo(UP, surfaceNormal)
                : new Quaternionf();
        clampTilt(target, maxAngle);

        float halfLife = (surfaceNormal != null)
                ? (float) ClientPlayerTilt.settings().smoothIn()
                : (float) ClientPlayerTilt.settings().smoothOut();

        float before = angleTo(bodyTilt, target);
        bodyTilt.slerp(target, smoothingStep(deltaTime, halfLife));
        if (surfaceNormal == null) finishTail(target, before, deltaTime, halfLife);

        settleClamp();

        if (!PlayerTilt.isMeaningful(target.w())
                && !com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.rocking()) {
            settleUpright();
        }
    }

    private static final double TAIL_ANGLE = Math.toRadians(2.0);

    private static final double TAIL_FLOOR = Math.toRadians(0.3);

    private static void finishTail(Quaternionf target, float before, float deltaTime, float halfLife) {
        if (halfLife <= 0f || before <= 0f) return;

        double speed = Math.max(TAIL_FLOOR, Math.log(2.0) / halfLife * Math.sqrt(TAIL_ANGLE * before));
        double wanted = Math.max(0.0, before - speed * deltaTime);

        float left = angleTo(bodyTilt, target);
        if (left <= wanted) return;

        if (wanted <= 0.0) {
            bodyTilt.set(target);
            return;
        }

        bodyTilt.slerp(target, (float) ((left - wanted) / left)).normalize();
    }

    private static float angleTo(Quaternionf from, Quaternionf to) {
        float dot = Math.abs(from.dot(to)) / (float) Math.sqrt(from.lengthSquared() * to.lengthSquared());
        return 2f * (float) Math.acos(Math.min(1f, dot));
    }

    private static void settleClamp() {
        float max = maxTiltAngle();

        if (releasing) {
            if (tiltAngle(bodyTilt) > max) return;
            releasing = false;
        }

        clampTilt(bodyTilt, max);
    }

    private static void settleUpright() {
        if (PlayerTilt.isVisible(bodyTilt.w())) return;

        bodyTilt.identity();
    }

    private static void aimAtLanding(TiltPrediction.Landing landing, float deltaTime) {
        float remaining = Math.max(landing.ticks() - TiltPrediction.settleLeadTicks(), 0f);

        boolean fresh = !wasPredicting || lastAim.dot(landing.normal()) < AIM_STABLE_COS;

        if (fresh || remaining > predictTotal) {
            predictTotal = Math.max(remaining, 1.0e-3f);
        }
        lastAim.set(landing.normal());
        wasPredicting = true;

        float now = ease(1f - remaining / predictTotal);
        float next = ease(1f - Math.max(remaining - deltaTime, 0f) / predictTotal);

        float step = now >= 1f ? 1f : (next - now) / (1f - now);

        bodyTilt.slerp(new Quaternionf().rotationTo(UP, landing.normal()),
                Math.min(1f, Math.max(0f, Math.min(step, aimSpeedLimit(deltaTime)))));

        settleClamp();
    }

    private static final Vector3f lastAim = new Vector3f();

    private static final float AIM_STABLE_COS = 0.9994f;

    private static float aimSpeedLimit(float deltaTime) {
        return smoothingStep(deltaTime, (float) ClientPlayerTilt.settings().smoothIn());
    }

    private static float ease(float progress) {
        float t = Math.min(1f, Math.max(0f, progress));
        return t * t * (3f - 2f * t);
    }

    public static Quaternionf getRawTilt() {
        return new Quaternionf(bodyTilt);
    }

    public static float getRawTiltW() {
        return bodyTilt.w();
    }

    public static float holdTicksSpent() {
        return holdTicks;
    }

    @javax.annotation.Nullable
    public static Quaternionf getCarrierRotation() {
        return carrierId == null ? null : new Quaternionf(carrierRotation);
    }

    @javax.annotation.Nullable
    public static java.util.UUID getCarrierId() {
        return carrierId;
    }

    public static void resetTilt() {
        bodyTilt.identity();
        releasing = false;
        hasLastShipRotation = false;
        lastShipId = null;
        carrierId = null;
        holdTicks = 0f;
        wasPredicting = false;
    }

    private static float smoothingStep(float deltaTime, float halfLife) {
        if (halfLife <= 0f) return 1f;
        return 1f - (float) Math.pow(0.5, deltaTime / halfLife);
    }

    private static float maxTiltAngle() {
        return (float) PlayerTilt.maxTiltAngle();
    }

    private static float tiltAngle(Quaternionf q) {
        return 2f * (float) Math.acos(Math.min(1.0f, Math.abs(q.w())));
    }

    private static void clampTilt(Quaternionf q, float maxAngle) {
        if (q.w() < 0f) q.set(-q.x(), -q.y(), -q.z(), -q.w());

        float angle = tiltAngle(q);
        if (angle <= maxAngle || angle < 1.0e-6f) return;

        q.set(new Quaternionf().slerp(q, maxAngle / angle));
    }
}
