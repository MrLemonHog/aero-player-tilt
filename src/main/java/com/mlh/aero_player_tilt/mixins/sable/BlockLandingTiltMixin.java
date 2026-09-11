package com.mlh.aero_player_tilt.mixins.sable;

import com.mlh.aero_player_tilt.tilt.DeckFlightAccess;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockLandingTiltMixin {
    @Inject(method = "updateEntityAfterFallOn", at = @At("HEAD"), cancellable = true)
    private void aeroCamSync$killSlideVelocity(BlockGetter level, Entity entity, CallbackInfo ci) {
        if (com.mlh.aero_player_tilt.tilt.Boots.holding(entity)) {
            aeroCamSync$stopAgainstFace(entity);
            ci.cancel();
            return;
        }

        if (aeroCamSync$landDeckFlight(entity)) {
            ci.cancel();
            return;
        }

        if (!PlayerTilt.isTilted(entity)) return;

        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        ci.cancel();
    }

    @Unique
    private static void aeroCamSync$stopAgainstFace(Entity entity) {
        Vector3d up = com.mlh.aero_player_tilt.tilt.Boots.support(entity, new Vector3d());
        if (up == null) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            return;
        }

        Vec3 step = entity.getDeltaMovement();
        double into = up.x * step.x + up.y * step.y + up.z * step.z;
        if (into >= 0.0) return;

        entity.setDeltaMovement(step.subtract(up.x * into, up.y * into, up.z * into));
    }

    @Unique
    private static boolean aeroCamSync$landDeckFlight(Entity entity) {
        if (!(entity instanceof DeckFlightAccess flight) || !flight.aero$inDeckFlight()) return false;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
        if (tilt == null) return false;

        Vector3d up = tilt.transform(new Vector3d(0.0, 1.0, 0.0));

        Vec3 step = entity.getDeltaMovement();

        Vector3d landed = new Vector3d(step.x, step.y, step.z);
        landed.fma(-up.dot(landed), up);

        entity.setDeltaMovement(landed.x, 0.0, landed.z);
        return true;
    }
}
