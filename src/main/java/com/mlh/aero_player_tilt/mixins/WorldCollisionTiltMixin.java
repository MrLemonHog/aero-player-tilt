package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mlh.aero_player_tilt.tilt.TiltedPlayerBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class WorldCollisionTiltMixin {
    @WrapMethod(method = "collide")
    private Vec3 aero$tiltedWorldCollision(Vec3 motion, Operation<Vec3> original) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player player) || !player.isLocalPlayer()) return original.call(motion);

        Vec3 tilted = TiltedPlayerBox.collideWithWorld(self, motion);
        return tilted != null ? tilted : original.call(motion);
    }
}
