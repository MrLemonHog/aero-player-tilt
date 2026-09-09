package com.mlh.aero_player_tilt.mixins.client;

import com.mlh.aero_player_tilt.client.tilt.ReplayAnchor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class ReplayWalkAnimationMixin {
    @ModifyVariable(method = "updateWalkAnimation", at = @At("HEAD"), argsOnly = true)
    private float aero$walkAlongDeck(float speed) {
        float alongDeck = ReplayAnchor.deckSpeed((LivingEntity) (Object) this);
        return alongDeck < 0f ? speed : alongDeck;
    }
}
