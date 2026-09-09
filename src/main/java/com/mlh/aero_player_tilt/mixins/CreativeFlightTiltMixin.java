package com.mlh.aero_player_tilt.mixins;

import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class CreativeFlightTiltMixin {
    @Unique
    private static final double AERO$VERTICAL_DRAG = 0.98;

    @Unique
    private double aero$flightGravity = 0.0;

    @Inject(method = "travel", at = @At("HEAD"))
    private void aero$captureFlightBranch(Vec3 travelVector, CallbackInfo ci) {
        Player self = (Player) (Object) this;

        if (!aero$vanillaFlightGravity(self)) {
            aero$flightGravity = 0.0;
            return;
        }

        double gravity = self.getGravity();
        if (self.getDeltaMovement().y <= 0.0 && self.hasEffect(MobEffects.SLOW_FALLING)) {
            gravity = Math.min(gravity, 0.01);
        }
        aero$flightGravity = gravity;
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void aero$undoSlopeGravity(Vec3 travelVector, CallbackInfo ci) {
        double gravity = aero$flightGravity;
        aero$flightGravity = 0.0;
        if (gravity == 0.0) return;

        Player self = (Player) (Object) this;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) return;

        Vector3d up = tilt.transform(new Vector3d(0.0, 1.0, 0.0));

        double damped = AERO$VERTICAL_DRAG * gravity;
        self.addDeltaMovement(new Vec3(
                -damped * up.x * up.y,
                damped * (1.0 - up.y * up.y),
                -damped * up.z * up.y));
    }

    @Unique
    private boolean aero$vanillaFlightGravity(Player self) {
        if (!self.getAbilities().flying || self.isPassenger()) return false;

        if (!self.isControlledByLocalInstance()) return false;

        if (self.isInWater() || self.isInLava() || self.isInFluidType()) return false;
        if (self.isFallFlying()) return false;

        if (self.hasEffect(MobEffects.LEVITATION)) return false;
        if (self.shouldDiscardFriction()) return false;

        Level level = self.level();
        BlockPos below = self.getBlockPosBelowThatAffectsMyMovement();
        return !level.isClientSide || level.hasChunkAt(below);
    }
}
