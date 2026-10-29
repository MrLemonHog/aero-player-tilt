package com.mlh.aero_player_tilt.apiimpl;

import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.ServerTiltStore;
import com.mlh.aero_player_tilt.api.PlayerTiltApi;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.BootsSettings;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.BootsSource;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.BootsSurfaceFilter;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.EntityTiltSource;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.GravitySource;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.Tilt;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltContext;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltListener;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltSettings;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltSettingsSource;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltSource;
import com.mlh.aero_player_tilt.api.PlayerTiltApi.TiltState;
import com.mlh.aero_player_tilt.network.Payload.ApiControlPayload;
import com.mlh.aero_player_tilt.network.Payload.EntityTiltPayload;
import com.mlh.aero_player_tilt.tilt.Boots;
import com.mlh.aero_player_tilt.tilt.DeckRiderTilt;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.TiltedPlayerBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ApiRegistry {
    private ApiRegistry() {}

    private record Entry<T>(String modId, int priority, T value) {}

    private record Seen(boolean tilted, boolean booted, @Nullable UUID deck) {}

    public record Claim(String modId, Tilt tilt) {}

    private static final class ServerTilt {
        int priority;
        Tilt target;
        float smooth;
        @Nullable Quaterniond current;
    }

    private static final class SyncedTilt {
        final Quaterniond target = new Quaterniond();
        final Quaterniond current = new Quaterniond();
        float smooth;
        long last = System.nanoTime();
    }

    private static final double RANGE_SQR = 128.0 * 128.0;

    private static final Comparator<Entry<?>> BY_PRIORITY =
            Comparator.<Entry<?>>comparingInt(Entry::priority).reversed();

    private static final Map<String, Handle> HANDLES = new ConcurrentHashMap<>();

    private static final Map<String, Long> LEASES = new ConcurrentHashMap<>();

    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Seen> SEEN_CLIENT = new ConcurrentHashMap<>();

    private static final Map<UUID, Seen> SEEN_SERVER = new ConcurrentHashMap<>();

    private static final Seen NOTHING = new Seen(false, false, null);

    private static final Map<String, ServerTilt> SERVER_TILTS = new ConcurrentHashMap<>();

    private static final Map<String, BootsSettings> SERVER_BOOTS = new ConcurrentHashMap<>();

    private static final Map<String, TiltSettings> SERVER_SETTINGS = new ConcurrentHashMap<>();

    private static final Map<Entity, Quaterniond> SERVER_ENTITY_TILTS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<Integer, SyncedTilt> CLIENT_ENTITY_TILTS = new ConcurrentHashMap<>();

    private static final AtomicReference<List<Entry<TiltSource>>> SOURCES = new AtomicReference<>(List.of());

    private static final AtomicReference<List<Entry<TiltSettingsSource>>> SETTINGS = new AtomicReference<>(List.of());

    private static final AtomicReference<List<Entry<EntityTiltSource>>> ENTITY_SOURCES = new AtomicReference<>(List.of());

    private static final AtomicReference<List<Entry<Predicate<Entity>>>> EXCLUSIONS = new AtomicReference<>(List.of());

    private static final AtomicReference<List<Entry<BootsSource>>> BOOTS = new AtomicReference<>(List.of());

    private static final AtomicReference<List<Entry<BootsSurfaceFilter>>> SURFACES = new AtomicReference<>(List.of());

    private static final AtomicReference<List<Entry<GravitySource>>> GRAVITY = new AtomicReference<>(List.of());

    private static final AtomicReference<List<Entry<TiltListener>>> LISTENERS = new AtomicReference<>(List.of());

    @Nullable
    private static volatile TiltSettings tuning;

    public static PlayerTiltApi.Handle forMod(String modId) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId must not be null or blank");
        }
        return HANDLES.computeIfAbsent(modId, Handle::new);
    }

    public static boolean suppressed() {
        if (LEASES.isEmpty()) return false;

        long now = System.currentTimeMillis();
        LEASES.values().removeIf(until -> until <= now);
        return !LEASES.isEmpty();
    }

    @Nullable
    public static Claim resolve(TiltContext context) {
        Claim server = serverClaim(context);
        if (server != null) return server;

        for (Entry<TiltSource> entry : SOURCES.get()) {
            try {
                Tilt tilt = entry.value().tilt(context);
                if (tilt != null && usable(tilt.rotation()) != null) return new Claim(entry.modId(), tilt);
            } catch (RuntimeException e) {
                fail(entry.modId(), "tilt source", e);
            }
        }
        return null;
    }

    @Nullable
    private static Claim serverClaim(TiltContext context) {
        String bestId = null;
        ServerTilt best = null;
        for (Map.Entry<String, ServerTilt> entry : SERVER_TILTS.entrySet()) {
            if (best == null || entry.getValue().priority > best.priority) {
                bestId = entry.getKey();
                best = entry.getValue();
            }
        }
        if (best == null) return null;

        if (best.current == null) best.current = new Quaterniond(context.ownTilt());

        double step = best.smooth <= 0f ? 1.0 : 1.0 - Math.pow(0.5, context.deltaTicks() / best.smooth);
        best.current.slerp(new Quaterniond(best.target.rotation()), step).normalize();

        Tilt target = best.target;
        return new Claim(bestId, new Tilt(new Quaternionf(best.current),
                target.free(), target.hitbox(), target.camera(), target.pivot()));
    }

    @Nullable
    public static TiltSettings resolveSettings(Player player, TiltSettings current) {
        for (TiltSettings server : SERVER_SETTINGS.values()) {
            tuning = server;
            return server;
        }

        TiltSettings result = null;
        for (Entry<TiltSettingsSource> entry : SETTINGS.get()) {
            try {
                result = entry.value().settings(player, current);
                if (result != null) break;
            } catch (RuntimeException e) {
                fail(entry.modId(), "tilt settings source", e);
            }
        }
        tuning = result;
        return result;
    }

    @Nullable
    public static TiltSettings tuning() {
        return tuning;
    }

    public static boolean excluded(@Nullable Entity entity) {
        if (entity == null || entity instanceof Player) return false;
        if (entity.getType().is(PlayerTiltApi.UPRIGHT)) return true;

        for (Entry<Predicate<Entity>> entry : EXCLUSIONS.get()) {
            try {
                if (entry.value().test(entity)) return true;
            } catch (RuntimeException e) {
                fail(entry.modId(), "entity exclusion", e);
            }
        }
        return false;
    }

    @Nullable
    public static Quaterniond entityTilt(@Nullable Entity entity, float partialTick) {
        if (entity == null || entity instanceof Player) return null;

        for (Entry<EntityTiltSource> entry : ENTITY_SOURCES.get()) {
            try {
                Quaternionf tilt = usable(entry.value().tilt(entity, partialTick));
                if (tilt != null) return new Quaterniond(tilt);
            } catch (RuntimeException e) {
                fail(entry.modId(), "entity tilt source", e);
            }
        }
        return null;
    }

    @Nullable
    public static BootsSettings bootsFor(@Nullable Player player) {
        if (player == null) return null;

        for (BootsSettings settings : SERVER_BOOTS.values()) return settings;

        for (Entry<BootsSource> entry : BOOTS.get()) {
            try {
                BootsSettings settings = entry.value().boots(player);
                if (settings != null) return settings;
            } catch (RuntimeException e) {
                fail(entry.modId(), "boots source", e);
            }
        }
        return null;
    }

    public static boolean bootsGrip(Player player, BlockState state, BlockPos pos, Direction face) {
        for (Entry<BootsSurfaceFilter> entry : SURFACES.get()) {
            try {
                if (!entry.value().grips(player, state, pos, face)) return false;
            } catch (RuntimeException e) {
                fail(entry.modId(), "boots surface filter", e);
            }
        }
        return true;
    }

    @Nullable
    public static Quaterniond syncedEntityTilt(@Nullable Entity entity) {
        if (entity == null || entity instanceof Player) return null;

        if (!entity.level().isClientSide) {
            if (SERVER_ENTITY_TILTS.isEmpty()) return null;
            Quaterniond tilt = SERVER_ENTITY_TILTS.get(entity);
            return tilt == null ? null : new Quaterniond(tilt);
        }

        if (CLIENT_ENTITY_TILTS.isEmpty()) return null;
        SyncedTilt tilt = CLIENT_ENTITY_TILTS.get(entity.getId());
        if (tilt == null) return null;

        long now = System.nanoTime();
        double ticks = (now - tilt.last) / 5.0e7;
        tilt.last = now;
        double step = tilt.smooth <= 0f ? 1.0 : 1.0 - Math.pow(0.5, ticks / tilt.smooth);
        tilt.current.slerp(tilt.target, Math.clamp(step, 0.0, 1.0)).normalize();
        return new Quaterniond(tilt.current);
    }

    public static void acceptEntityTilt(int entityId, @Nullable Quaternionf tilt, float smooth) {
        if (tilt == null) {
            CLIENT_ENTITY_TILTS.remove(entityId);
            return;
        }

        boolean fresh = !CLIENT_ENTITY_TILTS.containsKey(entityId);
        SyncedTilt synced = CLIENT_ENTITY_TILTS.computeIfAbsent(entityId, id -> new SyncedTilt());
        synced.target.set(tilt).normalize();
        synced.smooth = smooth;
        if (fresh && smooth <= 0f) synced.current.set(synced.target);
    }

    public static void sendEntityTilt(ServerPlayer viewer, Entity target) {
        if (SERVER_ENTITY_TILTS.isEmpty() || target instanceof Player) return;

        Quaterniond tilt = SERVER_ENTITY_TILTS.get(target);
        if (tilt == null || !viewer.connection.hasChannel(EntityTiltPayload.TYPE)) return;

        PacketDistributor.sendToPlayer(viewer, EntityTiltPayload.of(target.getId(), new Quaternionf(tilt), 0f));
    }

    @Nullable
    public static Vec3 gravity(LivingEntity entity) {
        for (Entry<GravitySource> entry : GRAVITY.get()) {
            try {
                Vec3 gravity = entry.value().gravity(entity);
                if (gravity != null && Double.isFinite(gravity.lengthSqr())) return gravity;
            } catch (RuntimeException e) {
                fail(entry.modId(), "gravity source", e);
            }
        }
        return null;
    }

    public static void accept(ApiControlPayload payload) {
        switch (payload.kind()) {
            case ApiControlPayload.TILT -> {
                Tilt target = payload.tilt();
                if (target == null) {
                    SERVER_TILTS.remove(payload.modId());
                    return;
                }
                ServerTilt tilt = SERVER_TILTS.computeIfAbsent(payload.modId(), id -> new ServerTilt());
                tilt.priority = payload.priority();
                tilt.target = target;
                tilt.smooth = payload.smooth();
            }
            case ApiControlPayload.BOOTS -> {
                if (payload.boots() == null) SERVER_BOOTS.remove(payload.modId());
                else SERVER_BOOTS.put(payload.modId(), payload.boots());
            }
            case ApiControlPayload.SUPPRESS -> lease(payload.modId() + "@server", payload.millis());
            case ApiControlPayload.SETTINGS -> {
                if (payload.settings() == null) SERVER_SETTINGS.remove(payload.modId());
                else SERVER_SETTINGS.put(payload.modId(), payload.settings());
            }
            default -> {}
        }
    }

    public static void clearServerControl() {
        SERVER_TILTS.clear();
        SERVER_BOOTS.clear();
        SERVER_SETTINGS.clear();
        CLIENT_ENTITY_TILTS.clear();
        LEASES.keySet().removeIf(key -> key.endsWith("@server"));
        SEEN_CLIENT.clear();
        tuning = null;
    }

    private static void lease(String key, long millis) {
        if (millis <= 0) LEASES.remove(key);
        else LEASES.merge(key, System.currentTimeMillis() + millis, Math::max);
    }

    public static void track(Player player) {
        if (LISTENERS.get().isEmpty()) return;

        Map<UUID, Seen> seen = player.level().isClientSide ? SEEN_CLIENT : SEEN_SERVER;
        Seen now = new Seen(PlayerTilt.isTilted(player), Boots.holding(player), deckOf(player));
        Seen was = Objects.requireNonNullElse(seen.put(player.getUUID(), now), NOTHING);

        if (now.tilted() != was.tilted()) notify(l -> l.tiltChanged(player, now.tilted()));
        if (now.booted() != was.booted()) notify(l -> l.bootsChanged(player, now.booted()));
        if (!Objects.equals(now.deck(), was.deck())) notify(l -> l.deckChanged(player, now.deck()));
    }

    public static void forget(UUID playerId) {
        SEEN_SERVER.remove(playerId);
    }

    private static void notify(Consumer<TiltListener> event) {
        for (Entry<TiltListener> entry : LISTENERS.get()) {
            try {
                event.accept(entry.value());
            } catch (RuntimeException e) {
                fail(entry.modId(), "listener", e);
            }
        }
    }

    @Nullable
    private static Quaternionf usable(@Nullable Quaternionf tilt) {
        if (tilt == null || !Float.isFinite(tilt.lengthSquared()) || tilt.lengthSquared() <= 1.0e-8f) return null;
        return new Quaternionf(tilt).normalize();
    }

    private static void fail(String modId, String what, RuntimeException e) {
        if (FAILED.add(modId + what)) {
            AeroPlayerTilt.LOGGER.error("[api] {} from {} threw, skipped", what, modId, e);
        }
    }

    private static <T> void add(AtomicReference<List<Entry<T>>> list, Entry<T> entry, boolean sorted) {
        Objects.requireNonNull(entry.value());
        list.updateAndGet(current -> {
            List<Entry<T>> next = new ArrayList<>(current);
            next.add(entry);
            if (sorted) next.sort(BY_PRIORITY);
            return List.copyOf(next);
        });
    }

    @Nullable
    private static UUID deckOf(Entity entity) {
        if (!(entity instanceof Player)) return DeckRiderTilt.deckId(entity);
        if (entity.level().isClientSide) return com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.deckOf(entity);
        return ServerTiltStore.bodyDeckId(entity.getUUID());
    }

    @Nullable
    private static org.joml.Vector3f face(Player player) {
        org.joml.Vector3d face = com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.supportUp(player);
        return face == null ? null : new org.joml.Vector3f((float) face.x, (float) face.y, (float) face.z);
    }

    private static void send(ServerPlayer player, ApiControlPayload payload) {
        if (player.connection.hasChannel(ApiControlPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private record Handle(String modId) implements PlayerTiltApi.Handle {
        @Override
        public TiltState state(Entity entity, float partialTick) {
            Quaterniond body = PlayerTilt.getRenderOrientation(entity, partialTick);
            boolean tilted = body != null && PlayerTilt.isRenderTilted(entity);
            Quaterniond model = PlayerTilt.getModelOrientation(entity, partialTick);
            boolean drawn = tilted || PlayerTilt.modelOffset(entity, partialTick) != null;

            boolean client = entity.level().isClientSide;
            boolean local = client && com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.isLocal(entity);

            return new TiltState(tilted,
                    tilted ? new Quaternionf(body) : new Quaternionf(),
                    drawn && model != null ? new Quaternionf(model) : new Quaternionf(),
                    PlayerTilt.modelPivot(entity, partialTick),
                    Boots.holding(entity),
                    local ? face((Player) entity) : null,
                    tilted ? deckOf(entity) : null,
                    local ? com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.surface() : null,
                    local ? com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.apiDriverOf(entity) : null);
        }

        @Override
        public Optional<Vec3> clip(Entity entity, Vec3 from, Vec3 to) {
            Optional<Vec3> hit = TiltedPlayerBox.clipRay(entity, entity.level(), from, to);
            return hit != null ? hit : entity.getBoundingBox().clip(from, to);
        }

        @Override
        public boolean contains(Entity entity, Vec3 point) {
            Boolean inside = TiltedPlayerBox.containsPoint(entity, entity.level(), point);
            return inside != null ? inside : entity.getBoundingBox().contains(point);
        }

        @Override
        public AABB bounds(Entity entity) {
            AABB box = TiltedPlayerBox.enclosingBox(entity);
            return box != null ? box : entity.getBoundingBox();
        }

        @Override
        public void suppress(long millis) {
            if (millis > 0) lease(modId, millis);
        }

        @Override
        public void release() {
            LEASES.remove(modId);
        }

        @Override
        public boolean isSuppressed() {
            return suppressed();
        }

        @Override
        public boolean isSuppressedByMe() {
            Long until = LEASES.get(modId);
            return until != null && until > System.currentTimeMillis();
        }

        @Override
        public void addTiltSource(int priority, TiltSource source) {
            add(SOURCES, new Entry<>(modId, priority, source), true);
        }

        @Override
        public void addTiltSettingsSource(TiltSettingsSource source) {
            add(SETTINGS, new Entry<>(modId, 0, source), false);
        }

        @Override
        public void addEntityTiltSource(int priority, EntityTiltSource source) {
            add(ENTITY_SOURCES, new Entry<>(modId, priority, source), true);
        }

        @Override
        public void addEntityExclusion(Predicate<Entity> excluded) {
            add(EXCLUSIONS, new Entry<>(modId, 0, excluded), false);
        }

        @Override
        public void addBootsSource(BootsSource source) {
            add(BOOTS, new Entry<>(modId, 0, source), false);
        }

        @Override
        public void addBootsSurfaceFilter(BootsSurfaceFilter filter) {
            add(SURFACES, new Entry<>(modId, 0, filter), false);
        }

        @Override
        public void setEntityTilt(Entity entity, @Nullable Quaternionf tilt, float smoothTicks) {
            if (entity instanceof Player) return;

            Quaternionf rotation = usable(tilt);
            if (entity.level().isClientSide) {
                acceptEntityTilt(entity.getId(), rotation, smoothTicks);
                return;
            }

            if (rotation == null) SERVER_ENTITY_TILTS.remove(entity);
            else SERVER_ENTITY_TILTS.put(entity, new Quaterniond(rotation));

            EntityTiltPayload payload = EntityTiltPayload.of(entity.getId(), rotation, smoothTicks);
            for (ServerPlayer viewer : ((ServerLevel) entity.level()).players()) {
                if (viewer.distanceToSqr(entity) > RANGE_SQR) continue;
                if (!viewer.connection.hasChannel(EntityTiltPayload.TYPE)) continue;
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }

        @Override
        public void releaseBoots() {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                com.mlh.aero_player_tilt.client.tilt.BootsController.letGo();
            }
        }

        @Override
        public void addGravitySource(GravitySource source) {
            add(GRAVITY, new Entry<>(modId, 0, source), false);
        }

        @Override
        public void addListener(TiltListener listener) {
            add(LISTENERS, new Entry<>(modId, 0, listener), false);
        }

        @Override
        public void setTilt(ServerPlayer player, int priority, @Nullable Tilt tilt, float smoothTicks) {
            send(player, ApiControlPayload.tilt(modId, priority, tilt, smoothTicks));
        }

        @Override
        public void setBoots(ServerPlayer player, @Nullable BootsSettings boots) {
            send(player, ApiControlPayload.boots(modId, boots));
        }

        @Override
        public void setSettings(ServerPlayer player, @Nullable TiltSettings settings) {
            send(player, ApiControlPayload.settings(modId, settings));
        }

        @Override
        public void suppress(ServerPlayer player, long millis) {
            if (millis > 0) send(player, ApiControlPayload.suppress(modId, millis));
        }

        @Override
        public void release(ServerPlayer player) {
            send(player, ApiControlPayload.suppress(modId, 0));
        }
    }
}
