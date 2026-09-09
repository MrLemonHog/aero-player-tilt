package com.mlh.aero_player_tilt.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mlh.aero_player_tilt.tilt.BoxOrientation;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class TiltedHitboxDebugMixin {
    @Inject(method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            + "Lnet/minecraft/world/entity/Entity;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private static void aeroCamSync$trueTiltedHitbox(PoseStack poseStack,
                                                     VertexConsumer consumer,
                                                     Entity entity,
                                                     float partialTick,
                                                     float red, float green, float blue,
                                                     CallbackInfo ci) {
        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, partialTick);
        if (tilt == null) return;

        if (!PlayerTilt.isRenderTilted(entity) && !aeroCamSync$onDeck(entity)) return;

        SubLevel deck = DeckFrame.forBox(entity);
        Quaterniond boxOrientation = deck instanceof ClientSubLevel clientSubLevel
                ? BoxOrientation.forBox(tilt, clientSubLevel.renderPose(), new Quaterniond())
                : new Quaterniond(tilt);

        AABB bounds = entity.getBoundingBox()
                .move(-entity.getX(), -entity.getY(), -entity.getZ());

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf(boxOrientation));
        LevelRenderer.renderLineBox(poseStack, consumer, bounds, red, green, blue, 1.0F);

        if (entity instanceof LivingEntity) {
            float eye = entity.getEyeHeight();
            LevelRenderer.renderLineBox(poseStack, consumer,
                    bounds.minX, eye - 0.01F, bounds.minZ,
                    bounds.maxX, eye + 0.01F, bounds.maxZ,
                    1.0F, 0.0F, 0.0F, 1.0F);
        }
        poseStack.popPose();

        Vector3d eyeOffset = boxOrientation.transform(new Vector3d(0.0, entity.getEyeHeight(), 0.0));
        aeroCamSync$renderVector(poseStack, consumer,
                new Vector3f((float) eyeOffset.x, (float) eyeOffset.y, (float) eyeOffset.z),
                entity.getViewVector(partialTick).scale(2.0), -16776961);

        ci.cancel();
    }

    private static void aeroCamSync$renderVector(PoseStack poseStack, VertexConsumer consumer,
                                                 Vector3f start, Vec3 vector, int color) {
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, start)
                .setColor(color)
                .setNormal(pose, (float) vector.x, (float) vector.y, (float) vector.z);
        consumer.addVertex(pose,
                        (float) (start.x() + vector.x),
                        (float) (start.y() + vector.y),
                        (float) (start.z() + vector.z))
                .setColor(color)
                .setNormal(pose, (float) vector.x, (float) vector.y, (float) vector.z);
    }

    private static boolean aeroCamSync$onDeck(Entity entity) {
        SubLevel live = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        return live != null && !live.isRemoved();
    }
}
