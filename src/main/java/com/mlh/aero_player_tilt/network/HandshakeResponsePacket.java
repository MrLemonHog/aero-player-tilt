package com.mlh.aero_player_tilt.network;

import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.SideManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record HandshakeResponsePacket() implements CustomPacketPayload {
    public static final Type<HandshakeResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroPlayerTilt.MODID, "handshake_response"));

    public static final StreamCodec<FriendlyByteBuf, HandshakeResponsePacket> STREAM_CODEC =
            StreamCodec.unit(new HandshakeResponsePacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandshakeResponsePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SideManager.setSide(SideManager.Side.CLIENT_SERVER);

            if (Config.isLoaded() && Config.DEBUG_MESSAGES.get()) {
                AeroPlayerTilt.LOGGER.info(
                        "[AeroPlayerTilt] Server confirmed mod presence -> CLIENT_SERVER mode"
                );
            }
        });
    }
}
