package com.mlh.aero_player_tilt.client;

import com.mlh.aero_player_tilt.AcsBridge;
import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.SideManager;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.config.ModConfigScreen;
import com.mlh.aero_player_tilt.client.config.alert.ConfigMigrationManager;
import com.mlh.aero_player_tilt.client.config.alert.ConfigResetScreen;
import com.mlh.aero_player_tilt.client.debug.DebugRayRenderer;
import com.mlh.aero_player_tilt.client.tilt.BodyTiltSource;
import com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt;
import com.mlh.aero_player_tilt.network.HandshakePacket;
import com.mlh.aero_player_tilt.network.Payload.TiltSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.nio.file.Files;

import static com.mlh.aero_player_tilt.AeroPlayerTilt.MODID;

@Mod(value = MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class AeroPlayerTiltClient {
    private static boolean pendingHandshake = false;

    public AeroPlayerTiltClient(ModContainer container) {
        boolean configExisted = Files.exists(FMLPaths.CONFIGDIR.get().resolve(MODID + "-client.toml"));
        ConfigMigrationManager.setConfigExisted(configExisted);

        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mc, parent) -> new ModConfigScreen(parent));
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE);
        event.register(KeyBindings.OPEN_CONFIG);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        BodyTiltSource camera = new BodyTiltSource();
        AcsBridge.ACS.addTiltSource(BodyTiltSource.PRIORITY, camera);
        AcsBridge.ACS.addConditions(camera);

        com.mlh.aero_player_tilt.client.utils.FirstPersonCompat.install();

        AeroPlayerTilt.LOGGER.info("{} Initialized!", MODID);
    }

    private static void leaveServerWithoutTheMod(Minecraft mc) {
        if (mc.getConnection() == null) return;

        mc.getConnection().getConnection().disconnect(
                Component.translatable("disconnect.aero_player_tilt.server_missing"));
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        if (ConfigMigrationManager.wasPromptShown()) return;
        if (!ConfigMigrationManager.needsResetPrompt()) return;

        event.setNewScreen(new ConfigResetScreen(event.getScreen()));
    }

    @SubscribeEvent
    static void onClientConnectedToServer(ClientPlayerNetworkEvent.LoggingIn event) {
        SideManager.beginSession();

        if (Minecraft.getInstance().hasSingleplayerServer()) {
            SideManager.setSide(SideManager.Side.CLIENT_SERVER);
            if (Config.DEBUG_MESSAGES.get())
                AeroPlayerTilt.LOGGER.info("[AeroPlayerTilt] Singleplayer detected -> CLIENT_SERVER (direct)");
            return;
        }

        pendingHandshake = true;
    }

    @SubscribeEvent
    static void onClientDisconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingHandshake = false;
        ClientPlayerTilt.clear();
        com.mlh.aero_player_tilt.client.tilt.TiltPrediction.forget();
        com.mlh.aero_player_tilt.client.tilt.ReplayAnchor.clear();
        com.mlh.aero_player_tilt.client.utils.SubLevelTracker.forgetDeck();
        com.mlh.aero_player_tilt.client.tilt.BootsController.forget();
        com.mlh.aero_player_tilt.client.debug.FrameTrace.forget();
        SideManager.reset();
        if (Config.DEBUG_MESSAGES.get()) {
            AeroPlayerTilt.LOGGER.info("[AeroPlayerTilt] Disconnected, SideManager reset");
        }
    }

    @SubscribeEvent
    static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        com.mlh.aero_player_tilt.client.debug.TiltSyncOverlay.render(
                event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            com.mlh.aero_player_tilt.client.tilt.ReplayAnchor.tick(mc.level);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();

        if (pendingHandshake) {
            pendingHandshake = false;

            if (mc.getConnection() != null) {
                boolean serverHasMod = mc.getConnection()
                        .getConnectionType()
                        .isNeoForge();

                boolean channelAvailable = mc.getConnection()
                        .hasChannel(HandshakePacket.TYPE);

                if (channelAvailable) {
                    PacketDistributor.sendToServer(new HandshakePacket());
                    if (Config.DEBUG_MESSAGES.get()) {
                        AeroPlayerTilt.LOGGER.info("[AeroPlayerTilt] Handshake sent to server (NeoForge: {})", serverHasMod);
                    }
                } else {
                    AeroPlayerTilt.LOGGER.warn("[AeroPlayerTilt] Server has no channel for us:"
                            + " either the mod is not installed, or the two sides are running"
                            + " DIFFERENT CHANNEL VERSIONS (different mod builds). Leaving.");
                    leaveServerWithoutTheMod(mc);
                }
            } else {
                if (Config.DEBUG_MESSAGES.get()) {
                    AeroPlayerTilt.LOGGER.info("[AeroPlayerTilt] No connection found");
                }
            }
        }

        if (mc.player != null && mc.level != null) {
            ClientPlayerTilt.beginTick();

            ClientPlayerTilt.pushLocal(mc.level.getGameTime());

            if (SideManager.isClientServer() || mc.hasSingleplayerServer()) {
                SideManager.sendTiltToServer();
            }
            ClientPlayerTilt.pruneStale(mc.level.getGameTime());
            com.mlh.aero_player_tilt.client.tilt.TiltDiagnostics.tick(mc.player);
        }

        while (KeyBindings.TOGGLE.consumeClick()) {
            boolean newValue = !Config.MOD_ENABLED.get();
            Config.MOD_ENABLED.set(newValue);

            if (mc.player != null) {
                String msgKey = newValue ? "msg.aero_player_tilt.enabled" : "msg.aero_player_tilt.disabled";
                mc.player.displayClientMessage(Component.translatable(msgKey), true);

                if (Config.DEBUG_MESSAGES.get()) {
                    AeroPlayerTilt.LOGGER.info(
                            "[AeroPlayerTilt] Toggled: {} | Side: {}",
                            newValue ? "ENABLED" : "DISABLED",
                            SideManager.getSide()
                    );
                }
            }
        }

        while (KeyBindings.OPEN_CONFIG.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new ModConfigScreen(null));
            }
        }
    }
}
