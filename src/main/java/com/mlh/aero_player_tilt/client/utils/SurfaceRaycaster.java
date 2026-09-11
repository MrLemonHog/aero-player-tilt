package com.mlh.aero_player_tilt.client.utils;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class SurfaceRaycaster {
    private SurfaceRaycaster() {}

    private static final double DEGENERATE = 1.0e-6;

    private static int rayCount()      { return Config.RAYCAST_COUNT.get(); }
    private static double radius()     { return Config.value(Config.RAYCAST_RADIUS, 0.58); }
    private static float offsetUp()    { return Config.RAYCAST_UP_LENGTH.get().floatValue(); }
    private static float offsetDown()  { return -Config.RAYCAST_DOWN_LENGTH.get().floatValue(); }

    private static double grip() { return Config.value(Config.FOOTING_GRIP, 0.6); }

    public record Patch(ClientSubLevel deck, double weight, Vector3f normal) {}

    public record Floor(@Nullable Vector3f normal, List<Patch> decks,
                        double world, double total, int hits) {
        public static final Floor NONE = new Floor(null, List.of(), 0.0, 0.0, 0);

        @Nullable
        public Patch strongest() {
            return decks.isEmpty() ? null : decks.get(0);
        }

        @Nullable
        public Patch patchOf(@Nullable ClientSubLevel deck) {
            if (deck == null) return null;

            for (Patch patch : decks) {
                if (patch.deck().getUniqueId().equals(deck.getUniqueId())) return patch;
            }
            return null;
        }

        public double share(double weight) {
            return total <= DEGENERATE ? 0.0 : weight / total;
        }

        @Nullable
        public Vector3f normalOf(@Nullable ClientSubLevel deck) {
            Patch patch = patchOf(deck);

            return patch == null ? normal : patch.normal();
        }
    }

    public static Floor survey(LocalPlayer player, float partialTick, @Nullable ClientSubLevel only) {
        Level level = player.level();
        Vec3[] origins = buildRayOrigins(player.position());
        double feetY = player.getY();

        Vector3f sum = new Vector3f();
        double total = 0.0;
        double world = 0.0;
        int hits = 0;

        List<Tally> tallies = new ArrayList<>(2);

        for (Vec3 origin : origins) {
            Vec3 from = new Vec3(origin.x, origin.y + offsetUp(),   origin.z);
            Vec3 to   = new Vec3(origin.x, origin.y + offsetDown(), origin.z);

            BlockHitResult hit = level.clip(new ClipContext(
                    from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

            if (hit.getType() == HitResult.Type.MISS) {
                DebugRayRenderer.submitRay(from, to, 1f, 0.2f, 0.2f);
                continue;
            }

            SubLevel space = Sable.HELPER.getContaining(level, hit.getLocation());
            ClientSubLevel deck = space instanceof ClientSubLevel client ? client : null;

            if (only != null && (deck == null || !deck.getUniqueId().equals(only.getUniqueId()))) {
                DebugRayRenderer.submitRay(from, to, 0.5f, 0.5f, 0.5f);
                continue;
            }

            Tally tally = deck == null ? null : tallyFor(tallies, deck);
            if (tally != null && !tally.allowed) {
                DebugRayRenderer.submitRay(from, to, 0.5f, 0.5f, 0.5f);
                continue;
            }

            Vector3f normal = MathUtils.directionToVector(hit.getDirection());
            if (deck != null) {
                Quaterniondc drawn = deck.renderPose(partialTick).orientation();
                normal = MathUtils.transformToWorldSpace(normal, drawn);
            }

            if (normal.y < (float) com.mlh.aero_player_tilt.tilt.PlayerTilt.floorNormalY()) {
                DebugRayRenderer.submitRay(from, to, 0.5f, 0.5f, 0.5f);
                continue;
            }

            double weight = weigh(Math.max(0.0, feetY - contactY(deck, hit)));

            sum.add(new Vector3f(normal).mul((float) weight));
            total += weight;
            hits++;

            if (deck != null) {
                tally.weight += weight;
                tally.sum.add(new Vector3f(normal).mul((float) weight));
            } else {
                world += weight;
            }

            DebugRayRenderer.submitRay(from, to,
                    deck == null ? 0.35f : 0.2f,
                    deck == null ? 0.6f : 1f,
                    deck == null ? 1f : 0.2f);
        }

        if (hits == 0 || sum.lengthSquared() < DEGENERATE) {
            lastSummary = "none";
            return Floor.NONE;
        }

        Floor floor = new Floor(sum.div((float) total).normalize(), patches(tallies),
                world, total, hits);
        describe(floor);

        return floor;
    }

    private static volatile String lastSummary = "-";

    public static String debugLast() {
        return lastSummary;
    }

    private static void describe(Floor floor) {
        if (!com.mlh.aero_player_tilt.client.debug.FallTrace.enabled()) return;

        StringBuilder out = new StringBuilder();
        out.append(String.format(java.util.Locale.ROOT, "blend=%.2f hits=%d",
                angle(floor.normal()), floor.hits()));

        for (Patch patch : floor.decks()) {
            out.append(String.format(java.util.Locale.ROOT, " %s:%.2f@%.2f",
                    patch.deck().getUniqueId().toString().substring(0, 4),
                    angle(patch.normal()), floor.share(patch.weight())));
        }

        if (floor.world() > 0.0) {
            out.append(String.format(java.util.Locale.ROOT, " world@%.2f",
                    floor.share(floor.world())));
        }

        lastSummary = out.toString();
    }

    private static double angle(@Nullable Vector3f normal) {
        if (normal == null) return -1.0;

        return Math.toDegrees(Math.acos(Math.min(1.0f, Math.max(-1.0f, normal.y))));
    }

    private static double weigh(double depth) {
        double reach = depth / grip();

        return 1.0 / (1.0 + reach * reach);
    }

    private static double contactY(@Nullable ClientSubLevel deck, BlockHitResult hit) {
        Vec3 point = hit.getLocation();
        if (deck == null) return point.y;

        return deck.logicalPose()
                .transformPosition(new org.joml.Vector3d(point.x, point.y, point.z)).y;
    }

    private static Vec3[] buildRayOrigins(Vec3 feet) {
        int count      = rayCount();
        double radius  = radius();
        Vec3[] origins = new Vec3[count + 1];
        origins[0]     = feet;
        for (int i = 0; i < count; i++) {
            double angle   = 2 * Math.PI * i / count;
            origins[i + 1] = feet.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        }
        return origins;
    }

    private static final class Tally {
        private final ClientSubLevel deck;
        private final boolean allowed;
        private final Vector3f sum = new Vector3f();
        private double weight;

        private Tally(ClientSubLevel deck) {
            this.deck = deck;
            this.allowed = SubLevelThresholds.passes(deck);
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

    private static List<Patch> patches(List<Tally> tallies) {
        List<Patch> patches = new ArrayList<>(tallies.size());
        for (Tally tally : tallies) {
            if (!tally.allowed || tally.weight <= 0.0) continue;
            if (tally.sum.lengthSquared() < DEGENERATE) continue;

            patches.add(new Patch(tally.deck, tally.weight,
                    new Vector3f(tally.sum).div((float) tally.weight).normalize()));
        }

        patches.sort((a, b) -> Double.compare(b.weight(), a.weight()));
        return patches;
    }
}
