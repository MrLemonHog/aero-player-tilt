package com.mlh.aero_player_tilt.mixins.sable;

import com.mlh.aero_player_tilt.tilt.DeckBedAnchor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DeckBedSleepResyncMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "tick", at = @At("HEAD"))
    private void aero$pinSleeperBeforeSnapshot(CallbackInfo ci) {
        DeckBedAnchor.pin(this.player);
    }

    @Redirect(method = "handleMovePlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
    private void aero$skipSleepResync(ServerGamePacketListenerImpl listener,
                                      double x, double y, double z, float yRot, float xRot) {
        if (DeckBedAnchor.pinned(this.player)) return;

        listener.teleport(x, y, z, yRot, xRot);
    }
}
