package com.mlh.aero_player_tilt.mixins.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class EntitySkyLightCentreMixin {
    @ModifyReturnValue(method = "getSkyLightLevel", at = @At("RETURN"))
    private int aeroCamSync$skyForTheBodyNotTheCell(int sky, Entity entity, BlockPos pos) {
        return Math.max(sky, entity.level().getBrightness(LightLayer.SKY, pos));
    }
}
