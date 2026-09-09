package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mlh.aero_player_tilt.tilt.TiltedPlayerBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(value = ProjectileUtil.class, priority = 900)
public abstract class EntityHitTiltMixin {
    private static final String WITH_SHOOTER =
            "getEntityHitResult(Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)"
                    + "Lnet/minecraft/world/phys/EntityHitResult;";

    private static final String WITH_LEVEL =
            "getEntityHitResult(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)"
                    + "Lnet/minecraft/world/phys/EntityHitResult;";

    private static final String CLIP = "Lnet/minecraft/world/phys/AABB;clip("
            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;";

    private static final String CONTAINS =
            "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z";

    @ModifyExpressionValue(method = WITH_SHOOTER, at = @At(value = "INVOKE", target = CLIP))
    private static Optional<Vec3> aeroCamSync$tiltedHit(Optional<Vec3> original,
                                                        @Local(argsOnly = true) Entity source,
                                                        @Local(argsOnly = true, ordinal = 0) Vec3 startVec,
                                                        @Local(argsOnly = true, ordinal = 1) Vec3 endVec,
                                                        @Local(ordinal = 2) Entity clipping) {
        Optional<Vec3> tilted = TiltedPlayerBox.clipRay(clipping, source.level(), startVec, endVec);
        return tilted != null ? tilted : original;
    }

    @ModifyExpressionValue(method = WITH_SHOOTER, at = @At(value = "INVOKE", target = CONTAINS))
    private static boolean aeroCamSync$tiltedContains(boolean original,
                                                      @Local(argsOnly = true) Entity source,
                                                      @Local(argsOnly = true, ordinal = 0) Vec3 startVec,
                                                      @Local(ordinal = 2) Entity clipping) {
        Boolean tilted = TiltedPlayerBox.containsPoint(clipping, source.level(), startVec);
        return tilted != null ? tilted : original;
    }

    @ModifyExpressionValue(method = WITH_LEVEL, at = @At(value = "INVOKE", target = CLIP))
    private static Optional<Vec3> aeroCamSync$tiltedHitInLevel(Optional<Vec3> original,
                                                               @Local(argsOnly = true) Level level,
                                                               @Local(argsOnly = true, ordinal = 0) Vec3 startVec,
                                                               @Local(argsOnly = true, ordinal = 1) Vec3 endVec,
                                                               @Local(ordinal = 2) Entity clipping) {
        Optional<Vec3> tilted = TiltedPlayerBox.clipRay(clipping, level, startVec, endVec);
        return tilted != null ? tilted : original;
    }
}
