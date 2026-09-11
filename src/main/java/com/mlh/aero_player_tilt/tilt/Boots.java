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

        if (player.level().isClientSide) {
            return com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.isBooted(player);
        }

        return player instanceof ServerPlayer server
                && com.mlh.aero_player_tilt.ServerTiltStore.isBooted(server.getUUID());
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

    public static double pull() {
        return com.mlh.aero_player_tilt.client.config.Config.value(
                com.mlh.aero_player_tilt.client.config.Config.MAGNETIC_PULL, 1.0);
    }
}
