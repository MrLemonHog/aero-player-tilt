package com.mlh.aero_player_tilt.tilt;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import javax.annotation.Nullable;
import java.util.UUID;

public final class TiltSnapshot {
    private final Quaterniond prev = new Quaterniond();
    private final Quaterniond cur = new Quaterniond();

    private final Quaterniond prevDeck = new Quaterniond();
    private final Quaterniond curDeck = new Quaterniond();

    @Nullable private UUID prevDeckId;
    @Nullable private UUID curDeckId;

    private boolean prevBoots;
    private boolean curBoots;

    private boolean active;

    private long lastUpdate;

    public void beginTick() {
        this.prev.set(this.cur);
        this.prevDeck.set(this.curDeck);
        this.prevDeckId = this.curDeckId;
        this.prevBoots = this.curBoots;
    }

    public void set(Quaterniondc value, boolean active, long gameTime) {
        set(value, null, null, active, false, gameTime);
    }

    public void set(Quaterniondc value,
                    @Nullable Quaterniondc deck,
                    @Nullable UUID deckId,
                    boolean active,
                    boolean boots,
                    long gameTime) {
        this.cur.set(value);
        this.curBoots = boots;

        if (deck != null && deckId != null) {
            this.curDeck.set(deck);
            this.curDeckId = deckId;
        } else {
            this.curDeck.identity();
            this.curDeckId = null;
        }

        this.active = active;
        this.lastUpdate = gameTime;
    }

    public Quaterniond get(float partialTicks) {
        return get(partialTicks, null, null);
    }

    public Quaterniond get(float partialTicks,
                           @Nullable Quaterniondc deckNow,
                           @Nullable UUID deckNowId) {
        if (!active) return null;

        if (partialTicks >= 1.0f) {
            return carry(cur, curDeck, curDeckId, curBoots, deckNow, deckNowId, new Quaterniond());
        }
        if (partialTicks <= 0.0f) {
            return carry(prev, prevDeck, prevDeckId, prevBoots, deckNow, deckNowId, new Quaterniond());
        }

        Quaterniond from = carry(prev, prevDeck, prevDeckId, prevBoots, deckNow, deckNowId, new Quaterniond());
        Quaterniond to = carry(cur, curDeck, curDeckId, curBoots, deckNow, deckNowId, new Quaterniond());
        return from.slerp(to, partialTicks);
    }

    private static Quaterniond carry(Quaterniondc value,
                                     Quaterniondc deckStamp,
                                     @Nullable UUID stampId,
                                     boolean boots,
                                     @Nullable Quaterniondc deckNow,
                                     @Nullable UUID deckNowId,
                                     Quaterniond dest) {
        if (deckNow == null || deckNowId == null || !deckNowId.equals(stampId)) {
            return dest.set(value);
        }

        dest.set(deckStamp).conjugate().premul(deckNow).mul(value).normalize();

        if (boots) return dest;

        PlayerTilt.dropTwist(dest);

        return PlayerTilt.clampToWalkable(dest);
    }

    @Nullable
    public UUID currentDeckId() {
        return curDeckId;
    }

    public Quaterniond currentRaw(Quaterniond dest) {
        return dest.set(cur);
    }

    public Quaterniond currentDeck(Quaterniond dest) {
        return dest.set(curDeck);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isBooted() {
        return curBoots;
    }

    public long lastUpdate() {
        return lastUpdate;
    }
}
