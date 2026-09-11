package com.mlh.aero_player_tilt.client.utils;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.tilt.ZoneTilt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class DeckZone {
    private DeckZone() {}

    public static ZoneTilt mode() {
        return Config.isLoaded() ? Config.ZONE_TILT.get() : ZoneTilt.OFF;
    }

    public static boolean always() {
        return mode() == ZoneTilt.ALWAYS;
    }

    private static double reach() {
        return Config.value(Config.ZONE_REACH, 2.0);
    }

    private static float lingerTicks() {
        return (float) Config.value(Config.ZONE_LINGER_TICKS, 10.0);
    }

    @Nullable private static ClientSubLevel held;
    private static double heldWeight;
    private static float lingerLeft;

    public static SurfaceRaycaster.Floor survey(LocalPlayer player, float partialTick,
                                               float deltaTicks, boolean wanted) {
        if (mode() == ZoneTilt.OFF) {
            forget();
            return SurfaceRaycaster.Floor.NONE;
        }

        if (!wanted) {
            linger(partialTick, deltaTicks);
            return SurfaceRaycaster.Floor.NONE;
        }

        double reach = reach();

        Vec3 position = player.position();

        Iterable<SubLevel> nearby = Sable.HELPER.getAllIntersecting(
                player.level(), new BoundingBox3d(player.getBoundingBox()).expand(reach));

        Vector3f sum = new Vector3f();
        double total = 0.0;
        int inside = 0;
        List<SurfaceRaycaster.Patch> decks = new ArrayList<>(2);

        float minNormalY = (float) com.mlh.aero_player_tilt.tilt.PlayerTilt.floorNormalY();

        for (SubLevel subLevel : nearby) {
            if (!(subLevel instanceof ClientSubLevel deck)) continue;
            if (deck.isRemoved()) continue;

            double depth = depthInside(deck, position, reach);
            if (depth <= 0.0) continue;

            if (!SubLevelThresholds.passes(deck)) continue;

            Vector3f up = MathUtils.transformToWorldSpace(
                    new Vector3f(0f, 1f, 0f), deck.renderPose(partialTick).orientation());

            if (up.y < minNormalY) continue;

            sum.add(new Vector3f(up).mul((float) depth));
            total += depth;
            inside++;
            decks.add(new SurfaceRaycaster.Patch(deck, depth, new Vector3f(up)));
        }

        if (inside == 0 || sum.lengthSquared() < 1.0e-9f) return linger(partialTick, deltaTicks);

        decks.sort((a, b) -> Double.compare(b.weight(), a.weight()));

        held = decks.get(0).deck();
        heldWeight = decks.get(0).weight();
        lingerLeft = lingerTicks();

        return new SurfaceRaycaster.Floor(
                sum.div((float) total).normalize(), decks, 0.0, total, inside);
    }

    private static SurfaceRaycaster.Floor linger(float partialTick, float deltaTicks) {
        if (held == null) return SurfaceRaycaster.Floor.NONE;

        if (held.isRemoved() || lingerLeft <= 0f) {
            forget();
            return SurfaceRaycaster.Floor.NONE;
        }

        lingerLeft = Math.max(0f, lingerLeft - deltaTicks);

        Vector3f up = MathUtils.transformToWorldSpace(
                new Vector3f(0f, 1f, 0f), held.renderPose(partialTick).orientation());

        if (up.y < (float) com.mlh.aero_player_tilt.tilt.PlayerTilt.floorNormalY()) {
            forget();
            return SurfaceRaycaster.Floor.NONE;
        }

        return new SurfaceRaycaster.Floor(up.normalize(),
                List.of(new SurfaceRaycaster.Patch(held, heldWeight, new Vector3f(up))),
                0.0, heldWeight, 1);
    }

    public static void forget() {
        held = null;
        heldWeight = 0.0;
        lingerLeft = 0f;
    }

    private static double depthInside(ClientSubLevel deck, Vec3 world, double reach) {
        BoundingBox3ic bounds = deck.getPlot().getBoundingBox();

        if (bounds.minX() > bounds.maxX()) return Double.NEGATIVE_INFINITY;

        Vector3d local = deck.logicalPose()
                .transformPositionInverse(new Vector3d(world.x, world.y, world.z));

        double depth = Math.min(
                Math.min(local.x - bounds.minX(), bounds.maxX() + 1 - local.x),
                Math.min(
                        Math.min(local.y - bounds.minY(), bounds.maxY() + 1 - local.y),
                        Math.min(local.z - bounds.minZ(), bounds.maxZ() + 1 - local.z)));

        return depth + reach;
    }
}
