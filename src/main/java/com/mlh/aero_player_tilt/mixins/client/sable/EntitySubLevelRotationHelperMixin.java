package com.mlh.aero_player_tilt.mixins.client.sable;

import com.mlh.aero_player_tilt.client.tilt.TiltDiagnostics;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(value = EntitySubLevelRotationHelper.class, remap = false)
public class EntitySubLevelRotationHelperMixin {
    @Inject(method = "getEntityOrientation", at = @At("HEAD"), cancellable = true)
    private static void aeroCamSync$noDoubleCameraTilt(Entity cameraEntity,
                                                       Function<SubLevel, Pose3dc> poseProvider,
                                                       float partialTicks,
                                                       EntitySubLevelRotationHelper.Type type,
                                                       CallbackInfoReturnable<Quaterniond> cir) {
        if (type == EntitySubLevelRotationHelper.Type.ENTITY) {
            if (com.mlh.aero_player_tilt.tilt.DeckBedAnchor.pinned(cameraEntity)) {
                cir.setReturnValue(null);
            }
            return;
        }

        if (type != EntitySubLevelRotationHelper.Type.CAMERA) return;

        if (!PlayerTilt.isRenderTilted(cameraEntity)) return;

        TiltDiagnostics.recordSableOrientation("cancel", 0.0);

        cir.setReturnValue(null);
    }
}
