package com.mlh.aero_player_tilt.client.utils;

import com.mlh.aero_player_tilt.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class BootsSurface {
    private BootsSurface() {}

    private static final double INNER_RADIUS = 0.30;
    private static final double OUTER_RADIUS = 0.55;

    private static final double CENTRE_WEIGHT = 1.5;
    private static final double INNER_WEIGHT = 1.0;
    private static final double OUTER_WEIGHT = 0.7;
    private static final double WRAP_WEIGHT = 0.5;

    private static final int STEPS = 8;

    private static final double BACKOFF = 0.10;

    private static final double WRAP_REACH = 1.25;

    public static final double FACE_MARGIN = 0.75;

    public static final double NO_EDGE = Double.MAX_VALUE;

    public record Contact(ClientSubLevel deck, double[] faces, double total,
                         @Nullable Direction centre, double[] edges, double edge) {
        public double weight(@Nullable Direction face) {
            return face == null ? 0.0 : faces[face.get3DDataValue()];
        }

        public double edgeOver(@Nullable Direction face) {
            return face == null ? NO_EDGE : edges[face.get3DDataValue()];
        }

        @Nullable
        public Direction best(Predicate<Direction> allowed) {
            Direction best = null;

            for (Direction face : Direction.values()) {
                double weight = faces[face.get3DDataValue()];
                if (weight <= 0.0) continue;
                if (!allowed.test(face)) continue;

                if (best == null || weight > faces[best.get3DDataValue()]) best = face;
            }

            return best;
        }
    }

    @Nullable
    public static Contact probe(LocalPlayer player, Quaterniondc frame, double reach, boolean wrap,
                                @Nullable ClientSubLevel prefer) {
        Level level = player.level();

        Vector3d down = frame.transform(new Vector3d(0.0, -1.0, 0.0)).normalize();
        Vector3d right = frame.transform(new Vector3d(1.0, 0.0, 0.0)).normalize();
        Vector3d forward = frame.transform(new Vector3d(0.0, 0.0, 1.0)).normalize();

        Vec3 feet = player.position();

        List<Tally> tallies = new ArrayList<>(2);

        cast(level, player, feet, down, reach, true, CENTRE_WEIGHT, true, tallies);

        double wrapDrop = Math.min(0.8, reach * 0.66);

        for (int step = 0; step < STEPS; step++) {
            double angle = 2.0 * Math.PI * step / STEPS;

            Vector3d side = new Vector3d(
                    right.x * Math.cos(angle) + forward.x * Math.sin(angle),
                    right.y * Math.cos(angle) + forward.y * Math.sin(angle),
                    right.z * Math.cos(angle) + forward.z * Math.sin(angle));

            cast(level, player, along(feet, side, INNER_RADIUS), down, reach, true,
                    INNER_WEIGHT, false, tallies);

            Vec3 ring = along(feet, side, OUTER_RADIUS);

            if (cast(level, player, ring, down, reach, true, OUTER_WEIGHT, false, tallies)) continue;
            if (!wrap) continue;

            Vec3 below = ring.add(down.x * wrapDrop, down.y * wrapDrop, down.z * wrapDrop);

            wrap(level, player, below, new Vector3d(side).negate(), OUTER_RADIUS + WRAP_REACH,
                    tallies);
        }

        Tally best = pick(tallies, prefer);
        if (best == null) return null;

        return new Contact(best.deck, best.faces, best.total, best.centre, best.edges, best.edge);
    }

    private static void wrap(Level level, LocalPlayer player, Vec3 origin, Vector3d direction,
                             double range, List<Tally> tallies) {
        Vec3 to = origin.add(direction.x * range, direction.y * range, direction.z * range);

        BlockHitResult hit = level.clip(new ClipContext(
                origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.MISS) {
            DebugRayRenderer.submitRay(origin, to, 1f, 0.25f, 0.25f);
            return;
        }

        SubLevel space = Sable.HELPER.getContaining(level, hit.getLocation());

        if (!(space instanceof ClientSubLevel deck) || deck.isRemoved()
                || !SubLevelThresholds.passes(deck)) {
            DebugRayRenderer.submitRay(origin, to, 0.5f, 0.5f, 0.5f);
            return;
        }

        DebugRayRenderer.submitRay(origin, to, 0.2f, 1f, 0.5f);

        Direction face = hit.getDirection();

        Tally tally = tallyFor(tallies, deck);
        tally.faces[face.get3DDataValue()] += WRAP_WEIGHT;
        tally.total += WRAP_WEIGHT;

        Vector3d landed = deck.logicalPose().transformPosition(new Vector3d(
                hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));

        double along = OUTER_RADIUS - landed.distance(origin.x, origin.y, origin.z);

        Vector3d outward = deck.logicalPose().orientation().transform(new Vector3d(
                face.getStepX(), face.getStepY(), face.getStepZ()));

        double square = -(direction.x * outward.x + direction.y * outward.y + direction.z * outward.z);
        double edge = square > 1.0e-3 ? along * square : along;

        int index = face.get3DDataValue();
        if (edge < tally.edges[index]) tally.edges[index] = edge;
        if (edge < tally.edge) tally.edge = edge;
    }

    private static Vec3 along(Vec3 feet, Vector3d side, double radius) {
        return feet.add(side.x * radius, side.y * radius, side.z * radius);
    }

    private static boolean cast(Level level, LocalPlayer player, Vec3 origin, Vector3d direction,
                                double range, boolean backOff, double weight, boolean centre,
                                List<Tally> tallies) {
        Vec3 step = new Vec3(direction.x, direction.y, direction.z);

        Vec3 from = backOff ? origin.subtract(step.scale(BACKOFF)) : origin;
        Vec3 to = origin.add(step.scale(range));

        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.MISS) {
            DebugRayRenderer.submitRay(from, to, 1f, 0.25f, 0.25f);
            return false;
        }

        SubLevel space = Sable.HELPER.getContaining(level, hit.getLocation());

        if (!(space instanceof ClientSubLevel deck) || deck.isRemoved()
                || !SubLevelThresholds.passes(deck)) {
            DebugRayRenderer.submitRay(from, to, 0.5f, 0.5f, 0.5f);
            return false;
        }

        DebugRayRenderer.submitRay(from, to, 0.2f, 1f, 0.5f);

        Tally tally = tallyFor(tallies, deck);
        tally.faces[hit.getDirection().get3DDataValue()] += weight;
        tally.total += weight;
        if (centre) tally.centre = hit.getDirection();

        return true;
    }

    private static final class Tally {
        private final ClientSubLevel deck;
        private final double[] faces = new double[6];
        private final double[] edges = new double[6];
        private double total;
        private double edge = NO_EDGE;
        @Nullable private Direction centre;

        private Tally(ClientSubLevel deck) {
            this.deck = deck;
            java.util.Arrays.fill(edges, NO_EDGE);
        }
    }

    private static Tally tallyFor(List<Tally> tallies, ClientSubLevel deck) {
        for (Tally tally : tallies) {
            if (tally.deck.getUniqueId().equals(deck.getUniqueId())) return tally;
        }

        Tally tally = new Tally(deck);
        tallies.add(tally);
        return tally;
    }

    @Nullable
    private static Tally pick(List<Tally> tallies, @Nullable ClientSubLevel prefer) {
        Tally best = null;

        for (Tally tally : tallies) {
            if (best == null || tally.total > best.total) {
                best = tally;
                continue;
            }

            if (prefer != null && tally.total == best.total
                    && tally.deck.getUniqueId().equals(prefer.getUniqueId())) {
                best = tally;
            }
        }

        return best;
    }
}
