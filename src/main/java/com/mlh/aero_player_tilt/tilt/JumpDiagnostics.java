package com.mlh.aero_player_tilt.tilt;

import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public final class JumpDiagnostics {
    private JumpDiagnostics() {}

    private static final Sample CLIENT = new Sample();
    private static final Sample SERVER = new Sample();

    private static final int MAX_FLIGHT_TICKS = 200;

    private static final int TRACE_TICKS = 16;

    private static final class Sample {
        boolean active;

        String cause = "jump";

        @Nullable UUID deckId;
        final Vector3d startLocal = new Vector3d();
        final Vec3[] startWorld = new Vec3[1];

        double tiltDegrees;
        @Nullable DeckGravity gravity;

        boolean launchCaptured;
        double launchNormal;
        double launchTangent;

        int flightTicks;
        int correctedTicks;
        final Vector3d correctionSum = new Vector3d();

        final double[] tangentTrace = new double[TRACE_TICKS];
        final Vector3d lastLocal = new Vector3d();
        boolean lastLocalValid;

        void reset() {
            active = false;
            deckId = null;
            launchCaptured = false;
            flightTicks = 0;
            correctedTicks = 0;
            correctionSum.zero();
            lastLocalValid = false;
            java.util.Arrays.fill(tangentTrace, 0.0);
        }
    }

    private static Sample sampleFor(LivingEntity entity) {
        return entity.level().isClientSide ? CLIENT : SERVER;
    }

    private static boolean enabled() {
        return Config.isLoaded() && Config.DEBUG_MESSAGES.get();
    }

    public static void markJump(LivingEntity entity) {
        if (!enabled() || !(entity instanceof Player)) return;

        Sample sample = sampleFor(entity);
        sample.reset();

        arm(entity, sample, "jump");
    }

    public static void markFall(LivingEntity entity) {
        if (!enabled() || !(entity instanceof Player)) return;

        Sample sample = sampleFor(entity);
        if (sample.active) return;

        sample.reset();
        arm(entity, sample, "fall");
    }

    private static void arm(LivingEntity entity, Sample sample, String cause) {
        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
        if (tilt == null) return;

        SubLevel deck = DeckFrame.forBox(entity);
        if (deck == null) return;

        sample.cause = cause;

        Vec3 position = entity.position();
        sample.deckId = deck.getUniqueId();
        sample.startWorld[0] = position;
        deck.logicalPose().transformPositionInverse(
                new Vector3d(position.x, position.y, position.z), sample.startLocal);
        sample.lastLocal.set(sample.startLocal);
        sample.lastLocalValid = true;

        sample.tiltDegrees = Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(tilt.w))));
        sample.gravity = TiltPolicy.deckGravity();
        sample.active = true;
    }

    public static void tick(LivingEntity entity, boolean onGround, @Nullable Vector3d correction) {
        if (!enabled() || !(entity instanceof Player)) return;

        Sample sample = sampleFor(entity);
        if (!sample.active) return;

        if (!sample.launchCaptured) {
            Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
            if (tilt != null) {
                Vector3d up = tilt.transform(new Vector3d(0.0, 1.0, 0.0));
                Vec3 velocity = entity.getDeltaMovement();
                Vector3d v = new Vector3d(velocity.x, velocity.y, velocity.z);

                sample.launchNormal = up.dot(v);
                sample.launchTangent = v.fma(-sample.launchNormal, up).length();
            }
            sample.launchCaptured = true;
        }

        sample.flightTicks++;
        if (correction != null) {
            sample.correctedTicks++;
            sample.correctionSum.add(correction);
        }

        aero$traceTangent(entity, sample);

        if (onGround || sample.flightTicks > MAX_FLIGHT_TICKS) {
            report(entity, sample, onGround);
            sample.reset();
        }
    }

    private static void aero$traceTangent(LivingEntity entity, Sample sample) {
        if (sample.flightTicks > TRACE_TICKS) return;

        SubLevel deck = DeckFrame.byId(entity.level(), sample.deckId);
        if (deck == null) {
            sample.lastLocalValid = false;
            return;
        }

        Vec3 position = entity.position();
        Vector3d local = deck.logicalPose().transformPositionInverse(
                new Vector3d(position.x, position.y, position.z), new Vector3d());

        if (sample.lastLocalValid) {
            double dx = local.x - sample.lastLocal.x;
            double dz = local.z - sample.lastLocal.z;
            sample.tangentTrace[sample.flightTicks - 1] = Math.sqrt(dx * dx + dz * dz);
        }

        sample.lastLocal.set(local);
        sample.lastLocalValid = true;
    }

    private static String trace(Sample sample) {
        int count = Math.min(sample.flightTicks, TRACE_TICKS);
        StringBuilder out = new StringBuilder(count * 7 + 2).append('[');
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append(' ');
            out.append(fmt(sample.tangentTrace[i]));
        }
        return out.append(']').toString();
    }

    private static void report(LivingEntity entity, Sample sample, boolean landed) {
        SubLevel deck = DeckFrame.byId(entity.level(), sample.deckId);

        if ("fall".equals(sample.cause) && sample.flightTicks <= 1 && deck != null) {
            Vec3 here = entity.position();
            Vector3d local = deck.logicalPose().transformPositionInverse(
                    new Vector3d(here.x, here.y, here.z), new Vector3d());

            double dx = local.x - sample.startLocal.x;
            double dz = local.z - sample.startLocal.z;
            if (Math.sqrt(dx * dx + dz * dz) < 0.01) return;
        }

        String driftDeck = "—";
        if (deck != null) {
            Vec3 position = entity.position();
            Vector3d endLocal = deck.logicalPose().transformPositionInverse(
                    new Vector3d(position.x, position.y, position.z), new Vector3d());

            driftDeck = String.format(Locale.ROOT, "(%+.3f, %+.3f)",
                    endLocal.x - sample.startLocal.x,
                    endLocal.z - sample.startLocal.z);
        }

        Vec3 start = sample.startWorld[0];
        Vec3 end = entity.position();

        AeroPlayerTilt.LOGGER.info(
                "[jump] side={} cause={} grav={} tilt={} ticks={}{}"
                        + " driftDeck={} driftWorld=({}, {}) launch(n={} t={})"
                        + " corr={} corrSum=({}, {}, {}) tan={}",
                entity.level().isClientSide ? "client" : "server",
                sample.cause,
                sample.gravity,
                fmt(sample.tiltDegrees), sample.flightTicks, landed ? "" : " NOLANDING",
                driftDeck,
                fmt(end.x - start.x), fmt(end.z - start.z),
                fmt(sample.launchNormal), fmt(sample.launchTangent),
                sample.correctedTicks,
                fmt(sample.correctionSum.x), fmt(sample.correctionSum.y), fmt(sample.correctionSum.z),
                trace(sample));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
