package com.mlh.aero_player_tilt;

import com.mlh.aero_player_tilt.network.Payload.BodyTiltPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BodyTiltBroadcaster {
    private BodyTiltBroadcaster() {}

    private static final double RANGE_SQR = 128.0 * 128.0;

    private static final double CHANGE_EPSILON = 1.0e-6;

    private static final int KEEPALIVE_TICKS = 40;

    private static final Map<UUID, Sent> LAST_SENT = new HashMap<>();

    private static final ServerTiltStore.BodySample SAMPLE = new ServerTiltStore.BodySample();

    private static final class Sent {
        final Quaternionf value = new Quaternionf();
        @Nullable UUID deckId;
        boolean active;
        long tick;
    }

    private static final Quaternionf WORLD = new Quaternionf();

    public static void tick(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            if (!LAST_SENT.isEmpty()) LAST_SENT.clear();
            return;
        }

        long gameTime = server.overworld().getGameTime();
        Quaternionf current = new Quaternionf();

        for (ServerPlayer subject : players) {
            UUID id = subject.getUUID();

            boolean known = ServerTiltStore.readBodyForBroadcast(id, SAMPLE);
            boolean active = known && SAMPLE.active;
            UUID deckId = null;

            if (!known) {
                current.identity();
                WORLD.identity();
            } else {
                deckId = toDeckRelative(SAMPLE, current);
                WORLD.set(SAMPLE.tilt);
            }

            Sent sent = LAST_SENT.get(id);

            if (sent == null && !active) continue;

            boolean changed = sent == null
                    || sent.active != active
                    || !Objects.equals(sent.deckId, deckId)
                    || (1.0 - Math.abs(sent.value.dot(current))) > CHANGE_EPSILON
                    || gameTime - sent.tick >= KEEPALIVE_TICKS;

            if (!changed) continue;

            if (sent == null) {
                sent = new Sent();
                LAST_SENT.put(id, sent);
            }
            sent.value.set(current);
            sent.deckId = deckId;
            sent.active = active;
            sent.tick = gameTime;

            broadcast(players, subject,
                    BodyTiltPayload.of(subject.getId(), current, active, deckId, WORLD));

            if (!active) LAST_SENT.remove(id);
        }

        LAST_SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    public static void sendTo(ServerPlayer viewer, ServerPlayer subject) {
        if (viewer == subject) return;
        if (!viewer.connection.hasChannel(BodyTiltPayload.TYPE)) return;

        ServerTiltStore.BodySample sample = new ServerTiltStore.BodySample();
        if (!ServerTiltStore.readBodyForBroadcast(subject.getUUID(), sample)) return;
        if (!sample.active) return;

        Quaternionf tilt = new Quaternionf();
        UUID deckId = toDeckRelative(sample, tilt);

        PacketDistributor.sendToPlayer(viewer, BodyTiltPayload.of(subject.getId(), tilt, true, deckId,
                new Quaternionf(sample.tilt)));
    }

    @Nullable
    private static UUID toDeckRelative(ServerTiltStore.BodySample sample, Quaternionf dest) {
        if (sample.deckId == null) {
            dest.set(sample.tilt);
            return null;
        }

        Quaterniond deckRelative = new Quaterniond(sample.deck)
                .conjugate()
                .mul(new Quaterniond(sample.tilt))
                .normalize();

        dest.set((float) deckRelative.x, (float) deckRelative.y,
                (float) deckRelative.z, (float) deckRelative.w);
        return sample.deckId;
    }

    private static void broadcast(Iterable<ServerPlayer> players, ServerPlayer subject, BodyTiltPayload payload) {
        for (ServerPlayer viewer : players) {
            if (viewer.level() != subject.level()) continue;
            if (viewer.distanceToSqr(subject) > RANGE_SQR) continue;
            if (!viewer.connection.hasChannel(BodyTiltPayload.TYPE)) continue;

            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    public static void onPlayerLeave(UUID playerId) {
        LAST_SENT.remove(playerId);
    }

    public static void reset() {
        LAST_SENT.clear();
    }
}
