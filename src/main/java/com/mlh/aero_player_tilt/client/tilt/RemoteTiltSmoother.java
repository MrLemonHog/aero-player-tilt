package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

final class RemoteTiltSmoother {
    private final Quaterniond value = new Quaterniond();

    @Nullable
    private UUID frameId;

    private boolean seeded;

    @Nullable
    UUID frameId() {
        return frameId;
    }

    void advance(Quaterniondc target,
                 @Nullable UUID targetFrameId,
                 @Nullable Quaterniondc oldFrameNow,
                 @Nullable Quaterniondc newFrameNow,
                 double halfLifeTicks,
                 float deltaTicks) {
        if (!seeded) {
            value.set(target).normalize();
            frameId = targetFrameId;
            seeded = true;
            return;
        }

        if (!Objects.equals(frameId, targetFrameId)) {
            rebase(oldFrameNow, newFrameNow);
            frameId = targetFrameId;
        }

        double step = step(halfLifeTicks, deltaTicks);
        if (step <= 0.0) return;

        value.slerp(target, Math.min(1.0, step)).normalize();
    }

    private void rebase(@Nullable Quaterniondc oldFrameNow, @Nullable Quaterniondc newFrameNow) {
        if (oldFrameNow != null) value.premul(oldFrameNow);
        if (newFrameNow != null) value.premul(new Quaterniond(newFrameNow).conjugate());
        value.normalize();
    }

    Quaterniond get(@Nullable Quaterniondc frameNow, Quaterniond dest) {
        dest.set(value);
        if (frameNow == null) return dest;

        dest.premul(frameNow).normalize();
        PlayerTilt.dropTwist(dest);
        return PlayerTilt.clampToWalkable(dest);
    }

    boolean isSettling() {
        return seeded && PlayerTilt.isMeaningful(value.w());
    }

    private static double step(double halfLifeTicks, float deltaTicks) {
        if (halfLifeTicks <= 0.0) return 1.0;
        if (deltaTicks <= 0f) return 0.0;
        return 1.0 - Math.pow(0.5, deltaTicks / halfLifeTicks);
    }
}
