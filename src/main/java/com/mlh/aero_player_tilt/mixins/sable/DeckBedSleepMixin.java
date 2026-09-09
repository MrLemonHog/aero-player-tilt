package com.mlh.aero_player_tilt.mixins.sable;

import com.mlh.aero_player_tilt.tilt.DeckBedAnchor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class DeckBedSleepMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void aero$pinToBedEarly(CallbackInfo ci) {
        DeckBedAnchor.pin((LivingEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aero$pinToBedLate(CallbackInfo ci) {
        DeckBedAnchor.pin((LivingEntity) (Object) this, true);
    }
}
