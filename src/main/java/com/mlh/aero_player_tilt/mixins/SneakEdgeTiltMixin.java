package com.mlh.aero_player_tilt.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mlh.aero_player_tilt.tilt.Boots;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class SneakEdgeTiltMixin {
    @Unique private static final double AERO$STEP = 0.05;
    @Unique private static final double AERO$LIFT = 0.1;

    @WrapMethod(method = "maybeBackOffFromEdge")
    private Vec3 aero$deckEdge(Vec3 movement, MoverType type, Operation<Vec3> original) {
        Player self = (Player) (Object) this;
        Quaterniond tilt = PlayerTilt.getRenderOrientation(self, 1.0f);
        if (tilt == null) return original.call(movement, type);

        Vector3d up = Boots.support(self, new Vector3d());
        if (up == null) return original.call(movement, type);

        if (self.getAbilities().flying || !self.isShiftKeyDown() || !self.onGround()) return movement;
        if (type != MoverType.SELF && type != MoverType.PLAYER) return movement;

        Vector3d right = tilt.transform(new Vector3d(1.0, 0.0, 0.0));
        right.fma(-right.dot(up), up).normalize();
        Vector3d forward = new Vector3d(up).cross(right).normalize();

        double into = movement.x * up.x + movement.y * up.y + movement.z * up.z;
        double a = movement.x * right.x + movement.y * right.y + movement.z * right.z;
        double b = movement.x * forward.x + movement.y * forward.y + movement.z * forward.z;

        if (into > 0.0) return movement;

        if (!aero$footed(self, up, right, forward, 0.0, 0.0)) return movement;

        while (a != 0.0 && !aero$footed(self, up, right, forward, a, 0.0)) a = aero$shrink(a);
        while (b != 0.0 && !aero$footed(self, up, right, forward, 0.0, b)) b = aero$shrink(b);
        while (a != 0.0 && b != 0.0 && !aero$footed(self, up, right, forward, a, b)) {
            a = aero$shrink(a);
            b = aero$shrink(b);
        }

        return new Vec3(
                up.x * into + right.x * a + forward.x * b,
                up.y * into + right.y * a + forward.y * b,
                up.z * into + right.z * a + forward.z * b);
    }

    @Unique
    private static double aero$shrink(double value) {
        return Math.abs(value) < AERO$STEP ? 0.0 : value - Math.copySign(AERO$STEP, value);
    }

    @Unique
    private static boolean aero$footed(Player player, Vector3d up, Vector3d right, Vector3d forward,
                                       double a, double b) {
        double half = player.getBbWidth() / 2.0;
        double depth = player.maxUpStep() + AERO$LIFT;

        for (int i = 0; i < 5; i++) {
            double ca = a + (i == 0 ? 0.0 : (i % 2 == 0 ? half : -half));
            double cb = b + (i == 0 ? 0.0 : (i < 3 ? half : -half));

            Vec3 foot = player.position().add(
                    right.x * ca + forward.x * cb,
                    right.y * ca + forward.y * cb,
                    right.z * ca + forward.z * cb);
            Vec3 from = foot.add(up.x * AERO$LIFT, up.y * AERO$LIFT, up.z * AERO$LIFT);
            Vec3 to = foot.subtract(up.x * depth, up.y * depth, up.z * depth);

            if (player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
                return true;
            }
        }

        return false;
    }
}
