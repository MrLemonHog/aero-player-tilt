package com.mlh.aero_player_tilt.network;

import com.mlh.aero_player_tilt.network.Payload.BodyTiltPayload;
import com.mlh.aero_player_tilt.network.Payload.TiltSyncPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    private static final String CHANNEL_VERSION = "8";

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(CHANNEL_VERSION)
                .optional();

        registrar.playToServer(
                HandshakePacket.TYPE,
                HandshakePacket.STREAM_CODEC,
                HandshakePacket::handle
        );

        registrar.playToClient(
                HandshakeResponsePacket.TYPE,
                HandshakeResponsePacket.STREAM_CODEC,
                HandshakeResponsePacket::handle
        );

        registrar.playToServer(
                TiltSyncPayload.TYPE,
                TiltSyncPayload.CODEC,
                TiltSyncPayload::handle
        );

        registrar.playToClient(
                BodyTiltPayload.TYPE,
                BodyTiltPayload.CODEC,
                BodyTiltPayload::handle
        );
    }
}
