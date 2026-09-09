package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.utils.MathUtils;
import com.mlh.aero_player_tilt.tilt.DeckFlightAccess;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public final class TiltPrediction {
    private TiltPrediction() {}

    public record Landing(Vector3f normal, float ticks) {}

    private static int horizonTicks() { return Config.value(Config.PREDICT_HORIZON_TICKS, 40); }

    private static final double AIR_DRAG = 0.91;
    private static final double VERTICAL_DRAG = 0.98;
    private static final double FALLBACK_GRAVITY = 0.08;

    private static final double SANE_STEP = 4.0;

    public static float settleLeadTicks() {
        return (float) Config.value(Config.LANDING_LEAD_TICKS, 2.0);
    }

    private static final double LOOK_REACH = 24.0;
    private static final float LOOK_TICKS_MIN = 4.0f;
    private static final float LOOK_TICKS_MAX = 20.0f;

    private static final float STABLE_COS = 0.9994f;

    private static long cachedTick = Long.MIN_VALUE;
    @Nullable private static java.util.UUID cachedPlayer;
    @Nullable private static Landing cached;

    @Nullable
    public static Landing predict(LocalPlayer player) {
        if (!Config.flag(Config.PREDICT_LANDING, true)) return null;

        long gameTime = player.level().getGameTime();
        if (gameTime == cachedTick && player.getUUID().equals(cachedPlayer)) return cached;

        cachedTick = gameTime;
        cachedPlayer = player.getUUID();
        cached = steady(cached, compute(player));

        return cached;
    }

    @Nullable
    public static Landing current() {
        return cached;
    }

    public static void forget() {
        cachedTick = Long.MIN_VALUE;
        cachedPlayer = null;
        cached = null;
    }

    @Nullable
    private static Landing steady(@Nullable Landing previous, @Nullable Landing current) {
        if (previous == null || current == null) return current;

        return previous.normal().dot(current.normal()) >= STABLE_COS
                ? new Landing(previous.normal(), current.ticks())
                : current;
    }

    @Nullable
    private static Landing compute(LocalPlayer player) {
        if (player.onGround() || player.isPassenger()) return null;
        if (player.getAbilities().flying) return null;
        if (player.isFallFlying() || player.isInWater() || player.isInLava()) return null;

        Level level = player.level();

        SubLevel deck = DeckFrame.forBox(player);
        if (deck != null && deck.isRemoved()) deck = null;

        if (deck == null && !PlayerTilt.isMeaningful(BodyTiltController.getRawTiltW())) return null;

        Pose3dc pose = deck == null ? null : deck.logicalPose();
        Quaterniondc frame = pose == null ? null : pose.orientation();

        Vec3 drift = player.position().subtract(new Vec3(player.xo, player.yo, player.zo));

        Vec3 point = toFrame(pose, player.position());
        Vec3 velocity = point.subtract(toFrame(
                lastPose(deck, pose), new Vec3(player.xo, player.yo, player.zo)));

        if (velocity.length() > SANE_STEP) return null;

        Vec3 gravity = gravityStep(player, frame);

        Vec3 pointWorld = player.position();

        int horizon = horizonTicks();
        for (int tick = 0; tick < horizon; tick++) {
            Vec3 next = point.add(velocity);
            Vec3 nextWorld = toWorld(pose, next);

            if (nextWorld.y < pointWorld.y) {
                Landing landing = surfaceBetween(player, level, pointWorld, nextWorld, tick);
                if (landing != null) return landing;
            }

            point = next;
            pointWorld = nextWorld;
            velocity = new Vec3(
                    (velocity.x + gravity.x) * AIR_DRAG,
                    (velocity.y + gravity.y) * VERTICAL_DRAG,
                    (velocity.z + gravity.z) * AIR_DRAG);
        }

        return lookedAt(player, level, deck, drift);
    }

    @Nullable
    private static Landing lookedAt(LocalPlayer player, Level level,
                                    @Nullable SubLevel deck, Vec3 drift) {
        if (drift.lengthSqr() < 1.0e-6) return null;

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 reach = eye.add(player.getLookAngle().scale(LOOK_REACH));

        BlockHitResult hit = level.clip(new ClipContext(
                eye, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.MISS) return null;

        SubLevel space = Sable.HELPER.getContaining(level, hit.getLocation());
        if (space == null || space == deck) return null;

        Vector3f normal = MathUtils.transformToWorldSpace(
                MathUtils.directionToVector(hit.getDirection()), space.logicalPose().orientation());

        if (normal.y < (float) com.mlh.aero_player_tilt.tilt.PlayerTilt.walkableNormalY()) return null;

        Vec3 toContact = toWorld(space, hit.getLocation()).subtract(eye);
        if (toContact.dot(drift) <= 0.0) return null;

        float ticks = (float) Math.min(LOOK_TICKS_MAX,
                Math.max(LOOK_TICKS_MIN, toContact.length() / drift.length()));

        return new Landing(normal, ticks);
    }

    @Nullable
    private static Pose3dc lastPose(@Nullable SubLevel deck, @Nullable Pose3dc current) {
        if (deck == null || current == null) return null;

        Pose3d last = new Pose3d(deck.lastPose());
        if (last.rotationPoint().lengthSquared() <= 0.0) {
            last.rotationPoint().set(current.rotationPoint());
        }
        return last;
    }

    @Nullable
    private static Landing surfaceBetween(LocalPlayer player, Level level,
                                          Vec3 from, Vec3 to, int tick) {
        if (from.distanceToSqr(to) < 1.0e-12) return null;

        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.MISS) return null;

        SubLevel hitSpace = Sable.HELPER.getContaining(level, hit.getLocation());

        Vector3f normal = MathUtils.directionToVector(hit.getDirection());
        if (hitSpace != null) {
            normal = MathUtils.transformToWorldSpace(normal, hitSpace.logicalPose().orientation());
        }

        if (normal.y < (float) com.mlh.aero_player_tilt.tilt.PlayerTilt.walkableNormalY()) return null;

        Vec3 contact = toWorld(hitSpace, hit.getLocation());

        double span = from.distanceTo(to);
        float fraction = span < 1.0e-9 ? 0f : (float) (from.distanceTo(contact) / span);

        return new Landing(normal, tick + Math.min(1f, Math.max(0f, fraction)));
    }

    private static Vec3 gravityStep(LocalPlayer player, @Nullable Quaterniondc frame) {
        double gravity = gravityOf(player);

        boolean alongDeck = frame == null
                || (player instanceof DeckFlightAccess flight && flight.aero$inDeckFlight());

        if (alongDeck) return new Vec3(0.0, -gravity, 0.0);

        Vector3d down = new Quaterniond(frame).conjugate().transform(new Vector3d(0.0, -gravity, 0.0));
        return new Vec3(down.x, down.y, down.z);
    }

    private static Vec3 toFrame(@Nullable Pose3dc frame, Vec3 world) {
        return frame == null ? world : frame.transformPositionInverse(world);
    }

    private static Vec3 toWorld(@Nullable Pose3dc frame, Vec3 local) {
        return frame == null ? local : frame.transformPosition(local);
    }

    private static Vec3 toWorld(@Nullable SubLevel space, Vec3 point) {
        return space == null ? point : space.logicalPose().transformPosition(point);
    }

    private static double gravityOf(LocalPlayer player) {
        if (!player.getAttributes().hasAttribute(Attributes.GRAVITY)) return FALLBACK_GRAVITY;

        double gravity = player.getAttributeValue(Attributes.GRAVITY);
        return gravity > 0.0 ? gravity : FALLBACK_GRAVITY;
    }
}
