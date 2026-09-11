package com.mlh.aero_player_tilt.mixins.sable;

import com.llamalad7.mixinextras.sugar.Local;
import com.mlh.aero_player_tilt.tilt.DeckCarryGrace;
import com.mlh.aero_player_tilt.tilt.DeckFlightAccess;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.StepUpBudget;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SubLevelEntityCollision.class, remap = false)
public class SubLevelEntityCollisionMixin {
    private static final String GET_EYE_HEIGHT = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F";

    @Inject(method = "collide", at = @At("HEAD"))
    private static void aeroCamSync$resetStepUpBudget(Entity entity, Vec3 collisionMotion,
                                                      Vec3 velocityMotion, LevelReusedVectors sink,
                                                      CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir) {
        StepUpBudget.reset(entity.level().isClientSide);
    }

    @Inject(method = "collide", at = @At("RETURN"))
    private static void aeroCamSync$releaseWallTracking(Entity entity, Vec3 collisionMotion,
                                                        Vec3 velocityMotion, LevelReusedVectors sink,
                                                        CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir) {
        SubLevelEntityCollision.CollisionInfo info = cir.getReturnValue();
        if (info == null || info.trackingSubLevel == null) return;
        if (!(entity instanceof Player)) return;
        if (!PlayerTilt.isTilted(entity)) return;

        if (info.verticalCollisionBelow) {
            DeckCarryGrace.supported(entity);
            return;
        }

        if (info.firstCollisions != null && info.firstCollisions.containsKey(info.trackingSubLevel)) {
            DeckCarryGrace.touched(entity);
        } else if (!aeroCamSync$midStride(entity, info.trackingSubLevel.getUniqueId())) {
            DeckCarryGrace.separated(entity);
        }

        if (DeckCarryGrace.supportedRecently(entity)) return;

        info.trackingSubLevel = null;
    }

    @Unique
    private static boolean aeroCamSync$midStride(Entity entity, java.util.UUID deckId) {
        return DeckCarryGrace.footedOn(entity, deckId) || DeckCarryGrace.supportedWithin(entity, 2);
    }

    @Redirect(method = "collide",
            at = @At(value = "INVOKE", target = GET_EYE_HEIGHT, ordinal = 1))
    private static float aeroCamSync$pivotUp(Entity entity) {
        return PlayerTilt.pivotHeight(entity);
    }

    @Redirect(method = "collide",
            at = @At(value = "INVOKE", target = GET_EYE_HEIGHT, ordinal = 2))
    private static float aeroCamSync$pivotDown(Entity entity) {
        return PlayerTilt.pivotHeight(entity);
    }

    @Redirect(method = "transformEntityBoundsCenter",
            at = @At(value = "INVOKE", target = GET_EYE_HEIGHT, ordinal = 0))
    private static float aeroCamSync$pivotInitial(Entity entity) {
        return PlayerTilt.pivotHeight(entity);
    }

