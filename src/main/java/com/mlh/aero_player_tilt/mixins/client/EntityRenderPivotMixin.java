package com.mlh.aero_player_tilt.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mlh.aero_player_tilt.client.tilt.TiltRenderPivot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderPivotMixin {
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render("
                            + "Lnet/minecraft/world/entity/Entity;FF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.BEFORE))
    private <E extends Entity> void aero$feetPivotOn(E entity, double x, double y, double z,
                                                     float rotationYaw, float partialTicks,
                                                     PoseStack poseStack, MultiBufferSource buffer,
                                                     int packedLight, CallbackInfo ci) {
        Vector3d correction = TiltRenderPivot.correction(entity, partialTicks, new Vector3d());
        if (correction == null) return;

        poseStack.translate(correction.x, correction.y, correction.z);
    }

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE))
    private <E extends Entity> void aero$feetPivotOff(E entity, double x, double y, double z,
                                                      float rotationYaw, float partialTicks,
                                                      PoseStack poseStack, MultiBufferSource buffer,
                                                      int packedLight, CallbackInfo ci) {
        Vector3d correction = TiltRenderPivot.correction(entity, partialTicks, new Vector3d());
        if (correction == null) return;

        poseStack.translate(-correction.x, -correction.y, -correction.z);
    }
}
