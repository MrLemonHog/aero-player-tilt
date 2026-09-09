package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.AcsBridge;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public final class TiltedZoom {
    private TiltedZoom() {}

    private static final float CORNER = 0.1f;

    @Nullable
    public static Float maxZoom(Camera camera, @Nullable BlockGetter level, float maxZoom) {
        if (level == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (camera != mc.gameRenderer.getMainCamera()) return null;

        LocalPlayer player = mc.player;
        if (player == null || camera.getEntity() != player) return null;

        if (AcsBridge.ACS.isSuppressed()) return null;

        Quaternionf lean = BodyTiltSource.handed();
        if (lean == null) return null;

        float partialTick = camera.getPartialTickTime();

        Vec3 eye = BodyTiltSource.cameraAnchor(player, camera.getPosition(), lean, partialTick);

        Vector3f back = lean.transform(new Vector3f(camera.getLookVector())).mul(-maxZoom);

        float furthest = maxZoom;

        for (int i = 0; i < 8; i++) {
            double x = ((i & 1) * 2 - 1) * CORNER;
            double y = ((i >> 1 & 1) * 2 - 1) * CORNER;
            double z = ((i >> 2 & 1) * 2 - 1) * CORNER;

            Vec3 from = eye.add(x, y, z);
            Vec3 to = from.add(back.x, back.y, back.z);

            HitResult hit = level.clip(new ClipContext(from, to,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));

            if (hit.getType() == HitResult.Type.MISS) continue;

            float distance = (float) hit.getLocation().distanceTo(eye);
            if (distance < furthest) furthest = distance;
        }

        return furthest;
    }
}
