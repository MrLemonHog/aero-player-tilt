package com.mlh.aero_player_tilt.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ArrowLayer.class)
public class StuckArrowTiltMixin {
    @Redirect(method = "renderStuckItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;"
                            + "render(Lnet/minecraft/world/entity/Entity;DDDFF"
                            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void aero$renderStuckArrowWithoutReentry(
            EntityRenderDispatcher dispatcher,
            Entity arrow, double x, double y, double z,
            float yRot, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            PoseStack hostPoseStack, MultiBufferSource hostBuffer, int hostLight,
            Entity host, float stuckX, float stuckY, float stuckZ, float hostPartialTick) {
        if (!aero$rotatedBySable(host, partialTick)) {
            dispatcher.render(arrow, x, y, z, yRot, partialTick, poseStack, buffer, packedLight);
            return;
        }

        EntityRenderer<? super Entity> renderer = dispatcher.getRenderer(arrow);
        Vec3 offset = renderer.getRenderOffset(arrow, partialTick);

        poseStack.pushPose();
        poseStack.translate(x + offset.x(), y + offset.y(), z + offset.z());
        renderer.render(arrow, yRot, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private static boolean aero$rotatedBySable(Entity host, float partialTick) {
        if (!EntitySubLevelUtil.shouldKick(host)) return false;

        return EntitySubLevelRotationHelper.getEntityOrientation(
                host,
                subLevel -> ((ClientSubLevel) subLevel).renderPose(),
                partialTick,
                EntitySubLevelRotationHelper.Type.ENTITY) != null;
    }
}
