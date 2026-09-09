package com.mlh.aero_player_tilt.mixins.sable;

import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.ActiveSableCompanion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class ActiveSableCompanionFeetPosMixin {
    public Vector3d getFeetPos(Entity entity, float yOffset, Quaterniondc rotation) {
        Vec3 position = entity.position();
        Vector3d feet = new Vector3d(position.x, position.y, position.z);

        if (rotation == null) {
            return feet.sub(0.0, yOffset, 0.0);
        }

        return feet.sub(rotation.transform(
                new Vector3d(0.0, yOffset + PlayerTilt.pivotHeight(entity), 0.0)));
    }
}
