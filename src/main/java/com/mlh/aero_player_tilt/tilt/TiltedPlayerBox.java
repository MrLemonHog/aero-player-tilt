package com.mlh.aero_player_tilt.tilt;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;

public final class TiltedPlayerBox {
    private TiltedPlayerBox() {}

    private static final double CLEARANCE = 0.75 / 16.0;

    private static final double INSIDE_SKIN = 1.0E-7;

    public static void logDecision(String branch, Entity entity, VoxelShape shape,
                                   boolean vanilla, @Nullable Boolean tilted) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;
        if (tilted == null && !PlayerTilt.isRenderTilted(entity)) return;

        AABB bounds = shape.bounds();
        com.mlh.aero_player_tilt.AeroPlayerTilt.LOGGER.debug(
                "[place/{}] side={} block=({}, {}, {}) vanilla={} tilted={} feet=({}, {}, {})",
                branch, entity.level().isClientSide ? "client" : "server",
                fmt(bounds.minX), fmt(bounds.minY), fmt(bounds.minZ),
                vanilla ? "BLOCKED" : "free",
                tilted == null ? "-" : (tilted ? "BLOCKED" : "free"),
                fmt(entity.getX()), fmt(entity.getY()), fmt(entity.getZ()));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    @Nullable
    public static AABB enclosingBox(Entity entity) {
        Quaterniond orientation = boxOrientation(entity);
        if (orientation == null) return null;

        AABB bounds = entity.getBoundingBox();
        double hx = bounds.getXsize() / 2.0;
        double hy = bounds.getYsize() / 2.0;
        double hz = bounds.getZsize() / 2.0;

        Vector3d center = orientation.transform(new Vector3d(0.0, hy, 0.0));
        center.add(entity.getX(), entity.getY(), entity.getZ());

        Vector3d ax = orientation.transform(new Vector3d(hx, 0.0, 0.0));
        Vector3d ay = orientation.transform(new Vector3d(0.0, hy, 0.0));
        Vector3d az = orientation.transform(new Vector3d(0.0, 0.0, hz));

        double ex = Math.abs(ax.x) + Math.abs(ay.x) + Math.abs(az.x);
        double ey = Math.abs(ax.y) + Math.abs(ay.y) + Math.abs(az.y);
        double ez = Math.abs(ax.z) + Math.abs(ay.z) + Math.abs(az.z);

        return new AABB(center.x - ex, center.y - ey, center.z - ez,
                center.x + ex, center.y + ey, center.z + ez);
    }

    @Nullable
    public static Boolean intersectsEntities(Entity first, Entity second) {
        Quaterniond firstOrientation = boxOrientation(first);
        Quaterniond secondOrientation = boxOrientation(second);
        if (firstOrientation == null && secondOrientation == null) return null;

        LevelReusedVectors sink = ((LevelExtension) first.level()).sable$getJOMLSink();

        OrientedBoundingBox3d firstBox = obb(first, firstOrientation, sink);
        OrientedBoundingBox3d secondBox = obb(second, secondOrientation, sink);

        Vector3d mtv = new Vector3d();
        OrientedBoundingBox3d.sat(firstBox, secondBox, mtv);

        return mtv.lengthSquared() > 0.0
                && mtv.x() != Double.MAX_VALUE
                && mtv.y() != Double.MAX_VALUE
                && mtv.z() != Double.MAX_VALUE;
    }

