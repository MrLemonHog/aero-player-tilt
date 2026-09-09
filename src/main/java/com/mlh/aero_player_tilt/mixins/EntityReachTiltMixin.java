package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.TiltedPlayerBox;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class EntityReachTiltMixin {
    @Shadow
    public ServerPlayer player;

    @WrapOperation(method = "handleInteract",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB aeroCamSync$tiltedReachBox(Entity target, Operation<AABB> original) {
        AABB vanilla = original.call(target);

        AABB tilted = TiltedPlayerBox.enclosingBox(target);
        AABB box = tilted != null ? vanilla.minmax(tilted) : vanilla;

        double headOffset = PlayerTilt.eyeDisplacement(this.player);
        return headOffset > 0.0 ? box.inflate(headOffset) : box;
    }
}
