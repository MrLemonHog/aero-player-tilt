package com.mlh.aero_player_tilt.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mlh.aero_player_tilt.client.tilt.TiltRenderPivot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderPivotMixin {
    @Unique
    private final Vector3f aero$snap = new Vector3f();

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
        aero$snap.zero();
        if (com.mlh.aero_player_tilt.client.compat.RealCameraCompat.drawsSelf(entity)) return;

        Vector3d correction = TiltRenderPivot.correction(entity, partialTicks, new Vector3d());
        if (correction != null) {
            Vector3f scaled = poseStack.last().pose().getScale(new Vector3f());
            poseStack.translate(correction.x / aero$axis(scaled.x),
                    correction.y / aero$axis(scaled.y),
                    correction.z / aero$axis(scaled.z));

            Vec3 drift = TiltRenderPivot.arcDrift(entity, partialTicks);
            if (drift != Vec3.ZERO) {
                Vec3 arc = entity.getPosition(partialTicks).add(drift);
                Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                Matrix4f pose = poseStack.last().pose();
                Vector3f at = pose.transformPosition(new Vector3f());

                aero$snap.set((float) (arc.x - cam.x - at.x),
                        (float) (arc.y - cam.y - at.y),
                        (float) (arc.z - cam.z - at.z));
                new Matrix4f(pose).invert().transformDirection(aero$snap);
                poseStack.translate(aero$snap.x, aero$snap.y, aero$snap.z);
            }
        }
    }

    @Unique
    private static float aero$axis(float scale) {
        return scale > 1.0e-6f ? scale : 1.0f;
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
        if (aero$snap.lengthSquared() > 0f) {
            poseStack.translate(-aero$snap.x, -aero$snap.y, -aero$snap.z);
            aero$snap.zero();
        }
        if (com.mlh.aero_player_tilt.client.compat.RealCameraCompat.drawsSelf(entity)) return;

        Vector3d correction = TiltRenderPivot.correction(entity, partialTicks, new Vector3d());
        if (correction == null) return;

        poseStack.translate(-correction.x, -correction.y, -correction.z);
    }
}
