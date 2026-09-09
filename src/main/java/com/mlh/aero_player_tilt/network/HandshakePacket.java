package com.mlh.aero_player_tilt.network;

import com.mlh.aero_player_tilt.AeroPlayerTilt;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HandshakePacket() implements CustomPacketPayload {
    public static final Type<HandshakePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroPlayerTilt.MODID, "handshake"));

    public static final StreamCodec<FriendlyByteBuf, HandshakePacket> STREAM_CODEC =
            StreamCodec.unit(new HandshakePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandshakePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ctx.reply(new HandshakeResponsePacket());

            AeroPlayerTilt.LOGGER.debug(
                    "[AeroPlayerTilt] Handshake received from: {}",
                    ctx.player().getName().getString()
            );
        });
    }
}
