package com.mlh.aero_player_tilt.mixins;

import com.mlh.aero_player_tilt.tilt.InsideBlockReach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockInsideTiltMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void aeroCamSync$onlyWhereTheBodyIs(Level level, BlockPos pos, Entity entity,
                                                CallbackInfo ci) {
        if (InsideBlockReach.reached(entity, level, pos)) return;

        ci.cancel();
    }
}
