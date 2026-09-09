package com.mlh.aero_player_tilt;

import com.mlh.aero_player_tilt.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;

@Mod(AeroPlayerTilt.MODID)
@EventBusSubscriber(modid = AeroPlayerTilt.MODID)
public class AeroPlayerTilt {
    public static final String MODID = "aero_player_tilt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeroPlayerTilt(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        modEventBus.addListener(NetworkHandler::register);

        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    private static final String MODRINTH_URL = "https://modrinth.com/project/aeronautics-player-tilt";

    private static final String NO_MOD_MESSAGE =
            "This server requires Aeronautics Player Tilt.\n\n"
                    + "Install it, along with Aeronautics Camera Sync, and join again:\n"
                    + MODRINTH_URL + "\n\n"
                    + "If you already have the mod, your copy is a different build from the"
                    + " server's and the two cannot talk to each other.";

    private static final java.util.Set<java.util.UUID> REFUSING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.connection.hasChannel(com.mlh.aero_player_tilt.network.Payload.BodyTiltPayload.TYPE)) return;

        if (player.server.isSingleplayerOwner(player.getGameProfile())) return;

        LOGGER.info("[AeroPlayerTilt] {} did not negotiate the body-tilt channel - refused."
                        + " They have no mod installed, or a BUILD WITH A DIFFERENT CHANNEL"
                        + " VERSION.",
                player.getGameProfile().getName());

        REFUSING.add(player.getUUID());
    }

    private static void refusePending(net.minecraft.server.MinecraftServer server) {
        if (REFUSING.isEmpty()) return;

        for (java.util.UUID id : REFUSING) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.connection.disconnect(
                        net.minecraft.network.chat.Component.literal(NO_MOD_MESSAGE));
            }
        }
        REFUSING.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerTiltStore.onPlayerLeave(event.getEntity().getUUID());
        BodyTiltBroadcaster.onPlayerLeave(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target
                && event.getEntity() instanceof ServerPlayer viewer) {
            BodyTiltBroadcaster.sendTo(viewer, target);
        }
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        ServerTiltStore.beginTick();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        refusePending(event.getServer());
        BodyTiltBroadcaster.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REFUSING.clear();
        BodyTiltBroadcaster.reset();
    }
}
