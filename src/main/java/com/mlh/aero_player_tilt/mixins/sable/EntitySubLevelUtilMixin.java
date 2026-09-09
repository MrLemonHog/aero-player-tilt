package com.mlh.aero_player_tilt.mixins.sable;

import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntitySubLevelUtil.class, remap = false)
public class EntitySubLevelUtilMixin {
    @Inject(method = "getCustomEntityOrientation", at = @At("HEAD"), cancellable = true)
    private static void aeroCamSync$playerBodyTilt(Entity entity, float partialTicks,
                                                   CallbackInfoReturnable<Quaterniondc> cir) {
        Quaterniond tilt = PlayerTilt.getOrientation(entity, partialTicks);
        if (tilt != null) {
            cir.setReturnValue(tilt);
        }
    }

    @Inject(method = "hasCustomEntityOrientation", at = @At("HEAD"), cancellable = true)
    private static void aeroCamSync$hasPlayerBodyTilt(Entity entity,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (PlayerTilt.isTilted(entity)) {
            cir.setReturnValue(true);
        }
    }
}
