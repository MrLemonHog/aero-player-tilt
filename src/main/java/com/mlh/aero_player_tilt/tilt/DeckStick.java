package com.mlh.aero_player_tilt.tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class DeckStick {
    private DeckStick() {}

    private static final double LIFT = 0.02;

    private static final double JUMPED_OFF = 0.1;

    public static boolean enabled() {
        return TiltPolicy.stickToDeck();
    }

    public static boolean keepFooting(LivingEntity entity, boolean wasGrounded, boolean jumped) {
        if (!enabled()) return false;
        if (!(entity instanceof Player)) return false;
        if (!wasGrounded || jumped || entity.onGround()) return false;
        if (entity.isPassenger() || entity.isFallFlying()) return false;
        if (entity instanceof Player player && player.getAbilities().flying) return false;

        if (entity.onClimbable()) return false;

        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
        if (tilt == null) return false;

        Vector3d up = Boots.holding(entity) ? Boots.support(entity, new Vector3d()) : null;
        if (up == null) up = tilt.transform(new Vector3d(0.0, 1.0, 0.0)).normalize();

        Vec3 motion = entity.getDeltaMovement();
        double climbing = motion.x * up.x + motion.y * up.y + motion.z * up.z;

        if (climbing > JUMPED_OFF) return false;

        double step = entity.maxUpStep();
        if (step <= 0.0) return false;

        Vec3 feet = entity.position();
        Vec3 from = feet.add(up.x * LIFT, up.y * LIFT, up.z * LIFT);
        Vec3 to = feet.subtract(up.x * step, up.y * step, up.z * step);

        Level level = entity.level();
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));

        if (hit.getType() == HitResult.Type.MISS) return false;

        SubLevel space = Sable.HELPER.getContaining(level, hit.getLocation());

        Vector3d normal = new Vector3d(
                hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
        if (space != null) space.logicalPose().orientation().transform(normal);

        if (Boots.holding(entity)) {
            if (normal.normalize().dot(up) < 0.5) return false;
        } else if (normal.y < PlayerTilt.floorNormalY()) {
            return false;
        }

        entity.setOnGround(true);
        entity.resetFallDistance();

        DeckCarryGrace.footing(entity, space == null ? null : space.getUniqueId());

        return true;
    }
}
