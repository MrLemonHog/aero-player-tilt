package com.mlh.aero_player_tilt.mixins.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeckCarryMixin {
    @Inject(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayerGameMode;tick()V",
                    shift = At.Shift.BEFORE))
    private void aero$carryDeckAtFeet(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;

        SubLevel deck = Sable.HELPER.getTrackingSubLevel(self);
        if (deck == null || deck.isRemoved()) return;

        Quaterniond rotation = new Quaterniond(deck.logicalPose().orientation())
                .mul(new Quaterniond(deck.lastPose().orientation()).conjugate())
                .normalize();

        Vec3 position = self.position();
        AABB bounds = self.getBoundingBox();

        Vector3d up = new Vector3d(
                bounds.minX + (bounds.maxX - bounds.minX) * 0.5 - position.x,
                bounds.minY + (bounds.maxY - bounds.minY) * 0.5 - position.y,
                bounds.minZ + (bounds.maxZ - bounds.minZ) * 0.5 - position.z);

        Vector3d rotated = rotation.transform(new Vector3d(up));
        double dx = up.x - rotated.x;
        double dy = up.y - rotated.y;
        double dz = up.z - rotated.z;

        if (dx * dx + dy * dy + dz * dz < 1.0e-18) return;

        self.setPos(position.x + dx, position.y + dy, position.z + dz);
    }
}
