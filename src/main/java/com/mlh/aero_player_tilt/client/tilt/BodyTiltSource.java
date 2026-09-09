package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.AcsBridge;
import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.utils.MathUtils;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.playsi.aero_cam_sync.api.AcsConditions;
import com.playsi.aero_cam_sync.api.ConditionContext;
import com.playsi.aero_cam_sync.api.FrameConditions;
import com.playsi.aero_cam_sync.api.TiltContext;
import com.playsi.aero_cam_sync.api.TiltSource;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public final class BodyTiltSource implements TiltSource, AcsConditions {
    public static final int PRIORITY = 100;

    @Override
    public boolean appliesTo(TiltContext context) {
        if (!claimsFrame(context.player(), context.partialTick())) return standDown();

        if (PlayerTilt.isTilted(context.player())) saidGoodbye = false;

        return true;
    }

    private static boolean claimsFrame(Player player, float partialTick) {
        if (player.getVehicle() != null) return false;

        if (PlayerTilt.isTilted(player)) return true;

        return claiming && !saidGoodbye
                && PlayerTilt.getOrientation(player, partialTick) != null;
    }

    @Override
    public void conditionsFor(ConditionContext context, FrameConditions conditions) {
        if (!claimsFrame(context.player(), context.partialTick())) return;

        conditions.takeOverCameraCollision(COLLISION_REASON);
    }

    private static final String COLLISION_REASON =
            "rotates the player and keeps a rotated hitbox, so the vanilla eye is not where the eye is";

    private static boolean standDown() {
        claiming = false;
        saidGoodbye = false;
        return false;
    }

    @Override
    @Nullable
    public Quaternionf tilt(TiltContext context) {
        Quaterniond body = PlayerTilt.getOrientation(context.player(), context.partialTick());
        if (body == null) {
            claiming = false;
            saidGoodbye = false;
            return null;
        }

        Quaternionf lean = levelOff(MathUtils.toQuaternionf(body));

        boolean rotates = rotatesCamera();
        Quaternionf look = rotates ? new Quaternionf(lean) : new Quaternionf();

        if (!claiming) {
            claiming = true;
            rotating = rotates;

            beginTakeOver(context.acsTilt(), context.acsTilt(), look);
        } else if (rotates != rotating) {
            rotating = rotates;

            beginTakeOver(frameHanded, framePose, look);
        }

        Quaternionf handed = spendTakeOver(look, lean, context.deltaTicks());

        saidGoodbye = !PlayerTilt.isTilted(context.player());

        com.mlh.aero_player_tilt.client.debug.FrameTrace.handed(
                handed, takeOverDegrees(), takeOverLeft);

        frameHanded.set(handed);

        return handed;
    }

    private static boolean rotatesCamera() {
        return Config.flag(Config.ROTATE_CAMERA, true);
    }

    @Override
    @Nullable
    public Vec3 eyeOffset(TiltContext context) {
        return cameraAnchor(context.player(), context.vanillaCameraPos(), framePose, frameHanded,
                        context.partialTick())
                .subtract(context.cameraPosFor(frameHanded));
    }

    public static Vec3 cameraAnchor(Player player, Vec3 vanillaCameraPos, Quaternionf lean,
                                    Quaternionf view, float partialTick) {
        double eyeHeight = player.getEyeHeight();

        Vec3 modelFeet = AcsBridge.ACS
                .withVanillaEye(() -> Sable.HELPER.getEyePositionInterpolated(player, partialTick))
                .subtract(0.0, eyeHeight, 0.0);

        Vec3 vanillaFeet = player.getPosition(partialTick);

        double drift = modelFeet.distanceTo(vanillaFeet);
        if (!(drift <= MAX_OFFSET)) {
            if (!warnedOffset) {
                warnedOffset = true;
                AeroPlayerTilt.LOGGER.warn("[tilt/eye] refused an eye offset of {} blocks", drift);
            }
            modelFeet = vanillaFeet;
        }

        Vector3d head = lean.transform(new Vector3d(0.0, eyeHeight, 0.0));

        Vector3d boom = view.transform(new Vector3d(
                vanillaCameraPos.x - vanillaFeet.x,
                vanillaCameraPos.y - vanillaFeet.y - eyeHeight,
                vanillaCameraPos.z - vanillaFeet.z));

        return modelFeet.add(head.x + boom.x, head.y + boom.y, head.z + boom.z);
    }

    private static final double MAX_OFFSET = 0.5;

    private static final Quaternionf frameHanded = new Quaternionf();

    private static final Quaternionf framePose = new Quaternionf();

    @Nullable
    public static Quaternionf handed() {
        return claiming ? new Quaternionf(frameHanded) : null;
    }

    @Nullable
    public static Quaternionf pose() {
        return claiming ? new Quaternionf(framePose) : null;
    }

    private static boolean warnedOffset;

    private static boolean claiming;

    private static boolean rotating = true;

    private static boolean saidGoodbye;

    private static final Quaternionf takeOver = new Quaternionf();

    private static final Quaternionf camera = new Quaternionf();

    private static final Quaternionf anchor = new Quaternionf();

    private static float takeOverLeft;
    private static float takeOverSpan;

    private static void beginTakeOver(Quaternionf fromLook, Quaternionf fromPose,
                                      Quaternionf look) {
        camera.set(fromLook);
        anchor.set(fromPose);

        takeOver.set(new Quaternionf(fromLook).mul(new Quaternionf(look).conjugate()));

        if (Float.isFinite(takeOver.lengthSquared()) && takeOver.lengthSquared() > 1.0e-9f) {
            shorterWay(takeOver.normalize());
        } else {
            takeOver.identity();
        }

        takeOverSpan = PlayerTilt.isMeaningful(takeOver.w())
                ? (float) Config.value(Config.TAKEOVER_TICKS, 4.0)
                : 0f;
        takeOverLeft = takeOverSpan;
    }

    private static Quaternionf spendTakeOver(Quaternionf look, Quaternionf lean,
                                             float deltaTicks) {
        if (takeOverLeft <= 0f) {
            takeOver.identity();
            framePose.set(lean);
            return look;
        }

        float before = ease(1f - takeOverLeft / takeOverSpan);
        takeOverLeft = Math.max(takeOverLeft - deltaTicks, 0f);
        float after = ease(1f - takeOverLeft / takeOverSpan);

        float step = Math.min(1f, Math.max(0f, before >= 1f ? 1f : (after - before) / (1f - before)));

        camera.slerp(look, step).normalize();
        anchor.slerp(lean, step).normalize();

        if (takeOverLeft <= 0f) {
            takeOver.identity();
            framePose.set(lean);
            return look;
        }

        framePose.set(anchor);

        shorterWay(takeOver.set(new Quaternionf(camera)
                .mul(new Quaternionf(look).conjugate())).normalize());

        return new Quaternionf(camera);
    }

    private static float ease(float progress) {
        float t = Math.min(1f, Math.max(0f, progress));
        return t * t * (3f - 2f * t);
    }

    private static void shorterWay(Quaternionf q) {
        if (q.w() < 0f) q.set(-q.x(), -q.y(), -q.z(), -q.w());
    }

    public static double takeOverDegrees() {
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(takeOver.w()))));
    }

    public static Quaternionf levelOff(Quaternionf full) {
        Quaternionf twist = new Quaternionf(0f, full.y(), 0f, full.w());

        if (twist.lengthSquared() < 1.0e-8f) return new Quaternionf(full);

        return new Quaternionf(full).mul(twist.normalize().conjugate()).normalize();
    }
}
