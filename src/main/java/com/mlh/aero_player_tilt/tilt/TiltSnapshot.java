package com.mlh.aero_player_tilt.tilt;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.UUID;

public final class TiltSnapshot {
    public record Extras(boolean free, Quaternionf model, float pivot) {
        public static final Extras NONE = new Extras(false, new Quaternionf(), 0f);

        public boolean hasModel() {
            return PlayerTilt.isVisible(model.w());
        }

        public boolean none() {
            return !free && pivot == 0f && !hasModel();
        }

        public void write(FriendlyByteBuf buf) {
            boolean model = hasModel();
            buf.writeByte((free ? 1 : 0) | (model ? 2 : 0) | (pivot != 0f ? 4 : 0));
            if (model) {
                buf.writeFloat(this.model.x()); buf.writeFloat(this.model.y());
                buf.writeFloat(this.model.z()); buf.writeFloat(this.model.w());
            }
            if (pivot != 0f) buf.writeFloat(pivot);
        }

        public static Extras read(FriendlyByteBuf buf) {
            int flags = buf.readByte();
            Quaternionf model = (flags & 2) != 0
                    ? new Quaternionf(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())
                    : new Quaternionf();
            float pivot = (flags & 4) != 0 ? buf.readFloat() : 0f;
            Extras extras = new Extras((flags & 1) != 0, model, pivot);
            return extras.none() ? NONE : extras;
        }
    }

    private final Quaterniond prev = new Quaterniond();
    private final Quaterniond cur = new Quaterniond();

    private final Quaterniond prevDeck = new Quaterniond();
    private final Quaterniond curDeck = new Quaterniond();

    @Nullable private UUID prevDeckId;
    @Nullable private UUID curDeckId;

    private boolean prevBoots;
    private boolean curBoots;

    private Extras prevExtras = Extras.NONE;
    private Extras curExtras = Extras.NONE;

    private boolean active;

    private long lastUpdate;

    public void beginTick() {
        this.prev.set(this.cur);
        this.prevDeck.set(this.curDeck);
        this.prevDeckId = this.curDeckId;
        this.prevBoots = this.curBoots;
        this.prevExtras = this.curExtras;
    }

    public void set(Quaterniondc value, boolean active, long gameTime) {
        set(value, null, null, active, false, Extras.NONE, gameTime);
    }

    public void set(Quaterniondc value,
                    @Nullable Quaterniondc deck,
                    @Nullable UUID deckId,
                    boolean active,
                    boolean boots,
                    Extras extras,
                    long gameTime) {
        this.cur.set(value);
        this.curBoots = boots;
        this.curExtras = extras;

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
            return carry(cur, curDeck, curDeckId, unclamped(true), deckNow, deckNowId, new Quaterniond());
        }
        if (partialTicks <= 0.0f) {
            return carry(prev, prevDeck, prevDeckId, unclamped(false), deckNow, deckNowId, new Quaterniond());
        }

        Quaterniond from = carry(prev, prevDeck, prevDeckId, unclamped(false), deckNow, deckNowId, new Quaterniond());
        Quaterniond to = carry(cur, curDeck, curDeckId, unclamped(true), deckNow, deckNowId, new Quaterniond());
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

    private boolean unclamped(boolean current) {
        return current ? curBoots || curExtras.free() : prevBoots || prevExtras.free();
    }

    public Extras extras() {
        return curExtras;
    }

    @Nullable
    public Quaterniond modelOffset(float partialTicks) {
        if (!prevExtras.hasModel() && !curExtras.hasModel()) return null;

        return new Quaterniond(prevExtras.model())
                .slerp(new Quaterniond(curExtras.model()), Math.clamp(partialTicks, 0f, 1f))
                .normalize();
    }

    public float pivot(float partialTicks) {
        float t = Math.clamp(partialTicks, 0f, 1f);
        return prevExtras.pivot() + (curExtras.pivot() - prevExtras.pivot()) * t;
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
