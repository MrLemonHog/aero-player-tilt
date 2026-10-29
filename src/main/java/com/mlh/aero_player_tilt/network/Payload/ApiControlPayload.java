package com.mlh.aero_player_tilt.network.Payload;

import com.mlh.aero_player_tilt.api.PlayerTiltApi.BootsSettings;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.Tilt;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltSettings;
import com.mlh.aero_player_tilt.apiimpl.ApiRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

public record ApiControlPayload(String modId, int kind, int priority, @Nullable Tilt tilt, float smooth,
                                long millis, @Nullable BootsSettings boots, @Nullable TiltSettings settings)
        implements CustomPacketPayload {
    public static final int TILT = 0;
    public static final int BOOTS = 1;
    public static final int SUPPRESS = 2;
    public static final int SETTINGS = 3;

    public static final Type<ApiControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aero_player_tilt", "api_control"));

    public static final StreamCodec<FriendlyByteBuf, ApiControlPayload> CODEC =
            StreamCodec.of(ApiControlPayload::write, ApiControlPayload::read);

    public static ApiControlPayload tilt(String modId, int priority, @Nullable Tilt tilt, float smooth) {
        return new ApiControlPayload(modId, TILT, priority, tilt, smooth, 0L, null, null);
    }

    public static ApiControlPayload boots(String modId, @Nullable BootsSettings boots) {
        return new ApiControlPayload(modId, BOOTS, 0, null, 0f, 0L, boots, null);
    }

    public static ApiControlPayload suppress(String modId, long millis) {
        return new ApiControlPayload(modId, SUPPRESS, 0, null, 0f, millis, null, null);
    }

    public static ApiControlPayload settings(String modId, @Nullable TiltSettings settings) {
        return new ApiControlPayload(modId, SETTINGS, 0, null, 0f, 0L, null, settings);
    }

    private static void write(FriendlyByteBuf buf, ApiControlPayload p) {
        buf.writeUtf(p.modId);
        buf.writeByte(p.kind);
        switch (p.kind) {
            case TILT -> {
                buf.writeVarInt(p.priority);
                buf.writeFloat(p.smooth);
                buf.writeBoolean(p.tilt != null);
                if (p.tilt != null) {
                    Quaternionf q = p.tilt.rotation();
                    buf.writeFloat(q.x); buf.writeFloat(q.y); buf.writeFloat(q.z); buf.writeFloat(q.w);
                    buf.writeByte((p.tilt.free() ? 1 : 0) | (p.tilt.hitbox() ? 2 : 0) | (p.tilt.camera() ? 4 : 0));
                    buf.writeFloat(p.tilt.pivot());
                }
            }
            case BOOTS -> {
                buf.writeBoolean(p.boots != null);
                if (p.boots != null) {
                    buf.writeDouble(p.boots.reach());
                    buf.writeDouble(p.boots.leanStart());
                    buf.writeDouble(p.boots.ledgeHeight());
                    buf.writeDouble(p.boots.grip());
                    buf.writeDouble(p.boots.turnTime());
                }
            }
            case SETTINGS -> {
                buf.writeBoolean(p.settings != null);
                if (p.settings != null) {
                    buf.writeDouble(p.settings.smoothIn());
                    buf.writeDouble(p.settings.smoothOut());
                    buf.writeDouble(p.settings.holdTicks());
                    buf.writeDouble(p.settings.airborneHoldTicks());
                    buf.writeBoolean(p.settings.predictLanding());
                    buf.writeDouble(p.settings.maxAngle());
                    buf.writeDouble(p.settings.multiplier());
                    buf.writeBoolean(p.settings.rotateCamera());
                    buf.writeBoolean(p.settings.turnWithDeck());
                    buf.writeBoolean(p.settings.sticky());
                }
            }
            default -> buf.writeLong(p.millis);
        }
    }

    private static ApiControlPayload read(FriendlyByteBuf buf) {
        String modId = buf.readUtf();
        int kind = buf.readByte();
        switch (kind) {
            case TILT -> {
                int priority = buf.readVarInt();
                float smooth = buf.readFloat();
                if (!buf.readBoolean()) return tilt(modId, priority, null, smooth);

                Quaternionf q = new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
                int flags = buf.readByte();
                float pivot = buf.readFloat();
                return tilt(modId, priority,
                        new Tilt(q, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, pivot), smooth);
            }
            case BOOTS -> {
                if (!buf.readBoolean()) return boots(modId, null);
                return boots(modId, new BootsSettings(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble()));
            }
            case SETTINGS -> {
                if (!buf.readBoolean()) return settings(modId, null);
                return settings(modId, new TiltSettings(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
                        buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
            }
            default -> {
                return suppress(modId, buf.readLong());
            }
        }
    }

    public static void handle(ApiControlPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ApiRegistry.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
