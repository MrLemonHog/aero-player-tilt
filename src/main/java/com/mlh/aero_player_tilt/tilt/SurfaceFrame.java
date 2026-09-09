package com.mlh.aero_player_tilt.tilt;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public final class SurfaceFrame {
    private SurfaceFrame() {}

    private static final double VERTICAL_COS = 0.9999;

    @Nullable
    public static Vec3 sharedUp(Entity first, Entity second) {
        Vec3 up = bodyUp(first);
        if (up == null) up = bodyUp(second);
        if (up == null) up = deckUp(first);
        if (up == null) up = deckUp(second);
        return up;
    }

    @Nullable
    private static Vec3 bodyUp(Entity entity) {
        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
        if (tilt == null) return null;
        return meaningful(tilt.transform(new Vector3d(0.0, 1.0, 0.0)));
    }

    @Nullable
    private static Vec3 deckUp(Entity entity) {
        SubLevel deck = DeckFrame.of(entity);
        if (deck == null) return null;
        return meaningful(deck.logicalPose().orientation().transform(new Vector3d(0.0, 1.0, 0.0)));
    }

    @Nullable
    private static Vec3 meaningful(Vector3d up) {
        up.normalize();
        if (up.y > VERTICAL_COS) return null;
        return new Vec3(up.x, up.y, up.z);
    }
}
