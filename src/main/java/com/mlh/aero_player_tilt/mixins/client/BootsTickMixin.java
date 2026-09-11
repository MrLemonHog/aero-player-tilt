package com.mlh.aero_player_tilt.mixins.client;

import com.mlh.aero_player_tilt.client.tilt.BootsController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class BootsTickMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void aero$magneticBoots(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        BootsController.tick(self);
        BootsController.carry(self);
    }
}
