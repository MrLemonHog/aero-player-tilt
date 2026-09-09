package com.mlh.aero_player_tilt.tilt;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public final class BoxOrientation {
    private BoxOrientation() {}

    private static final double PARALLEL_COS = 0.9;

    public static Quaterniond forBox(Quaterniondc tilt, Pose3dc shipPose, Quaterniond dest) {
        Vector3d up = tilt.transform(new Vector3d(0.0, 1.0, 0.0)).normalize();

        Quaterniondc ship = shipPose.orientation();
        Vector3d along = ship.transform(new Vector3d(1.0, 0.0, 0.0));
        if (Math.abs(along.dot(up)) > PARALLEL_COS) {
            along = ship.transform(new Vector3d(0.0, 0.0, 1.0));
        }

        Vector3d forward = along.fma(-along.dot(up), up).normalize();
        Vector3d side = new Vector3d(forward).cross(up);

        Matrix3d basis = new Matrix3d(
                forward.x, forward.y, forward.z,
                up.x, up.y, up.z,
                side.x, side.y, side.z);

        return dest.setFromNormalized(basis);
    }
}
