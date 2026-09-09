package com.mlh.aero_player_tilt.mixins.client;

import com.mlh.aero_player_tilt.client.tilt.DeckTurn;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class DeckMomentumMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void aero$carryMomentum(CallbackInfo ci) {
        DeckTurn.carryMomentum((LocalPlayer) (Object) this);
    }
}
