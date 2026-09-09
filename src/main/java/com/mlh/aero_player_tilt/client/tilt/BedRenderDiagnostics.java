package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Locale;

public final class BedRenderDiagnostics {
    private BedRenderDiagnostics() {}

    private static final double BED_OFFSET = 0.6875;

    private static long lastReport = 0L;

    public static void report(Player player, float partialTick) {
        if (player == null || !player.isSleeping()) return;
        if (!Config.flag(Config.DEBUG_MESSAGES, false)) return;

        long now = System.currentTimeMillis();
        if (now - lastReport < 1000L) return;

        BlockPos bed = player.getSleepingPos().orElse(null);
        if (bed == null) return;

        SubLevel deck = Sable.HELPER.getContaining(player.level(), bed);
        if (!(deck instanceof ClientSubLevel clientDeck) || clientDeck.isRemoved()) return;

        lastReport = now;

        Vec3 rendered = player.getPosition(partialTick);
        Vector3d local = clientDeck.renderPose(partialTick)
                .transformPositionInverse(new Vector3d(rendered.x, rendered.y, rendered.z), new Vector3d());

        double alongX = local.x - (bed.getX() + 0.5);
        double up = local.y - (bed.getY() + BED_OFFSET);
        double alongZ = local.z - (bed.getZ() + 0.5);

        AeroPlayerTilt.LOGGER.info(String.format(Locale.ROOT,
                "[bed/render] pt=%.2f inBed=(%.4f, %.4f, %.4f) off=%.4f",
                partialTick, alongX, up, alongZ,
                Math.sqrt(alongX * alongX + up * up + alongZ * alongZ)));
    }
}
