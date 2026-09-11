package com.mlh.aero_player_tilt.tilt;

import com.mlh.aero_player_tilt.ServerTiltStore;
import com.mlh.aero_player_tilt.client.config.Config;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;

import javax.annotation.Nullable;

public final class PlayerTilt {
    private PlayerTilt() {}

    public static final double IDENTITY_COS = 0.99999;

    public static boolean isMeaningful(double w) {
        return Math.abs(w) < IDENTITY_COS;
    }

    public static double maxTiltAngle() {
        double minNormalY = walkableNormalY();
        if (minNormalY <= 0.0) return Math.PI;
        if (minNormalY >= 1.0) return 0.0;
        return Math.acos(minNormalY);
    }

    public static double walkableNormalY() {
        return TiltPolicy.minNormalY();
    }

    public static boolean anyFace() {
        return walkableNormalY() <= 0.0;
    }

    public static double floorNormalY() {
        return Math.max(UPRIGHT_EPSILON, walkableNormalY());
    }

    private static final double UPRIGHT_EPSILON = 1.0e-4;

    public static boolean scaled() {
        return tiltMultiplier() < 1.0;
    }

    public static double tiltMultiplier() {
        return TiltPolicy.tiltMultiplier();
    }

    @Nullable
    public static Quaterniond leanPartially(@Nullable Quaterniond tilt) {
        if (tilt == null) return tilt;

        double multiplier = tiltMultiplier();
        if (multiplier >= 1.0) return tilt;

        return tilt.set(new Quaterniond().slerp(tilt, multiplier));
    }

    public static Quaterniond dropTwist(Quaterniond q) {
        if (Math.abs(q.y) < 1.0e-9) return q;

        org.joml.Vector3d up = q.transform(new org.joml.Vector3d(0.0, 1.0, 0.0));
        return q.rotationTo(0.0, 1.0, 0.0, up.x, up.y, up.z).normalize();
    }

    public static org.joml.Quaternionf dropTwist(org.joml.Quaternionf q) {
        if (Math.abs(q.y()) < 1.0e-7f) return q;

        org.joml.Vector3f up = q.transform(new org.joml.Vector3f(0f, 1f, 0f));
        return q.rotationTo(0f, 1f, 0f, up.x, up.y, up.z).normalize();
    }

    public static Quaterniond clampToWalkable(Quaterniond q) {
        if (q.w < 0.0) q.set(-q.x, -q.y, -q.z, -q.w);

        double angle = 2.0 * Math.acos(Math.min(1.0, Math.abs(q.w)));
        double max = maxTiltAngle();
        if (angle <= max || angle < 1.0e-9) return q;

        return q.set(new Quaterniond().slerp(q, max / angle));
    }

    @Nullable
    public static Quaterniond getOrientation(@Nullable Entity entity, float partialTicks) {
        return getRenderOrientation(entity, partialTicks);
    }

    @Nullable
    public static Quaterniond getRenderOrientation(@Nullable Entity entity, float partialTicks) {
        if (!(entity instanceof Player player)) return DeckRiderTilt.orientation(entity, partialTicks);

        if (!appliesTo(player)) return null;

        if (player.level().isClientSide) {
            return com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.get(player, partialTicks);
        }

        if (player instanceof ServerPlayer sp) {
            if (!ServerTiltStore.isBodyActive(sp.getUUID())) return null;

            java.util.UUID deckId = ServerTiltStore.bodyDeckId(sp.getUUID());
            SubLevel deck = DeckFrame.byId(sp.level(), deckId);
            if (deck == null) return ServerTiltStore.getBodyTilt(sp.getUUID(), partialTicks);

            return ServerTiltStore.getBodyTilt(sp.getUUID(), partialTicks,
                    DeckFrame.orientationAt(deck, partialTicks, new Quaterniond()),
                    deckId);
        }

        return null;
    }

    public static boolean isTilted(@Nullable Entity entity) {
        return isRenderTilted(entity);
    }

    public static boolean isRenderTilted(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) return DeckRiderTilt.isTilted(entity);
        if (!appliesTo(player)) return false;

        if (player.level().isClientSide) {
            return com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.isTilted(player);
        }
        if (player instanceof ServerPlayer sp) {
            return ServerTiltStore.isBodyActive(sp.getUUID());
        }
        return false;
    }

    public static boolean anyTilted(net.minecraft.world.level.Level level) {
        if (!level.isClientSide) return ServerTiltStore.anyBodyActive();

        if (level.isClientSide) {
            return com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.anyTilted();
        }
        return ServerTiltStore.anyBodyActive();
    }

    private static boolean appliesTo(Player player) {
        return player.getVehicle() == null && !player.isSleeping();
    }

    public static float pivotHeight(Entity entity) {
        return isTilted(entity) ? 0.0f : entity.getEyeHeight();
    }

    public static double eyeDisplacement(@Nullable Entity entity) {
        if (entity == null) return 0.0;

        Quaterniond tilt = getOrientation(entity, 1.0f);
        if (tilt == null) return 0.0;

        double eyeHeight = entity.getEyeHeight();
        org.joml.Vector3d tilted = tilt.transform(new org.joml.Vector3d(0.0, eyeHeight, 0.0));
        return tilted.distance(0.0, eyeHeight, 0.0);
    }
}
