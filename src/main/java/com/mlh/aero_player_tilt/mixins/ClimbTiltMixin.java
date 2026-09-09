package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ClimbTiltMixin {
    @Shadow protected boolean jumping;

    @Unique
    private static final double AERO$CLIMB_CLAMP = 0.15;

    @Unique
    private static final double AERO$CLIMB_RISE = 0.2;

    @Inject(method = "handleOnClimbable", at = @At("HEAD"), cancellable = true)
    private void aero$clampAlongDeck(Vec3 deltaMovement, CallbackInfoReturnable<Vec3> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) return;
        if (!self.onClimbable()) return;

        self.resetFallDistance();

        Vector3d step = tilt.transformInverse(
                new Vector3d(deltaMovement.x, deltaMovement.y, deltaMovement.z));

        step.x = Mth.clamp(step.x, -AERO$CLIMB_CLAMP, AERO$CLIMB_CLAMP);
        step.z = Mth.clamp(step.z, -AERO$CLIMB_CLAMP, AERO$CLIMB_CLAMP);
        step.y = Math.max(step.y, -AERO$CLIMB_CLAMP);

        if (step.y < 0.0
                && self instanceof Player
                && self.isSuppressingSlidingDownLadder()
                && !self.getInBlockState().isScaffolding(self)) {
            step.y = 0.0;
        }

        tilt.transform(step);

        cir.setReturnValue(new Vec3(step.x, step.y, step.z));
    }

    @ModifyReturnValue(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"))
    private Vec3 aero$riseAlongDeck(Vec3 climbed) {
        LivingEntity self = (LivingEntity) (Object) this;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) return climbed;

        if (!self.horizontalCollision && !this.jumping) return climbed;
        if (!self.onClimbable()) return climbed;

        boolean pushing = Math.abs(self.xxa) > 1.0E-5F || Math.abs(self.zza) > 1.0E-5F;
        boolean asked = this.jumping || (self.horizontalCollision && pushing);

        if (self.level().isClientSide) {
            com.mlh.aero_player_tilt.client.tilt.TiltDiagnostics.recordClimb(
                    self, asked, self.horizontalCollision, this.jumping, self.xxa, self.zza);
        }

        Vec3 moved = self.getDeltaMovement();
        if (!asked) return moved;

        Vector3d step = tilt.transformInverse(new Vector3d(moved.x, moved.y, moved.z));
        step.y = AERO$CLIMB_RISE;
        tilt.transform(step);

        return new Vec3(step.x, step.y, step.z);
    }
}
