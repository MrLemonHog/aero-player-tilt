package com.mlh.aero_player_tilt.tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Optional;

public final class DeckBedAnchor {
    private DeckBedAnchor() {}

    private static final double BED_OFFSET = 0.6875;

    public static void pin(LivingEntity entity) {
        pin(entity, false);
    }

    public static void pin(LivingEntity entity, boolean report) {
        if (!entity.isSleeping()) return;

        BlockPos bed = entity.getSleepingPos().orElse(null);
        SubLevel deck = deckOf(entity, bed);

        Vector3d anchor = deck == null ? null : deck.logicalPose().transformPosition(
                new Vector3d(bed.getX() + 0.5, bed.getY() + BED_OFFSET, bed.getZ() + 0.5),
                new Vector3d());

        Vec3 drifted = entity.position();

        if (anchor != null) {
            ((EntityMovementExtension) entity).sable$setTrackingSubLevel(null);

            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();

            entity.walkAnimation.setSpeed(0.0f);

            Vec3 position = entity.position();
            if (position.x != anchor.x || position.y != anchor.y || position.z != anchor.z) {
                entity.setPos(anchor.x, anchor.y, anchor.z);
            }
        }

        if (report) report(entity, bed, deck, anchor, drifted);
    }

    private static void report(LivingEntity entity, @javax.annotation.Nullable BlockPos bed,
                               @javax.annotation.Nullable SubLevel deck,
                               @javax.annotation.Nullable Vector3d anchor, Vec3 drifted) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;
        if (!Config.flag(Config.DEBUG_MESSAGES, false)) return;
        if (entity.tickCount % 20 != 0) return;

        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(entity);

        com.mlh.aero_player_tilt.AeroPlayerTilt.LOGGER.info(String.format(java.util.Locale.ROOT,
                "[bed] %s bed=%s deck=%s track=%s pos=(%.3f, %.3f, %.3f) anchor=%s off=%s",
                entity.level().isClientSide ? "CLIENT" : "SERVER",
                bed == null ? "none" : bed.toShortString(),
                shortId(deck),
                shortId(tracking),
                drifted.x, drifted.y, drifted.z,
                anchor == null ? "none"
                        : String.format(java.util.Locale.ROOT, "(%.3f, %.3f, %.3f)", anchor.x, anchor.y, anchor.z),
                anchor == null ? "-"
                        : String.format(java.util.Locale.ROOT, "%.4f",
                                anchor.distance(drifted.x, drifted.y, drifted.z))));
    }

    private static String shortId(@javax.annotation.Nullable SubLevel deck) {
        return deck == null ? "none" : deck.getUniqueId().toString().substring(0, 8);
    }

    public static boolean pinned(@javax.annotation.Nullable net.minecraft.world.entity.Entity entity) {
        return entity instanceof LivingEntity living && pinned(living);
    }

    public static boolean pinned(@javax.annotation.Nullable LivingEntity entity) {
        if (entity == null || !entity.isSleeping()) return false;

        return deckOf(entity, entity.getSleepingPos().orElse(null)) != null;
    }

    @javax.annotation.Nullable
    private static SubLevel deckOf(LivingEntity entity, @javax.annotation.Nullable BlockPos bed) {
        if (bed == null) return null;

        SubLevel deck = Sable.HELPER.getContaining(entity.level(), bed);
        return deck == null || deck.isRemoved() ? null : deck;
    }
}
