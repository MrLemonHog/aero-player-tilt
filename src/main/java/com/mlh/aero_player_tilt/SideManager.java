package com.mlh.aero_player_tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.tilt.BodyTiltController;
import com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt;
import com.mlh.aero_player_tilt.network.Payload.TiltSyncPayload;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

public class SideManager {
    public enum Side {
        UNKNOWN,
        CLIENT_SERVER
    }

    private static Side currentSide = Side.UNKNOWN;

    public static Side getSide() {
        return currentSide;
    }

    public static void setSide(Side side) {
        if (Config.DEBUG_MESSAGES.get()) {
            AeroPlayerTilt.LOGGER.info("[AeroPlayerTilt] SideManager -> {}", side);
        }
        currentSide = side;
    }

    public static boolean isClientServer() {
        return currentSide == Side.CLIENT_SERVER;
    }

    public static void sendTiltToServer() {
        Minecraft mc = Minecraft.getInstance();

        if (com.mlh.aero_player_tilt.client.utils.ReplayCompat.inReplay()) return;

        Quaternionf body = new Quaternionf();
        boolean bodyActive = false;
        boolean boots = false;
        Quaterniond deckStamp = null;
        java.util.UUID deckStampId = null;

        if (mc.player != null
                && com.mlh.aero_player_tilt.tilt.TiltPolicy.playerTilt()
                && BodyTiltController.shouldComputeTilt(mc.player)) {
            Quaterniond snapshot = ClientPlayerTilt.currentLocal();
            Quaterniond world = snapshot != null
                    ? snapshot
                    : new Quaterniond(BodyTiltController.getRawTilt());

            boots = com.mlh.aero_player_tilt.client.tilt.BootsController.attached();

            if (!boots) PlayerTilt.leanPartially(world);

            bodyActive = boots || PlayerTilt.isMeaningful((float) world.w());

            body.set((float) world.x(), (float) world.y(), (float) world.z(), (float) world.w());

            deckStampId = ClientPlayerTilt.currentDeckId();
            if (deckStampId != null) deckStamp = ClientPlayerTilt.currentDeck();
        }
        if (!bodyActive) {
            boots = false;
            body.identity();
            deckStamp = null;
            deckStampId = null;
        }

        if (mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null && mc.player != null) {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) {
                    if (bodyActive) {
                        ServerTiltStore.set(sp.getUUID(), body, true, boots,
                                sp.level().getGameTime(),
                                deckStamp, deckStampId);
                    } else {
                        ServerTiltStore.clear(sp.getUUID());
                    }
                }
            }
            return;
        }

        if (currentSide != Side.CLIENT_SERVER) return;
        if (mc.getConnection() == null) return;

        PacketDistributor.sendToServer(
                TiltSyncPayload.from(body, bodyActive, boots, deckStampId, deckStamp));
    }

    public static void beginSession() {
        currentSide = Side.UNKNOWN;
    }

    public static void reset() {
        if (Config.isLoaded() && Config.DEBUG_MESSAGES.get()) {
            AeroPlayerTilt.LOGGER.info("[AeroPlayerTilt] SideManager reset (disconnect)");
        }
        currentSide = Side.UNKNOWN;
    }
}