    private static OrientedBoundingBox3d obb(Entity entity, @Nullable Quaterniond orientation,
                                             LevelReusedVectors sink) {
        AABB bounds = entity.getBoundingBox();

        if (orientation == null) {
            OrientedBoundingBox3d box = new OrientedBoundingBox3d(sink);
            box.getOrientation().identity();
            box.getPosition().set((bounds.minX + bounds.maxX) / 2.0,
                    (bounds.minY + bounds.maxY) / 2.0,
                    (bounds.minZ + bounds.maxZ) / 2.0);
            box.getDimensions().set(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
            return box;
        }

        Vector3d center = orientation.transform(new Vector3d(0.0, bounds.getYsize() / 2.0, 0.0));
        center.add(entity.getX(), entity.getY(), entity.getZ());

        return new OrientedBoundingBox3d(
                center.x, center.y, center.z,
                bounds.getXsize(), bounds.getYsize(), bounds.getZsize(),
                orientation, sink);
    }

    @Nullable
    private static Quaterniond boxOrientation(Entity entity) {
        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
        if (tilt == null) return null;

        SubLevel deck = DeckFrame.forBox(entity);
        return deck != null
                ? BoxOrientation.forBox(tilt, deck.lastPose(), new Quaterniond())
                : new Quaterniond(tilt);
    }

    @Nullable
    public static java.util.Optional<net.minecraft.world.phys.Vec3> clipRay(
            Entity entity, net.minecraft.world.level.Level rayLevel,
            net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
        Quaterniond orientation = boxOrientation(entity);
        if (orientation == null) return null;

        SubLevel targetSpace = spaceOf(entity);
        net.minecraft.world.phys.Vec3 start = toTargetSpace(rayLevel, from, targetSpace);
        net.minecraft.world.phys.Vec3 end = toTargetSpace(rayLevel, to, targetSpace);
        rotationToTargetSpace(rayLevel, orientation, targetSpace);

        AABB bounds = entity.getBoundingBox();

        Vector3d center = orientation.transform(new Vector3d(0.0, bounds.getYsize() / 2.0, 0.0));
        center.add(entity.getX(), entity.getY(), entity.getZ());

        double hx = bounds.getXsize() / 2.0;
        double hy = bounds.getYsize() / 2.0;
        double hz = bounds.getZsize() / 2.0;

        Quaterniond inverse = new Quaterniond(orientation).invert();
        Vector3d origin = inverse.transform(new Vector3d(start.x - center.x, start.y - center.y, start.z - center.z));
        Vector3d dir = inverse.transform(new Vector3d(end.x - start.x, end.y - start.y, end.z - start.z));

        double tMin = 0.0;
        double tMax = 1.0;

        double[] o = {origin.x, origin.y, origin.z};
        double[] d = {dir.x, dir.y, dir.z};
        double[] h = {hx, hy, hz};

        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < 1.0e-9) {
                if (o[axis] < -h[axis] || o[axis] > h[axis]) return java.util.Optional.empty();
                continue;
            }
            double inv = 1.0 / d[axis];
            double t1 = (-h[axis] - o[axis]) * inv;
            double t2 = (h[axis] - o[axis]) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return java.util.Optional.empty();
        }

