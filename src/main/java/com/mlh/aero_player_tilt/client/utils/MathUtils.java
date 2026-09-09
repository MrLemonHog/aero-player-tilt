package com.mlh.aero_player_tilt.client.utils;

import net.minecraft.core.Direction;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MathUtils {
    private MathUtils() {}

    public static Vector3f directionToVector(Direction direction) {
        return new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    public static Vector3f transformToWorldSpace(Vector3f localVector, Quaterniondc orientation) {
        return toQuaternionf(orientation).transform(localVector);
    }

    public static Quaternionf toQuaternionf(Quaterniondc q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }
}
