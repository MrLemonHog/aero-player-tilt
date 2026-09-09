package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class TiltedLightProbe {
    private TiltedLightProbe() {}

    private static final int STEPS = 4;

    public static Vec3 resolve(Entity entity, float partialTick, Quaterniond tilt) {
        Vec3 feet = entity.getPosition(partialTick);
        double eyeHeight = entity.getEyeHeight();

        Vector3d tilted = tilt.transform(new Vector3d(0.0, eyeHeight, 0.0));

        Vec3 upright = new Vec3(feet.x, feet.y + eyeHeight, feet.z);
        Vec3 head = new Vec3(feet.x + tilted.x, feet.y + tilted.y, feet.z + tilted.z);

        Level level = entity.level();
        Vector3d probe = new Vector3d();
        Vector3d local = new Vector3d();
        BlockPos.MutableBlockPos localPos = new BlockPos.MutableBlockPos();
        BlockPos previous = null;

        Vec3 chosen = upright;
        int chosenStep = STEPS;

        for (int step = 0; step < STEPS; step++) {
            double t = 1.0 - (double) step / STEPS;

            probe.set(feet.x + tilted.x * t,
                    feet.y + tilted.y * t,
                    feet.z + tilted.z * t);

            BlockPos worldPos = BlockPos.containing(probe.x, probe.y, probe.z);
            if (worldPos.equals(previous)) continue;
            previous = worldPos;

            if (!disagrees(level, probe, worldPos, local, localPos)) {
                chosen = new Vec3(probe.x, probe.y, probe.z);
                chosenStep = step;
                break;
            }
        }

        if (entity instanceof net.minecraft.client.player.LocalPlayer) {
            DebugRayRenderer.submitRay(upright, head, 1f, 0.3f, 0.3f);
            DebugRayRenderer.submitRay(upright, chosen, 0.3f, 1f, 0.3f);
            TiltDiagnostics.recordLightProbe(entity, upright, head, chosen, chosenStep);
        }

        return chosen;
    }

    private static boolean disagrees(Level level, Vector3d probe, BlockPos worldPos,
                                     Vector3d local, BlockPos.MutableBlockPos localPos) {
        if (level.getBlockState(worldPos).isSolidRender(level, worldPos)) return true;

        Vector3d centre = new Vector3d(
                worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);

        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(worldPos))) {
            if (!(subLevel instanceof ClientSubLevel clientSubLevel)) continue;

            Level subLevelLevel = subLevel.getLevel();

            clientSubLevel.renderPose().transformPositionInverse(probe, local);
            localPos.set(local.x, local.y, local.z);

            if (subLevelLevel.getBlockState(localPos).isSolidRender(subLevelLevel, localPos)) return true;
            int exactSky = subLevelLevel.getBrightness(LightLayer.SKY, localPos);

            clientSubLevel.renderPose().transformPositionInverse(centre, local);
            localPos.set(local.x, local.y, local.z);

            if (subLevelLevel.getBrightness(LightLayer.SKY, localPos) < exactSky) return true;
        }

        return false;
    }
}
