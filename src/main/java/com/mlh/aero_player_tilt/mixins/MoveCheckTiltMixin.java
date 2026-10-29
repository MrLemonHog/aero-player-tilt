package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mlh.aero_player_tilt.tilt.TiltedPlayerBox;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MoveCheckTiltMixin {
    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "isPlayerCollidingWithAnythingNew")
    private boolean aero$tiltedMoveCheck(LevelReader level, AABB box, double x, double y, double z,
                                         Operation<Boolean> original) {
        Boolean tilted = TiltedPlayerBox.collidesWithWorldAnew(this.player, x, y, z);
        return tilted != null ? tilted : original.call(level, box, x, y, z);
    }
}
