package com.mlh.aero_player_tilt.tilt;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class InsideBlockReach {
    private InsideBlockReach() {}

    public static boolean reached(Entity entity, Level level, BlockPos pos) {
        if (!PlayerTilt.isRenderTilted(entity)) return true;

        SubLevel owner = Sable.HELPER.getContaining(level, pos);

        if (owner != null && Sable.HELPER.getContaining(level, entity.blockPosition()) == owner) {
            return true;
        }

        Boolean reached = TiltedPlayerBox.touchesCell(
                entity, owner == null ? null : owner.logicalPose(), pos);

        boolean answer = reached == null || reached;

        if (level.isClientSide) {
            com.mlh.aero_player_tilt.client.tilt.TiltDiagnostics.recordInside(
                    entity, level, pos, owner != null, answer);
        }

        return answer;
    }
}
