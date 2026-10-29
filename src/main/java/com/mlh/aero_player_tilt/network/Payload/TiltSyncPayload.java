package com.mlh.aero_player_tilt.network.Payload;

import com.mlh.aero_player_tilt.ServerTiltStore;
import com.mlh.aero_player_tilt.tilt.TiltSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.UUID;

public record TiltSyncPayload(float bx, float by, float bz, float bw, boolean bodyActive,
                              boolean boots,
                              @Nullable UUID deckId,
                              float dx, float dy, float dz, float dw,
                              TiltSnapshot.Extras extras)
        implements CustomPacketPayload {
    public static final Type<TiltSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aero_player_tilt", "tilt_sync"));

    public static final StreamCodec<FriendlyByteBuf, TiltSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.bx); buf.writeFloat(p.by);
                        buf.writeFloat(p.bz); buf.writeFloat(p.bw);
                        buf.writeBoolean(p.bodyActive);
                        buf.writeBoolean(p.boots);

                        buf.writeBoolean(p.deckId != null);
                        if (p.deckId != null) {
                            buf.writeUUID(p.deckId);
                            buf.writeFloat(p.dx); buf.writeFloat(p.dy);
                            buf.writeFloat(p.dz); buf.writeFloat(p.dw);
                        }

                        p.extras.write(buf);
                    },
                    buf -> {
                        float bx = buf.readFloat(), by = buf.readFloat();
                        float bz = buf.readFloat(), bw = buf.readFloat();
                        boolean active = buf.readBoolean();
                        boolean boots = buf.readBoolean();

                        if (!buf.readBoolean()) {
                            return new TiltSyncPayload(bx, by, bz, bw, active, boots,
                                    null, 0f, 0f, 0f, 1f, TiltSnapshot.Extras.read(buf));
                        }

                        UUID deckId = buf.readUUID();
                        return new TiltSyncPayload(bx, by, bz, bw, active, boots, deckId,
                                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                                TiltSnapshot.Extras.read(buf));
                    }
            );

    public static TiltSyncPayload from(Quaternionf body, boolean bodyActive, boolean boots,
                                       @Nullable UUID deckId, @Nullable Quaterniondc deck,
                                       TiltSnapshot.Extras extras) {
        if (deckId == null || deck == null) {
            return new TiltSyncPayload(body.x, body.y, body.z, body.w, bodyActive, boots,
                    null, 0f, 0f, 0f, 1f, extras);
        }

        return new TiltSyncPayload(body.x, body.y, body.z, body.w, bodyActive, boots, deckId,
                (float) deck.x(), (float) deck.y(), (float) deck.z(), (float) deck.w(), extras);
    }

    public Quaternionf bodyQuaternion() {
        return new Quaternionf(bx, by, bz, bw);
    }

    public Quaterniond deckQuaternion() {
        return new Quaterniond(dx, dy, dz, dw);
    }

    public static void handle(TiltSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (!payload.bodyActive() && payload.extras().none()) {
                ServerTiltStore.clear(player.getUUID());
                return;
            }

            ServerTiltStore.set(player.getUUID(),
                    payload.bodyQuaternion(), payload.bodyActive(), payload.boots(),
                    player.level().getGameTime(),
                    payload.deckId() == null ? null : payload.deckQuaternion(),
                    payload.deckId(), payload.extras());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
