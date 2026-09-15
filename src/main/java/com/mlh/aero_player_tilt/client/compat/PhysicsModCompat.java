package com.mlh.aero_player_tilt.client.compat;

import com.mlh.aero_player_tilt.client.tilt.TiltPrediction;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public final class PhysicsModCompat {
    private PhysicsModCompat() {}

    private static final boolean PHYSICS_MOD = ModList.get().isLoaded("physicsmod");

    @Nullable private static final Method OCEAN_OFFSET = resolveOcean();

    private static final long ROCK_GRACE_TICKS = 40;

    private static final long REMEMBER_TICKS = 60;

    private static final double REMEMBER_REACH = 4.0;

    private static final double NEGLIGIBLE = 1.0e-10;

    private static final long GROUND_GRACE_TICKS = 6;

    private record Memory(ClientSubLevel deck, long tick, long groundedAt) {}

    private static final Map<Entity, Memory> REMEMBERED = new WeakHashMap<>();

    @Nullable private static Quaterniond held;

    private static long rockedAt = Long.MIN_VALUE;

    public static Quaterniond steady(SubLevel deck, float partialTick) {
        return DeckFrame.orientationAt(deck, partialTick, new Quaterniond());
    }

    public static void hold(@Nullable ClientSubLevel deck, float partialTick) {
        held = PHYSICS_MOD && deck != null && !deck.isRemoved() ? swayOf(deck, partialTick) : null;

        Minecraft mc = Minecraft.getInstance();
        if (deck == null || mc.level == null) {
            rockedAt = Long.MIN_VALUE;
        } else if (held != null && PlayerTilt.isMeaningful(held.w)) {
            rockedAt = mc.level.getGameTime();
        }
    }

    public static void release() {
        held = null;
        rockedAt = Long.MIN_VALUE;
    }

    public static boolean rocking() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || rockedAt == Long.MIN_VALUE) return false;

        return mc.level.getGameTime() - rockedAt <= ROCK_GRACE_TICKS;
    }

    @Nullable
    public static TiltPrediction.Landing drawnLanding(@Nullable TiltPrediction.Landing landing) {
        Quaterniond sway = held;
        if (landing == null || sway == null) return landing;

        Vector3f normal = new Quaternionf(sway).transform(new Vector3f(landing.normal())).normalize();
        return new TiltPrediction.Landing(normal, landing.ticks());
    }

    public static double heldDegrees() {
        Quaterniond sway = held;
        return sway == null ? 0.0 : Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(sway.w))));
    }

    public static Vec3 drawn(Entity entity, float partialTick, Vec3 world) {
        ClientSubLevel deck = swaying(entity);
        if (deck == null) return world;

        Vector3d local = steadyPose(deck, partialTick)
                .transformPositionInverse(new Vector3d(world.x, world.y, world.z));
        Vector3d out = deck.renderPose(partialTick).transformPosition(local);

        return new Vec3(out.x, out.y, out.z);
    }

    public static Quaterniond steadyTilt(Quaterniond tilt) {
        Quaterniond sway = held;
        return sway == null ? tilt : tilt.premul(new Quaterniond(sway).conjugate()).normalize();
    }

    public static Quaterniond steadyLean(Quaterniond tilt) {
        return held == null ? tilt : PlayerTilt.dropTwist(steadyTilt(tilt));
    }

    @Nullable
    public static Quaterniond of(@Nullable Entity entity, float partialTick) {
        ClientSubLevel deck = swaying(entity);
        return deck == null ? null : swayOf(deck, partialTick);
    }

    @Nullable
    public static Quaterniond apply(@Nullable Entity entity, float partialTick,
                                    @Nullable Quaterniond tilt) {
        if (tilt == null) return null;

        Quaterniond sway = swayFor(entity, partialTick);
        return sway == null ? tilt : new Quaterniond(sway).mul(tilt).normalize();
    }

    public static boolean sways(@Nullable Entity entity, float partialTick) {
        return swayFor(entity, partialTick) != null;
    }

    public static Vec3 lift(Entity entity, float partialTick, double height) {
        Quaterniond sway = of(entity, partialTick);
        if (sway == null) return new Vec3(0.0, height, 0.0);

        Vector3d up = sway.transform(new Vector3d(0.0, height, 0.0));
        return new Vec3(up.x, up.y, up.z);
    }

    public static Vec3 drift(Entity entity, float partialTick, double height) {
        ClientSubLevel deck = swaying(entity);
        if (deck == null) return Vec3.ZERO;

        Vector3d local = deck.lastPose().transformPositionInverse(
                new Vector3d(entity.xo, entity.yo + height, entity.zo));
        Vector3d now = deck.logicalPose().transformPositionInverse(
                new Vector3d(entity.getX(), entity.getY() + height, entity.getZ()));
        local.lerp(now, partialTick);

        Vector3d drawn = deck.renderPose(partialTick).transformPosition(new Vector3d(local));
        Vector3d kept = steadyPose(deck, partialTick).transformPosition(local);

        return new Vec3(drawn.x - kept.x, drawn.y - kept.y, drawn.z - kept.z);
    }

    public static boolean tracked(Entity entity) {
        return Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != null;
    }

    public static Vec3 untrackedShift(Entity entity, float partialTick) {
        if (tracked(entity)) return Vec3.ZERO;

        Vec3 wave = drift(entity, partialTick, 0.0);
        if (wave == Vec3.ZERO) return Vec3.ZERO;

        return wave.subtract(0.0, oceanOffset(entity, partialTick), 0.0);
    }

    public static double oceanOffset(Entity entity, float partialTick) {
        Method method = OCEAN_OFFSET;
        if (method == null || !method.getDeclaringClass().isInstance(entity)) return 0.0;

        try {
            return (double) method.invoke(entity, partialTick);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    @Nullable
    private static Quaterniond swayFor(@Nullable Entity entity, float partialTick) {
        return entity != null && entity == Minecraft.getInstance().player
                ? held
                : of(entity, partialTick);
    }

    @Nullable
    private static Quaterniond swayOf(ClientSubLevel deck, float partialTick) {
        Quaterniond sway = new Quaterniond(deck.renderPose(partialTick).orientation())
                .mul(steady(deck, partialTick).conjugate())
                .normalize();

        return 1.0 - Math.abs(sway.w) > NEGLIGIBLE ? sway : null;
    }

    private static Pose3d steadyPose(ClientSubLevel deck, float partialTick) {
        Pose3d pose = new Pose3d(deck.lastPose());
        Pose3dc target = deck.logicalPose();

        pose.position().lerp(target.position(), partialTick);
        pose.orientation().slerp(target.orientation(), partialTick);
        pose.rotationPoint().lerp(target.rotationPoint(), partialTick);
        pose.scale().lerp(target.scale(), partialTick);

        return pose;
    }

    @Nullable
    private static ClientSubLevel swaying(@Nullable Entity entity) {
        if (!PHYSICS_MOD || entity == null || !entity.level().isClientSide) return null;

        long now = entity.level().getGameTime();

        SubLevel deck = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        if (deck instanceof ClientSubLevel client && !client.isRemoved()) {
            REMEMBERED.put(entity, new Memory(client, now, Long.MIN_VALUE));
            return client;
        }

        Memory memory = REMEMBERED.get(entity);
        if (memory == null) return null;

        long groundedAt = !entity.onGround()
                ? Long.MIN_VALUE
                : (memory.groundedAt() == Long.MIN_VALUE ? now : memory.groundedAt());

        if (memory.deck().isRemoved() || now - memory.tick() > REMEMBER_TICKS
                || (groundedAt != Long.MIN_VALUE && now - groundedAt > GROUND_GRACE_TICKS)
                || entity.isInWater() || !near(memory.deck(), entity)) {
            REMEMBERED.remove(entity);
            return null;
        }

        if (groundedAt != memory.groundedAt()) {
            REMEMBERED.put(entity, new Memory(memory.deck(), memory.tick(), groundedAt));
        }

        return memory.deck();
    }

    public static String debug(Entity entity) {
        if (!PHYSICS_MOD) return "-";
        if (tracked(entity)) return "trk";

        return swaying(entity) != null ? "mem" : "-";
    }

    private static boolean near(ClientSubLevel deck, Entity entity) {
        BoundingBox3dc box = deck.boundingBox();
        Vec3 at = entity.position();

        return at.x > box.minX() - REMEMBER_REACH && at.x < box.maxX() + REMEMBER_REACH
                && at.y > box.minY() - REMEMBER_REACH && at.y < box.maxY() + REMEMBER_REACH
                && at.z > box.minZ() - REMEMBER_REACH && at.z < box.maxZ() + REMEMBER_REACH;
    }

    @Nullable
    private static Method resolveOcean() {
        if (!PHYSICS_MOD) return null;

        try {
            return Class.forName("net.diebuddies.physics.ocean.EntityOcean", false,
                    PhysicsModCompat.class.getClassLoader()).getMethod("getPhysicsYOffset", float.class);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
