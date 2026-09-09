package com.mlh.aero_player_tilt;

import com.mlh.aero_player_tilt.tilt.TiltSnapshot;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerTiltStore {
    private ServerTiltStore() {}

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static final class Entry {
        final TiltSnapshot body = new TiltSnapshot();
        final Quaternionf bodyRaw = new Quaternionf();
        final Quaterniond bodyRawDeck = new Quaterniond();
        @Nullable UUID bodyRawDeckId;
        boolean bodyActive;
    }

    public static final class BodySample {
        public final Quaternionf tilt = new Quaternionf();
        public final Quaterniond deck = new Quaterniond();
        @Nullable public UUID deckId;
        public boolean active;
    }

    public static void set(UUID playerId, Quaternionf bodyTilt, boolean bodyActive, long gameTime) {
        set(playerId, bodyTilt, bodyActive, gameTime, null, null);
    }

    public static void set(UUID playerId, Quaternionf bodyTilt, boolean bodyActive,
                           long gameTime,
                           @Nullable org.joml.Quaterniondc deck, @Nullable UUID deckId) {
        Entry entry = ENTRIES.computeIfAbsent(playerId, id -> new Entry());

        entry.bodyRaw.set(bodyTilt);
        entry.bodyActive = bodyActive;

        if (deck != null && deckId != null) {
            entry.bodyRawDeck.set(deck);
            entry.bodyRawDeckId = deckId;
        } else {
            entry.bodyRawDeck.identity();
            entry.bodyRawDeckId = null;
        }

        entry.body.set(new Quaterniond(bodyTilt), deck, deckId, bodyActive, gameTime);
    }

    public static void beginTick() {
        for (Entry entry : ENTRIES.values()) {
            entry.body.beginTick();
        }
    }

    public static void clear(UUID playerId) {
        ENTRIES.remove(playerId);
    }

    public static void onPlayerLeave(UUID playerId) {
        clear(playerId);
    }

    @Nullable
    public static Quaterniond getBodyTilt(UUID playerId, float partialTicks) {
        return getBodyTilt(playerId, partialTicks, null, null);
    }

    @Nullable
    public static Quaterniond getBodyTilt(UUID playerId, float partialTicks,
                                          @Nullable org.joml.Quaterniondc deckNow,
                                          @Nullable UUID deckNowId) {
        Entry entry = ENTRIES.get(playerId);
        return entry == null ? null : entry.body.get(partialTicks, deckNow, deckNowId);
    }

    public static boolean readBodyForBroadcast(UUID playerId, BodySample dest) {
        Entry entry = ENTRIES.get(playerId);
        if (entry == null) return false;

        dest.tilt.set(entry.bodyRaw);
        dest.deck.set(entry.bodyRawDeck);
        dest.deckId = entry.bodyRawDeckId;
        dest.active = entry.bodyActive;
        return true;
    }

    @Nullable
    public static UUID bodyDeckId(UUID playerId) {
        Entry entry = ENTRIES.get(playerId);
        return entry == null ? null : entry.bodyRawDeckId;
    }

    public static boolean isBodyActive(UUID playerId) {
        Entry entry = ENTRIES.get(playerId);
        return entry != null && entry.bodyActive;
    }

    public static boolean anyBodyActive() {
        for (Entry entry : ENTRIES.values()) {
            if (entry.bodyActive) return true;
        }
        return false;
    }
}
