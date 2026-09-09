package com.mlh.aero_player_tilt.tilt;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class DeckRiderTilt {
    private DeckRiderTilt() {}

    private static final double UP_Y_LIMIT = 2.0 * PlayerTilt.IDENTITY_COS * PlayerTilt.IDENTITY_COS - 1.0;

    private static final int NO_AXIS = -1;

    private static final int NEGATIVE = 4;

    private static final int HOLD_TICKS = 10;

    private record Ride(SubLevel deck, int tick) {}

    private static final Map<Entity, Ride> HELD =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Nullable
    public static Quaterniond orientation(@Nullable Entity entity, float partialTicks) {
        SubLevel deck = tiltingDeck(entity);
        if (deck == null) return null;

        int axis = supportAxis(deck.logicalPose().orientation());
        if (axis == NO_AXIS) return null;

        Vector3d normal = DeckFrame.orientationAt(deck, partialTicks, new Quaterniond())
                .transform(axisVector(axis, new Vector3d()));

        return new Quaterniond().rotationTo(0.0, 1.0, 0.0, normal.x, normal.y, normal.z);
    }

    public static boolean isTilted(@Nullable Entity entity) {
        return tiltingDeck(entity) != null;
    }

    @Nullable
    private static SubLevel tiltingDeck(@Nullable Entity entity) {
        if (entity == null) return null;
        if (!enabledFor(entity)) return null;

        if (entity.getVehicle() != null) {
            HELD.remove(entity);
            return null;
        }

        SubLevel live = Sable.HELPER.getTrackingSubLevel(entity);
        if (tilting(live)) {
            HELD.put(entity, new Ride(live, entity.tickCount));
            return live;
        }

        return heldDeck(entity);
    }

    private static boolean tilting(@Nullable SubLevel deck) {
        return deck != null
                && !deck.isRemoved()
                && supportAxis(deck.logicalPose().orientation()) != NO_AXIS;
    }

    @Nullable
    private static SubLevel heldDeck(Entity entity) {
        Ride ride = HELD.get(entity);
        if (ride == null) return null;

        int age = entity.tickCount - ride.tick();
        if (age >= 0 && age <= HOLD_TICKS && tilting(ride.deck())) return ride.deck();

        HELD.remove(entity);
        return null;
    }

    private static int supportAxis(Quaterniondc rotation) {
        double x = rotation.x();
        double y = rotation.y();
        double z = rotation.z();
        double w = rotation.w();

        double alongX = 2.0 * (x * y + w * z);
        double alongY = 1.0 - 2.0 * (x * x + z * z);
        double alongZ = 2.0 * (y * z - w * x);

        double upX = Math.abs(alongX);
        double upY = Math.abs(alongY);
        double upZ = Math.abs(alongZ);

        int axis;
        double up;
        double signed;
        if (upY >= upX && upY >= upZ) {
            axis = 1; up = upY; signed = alongY;
        } else if (upX >= upZ) {
            axis = 0; up = upX; signed = alongX;
        } else {
            axis = 2; up = upZ; signed = alongZ;
        }

        if (up < TiltPolicy.entityMinNormalY()) return NO_AXIS;

        if (up > UP_Y_LIMIT) return NO_AXIS;

        return signed < 0.0 ? (axis | NEGATIVE) : axis;
    }

    private static Vector3d axisVector(int axis, Vector3d dest) {
        double sign = (axis & NEGATIVE) != 0 ? -1.0 : 1.0;
        int index = axis & 3;
        return dest.set(index == 0 ? sign : 0.0,
                        index == 1 ? sign : 0.0,
                        index == 2 ? sign : 0.0);
    }

    private static boolean enabledFor(Entity entity) {
        if (entity instanceof ItemEntity) return TiltPolicy.tiltItems();
        if (entity instanceof LivingEntity) return TiltPolicy.tiltMobs();
        return false;
    }
}
