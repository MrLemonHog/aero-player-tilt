package com.mlh.aero_player_tilt.mixins.client.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LocalPlayer.class, priority = 900)
public abstract class LocalPlayerSelfSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",
            at = @At("HEAD"), cancellable = true, order = 900)
    private void aeroCamSync$hearOurselves(SoundEvent sound, float volume, float pitch,
                                           CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        if (!EntitySubLevelUtil.hasCustomEntityOrientation(self)) return;

        Vector3d feet = Sable.HELPER.getFeetPos(self, 0.0f);

        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        PlayLevelSoundEvent.AtEntity event =
                EventHooks.onPlaySoundAtEntity(self, holder, self.getSoundSource(), volume, pitch);

        if (!event.isCanceled() && event.getSound() != null) {
            self.level().playLocalSound(feet.x, feet.y, feet.z,
                    event.getSound().value(), event.getSource(),
                    event.getNewVolume(), event.getNewPitch(), false);
        }

        ci.cancel();
    }
}
