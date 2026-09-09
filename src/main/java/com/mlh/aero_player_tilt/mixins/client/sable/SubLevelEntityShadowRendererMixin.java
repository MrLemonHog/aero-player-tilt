package com.mlh.aero_player_tilt.mixins.client.sable;

import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.shadows.SubLevelEntityShadowRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SubLevelEntityShadowRenderer.class, remap = false)
public class SubLevelEntityShadowRendererMixin {
    @Redirect(method = "renderEntityShadowOnSubLevels",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 aeroCamSync$shadowFeetPivot(Entity entity, float partialTick) {
        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, partialTick);
        if (tilt == null) {
            return entity.getEyePosition(partialTick);
        }

        org.joml.Vector3d up = tilt.transform(new org.joml.Vector3d(0.0, entity.getEyeHeight(), 0.0));
        return entity.getPosition(partialTick).add(up.x, up.y, up.z);
    }
}
