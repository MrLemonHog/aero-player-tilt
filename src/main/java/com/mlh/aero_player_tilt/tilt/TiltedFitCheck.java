package com.mlh.aero_player_tilt.tilt;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.voxel_shape_iteration.FastVoxelShapeIterable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.Iterator;

public final class TiltedFitCheck {
    private TiltedFitCheck() {}

    private static final double FLOOR_CLEARANCE = 0.15;

    private static final double CONTACT_EPSILON = 1.0e-4;

    private static final double HORIZONTAL_BITE = 0.25;

    private static final double SEARCH_MARGIN = 1.05;

    public static boolean collidesWithSubLevels(Level level, AABB aabb, Quaterniondc tilt) {
        return collidesWithSubLevels(level, aabb, tilt, null);
    }

    public static boolean collidesWithSubLevels(Level level, AABB aabb, Quaterniondc tilt,
                                                @javax.annotation.Nullable Vector3d blockerOut) {
        BoundingBox3d consideration = new BoundingBox3d(aabb);
        consideration.expand(SEARCH_MARGIN, consideration);

        Iterable<SubLevel> intersecting = Sable.HELPER.getAllIntersecting(level, consideration);
        if (!intersecting.iterator().hasNext()) return false;

        double centerX = (aabb.minX + aabb.maxX) / 2.0;
        double centerZ = (aabb.minZ + aabb.maxZ) / 2.0;
        double feetY = aabb.minY;

        double poseHeight = aabb.getYsize();
        double centerOffset = (poseHeight + FLOOR_CLEARANCE) / 2.0;

        double halfX = aabb.getXsize() / 2.0;
        double halfZ = aabb.getZsize() / 2.0;

        Quaterniond orientation = new Quaterniond();
        Quaterniond localOrientation = new Quaterniond();
        Vector3d localCenter = new Vector3d();

        BoundingBox3d localBounds = new BoundingBox3d();
        Matrix4d bakedPose = new Matrix4d();
        Vector3d blockCenter = new Vector3d();
        Vector3d blockSize = new Vector3d();
        Vector3d rel = new Vector3d();
        Vector3d feetToCenter = new Vector3d();

        for (SubLevel subLevel : intersecting) {
            Pose3dc pose = subLevel.lastPose();

            BoxOrientation.forBox(tilt, pose, orientation);

            localOrientation.set(pose.orientation()).conjugate().mul(orientation).normalize();

            tilt.transform(feetToCenter.set(0.0, centerOffset, 0.0));
            localCenter.set(centerX + feetToCenter.x, feetY + feetToCenter.y, centerZ + feetToCenter.z);
            pose.transformPositionInverse(localCenter);

            localBounds.set(aabb);
            localBounds.expand(SEARCH_MARGIN, localBounds);
            localBounds.transformInverse(pose, bakedPose, localBounds);

            Iterable<BlockPos> blocks = BlockPos.betweenClosed(
                    BlockPos.containing(localBounds.minX, localBounds.minY, localBounds.minZ),
                    BlockPos.containing(localBounds.maxX, localBounds.maxY, localBounds.maxZ));

            for (BlockPos block : blocks) {
                BlockState state = level.getBlockState(block);
                if (state.isAir()) continue;

                VoxelShape voxelShape = state.getCollisionShape(level, block);
                Iterator<BoundingBox3dc> iterator = ((FastVoxelShapeIterable) voxelShape).sable$allBoxes();

                while (iterator.hasNext()) {
                    BoundingBox3dc box = iterator.next();
                    box.center(blockCenter);
                    box.size(blockSize);

                    rel.set(block.getX() + blockCenter.x,
                            block.getY() + blockCenter.y,
                            block.getZ() + blockCenter.z).sub(localCenter);
                    localOrientation.transformInverse(rel);
                    rel.y += centerOffset;

                    double bite = Math.min(
                            (halfX + blockSize.x / 2.0) - Math.abs(rel.x),
                            (halfZ + blockSize.z / 2.0) - Math.abs(rel.z));
                    if (bite <= HORIZONTAL_BITE) continue;

                    double lo = Math.max(FLOOR_CLEARANCE, rel.y - blockSize.y / 2.0);
                    double hi = Math.min(poseHeight, rel.y + blockSize.y / 2.0);
                    if (hi - lo <= CONTACT_EPSILON) continue;

                    if (blockerOut != null) blockerOut.set(rel);
                    return true;
                }
            }
        }

        return false;
    }
}
