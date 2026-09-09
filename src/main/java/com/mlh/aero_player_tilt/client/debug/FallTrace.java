package com.mlh.aero_player_tilt.client.debug;

import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.tilt.TiltDiagnostics;
import com.mlh.aero_player_tilt.tilt.BoxOrientation;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FallTrace {
    private FallTrace() {}

    private static final int AFTER_LANDING = 60;

    private static final int MAX_LINES = 400;

    private static final double WORTH_READING = 1.5;

    private static final double WORTH_READING_DRIFT = 0.002;

    private static boolean falling;
    private static int ticksInAir;
    private static int settling;

    private static Vec3 lastPos;

    @Nullable
    private static UUID driftDeck;
    private static final Vector3d startLocal = new Vector3d();
    private static double drifted;

    private static double startY;
    private static double peakY;
    private static double lowestY;
    private static boolean brushedSomething;

    private static final List<String> pending = new ArrayList<>();

    public static boolean enabled() {
        return Config.isLoaded() && Config.flag(Config.DEBUG_FRAME_TRACE, false);
    }

    public static void tick(LocalPlayer player) {
        if (!enabled() || flying(player)) {
            reset();
            return;
        }

        boolean onGround = player.onGround();

        if (!onGround) {
            if (!falling) {
                finish();

                falling = true;
                ticksInAir = 0;
                aimDrift(player);
                startY = player.getY();
                peakY = player.getY();
                lowestY = player.getY();
                brushedSomething = false;
                lastPos = null;
            }
            ticksInAir++;
            settling = AFTER_LANDING;
            peakY = Math.max(peakY, player.getY());
            lowestY = Math.min(lowestY, player.getY());
            brushedSomething |= player.horizontalCollision;
            line(player, "air");
            return;
        }

        if (falling) {
            falling = false;
            line(player, "LAND");
            return;
        }

        if (settling > 0) {
            settling--;
            line(player, "down");
            if (settling == 0) finish();
        }
    }

    private static boolean flying(LocalPlayer player) {
        return player.getAbilities().flying || player.isFallFlying()
                || player.isPassenger() || player.isSpectator();
    }

    private static void aimDrift(LocalPlayer player) {
        drifted = 0.0;
        driftDeck = null;

        SubLevel deck = DeckFrame.forBox(player);
        if (deck == null) return;

        Vec3 pos = player.position();
        deck.logicalPose().transformPositionInverse(
                new Vector3d(pos.x, pos.y, pos.z), startLocal);
        driftDeck = deck.getUniqueId();
    }

    private static String drift(LocalPlayer player, String phase) {
        SubLevel deck = DeckFrame.byId(player.level(), driftDeck);
        if (deck == null) return "-";

        Vec3 pos = player.position();
        Vector3d local = deck.logicalPose().transformPositionInverse(
                new Vector3d(pos.x, pos.y, pos.z), new Vector3d());

        double dx = local.x - startLocal.x;
        double dz = local.z - startLocal.z;

        if (!"down".equals(phase)) drifted = Math.sqrt(dx * dx + dz * dz);

        return String.format(Locale.ROOT, "(%+.4f, %+.4f)", dx, dz);
    }

    private static void reset() {
        falling = false;
        settling = 0;
        lastPos = null;
        pending.clear();
    }

    private static void finish() {
        if (pending.isEmpty()) return;

        double drop = peakY - lowestY;

        if (drop < WORTH_READING && !brushedSomething && drifted < WORTH_READING_DRIFT) {
            pending.clear();
            return;
        }

        AeroPlayerTilt.LOGGER.info("[fall] ==== dropped {} blocks over {} ticks, {} along the deck{} ====",
                fmt(drop), ticksInAir, fmt(drifted),
                brushedSomething ? ", touched something sideways" : "");

        for (String line : pending) AeroPlayerTilt.LOGGER.info("[fall] {}", line);

        AeroPlayerTilt.LOGGER.info("[fall] ==== end ====");
        pending.clear();
    }

    private static void line(LocalPlayer player, String phase) {
        Vec3 pos = player.position();
        Vec3 moved = lastPos == null ? Vec3.ZERO : pos.subtract(lastPos);
        lastPos = pos;

        Vec3 motion = player.getDeltaMovement();

        Quaterniond tilt = PlayerTilt.getOrientation(player, 1.0f);
        double tiltAngle = tilt == null ? 0.0 : angle(tilt);

        SubLevel box = DeckFrame.forBox(player);
        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(player);

        ServerPlayer server = server(player);
        Vec3 serverPos = server == null ? null : server.position();
        Quaterniond serverTilt = server == null ? null : PlayerTilt.getOrientation(server, 1.0f);
        SubLevel serverBox = server == null ? null : DeckFrame.forBox(server);

        if (pending.size() >= MAX_LINES) {
            finish();
            reset();
            return;
        }

        pending.add(String.format(Locale.ROOT,
                "%s n=%d pos=(%s, %s, %s) moved=(%s, %s, %s) dm=(%s, %s, %s)"
                        + " speed=%s sprint=%d g=%d hcol=%d vcol=%d grip=%s under=%s"
                        + " tilt=%s drift=%s boxYaw=%s box=%s track=%s"
                        + " srvErr=%s srvTilt=%s srvBox=%s srvG=%s floor[%s] mtv[%s]",
                phase, ticksInAir,
                fmt(pos.x), fmt(pos.y), fmt(pos.z),
                fmt(moved.x), fmt(moved.y), fmt(moved.z),
                fmt(motion.x), fmt(motion.y), fmt(motion.z),
                fmt(Math.sqrt(motion.x * motion.x + motion.z * motion.z)),
                player.isSprinting() ? 1 : 0,
                player.onGround() ? 1 : 0,
                player.horizontalCollision ? 1 : 0, player.verticalCollision ? 1 : 0,
                grip(player), under(player),
                fmt(tiltAngle), drift(player, phase), boxYaw(player, tilt, box), id(box), id(tracking),
                serverPos == null ? "-" : fmt(serverPos.distanceTo(pos)),
                serverTilt == null ? "-" : fmt(angle(serverTilt)),
                id(serverBox), server == null ? "-" : (server.onGround() ? "1" : "0"),
                com.mlh.aero_player_tilt.client.utils.SurfaceRaycaster.debugLast(),
                TiltDiagnostics.takeMtvs()));
    }

    private static String grip(LocalPlayer player) {
        net.minecraft.core.BlockPos below = player.getBlockPosBelowThatAffectsMyMovement();

        return fmt(player.level().getBlockState(below).getFriction(player.level(), below, player));
    }

    private static String under(LocalPlayer player) {
        net.minecraft.core.BlockPos below = player.getBlockPosBelowThatAffectsMyMovement();
        net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(below);

        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).getPath();

        return name;
    }

    private static String boxYaw(LocalPlayer player, @Nullable Quaterniond tilt,
                                 @Nullable SubLevel deck) {
        if (tilt == null || deck == null) return "-";

        Quaterniond box = BoxOrientation.forBox(tilt, deck.lastPose(), new Quaterniond());
        Vector3d forward = box.transform(new Vector3d(1.0, 0.0, 0.0));

        return fmt(Math.toDegrees(Math.atan2(forward.z, forward.x)));
    }

    @Nullable
    private static ServerPlayer server(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer()) return null;

        MinecraftServer server = mc.getSingleplayerServer();
        return server == null ? null : server.getPlayerList().getPlayer(player.getUUID());
    }

    private static double angle(Quaterniond q) {
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(q.w))));
    }

    private static String id(@Nullable SubLevel deck) {
        if (deck == null) return "-";

        UUID uuid = deck.getUniqueId();
        String tag = uuid.toString().substring(0, 4);

        return deck instanceof ClientSubLevel ? tag : tag;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
