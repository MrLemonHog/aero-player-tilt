package com.mlh.aero_player_tilt.network.Payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.UUID;

public record BodyTiltPayload(int entityId, float qx, float qy, float qz, float qw, boolean active,
                              @Nullable UUID deckId,
                              float wx, float wy, float wz, float ww)
        implements CustomPacketPayload {
    public static final Type<BodyTiltPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aero_player_tilt", "body_tilt"));

    public static final StreamCodec<FriendlyByteBuf, BodyTiltPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.entityId);
                        buf.writeFloat(p.qx); buf.writeFloat(p.qy);
                        buf.writeFloat(p.qz); buf.writeFloat(p.qw);
                        buf.writeBoolean(p.active);
                        buf.writeBoolean(p.deckId != null);
                        if (p.deckId != null) buf.writeUUID(p.deckId);
                        buf.writeFloat(p.wx); buf.writeFloat(p.wy);
                        buf.writeFloat(p.wz); buf.writeFloat(p.ww);
                    },
                    buf -> {
                        int entityId = buf.readVarInt();
                        float qx = buf.readFloat(), qy = buf.readFloat();
                        float qz = buf.readFloat(), qw = buf.readFloat();
                        boolean active = buf.readBoolean();
                        UUID deckId = buf.readBoolean() ? buf.readUUID() : null;
                        float wx = buf.readFloat(), wy = buf.readFloat();
                        float wz = buf.readFloat(), ww = buf.readFloat();
                        return new BodyTiltPayload(entityId, qx, qy, qz, qw, active, deckId,
                                wx, wy, wz, ww);
                    }
            );

    public static BodyTiltPayload of(int entityId, Quaternionf q, boolean active,
                                     @Nullable UUID deckId, Quaternionf world) {
        return new BodyTiltPayload(entityId, q.x, q.y, q.z, q.w, active, deckId,
                world.x, world.y, world.z, world.w);
    }

    public Quaternionf toQuaternion() {
        return new Quaternionf(qx, qy, qz, qw);
    }

    public Quaternionf toWorldQuaternion() {
        return new Quaternionf(wx, wy, wz, ww);
    }

    public static void handle(BodyTiltPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.accept(
                payload.entityId(), payload.toQuaternion(), payload.toWorldQuaternion(),
                payload.deckId(), payload.active()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
