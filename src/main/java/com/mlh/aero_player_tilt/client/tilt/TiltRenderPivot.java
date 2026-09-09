package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.entity.Entity;
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

        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, partialTick);
        if (tilt == null) return null;

        double eyeHeight = entity.getEyeHeight();
        Vector3d pivot = tilt.transformInverse(dest.set(0.0, eyeHeight, 0.0));
        dest.set(-pivot.x, eyeHeight - pivot.y, -pivot.z);

        return dest.lengthSquared() < 1.0e-12 ? null : dest;
    }
}
