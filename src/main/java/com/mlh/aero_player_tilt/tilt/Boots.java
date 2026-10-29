package com.mlh.aero_player_tilt.tilt;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public final class Boots {
    private Boots() {}

    public static boolean holding(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) return false;
        if (!TiltPolicy.allowsMagneticBoots() && !TiltPolicy.allowsStickyTilt()) return false;

        if (player.level().isClientSide) {
            return com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.isBooted(player);
        }

        return player instanceof ServerPlayer server
                && com.mlh.aero_player_tilt.ServerTiltStore.isBooted(server.getUUID());
    }

    private static boolean stepping;

    public static void stepping(boolean on) {
        stepping = on;
    }

    public static Quaterniond footing(@Nullable Entity entity, Quaterniond tilt) {
        if (!stepping || entity == null || !entity.level().isClientSide || !holding(entity)) return tilt;

        Vector3d face = support(entity, new Vector3d());
        if (face == null) return tilt;

        Vector3d up = tilt.transform(new Vector3d(0.0, 1.0, 0.0)).normalize();
        double dot = up.dot(face);
        if (dot > 0.999999) return tilt;

        if (dot < -0.9999) {
            Vector3d right = tilt.transform(new Vector3d(1.0, 0.0, 0.0)).normalize();
            return new Quaterniond().fromAxisAngleRad(right.x, right.y, right.z, Math.PI).mul(tilt).normalize();
        }

        return new Quaterniond().rotationTo(up, face).mul(tilt).normalize();
    }

    public static boolean wraps(@Nullable Entity entity) {
        if (!holding(entity)) return false;
        return !entity.level().isClientSide || com.mlh.aero_player_tilt.client.tilt.BootsController.flips();
    }

    public static boolean adrift(@Nullable Entity entity) {
        return entity instanceof Player player && player.level().isClientSide
                && com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.isAdrift(player);
    }

    @Nullable
    public static Vector3d up(@Nullable Entity entity, Vector3d dest) {
        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
        if (tilt == null) return null;

        return tilt.transform(dest.set(0.0, 1.0, 0.0)).normalize();
    }

    @Nullable
    public static Vector3d support(@Nullable Entity entity, Vector3d dest) {
        if (entity instanceof Player player && player.level().isClientSide) {
            Vector3d face = com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.supportUp(player);
            if (face != null) return dest.set(face).normalize();
        }

        return up(entity, dest);
    }

    @Nullable
    public static Vector3d pullUp(@Nullable Entity entity, Vector3d dest) {
        if (entity instanceof Player player && player.level().isClientSide) {
            Vector3d face = com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.pullUp(player);
            if (face != null) return dest.set(face).normalize();
        }

        return support(entity, dest);
    }

    private static volatile double grip = 1.0;

    public static void grip(double value) {
        grip = value;
    }

    public static double pull() {
        return grip;
    }
}
