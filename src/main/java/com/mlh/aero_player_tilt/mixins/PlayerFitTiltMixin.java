package com.mlh.aero_player_tilt.mixins;

import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.TiltedFitCheck;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerFitTiltMixin {
    @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
    private void aeroCamSync$tiltedFit(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) return;

        AABB box = self.getDimensions(pose).makeBoundingBox(self.position()).deflate(1.0E-7);

        org.joml.Vector3d blocker = self.level().isClientSide ? new org.joml.Vector3d() : null;

        boolean worldFits = self.level().noCollision(self, box);
        boolean subLevelHits = worldFits
                && TiltedFitCheck.collidesWithSubLevels(self.level(), box, tilt, blocker);

        if (self.level().isClientSide) {
            com.mlh.aero_player_tilt.client.tilt.TiltDiagnostics.recordFit(
                    self, pose, worldFits, subLevelHits, subLevelHits ? blocker : null);
        }

        cir.setReturnValue(worldFits && !subLevelHits);
    }
}
