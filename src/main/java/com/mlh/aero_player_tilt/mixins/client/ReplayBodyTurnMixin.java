package com.mlh.aero_player_tilt.mixins.client;

import com.mlh.aero_player_tilt.client.tilt.ReplayAnchor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LivingEntity.class, priority = 2000)
public class ReplayBodyTurnMixin {
    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private double aero$bodyTurnAlongDeckX(double dx) {
        Vec3 move = ReplayAnchor.bodyMove((LivingEntity) (Object) this);
        return move == null ? dx : move.x;
    }

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 1)
    private double aero$bodyTurnAlongDeckZ(double dz) {
        Vec3 move = ReplayAnchor.bodyMove((LivingEntity) (Object) this);
        return move == null ? dz : move.z;
    }
}