    @Redirect(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Vector3d;dot(Lorg/joml/Vector3dc;)D",
                    ordinal = 0))
    private static double aeroCamSync$floorByWorldUp(Vector3d normalizedMtv, Vector3dc entityUp,
                                                     @Local(argsOnly = true) Entity entity) {
        if (!PlayerTilt.isTilted(entity)) return normalizedMtv.dot(entityUp);

        if (com.mlh.aero_player_tilt.tilt.Boots.holding(entity)) return normalizedMtv.dot(entityUp);

        return normalizedMtv.y;
    }

    @Redirect(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Vector3d;mul(DLorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                    ordinal = 1))
    private static Vector3d aeroCamSync$antiSlide(Vector3d entityUp, double alongEntityUp, Vector3d maxMTV,
                                                  @Local(argsOnly = true) Entity entity) {
        if (!PlayerTilt.isTilted(entity)) {
            return entityUp.mul(alongEntityUp, maxMTV);
        }

        if (com.mlh.aero_player_tilt.tilt.Boots.holding(entity)) {
            aeroCamSync$recordMtv(entity, "boots", maxMTV);

            Vector3d support = com.mlh.aero_player_tilt.tilt.Boots.support(entity, new Vector3d());
            double outwards = support == null ? 0.0 : support.dot(maxMTV);

            if (outwards != 0.0) {
                return maxMTV.set(support).mul(Math.signum(outwards));
            }

            return entityUp.mul(alongEntityUp, maxMTV);
        }

        if (entity instanceof DeckFlightAccess flight && flight.aero$inDeckFlight()) {
            aeroCamSync$recordMtv(entity, "vertDeck", maxMTV);
            return entityUp.mul(alongEntityUp, maxMTV);
        }

        double vertical = maxMTV.y;
        double depth = maxMTV.length();

        if (depth < 1.0e-9 || vertical / depth < PlayerTilt.floorNormalY()) {
            aeroCamSync$recordMtv(entity, "wallKeep", maxMTV);
            aeroCamSync$armStraightenScale(entity, 1.0);
            return entityUp.mul(alongEntityUp, maxMTV);
        }

        aeroCamSync$recordMtv(entity, "vert", maxMTV);

        aeroCamSync$armStraightenScale(entity, depth / Math.abs(vertical));
        return maxMTV.set(0.0, vertical, 0.0);
    }

    @Redirect(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Vector3d;normalize(D)Lorg/joml/Vector3d;"))
    private static Vector3d aeroCamSync$antiSlideSeparation(Vector3d straightened, double preLength,
                                                            @Local(argsOnly = true) Entity entity) {
        return straightened.normalize(preLength * aeroCamSync$takeStraightenScale(entity));
    }

    @Unique private static double aeroCamSync$clientStraightenScale = 1.0;
    @Unique private static double aeroCamSync$serverStraightenScale = 1.0;

    @Unique private static final double AERO$MAX_STRAIGHTEN_SCALE = 4.0;

    @Unique
    private static void aeroCamSync$armStraightenScale(Entity entity, double scale) {
        double clamped = Math.min(Math.max(scale, 1.0), AERO$MAX_STRAIGHTEN_SCALE);
        if (entity.level().isClientSide) aeroCamSync$clientStraightenScale = clamped;
        else aeroCamSync$serverStraightenScale = clamped;
    }

    @Unique
    private static double aeroCamSync$takeStraightenScale(Entity entity) {
        if (entity.level().isClientSide) {
            double scale = aeroCamSync$clientStraightenScale;
            aeroCamSync$clientStraightenScale = 1.0;
            return scale;
        }
        double scale = aeroCamSync$serverStraightenScale;
        aeroCamSync$serverStraightenScale = 1.0;
        return scale;
    }

    @Redirect(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Vector3d;mul(D)Lorg/joml/Vector3d;",
                    ordinal = 0))
    private static Vector3d aeroCamSync$horizontalWallLoss(Vector3d normalizedMtv, double alongNormal,
                                                           @Local(argsOnly = true) Entity entity,
                                                           @Local(argsOnly = true) LevelReusedVectors sink) {
        if (!PlayerTilt.isTilted(entity) || com.mlh.aero_player_tilt.tilt.Boots.holding(entity)) {
            return normalizedMtv.mul(alongNormal);
        }

        aeroCamSync$recordMtv(entity, "horiz", normalizedMtv);

        double hx = normalizedMtv.x;
        double hz = normalizedMtv.z;
        double horizontal = Math.sqrt(hx * hx + hz * hz);

        if (horizontal < 1.0e-6) {
            return normalizedMtv.set(0.0, 0.0, 0.0);
        }

        hx /= horizontal;
        hz /= horizontal;

        Vec3 velocity = entity.getDeltaMovement();
        double into = hx * velocity.x + hz * velocity.z;

        if (into < 0.0) {
            Vector3dc up = sink.entityUpDirection;
            double alongUp = velocity.x * up.x() + velocity.y * up.y() + velocity.z * up.z();
            double tangential = into - alongUp * (hx * up.x() + hz * up.z());

            into = Math.max(into, Math.min(0.0, tangential));
        }

        return normalizedMtv.set(hx * into, 0.0, hz * into);
    }

    @Redirect(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;sat("
                            + "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;"
                            + "Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;"
                            + "Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"))
    private static Vector3d aeroCamSync$hideCeilingFromSolver(OrientedBoundingBox3d entityBox,
                                                              OrientedBoundingBox3d blockBox,
                                                              Vector3d dest,
                                                              @Local(argsOnly = true) Entity entity,
                                                              @Local(argsOnly = true) LevelReusedVectors sink) {
        Vector3d mtv = OrientedBoundingBox3d.sat(entityBox, blockBox, dest);

        if (mtv.x == Double.MAX_VALUE || mtv.y == Double.MAX_VALUE || mtv.z == Double.MAX_VALUE) {
            return mtv;
        }

        double length = mtv.length();
        if (length < 1.0e-9) return mtv;

        if (!PlayerTilt.isTilted(entity)) return mtv;

        if (com.mlh.aero_player_tilt.tilt.Boots.holding(entity)) return mtv;

        Vector3dc entityUp = sink.entityUpDirection;

        if (mtv.dot(entityUp) / length > -0.6) return mtv;

        Vec3 velocity = entity.getDeltaMovement();
        double intoCeiling = velocity.x * entityUp.x() + velocity.y * entityUp.y() + velocity.z * entityUp.z();
        if (intoCeiling > 0.0) return aeroCamSync$straightenCeiling(entity, mtv, length);

        aeroCamSync$recordMtv(entity, "ceilHide", mtv);
        return mtv.zero();
    }

    @Unique
    private static Vector3d aeroCamSync$straightenCeiling(Entity entity, Vector3d mtv, double length) {
        double vertical = Math.abs(mtv.y);
        if (vertical < 1.0e-9) return mtv;

        double straightened = Math.min(length * length / vertical, length * AERO$MAX_STRAIGHTEN_SCALE);

        aeroCamSync$recordMtv(entity, "ceilFlat", mtv);

        return mtv.set(0.0, -straightened, 0.0);
    }

    @Redirect(method = "tryStepUp",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;maxUpStep()F"))
    private static float aeroCamSync$stepUpBudget(Entity entity) {
        return StepUpBudget.remaining(entity);
    }

    @Redirect(method = "tryStepUp",
            at = @At(value = "INVOKE",
                    target = "Lorg/joml/Vector3d;fma(DLorg/joml/Vector3dc;)Lorg/joml/Vector3d;",
                    ordinal = 2))
    private static Vector3d aeroCamSync$accountStepUp(Vector3d collisionMotion, double stepUp,
                                                      Vector3dc up,
                                                      @Local(argsOnly = true) Entity entity) {
        StepUpBudget.spend(entity, stepUp);
        aeroCamSync$recordMtv(entity, "step", new Vector3d(up).mul(stepUp));

        return collisionMotion.fma(stepUp, up);
    }

    @Unique
    private static void aeroCamSync$recordMtv(Entity entity, String branch, Vector3dc mtv) {
        if (!entity.level().isClientSide) return;
        if (!(entity instanceof net.minecraft.client.player.LocalPlayer)) return;

        com.mlh.aero_player_tilt.client.tilt.TiltDiagnostics.recordMtv(branch, mtv.x(), mtv.y(), mtv.z());
    }

    @Inject(method = "collide",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/Pose3d;lerp("
                            + "Ldev/ryanhcode/sable/companion/math/Pose3dc;D"
                            + "Ldev/ryanhcode/sable/companion/math/Pose3d;)"
                            + "Ldev/ryanhcode/sable/companion/math/Pose3d;",
                    ordinal = 1,
                    shift = At.Shift.AFTER),
            require = 0)
    private static void aeroCamSync$reorientBox(Entity entity,
                                                Vec3 collisionMotion,
                                                Vec3 velocityMotion,
                                                LevelReusedVectors sink,
                                                CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir,
                                                @Local Quaterniondc customEntityOrientation,
                                                @Local(ordinal = 0) OrientedBoundingBox3d entityBoundsOBB) {
        if (customEntityOrientation != null) {
            com.mlh.aero_player_tilt.tilt.BoxOrientation.forBox(
                    customEntityOrientation, sink.subLevelPose, sink.entityBoxOrientation);
        } else {
            sink.entityBoxOrientation
                    .identity()
                    .rotateY(SubLevelEntityCollision.getHitBoxYaw(sink.subLevelPose));
        }

        entityBoundsOBB.setOrientation(sink.entityBoxOrientation);
    }
}
