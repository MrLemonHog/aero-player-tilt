package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.TiltSnapshot;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPlayerTilt {
    private ClientPlayerTilt() {}

    private static final Map<Integer, TiltSnapshot> REMOTE = new ConcurrentHashMap<>();

    private static final Map<Integer, RemoteTiltSmoother> SMOOTHERS = new ConcurrentHashMap<>();

    private static final long STALE_TICKS = 100;

    @Nullable
    public static Quaterniond get(Player player, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == player) {
            return local(player, partialTicks);
        }

        TiltSnapshot snapshot = REMOTE.get(player.getId());
        if (snapshot == null) return null;

        RemoteTiltSmoother smoother = smoothing() ? SMOOTHERS.get(player.getId()) : null;
        if (smoother != null) {
            if (!snapshot.isActive() && !smoother.isSettling()) return null;

            return smoother.get(frameNow(player.level(), smoother.frameId(), partialTicks),
                    new Quaterniond());
        }

        return snapshot.get(partialTicks, remoteDeck(player, snapshot, partialTicks),
                snapshot.currentDeckId());
    }

    public static void advanceRemote(float deltaTicks, float partialTicks) {
        if (REMOTE.isEmpty()) {
            if (!SMOOTHERS.isEmpty()) SMOOTHERS.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SMOOTHERS.clear();
            return;
        }

        double halfLife = Config.value(Config.REMOTE_SMOOTH_SPEED, 1.5);

        REMOTE.forEach((id, snapshot) -> {
            RemoteTiltSmoother smoother =
                    SMOOTHERS.computeIfAbsent(id, key -> new RemoteTiltSmoother());

            java.util.UUID deckId = snapshot.currentDeckId();
            Quaterniond newFrame = frameNow(mc.level, deckId, partialTicks);
            if (newFrame == null) deckId = null;

            Quaterniond target = snapshot.currentRaw(new Quaterniond());
            if (deckId != null) {
                target.set(snapshot.currentDeck(new Quaterniond()).conjugate().mul(target))
                        .normalize();
            }

            Quaterniond oldFrame = java.util.Objects.equals(smoother.frameId(), deckId)
                    ? null
                    : frameNow(mc.level, smoother.frameId(), partialTicks);

            smoother.advance(target, deckId, oldFrame, newFrame, halfLife, deltaTicks);
        });

        SMOOTHERS.keySet().removeIf(id -> !REMOTE.containsKey(id));
    }

    private static boolean smoothing() {
        return Config.value(Config.REMOTE_SMOOTH_SPEED, 1.5) > 0.0;
    }

    @Nullable
    private static Quaterniond frameNow(@Nullable net.minecraft.world.level.Level level,
                                        @Nullable java.util.UUID deckId,
                                        float partialTicks) {
        if (level == null || deckId == null) return null;

        SubLevel deck = DeckFrame.byId(level, deckId);
        return deck == null ? null : drawnOrientation(deck, partialTicks);
    }

    @Nullable
    private static Quaterniond remoteDeck(Player player, TiltSnapshot snapshot, float partialTicks) {
        SubLevel deck = DeckFrame.byId(player.level(), snapshot.currentDeckId());
        if (deck == null) return null;

        return drawnOrientation(deck, partialTicks);
    }

    private static Quaterniond drawnOrientation(SubLevel deck, float partialTicks) {
        if (deck instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel client) {
            return new Quaterniond(client.renderPose(partialTicks).orientation());
        }

        return DeckFrame.orientationAt(deck, partialTicks, new Quaterniond());
    }

    public static boolean isTilted(Player player) {
        if (Minecraft.getInstance().player == player) {
            return localActive();
        }
        TiltSnapshot snapshot = REMOTE.get(player.getId());
        if (snapshot == null) return false;
        if (snapshot.isActive()) return true;

        return settling(player.getId());
    }

    public static boolean anyTilted() {
        if (localActive()) return true;

        for (Map.Entry<Integer, TiltSnapshot> entry : REMOTE.entrySet()) {
            if (entry.getValue().isActive() || settling(entry.getKey())) return true;
        }
        return false;
    }

    private static boolean settling(int entityId) {
        if (!smoothing()) return false;

        RemoteTiltSmoother smoother = SMOOTHERS.get(entityId);
        return smoother != null && smoother.isSettling();
    }

    private static boolean localActive() {
        return localAllowed() && PlayerTilt.isMeaningful(BodyTiltController.getRawTiltW());
    }

    private static boolean localAllowed() {
        Player player = Minecraft.getInstance().player;
        return Config.isLoaded()
                && com.mlh.aero_player_tilt.tilt.TiltPolicy.playerTilt()
                && player != null
                && BodyTiltController.shouldComputeTilt(player);
    }

    private static final TiltSnapshot LOCAL = new TiltSnapshot();

    public static void beginTick() {
        LOCAL.beginTick();
        REMOTE.values().forEach(TiltSnapshot::beginTick);
    }

    public static void pushLocal(long gameTime) {
        if (!localAllowed()) {
            LOCAL.set(new Quaterniond(), false, gameTime);
            return;
        }

        Quaternionf tilt = BodyTiltController.getRawTilt();

        Quaternionf carrier = BodyTiltController.getCarrierRotation();
        LOCAL.set(new Quaterniond(tilt),
                carrier != null ? new Quaterniond(carrier) : null,
                BodyTiltController.getCarrierId(),
                PlayerTilt.isMeaningful(tilt.w()), gameTime);
    }

    @Nullable
    public static Quaterniond currentLocal() {
        return LOCAL.get(1.0f);
    }

    @Nullable
    public static Quaterniond previousLocal() {
        return LOCAL.get(0.0f);
    }

    @Nullable
    public static java.util.UUID currentDeckId() {
        return LOCAL.currentDeckId();
    }

    public static Quaterniond currentDeck() {
        return LOCAL.currentDeck(new Quaterniond());
    }

    @Nullable
    private static Quaterniond local(Player player, float partialTicks) {
        if (!localAllowed()) return null;

        return PlayerTilt.leanPartially(new Quaterniond(BodyTiltController.getRawTilt()));
    }

    public static void accept(int entityId, Quaternionf tilt, Quaternionf worldTilt,
                              @Nullable java.util.UUID deckId, boolean active) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (mc.player != null && mc.player.getId() == entityId
                && !com.mlh.aero_player_tilt.client.utils.ReplayCompat.inReplay()) {
            return;
        }

        put(entityId, tilt, worldTilt, deckId, active, mc.level.getGameTime());
    }

    public static void put(int entityId, Quaternionf tilt, Quaternionf worldTilt,
                           @Nullable java.util.UUID deckId, boolean active, long gameTime) {
        Quaterniond world = new Quaterniond(tilt);
        Quaterniond stamp = new Quaterniond();

        SubLevel deck = DeckFrame.byId(Minecraft.getInstance().level, deckId);
        if (deck != null) {
            DeckFrame.orientationAt(deck, 1.0f, stamp);
            world.set(new Quaterniond(stamp).mul(world)).normalize();
        } else if (deckId != null) {
            world.set(worldTilt);
            deckId = null;
        }

        if (!active) {
            TiltSnapshot existing = REMOTE.get(entityId);
            if (existing != null) {
                existing.set(world, stamp, deckId, false, gameTime);
            }
            return;
        }

        TiltSnapshot snapshot = REMOTE.get(entityId);
        boolean fresh = snapshot == null;
        if (fresh) {
            snapshot = new TiltSnapshot();
            REMOTE.put(entityId, snapshot);
        }

        snapshot.set(world, stamp, deckId, true, gameTime);

        if (fresh) snapshot.beginTick();
    }

    @Nullable
    public static java.util.UUID deckIdOf(int entityId) {
        TiltSnapshot snapshot = REMOTE.get(entityId);
        return snapshot == null ? null : snapshot.currentDeckId();
    }

    public static void pruneStale(long gameTime) {
        if (REMOTE.isEmpty()) return;

        REMOTE.entrySet().removeIf(e -> gameTime - e.getValue().lastUpdate() > STALE_TICKS);
        SMOOTHERS.keySet().removeIf(id -> !REMOTE.containsKey(id));
    }

    public static void clear() {
        REMOTE.clear();
        SMOOTHERS.clear();
    }

    public static String debugRemote() {
        if (REMOTE.isEmpty()) return "-";

        StringBuilder out = new StringBuilder();
        REMOTE.forEach((id, snapshot) -> {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.world.entity.Entity entity = mc.level == null ? null : mc.level.getEntity(id);

            Quaterniond tilt = entity instanceof Player player
                    ? get(player, 1.0f)
                    : snapshot.get(1.0f);

            double angle = tilt == null
                    ? 0.0
                    : Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(tilt.w()))));

            String deck = snapshot.currentDeckId() == null
                    ? "NODECK"
                    : (entity != null && DeckFrame.byId(entity.level(), snapshot.currentDeckId()) != null
                            ? "deck" : "LOST");

            if (out.length() > 0) out.append(' ');
            out.append(id).append('=')
                    .append(String.format(java.util.Locale.ROOT, "%.2f", angle))
                    .append('/').append(snapshot.isActive() ? "on" : "off")
                    .append('/').append(deck);
        });
        return out.toString();
    }
}
