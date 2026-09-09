package com.mlh.aero_player_tilt.mixins.client.sable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mlh.aero_player_tilt.AcsBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderVanillaEyeMixin {

    @WrapMethod(method = "render")
    private void aeroCamSync$renderOffVanillaEye(Entity entity, double x, double y, double z,
                                                 float rotationYaw, float partialTick,
                                                 PoseStack poseStack, MultiBufferSource buffer,
                                                 int packedLight, Operation<Void> original) {
        if (entity != Minecraft.getInstance().player) {
            original.call(entity, x, y, z, rotationYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        AcsBridge.ACS.withVanillaEye(() -> original.call(
                entity, x, y, z, rotationYaw, partialTick, poseStack, buffer, packedLight));
    }
}
