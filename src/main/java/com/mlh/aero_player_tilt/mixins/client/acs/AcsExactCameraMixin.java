package com.mlh.aero_player_tilt.mixins.client.acs;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mlh.aero_player_tilt.client.tilt.BodyTiltSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = com.playsi.aero_cam_sync.client.tilt.CameraController.class, remap = false)
public abstract class AcsExactCameraMixin {
    @Shadow private static float wallScale;

    @Shadow
    private static boolean collisionEnabled() {
        throw new AssertionError();
    }

    @Shadow
    private static Vec3 clampToCollision(LocalPlayer player, Vec3 from, Vec3 to, float partialTick) {
        throw new AssertionError();
    }

    @WrapMethod(method = "computeCameraPosition")
    private static Vec3 aero$exactCamera(Vec3 vanilla, float partialTick, Operation<Vec3> original) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || wallScale < 1f) return original.call(vanilla, partialTick);

        Vec3 exact = BodyTiltSource.exactCamera(player, vanilla, partialTick);
        if (exact == null) return original.call(vanilla, partialTick);

        return collisionEnabled() ? clampToCollision(player, vanilla, exact, partialTick) : exact;
    }
}
