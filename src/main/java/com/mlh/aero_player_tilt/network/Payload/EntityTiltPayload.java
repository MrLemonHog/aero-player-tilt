package com.mlh.aero_player_tilt.network.Payload;

import com.mlh.aero_player_tilt.apiimpl.ApiRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

public record EntityTiltPayload(int entityId, @Nullable Quaternionf tilt, float smooth)
        implements CustomPacketPayload {
    public static final Type<EntityTiltPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aero_player_tilt", "entity_tilt"));

    public static final StreamCodec<FriendlyByteBuf, EntityTiltPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.entityId);
                        buf.writeFloat(p.smooth);
                        buf.writeBoolean(p.tilt != null);
                        if (p.tilt != null) {
                            buf.writeFloat(p.tilt.x); buf.writeFloat(p.tilt.y);
                            buf.writeFloat(p.tilt.z); buf.writeFloat(p.tilt.w);
                        }
                    },
                    buf -> {
                        int entityId = buf.readVarInt();
                        float smooth = buf.readFloat();
                        Quaternionf tilt = buf.readBoolean()
                                ? new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())
                                : null;
                        return new EntityTiltPayload(entityId, tilt, smooth);
                    }
            );

    public static EntityTiltPayload of(int entityId, @Nullable Quaternionf tilt, float smooth) {
        return new EntityTiltPayload(entityId, tilt, smooth);
    }

    public static void handle(EntityTiltPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ApiRegistry.acceptEntityTilt(payload.entityId(), payload.tilt(), payload.smooth()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
