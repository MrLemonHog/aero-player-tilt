package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.compat.PhysicsModCompat;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public final class TiltRenderPivot {
    private TiltRenderPivot() {}

    @Nullable
    public static Vector3d correction(Entity entity, float partialTick, Vector3d dest) {
        if (!EntitySubLevelUtil.shouldKick(entity)) return null;

        if (EntitySubLevelRotationHelper.getSubLevelInheritedOrientation(
                entity,
                subLevel -> ((ClientSubLevel) subLevel).renderPose(),
                EntitySubLevelRotationHelper.Type.ENTITY) != null) {
            return null;
        }

        Quaterniond tilt = PhysicsModCompat.apply(entity, partialTick,
                PlayerTilt.getRenderOrientation(entity, partialTick));
        Vec3 shift = PhysicsModCompat.untrackedShift(entity, partialTick);

        if (tilt == null) {
            if (shift == Vec3.ZERO) return null;
            return dest.set(shift.x, shift.y, shift.z);
        }

        double eyeHeight = entity.getEyeHeight();
        Vector3d pivot = tilt.transformInverse(dest.set(0.0, eyeHeight, 0.0));
        dest.set(-pivot.x, eyeHeight - pivot.y, -pivot.z);

        Vec3 move = PhysicsModCompat.tracked(entity)
                ? PhysicsModCompat.drift(entity, partialTick, eyeHeight).scale(-1.0)
                : shift;
        if (move.lengthSqr() > 0.0) {
            dest.add(tilt.transformInverse(new Vector3d(move.x, move.y, move.z)));
        }

        return dest.lengthSquared() < 1.0e-12 ? null : dest;
    }
}
