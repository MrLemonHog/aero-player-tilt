package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.api.PlayerTiltApi;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltContext;
import com.mlh.aero_player_tilt.apiimpl.ApiRegistry;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.TiltSnapshot;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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

            smoother.advance(target, snapshot.isBooted() || snapshot.extras().free(), deckId, oldFrame, newFrame,
                    halfLife, deltaTicks);
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
        if (!localAllowed()) return false;

        Quaterniond api = apiBody();
        if (api != null) return PlayerTilt.isVisible(api.w);

        return BootsController.attached()
                || leaning(new Quaterniond(BodyTiltController.getRawTilt()));
    }

    public static boolean leaning(Quaterniond raw) {
        return com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.rocking()
                || PlayerTilt.isVisible(raw.w)
                || PlayerTilt.isVisible(
                        com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.steadyLean(new Quaterniond(raw)).w);
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

        Quaterniond api = apiBody();
        if (api != null) {
            LOCAL.set(api, PlayerTilt.isVisible(api.w), gameTime);
            return;
        }

        Quaternionf tilt = BodyTiltController.getRawTilt();
        boolean boots = BootsController.attached();

        Quaternionf carrier = BodyTiltController.getCarrierRotation();
        LOCAL.set(new Quaterniond(tilt),
                carrier != null ? new Quaterniond(carrier) : null,
                BodyTiltController.getCarrierId(),
                boots || leaning(new Quaterniond(tilt)), boots, TiltSnapshot.Extras.NONE, gameTime);
    }

    public static boolean isBooted(Player player) {
        if (Minecraft.getInstance().player == player) return BootsController.attached();

        TiltSnapshot snapshot = REMOTE.get(player.getId());
        return snapshot != null && snapshot.isActive() && snapshot.isBooted();
    }

    @Nullable
    public static org.joml.Vector3d pullUp(Player player) {
        if (Minecraft.getInstance().player != player) return null;

        return BootsController.pullUp();
    }

    public static boolean isAdrift(Player player) {
        return Minecraft.getInstance().player == player && BootsController.adrift();
    }

    @Nullable
    public static org.joml.Vector3d supportUp(Player player) {
        if (Minecraft.getInstance().player != player) return null;

        return BootsController.supportUp();
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

        Quaterniond api = apiBody();
        return api != null ? api : own();
    }

    @Nullable
    private static Quaterniond own() {
        if (!localAllowed()) return null;

        Quaterniond raw = new Quaterniond(BodyTiltController.getRawTilt());

        if (BootsController.attached()) {
            return com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.steadyTilt(raw);
        }

        return PlayerTilt.leanPartially(com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.steadyLean(raw));
    }

    private static final double API_SETTLED = Math.toRadians(0.5);

    private static final double HUSH_HALF_LIVES = 4.0;

    private static double hush;

    @Nullable
    private static Quaterniond apiApplied;

    private static PlayerTiltApi.Tilt apiShape = PlayerTiltApi.Tilt.of(new Quaternionf());

    private static float apiPivot;

    @Nullable
    private static String apiDriver;

    @Nullable
    private static Vector3f surface;

    private static PlayerTiltApi.TiltSettings settings = PlayerTiltApi.TiltSettings.DEFAULT;

    public static void resolveSettings(Player player) {
        PlayerTiltApi.TiltSettings configured = configured();
        PlayerTiltApi.TiltSettings tuned = ApiRegistry.resolveSettings(player, configured);
        settings = tuned != null ? tuned : configured;
    }

    public static PlayerTiltApi.TiltSettings settings() {
        return settings;
    }

    private static PlayerTiltApi.TiltSettings configured() {
        double minNormalY = Math.clamp(com.mlh.aero_player_tilt.tilt.TiltPolicy.configuredMinNormalY(), -1.0, 1.0);
        return new PlayerTiltApi.TiltSettings(
                Config.value(Config.SMOOTH_SPEED, 1.7),
                Config.value(Config.SMOOTH_SPEED_EXIT, 4.0),
                Config.value(Config.HOLD_TICKS, 5.0),
                Config.value(Config.AIRBORNE_HOLD_TICKS, 60.0),
                Config.flag(Config.PREDICT_LANDING, true),
                Math.toDegrees(Math.acos(minNormalY)),
                com.mlh.aero_player_tilt.tilt.TiltPolicy.configuredTiltMultiplier(),
                Config.flag(Config.ROTATE_CAMERA, true),
                Config.flag(Config.TURN_WITH_DECK, true),
                Config.flag(Config.GRIP_SLOPES, false));
    }

    public static void surface(@Nullable Vector3f normal) {
        surface = normal == null ? null : new Vector3f(normal);
    }

    @Nullable
    public static Vector3f surface() {
        return surface == null ? null : new Vector3f(surface);
    }

    public static void apiFrame(Player player, float partialTick, float deltaTicks) {
        hushFrame(deltaTicks);

        Quaterniond own = own();
        if (own == null) {
            apiApplied = null;
            apiDriver = null;
            apiPivot = 0f;
            return;
        }

        ApiRegistry.Claim claim = ApiRegistry.resolve(
                new TiltContext(player, partialTick, deltaTicks, new Quaternionf(own), surface()));

        if (claim != null) {
            Quaterniond tilt = new Quaterniond(claim.tilt().rotation()).normalize();
            if (!claim.tilt().free()) PlayerTilt.clampToWalkable(PlayerTilt.dropTwist(tilt));

            apiApplied = tilt;
            apiShape = claim.tilt();
            apiPivot = claim.tilt().pivot();
            apiDriver = claim.modId();
            return;
        }

        apiDriver = null;
        if (apiApplied == null) return;

        double halfLife = settings.smoothOut();
        double step = halfLife <= 0.0 ? 1.0 : 1.0 - Math.pow(0.5, deltaTicks / halfLife);
        apiApplied.slerp(own, step).normalize();
        apiPivot *= (float) (1.0 - step);

        double dot = Math.min(1.0, Math.abs(apiApplied.dot(own)));
        if (2.0 * Math.acos(dot) < API_SETTLED && apiPivot < 1.0e-3f) {
            apiApplied = null;
            apiPivot = 0f;
        }
    }

    @Nullable
    public static Quaterniond apiBody() {
        return apiApplied == null || !apiShape.hitbox() ? null : new Quaterniond(apiApplied);
    }

    private static void hushFrame(float deltaTicks) {
        boolean suppressed = ApiRegistry.suppressed();
        double halfLife = suppressed ? settings.smoothOut() : settings.smoothIn();
        double step = halfLife <= 0.0 ? 1.0 : deltaTicks / (HUSH_HALF_LIVES * halfLife);
        hush = Math.clamp(hush + (suppressed ? step : -step), 0.0, 1.0);
    }

    @Nullable
    private static Quaterniond apiModelOffset() {
        boolean shaped = apiApplied != null && !apiShape.hitbox();
        if (!shaped && hush <= 0.0) return null;

        Quaterniond body = apiBody();
        if (body == null) body = own();
        if (body == null) body = new Quaterniond();

        Quaterniond model = shaped ? new Quaterniond(apiApplied) : new Quaterniond(body);
        if (hush > 0.0) {
            Quaterniond upright = PlayerTilt.dropTwist(new Quaterniond(model)).conjugate().mul(model).normalize();
            double t = hush * hush * (3.0 - 2.0 * hush);
            model.slerp(upright, t).normalize();
        }

        Quaterniond offset = body.conjugate().mul(model).normalize();
        return PlayerTilt.isVisible(offset.w) ? offset : null;
    }

    public static boolean apiDriving() {
        return apiDriver != null;
    }

    public static boolean apiDrivesBody() {
        return apiDriver != null && apiShape.hitbox();
    }

    public static boolean apiCamera() {
        return apiApplied != null ? apiShape.camera() : settings.rotateCamera();
    }

    public static TiltSnapshot.Extras localExtras() {
        if ((apiApplied == null && hush <= 0.0) || !localAllowed()) return TiltSnapshot.Extras.NONE;

        Quaterniond offset = apiModelOffset();
        TiltSnapshot.Extras extras = new TiltSnapshot.Extras(apiApplied != null && apiShape.free(),
                offset == null ? new Quaternionf() : new Quaternionf(offset), apiPivot);
        return extras.none() ? TiltSnapshot.Extras.NONE : extras;
    }

    public static boolean isLocal(net.minecraft.world.entity.Entity entity) {
        return entity == Minecraft.getInstance().player;
    }

    @Nullable
    public static Quaterniond modelOffset(Player player, float partialTicks) {
        if (isLocal(player)) return localAllowed() ? apiModelOffset() : null;

        TiltSnapshot snapshot = REMOTE.get(player.getId());
        return snapshot == null ? null : snapshot.modelOffset(partialTicks);
    }

    public static float pivot(Player player, float partialTicks) {
        if (isLocal(player)) return localAllowed() && apiApplied != null ? apiPivot : 0f;

        TiltSnapshot snapshot = REMOTE.get(player.getId());
        return snapshot == null ? 0f : snapshot.pivot(partialTicks);
    }

    public static boolean free(Player player) {
        if (isLocal(player)) return apiApplied != null && apiShape.free();

        TiltSnapshot snapshot = REMOTE.get(player.getId());
        return snapshot != null && snapshot.extras().free();
    }

    public static boolean localDrawn() {
        return localActive() || (localAllowed() && apiModelOffset() != null);
    }

    @Nullable
    public static String apiDriverOf(net.minecraft.world.entity.Entity entity) {
        return isLocal(entity) ? apiDriver : null;
    }

    public static void accept(int entityId, Quaternionf tilt, Quaternionf worldTilt,
                              @Nullable java.util.UUID deckId, boolean active, boolean boots,
                              TiltSnapshot.Extras extras) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (mc.player != null && mc.player.getId() == entityId
                && !com.mlh.aero_player_tilt.client.compat.ReplayCompat.inReplay()) {
            return;
        }

        put(entityId, tilt, worldTilt, deckId, active, boots, extras, mc.level.getGameTime());
    }

    public static void put(int entityId, Quaternionf tilt, Quaternionf worldTilt,
                           @Nullable java.util.UUID deckId, boolean active, boolean boots,
                           TiltSnapshot.Extras extras, long gameTime) {
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

        if (!active && extras.none()) {
            TiltSnapshot existing = REMOTE.get(entityId);
            if (existing != null) {
                existing.set(world, stamp, deckId, false, boots, extras, gameTime);
            }
            return;
        }

        TiltSnapshot snapshot = REMOTE.get(entityId);
        boolean fresh = snapshot == null;
        if (fresh) {
            snapshot = new TiltSnapshot();
            REMOTE.put(entityId, snapshot);
        }

        snapshot.set(world, stamp, deckId, active, boots, extras, gameTime);

        if (fresh) snapshot.beginTick();
    }

    @Nullable
    public static java.util.UUID deckOf(net.minecraft.world.entity.Entity entity) {
        return entity == Minecraft.getInstance().player ? currentDeckId() : deckIdOf(entity.getId());
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