        return java.util.Optional.of(new net.minecraft.world.phys.Vec3(
                start.x + (end.x - start.x) * tMin,
                start.y + (end.y - start.y) * tMin,
                start.z + (end.z - start.z) * tMin));
    }

    @Nullable
    public static Boolean containsPoint(Entity entity, net.minecraft.world.level.Level rayLevel,
                                        net.minecraft.world.phys.Vec3 point) {
        Quaterniond orientation = boxOrientation(entity);
        if (orientation == null) return null;

        SubLevel targetSpace = spaceOf(entity);
        net.minecraft.world.phys.Vec3 local = toTargetSpace(rayLevel, point, targetSpace);
        rotationToTargetSpace(rayLevel, orientation, targetSpace);

        AABB bounds = entity.getBoundingBox();

        Vector3d center = orientation.transform(new Vector3d(0.0, bounds.getYsize() / 2.0, 0.0));
        center.add(entity.getX(), entity.getY(), entity.getZ());

        Vector3d offset = new Quaterniond(orientation).invert()
                .transform(new Vector3d(local.x - center.x, local.y - center.y, local.z - center.z));

        return Math.abs(offset.x) <= bounds.getXsize() / 2.0
                && Math.abs(offset.y) <= bounds.getYsize() / 2.0
                && Math.abs(offset.z) <= bounds.getZsize() / 2.0;
    }

    @Nullable
    private static SubLevel spaceOf(Entity entity) {
        return Sable.HELPER.getContaining(entity.level(), entity.position());
    }

    private static net.minecraft.world.phys.Vec3 toTargetSpace(
            net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 point,
            @Nullable SubLevel targetSpace) {
        SubLevel sourceSpace = Sable.HELPER.getContaining(level, point);
        if (sourceSpace == targetSpace) return point;

        net.minecraft.world.phys.Vec3 result = point;
        if (sourceSpace != null) result = poseOf(level, sourceSpace).transformPosition(result);
        if (targetSpace != null) result = poseOf(level, targetSpace).transformPositionInverse(result);
        return result;
    }

    private static void rotationToTargetSpace(net.minecraft.world.level.Level level, Quaterniond orientation,
                                              @Nullable SubLevel targetSpace) {
        if (targetSpace == null) return;
        orientation.premul(new Quaterniond(poseOf(level, targetSpace).orientation()).invert());
    }

    private static Pose3dc poseOf(net.minecraft.world.level.Level level, SubLevel subLevel) {
        return level instanceof LevelPoseProviderExtension provider
                ? provider.sable$getPose(subLevel)
                : subLevel.logicalPose();
    }

    @Nullable
    public static Boolean intersects(Entity entity, VoxelShape shape, @Nullable Pose3dc shapeSpace) {
        Quaterniond tilt = PlayerTilt.getRenderOrientation(entity, 1.0f);
        if (tilt == null) return null;

        if (shape.isEmpty()) return Boolean.FALSE;

        SubLevel deck = DeckFrame.forBox(entity);
        Quaterniond orientation = deck != null
                ? BoxOrientation.forBox(tilt, deck.lastPose(), new Quaterniond())
                : new Quaterniond(tilt);

        AABB bounds = entity.getBoundingBox();
        double halfHeight = bounds.getYsize() / 2.0;

        Vector3d center = orientation.transform(new Vector3d(0.0, halfHeight, 0.0));
        center.add(entity.getX(), entity.getY(), entity.getZ());

        if (shapeSpace != null) {
            shapeSpace.transformPositionInverse(center);
            orientation.premul(new Quaterniond(shapeSpace.orientation()).invert());
        }

        LevelReusedVectors sink = ((LevelExtension) entity.level()).sable$getJOMLSink();

        OrientedBoundingBox3d playerOBB = new OrientedBoundingBox3d(
                center.x, center.y, center.z,
                Math.max(0.0, bounds.getXsize() - 2.0 * CLEARANCE),
                Math.max(0.0, bounds.getYsize() - 2.0 * CLEARANCE),
                Math.max(0.0, bounds.getZsize() - 2.0 * CLEARANCE),
                orientation, sink);

        OrientedBoundingBox3d blockOBB = new OrientedBoundingBox3d(sink);
        blockOBB.getOrientation().identity();

        Vector3d satResult = new Vector3d();

        List<AABB> boxes = shape.toAabbs();
        for (AABB box : boxes) {
            blockOBB.getPosition().set(
                    (box.minX + box.maxX) / 2.0,
                    (box.minY + box.maxY) / 2.0,
                    (box.minZ + box.maxZ) / 2.0);
            blockOBB.getDimensions().set(box.getXsize(), box.getYsize(), box.getZsize());

            OrientedBoundingBox3d.sat(playerOBB, blockOBB, satResult);

            if (satResult.lengthSquared() > 0.0
                    && satResult.x() != Double.MAX_VALUE
                    && satResult.y() != Double.MAX_VALUE
                    && satResult.z() != Double.MAX_VALUE) {
                logGeometry(entity, center, bounds, shape, true);
                return Boolean.TRUE;
            }
        }

        logGeometry(entity, center, bounds, shape, false);
        return Boolean.FALSE;
    }

    @Nullable
    public static Boolean touchesCell(Entity entity, @Nullable Pose3dc space,
                                      net.minecraft.core.BlockPos pos) {
        Quaterniond orientation = boxOrientation(entity);
        if (orientation == null) return null;

        AABB bounds = entity.getBoundingBox();

        Vector3d center = orientation.transform(new Vector3d(0.0, bounds.getYsize() / 2.0, 0.0));
        center.add(entity.getX(), entity.getY(), entity.getZ());

        if (space != null) {
            space.transformPositionInverse(center);
            orientation.premul(new Quaterniond(space.orientation()).invert());
        }

        LevelReusedVectors sink = ((LevelExtension) entity.level()).sable$getJOMLSink();

        OrientedBoundingBox3d body = new OrientedBoundingBox3d(
                center.x, center.y, center.z,
                Math.max(0.0, bounds.getXsize() - 2.0 * INSIDE_SKIN),
                Math.max(0.0, bounds.getYsize() - 2.0 * INSIDE_SKIN),
                Math.max(0.0, bounds.getZsize() - 2.0 * INSIDE_SKIN),
                orientation, sink);

        OrientedBoundingBox3d cell = new OrientedBoundingBox3d(sink);
        cell.getOrientation().identity();
        cell.getPosition().set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        cell.getDimensions().set(1.0, 1.0, 1.0);

        Vector3d mtv = new Vector3d();
        OrientedBoundingBox3d.sat(body, cell, mtv);

        return mtv.lengthSquared() > 0.0
                && mtv.x() != Double.MAX_VALUE
                && mtv.y() != Double.MAX_VALUE
                && mtv.z() != Double.MAX_VALUE;
    }

    private static void logGeometry(Entity entity, Vector3d center, AABB bounds,
                                    VoxelShape shape, boolean hit) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return;

        AABB b = shape.bounds();
        com.mlh.aero_player_tilt.AeroPlayerTilt.LOGGER.debug(
                "[place/geom] side={} hit={} center=({}, {}, {}) half=({}, {}, {})"
                        + " shape=[{}..{}, {}..{}, {}..{}]",
                entity.level().isClientSide ? "client" : "server", hit,
                fmt(center.x), fmt(center.y), fmt(center.z),
                fmt(bounds.getXsize() / 2.0), fmt(bounds.getYsize() / 2.0), fmt(bounds.getZsize() / 2.0),
                fmt(b.minX), fmt(b.maxX), fmt(b.minY), fmt(b.maxY), fmt(b.minZ), fmt(b.maxZ));
    }
}
