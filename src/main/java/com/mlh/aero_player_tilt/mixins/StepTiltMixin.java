package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class StepTiltMixin {
    @Unique private Vec3 aero$movedFrom = Vec3.ZERO;

    @Shadow public abstract Vec3 position();

    @Shadow
    private boolean isStateClimbable(BlockState state) {
        throw new AssertionError();
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void aero$noteStart(MoverType type, Vec3 pos, CallbackInfo ci) {
        aero$movedFrom = position();
    }

    @WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Ljava/lang/Math;sqrt(D)D"))
    private double aero$strideAlongTheFace(double lengthSqr, Operation<Double> original) {
        Entity self = (Entity) (Object) this;

        Quaterniondc orientation = EntitySubLevelUtil.getCustomEntityOrientation(self, 1.0f);
        if (orientation == null) return original.call(lengthSqr);

        Vec3 moved = position().subtract(aero$movedFrom);
        Vector3d local = orientation.transformInverse(new Vector3d(moved.x, moved.y, moved.z));

        if (!isStateClimbable(self.level().getBlockState(self.getOnPos()))) local.y = 0.0;

        return original.call(local.lengthSquared());
    }
}
