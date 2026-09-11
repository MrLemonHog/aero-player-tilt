package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.tilt.Boots;
import com.mlh.aero_player_tilt.tilt.DeckFlightAccess;
import com.mlh.aero_player_tilt.tilt.DeckGravity;
import com.mlh.aero_player_tilt.tilt.JumpDiagnostics;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(LivingEntity.class)
public abstract class JumpTiltMixin implements DeckFlightAccess {
    @Shadow protected abstract float getJumpPower();

    @Unique
    private static final double AERO$VERTICAL_DRAG = 0.98;

    @Unique
    private static final double AERO$AIR_DRAG = 0.91;

    @Unique
    private static final double AERO$SETTLE = 0.003;

    @Unique
    private boolean aero$deckJump = false;

    @Unique
    private int aero$deckJumpTicks = 0;

    @Unique
    private static final int AERO$MAX_DECK_JUMP_TICKS = 100;

    @Unique
    private boolean aero$groundedBeforeTravel = true;

    @Unique
    private float aero$frictionBeforeTravel = 0.6f;

    @Unique
    private boolean aero$chunkBelowLoaded = true;

    @Unique
    private Vec3 aero$posIntoMove = Vec3.ZERO;

    @Unique
    private Vec3 aero$motionBeforeTravel = Vec3.ZERO;

    @Unique
    private Vec3 aero$stepIntoMove = Vec3.ZERO;

