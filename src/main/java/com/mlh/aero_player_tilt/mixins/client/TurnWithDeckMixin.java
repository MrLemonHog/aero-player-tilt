package com.mlh.aero_player_tilt.mixins.client;

import com.mlh.aero_player_tilt.client.tilt.DeckTurn;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class TurnWithDeckMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void aero$turnWithDeck(DeltaTracker deltaTracker, CallbackInfo ci) {
        DeckTurn.follow(this.minecraft.player);
    }
}
