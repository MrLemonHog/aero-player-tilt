package com.mlh.aero_player_tilt.mixins.client;

import com.mlh.aero_player_tilt.client.tilt.TiltedZoom;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class ThirdPersonZoomMixin {

    @Shadow
    private BlockGetter level;

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void aero$zoomFromTheEyeWeDrew(float maxZoom, CallbackInfoReturnable<Float> cir) {
        Float tilted = TiltedZoom.maxZoom((Camera) (Object) this, this.level, maxZoom);
        if (tilted != null) cir.setReturnValue(tilted);
    }
}