    @Redirect(method = "aiStep",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V",
                    ordinal = 0))
    private void aero$settleInBodyFrame(LivingEntity self, double x, double y, double z) {
        if (!Boots.holding(self)) {
            self.setDeltaMovement(x, y, z);
            return;
        }

        Vector3d up = Boots.support(self, new Vector3d());
        if (up == null) {
            self.setDeltaMovement(x, y, z);
            return;
        }

        Vec3 raw = self.getDeltaMovement();

        double into = raw.x * up.x + raw.y * up.y + raw.z * up.z;
        Vector3d along = new Vector3d(raw.x, raw.y, raw.z).fma(-into, up);

        if (along.length() < AERO$SETTLE) along.zero();
        if (Math.abs(into) < AERO$SETTLE) into = 0.0;

        along.fma(into, up);

        self.setDeltaMovement(along.x, along.y, along.z);
    }

    @Inject(method = "aiStep",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;jumpFromGround()V",
                    shift = At.Shift.BEFORE))
    private void aero$armDeckJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        JumpDiagnostics.markJump(self);

        if (com.mlh.aero_player_tilt.tilt.TiltPolicy.deckGravity() == DeckGravity.WORLD) return;

        if (!PlayerTilt.isRenderTilted(self)) return;

        if (self.onClimbable()) return;

        if (this.getJumpPower() <= 1.0E-5F) return;

        if (!Boots.holding(self)) {
            if (!aero$hasMovementInput(self)) {
                self.setDeltaMovement(Vec3.ZERO);
            } else {
                aero$dropLaunchResidue(self);
            }
        }

        aero$deckJump = true;
        aero$deckJumpTicks = 0;
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void aero$captureVanillaDrag(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        aero$groundedBeforeTravel = self.onGround();
        aero$motionBeforeTravel = self.getDeltaMovement();
        aero$stepIntoMove = Vec3.ZERO;
        aero$posIntoMove = Vec3.ZERO;

        if (!aero$deckGravityArmed(self)) return;

        Level level = self.level();
        BlockPos below = self.getBlockPosBelowThatAffectsMyMovement();
        aero$frictionBeforeTravel = level.getBlockState(below).getFriction(level, below, self);
        aero$chunkBelowLoaded = !level.isClientSide || level.hasChunkAt(below);
    }

    @Inject(method = "handleRelativeFrictionAndCalculateMovement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;move("
                            + "Lnet/minecraft/world/entity/MoverType;"
                            + "Lnet/minecraft/world/phys/Vec3;)V"))
    private void aero$captureStep(Vec3 deltaMovement, float friction,
                                  CallbackInfoReturnable<Vec3> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        aero$stepIntoMove = self.getDeltaMovement();
        aero$posIntoMove = self.position();
    }

    @ModifyReturnValue(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"))
    private Vec3 aero$holdAgainstFace(Vec3 moved) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (!self.onGround() || self.onClimbable()) return moved;
        if (!Boots.holding(self)) return moved;

        Vector3d up = Boots.support(self, new Vector3d());
        if (up == null) return moved;

        double into = up.x * moved.x + up.y * moved.y + up.z * moved.z;
        if (into >= 0.0) return moved;

        return moved.subtract(up.x * into, up.y * into, up.z * into);
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void aero$deckGravity(Vec3 travelVector, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        boolean booted = Boots.holding(self);

        if (!booted) aero$undoStandingPushStep(self);

        boolean caught = com.mlh.aero_player_tilt.tilt.DeckStick.keepFooting(
                self, aero$groundedBeforeTravel, aero$deckJump);

        boolean onGround = caught || self.onGround();

        Vector3d correction = null;
        if (booted) {
            aero$deckJump = false;
            aero$deckJumpTicks = 0;
            correction = aero$applyDeckGravity(self);
        } else if (onGround) {
            aero$deckJump = false;
            aero$deckJumpTicks = 0;
        } else if (aero$deckJump && ++aero$deckJumpTicks > AERO$MAX_DECK_JUMP_TICKS) {
            aero$deckJump = false;
            aero$deckJumpTicks = 0;
        } else {
            correction = aero$applyDeckGravity(self);
        }

        if (!self.onGround() && !aero$deckJump) {
            JumpDiagnostics.markFall(self);
        }

        JumpDiagnostics.tick(self, onGround, correction);
    }

    @Unique
    private static boolean aero$hasMovementInput(LivingEntity self) {
        return Math.abs(self.xxa) > 1.0E-5F || Math.abs(self.zza) > 1.0E-5F;
    }

    @Unique
    private void aero$dropLaunchResidue(LivingEntity self) {
        if (!self.onGround()) return;
        if (self.isInWater() || self.isInLava() || self.isInFluidType()) return;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) return;

        double gravity = self.getGravity();
        if (self.hasEffect(MobEffects.SLOW_FALLING)) gravity = Math.min(gravity, 0.01);

        double residue = AERO$VERTICAL_DRAG * gravity;
        if (residue <= 0.0) return;

        Vector3d up = tilt.transform(new Vector3d(0.0, 1.0, 0.0)).normalize();

        Vector3d tangent = new Vector3d(0.0, -residue, 0.0);
        tangent.fma(up.y * residue, up);

        self.setDeltaMovement(self.getDeltaMovement().subtract(tangent.x, tangent.y, tangent.z));
    }

    @Override
    public boolean aero$inDeckFlight() {
        if (aero$groundedBeforeTravel) return false;

        LivingEntity self = (LivingEntity) (Object) this;
        return aero$deckGravityArmed(self) && aero$vanillaAppliedAirGravity(self);
    }

    @Unique
    private boolean aero$deckGravityArmed(LivingEntity self) {
        if (Boots.holding(self)) return true;

        if (com.mlh.aero_player_tilt.tilt.TiltPolicy.deckGravity() == DeckGravity.WORLD) return false;

        if (aero$deckJump) return true;

        return PlayerTilt.isRenderTilted(self);
    }

    @Unique
    @Nullable
    private Vector3d aero$applyDeckGravity(LivingEntity self) {
        if (!aero$deckGravityArmed(self)) return null;
        if (!aero$vanillaAppliedAirGravity(self)) return null;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) {
            aero$deckJump = false;
            return null;
        }

        double gravity = self.getGravity();
        if (self.getDeltaMovement().y <= 0.0 && self.hasEffect(MobEffects.SLOW_FALLING)) {
            gravity = Math.min(gravity, 0.01);
        }
        if (gravity == 0.0) return null;

        double horizontalDrag = aero$groundedBeforeTravel
                ? aero$frictionBeforeTravel * AERO$AIR_DRAG
                : AERO$AIR_DRAG;
        if (horizontalDrag < 1.0e-4) return null;

        boolean booted = Boots.holding(self);

        Vector3d up = booted ? Boots.support(self, new Vector3d()) : null;
        if (up == null) up = tilt.transform(new Vector3d(0.0, 1.0, 0.0));

        Vec3 velocity = self.getDeltaMovement();

        Vector3d beforeDrag = new Vector3d(
                velocity.x / horizontalDrag,
                velocity.y / AERO$VERTICAL_DRAG + gravity,
                velocity.z / horizontalDrag);

        if (!booted) aero$restoreClippedStep(self, beforeDrag);

        double anisotropy = AERO$VERTICAL_DRAG - horizontalDrag;
        Vector3d correction = new Vector3d(up).mul(anisotropy * up.dot(beforeDrag));
        correction.y -= anisotropy * beforeDrag.y;

        double damped = AERO$VERTICAL_DRAG * gravity;
        correction.add(-damped * up.x, damped * (1.0 - up.y), -damped * up.z);

        if (booted) {
            if (self.onGround()) {
                double extra = (Boots.pull() - 1.0) * damped;
                if (extra != 0.0) correction.fma(-extra, up);
            }
        } else {
            aero$dropStandingResidue(correction, up, horizontalDrag, gravity);
        }

        self.addDeltaMovement(new Vec3(correction.x, correction.y, correction.z));
        return correction;
    }

    @Unique
    private void aero$restoreClippedStep(LivingEntity self, Vector3d beforeDrag) {
        if (self.onClimbable()) return;

        Vec3 step = aero$stepIntoMove;

        if (Math.abs(step.x) > Math.abs(beforeDrag.x)) beforeDrag.x = step.x;
        if (Math.abs(step.z) > Math.abs(beforeDrag.z)) beforeDrag.z = step.z;
    }

    @Unique
    private double aero$standingResidue(double gravity) {
        return Math.max(aero$motionBeforeTravel.y, -Math.abs(gravity));
    }

    @Unique
    private void aero$dropStandingResidue(Vector3d correction, Vector3d up,
                                          double horizontalDrag, double gravity) {
        if (!aero$groundedBeforeTravel || aero$deckJump) return;

        double residue = aero$standingResidue(gravity);
        if (residue >= 0.0) return;

        Vector3d tangent = new Vector3d(0.0, residue, 0.0);
        tangent.fma(-up.y * residue, up);
        tangent.mul(horizontalDrag);

        correction.sub(tangent);
    }

    @Unique
    private void aero$undoStandingPushStep(LivingEntity self) {
        if (!aero$groundedBeforeTravel || self.onGround() || aero$deckJump) return;
        if (!aero$deckGravityArmed(self) || !aero$vanillaAppliedAirGravity(self)) return;

        double residue = aero$standingResidue(self.getGravity());
        if (residue >= 0.0) return;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) return;

        Vector3d up = tilt.transform(new Vector3d(0.0, 1.0, 0.0));

        Vec3 step = aero$stepIntoMove;
        double stepNormal = up.x * step.x + up.y * step.y + up.z * step.z;
        if (stepNormal > -1.0e-9) return;

        Vec3 moved = self.position().subtract(aero$posIntoMove);
        double movedNormal = up.x * moved.x + up.y * moved.y + up.z * moved.z;

        double spent = movedNormal / stepNormal;
        if (spent <= 0.0) return;
        if (spent > 1.0) spent = 1.0;

        Vector3d tangent = new Vector3d(0.0, residue, 0.0);
        tangent.fma(-up.y * residue, up);
        tangent.mul(spent);

        self.setPos(self.getX() - tangent.x,
                self.getY() - tangent.y,
                self.getZ() - tangent.z);
    }

    @Unique
    private boolean aero$vanillaAppliedAirGravity(LivingEntity self) {
        if (!self.isControlledByLocalInstance()) return false;

        if (self.isInWater() || self.isInLava() || self.isInFluidType()) return false;
        if (self.isFallFlying()) return false;

        if (self.hasEffect(MobEffects.LEVITATION)) return false;
        if (!aero$chunkBelowLoaded) return false;
        if (self.shouldDiscardFriction()) return false;

        if (self instanceof FlyingAnimal) return false;

        return !(self instanceof Player player) || !player.getAbilities().flying;
    }
}
