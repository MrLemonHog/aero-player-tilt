package com.mlh.aero_player_tilt.mixins.client.acs;

import com.mlh.aero_player_tilt.client.utils.FirstPersonCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = com.playsi.aero_cam_sync.client.compat.FirstPersonCompat.class, remap = false)
public class AcsFirstPersonBodyMixin {
    @Inject(method = "isRenderingFirstPersonBody", at = @At("HEAD"), cancellable = true)
    private static void aero$ourBodyIsAlreadyLeaned(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!FirstPersonCompat.leanedBySable(mc.player, mc.getTimer().getGameTimeDeltaPartialTick(true))) {
            return;
        }

        cir.setReturnValue(false);
    }
}
