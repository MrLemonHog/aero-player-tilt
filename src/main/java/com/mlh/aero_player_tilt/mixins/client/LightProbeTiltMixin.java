package com.mlh.aero_player_tilt.mixins.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mlh.aero_player_tilt.client.tilt.TiltedLightProbe;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class LightProbeTiltMixin {
    @ModifyReturnValue(method = "getLightProbePosition", at = @At("RETURN"))
    private Vec3 aeroCamSync$tiltedLightProbe(Vec3 probe, float partialTick) {
        Entity self = (Entity) (Object) this;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, partialTick);
        if (tilt == null) return probe;

        return TiltedLightProbe.resolve(self, partialTick, tilt);
    }
}
