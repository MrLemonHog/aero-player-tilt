package com.mlh.aero_player_tilt.api;

import com.mlh.aero_player_tilt.apiimpl.ApiRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class PlayerTiltApi {
    private PlayerTiltApi() {}

    public static final TagKey<EntityType<?>> UPRIGHT =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("aero_player_tilt", "upright"));

    public static Handle forMod(String modId) {
        return ApiRegistry.forMod(modId);
    }

    public interface Handle {
        String modId();

        TiltState state(Entity entity, float partialTick);

        Optional<Vec3> clip(Entity entity, Vec3 from, Vec3 to);

        boolean contains(Entity entity, Vec3 point);

        AABB bounds(Entity entity);

        void suppress(long millis);

        void release();

        boolean isSuppressed();

        boolean isSuppressedByMe();

        void addTiltSource(int priority, TiltSource source);

        void addTiltSettingsSource(TiltSettingsSource source);

        void addEntityTiltSource(int priority, EntityTiltSource source);

        void addEntityExclusion(Predicate<Entity> excluded);

        void setEntityTilt(Entity entity, @Nullable Quaternionf tilt, float smoothTicks);

        void addBootsSource(BootsSource source);

        void addBootsSurfaceFilter(BootsSurfaceFilter filter);

        void releaseBoots();

        void addGravitySource(GravitySource source);

        void addListener(TiltListener listener);

        void setTilt(ServerPlayer player, int priority, @Nullable Tilt tilt, float smoothTicks);

        void setBoots(ServerPlayer player, @Nullable BootsSettings boots);

        void setSettings(ServerPlayer player, @Nullable TiltSettings settings);

        void suppress(ServerPlayer player, long millis);

        void release(ServerPlayer player);
    }

    @FunctionalInterface
    public interface TiltSource {
        @Nullable Tilt tilt(TiltContext context);
    }

    @FunctionalInterface
    public interface TiltSettingsSource {
        @Nullable TiltSettings settings(Player player, TiltSettings current);
    }

    @FunctionalInterface
    public interface EntityTiltSource {
        @Nullable Quaternionf tilt(Entity entity, float partialTick);
    }

    @FunctionalInterface
    public interface BootsSource {
        @Nullable BootsSettings boots(Player player);
    }

    @FunctionalInterface
    public interface BootsSurfaceFilter {
        boolean grips(Player player, BlockState state, BlockPos pos, Direction face);
    }

    @FunctionalInterface
    public interface GravitySource {
        @Nullable Vec3 gravity(LivingEntity entity);
    }

    public interface TiltListener {
        default void tiltChanged(Player player, boolean tilted) {}

        default void bootsChanged(Player player, boolean booted) {}

        default void deckChanged(Player player, @Nullable UUID deck) {}
    }

    public record TiltContext(Player player, float partialTick, float deltaTicks, Quaternionf ownTilt,
                              @Nullable Vector3f surface) {
    }

    public record Tilt(Quaternionf rotation, boolean free, boolean hitbox, boolean camera, float pivot) {
        public Tilt {
            rotation = new Quaternionf(rotation);
            pivot = Float.isFinite(pivot) ? Math.clamp(pivot, 0f, 4f) : 0f;
        }

        public static Tilt of(Quaternionf rotation) {
            return new Tilt(rotation, false, true, true, 0f);
        }

        public Tilt withFree(boolean free) {
            return new Tilt(rotation, free, hitbox, camera, pivot);
        }

        public Tilt withHitbox(boolean hitbox) {
            return new Tilt(rotation, free, hitbox, camera, pivot);
        }

        public Tilt withCamera(boolean camera) {
            return new Tilt(rotation, free, hitbox, camera, pivot);
        }

        public Tilt withPivot(float pivot) {
            return new Tilt(rotation, free, hitbox, camera, pivot);
        }
    }

    public record TiltState(boolean tilted, Quaternionf orientation, Quaternionf model, float pivot,
                            boolean booted, @Nullable Vector3f face, @Nullable UUID deck,
                            @Nullable Vector3f surface, @Nullable String drivenBy) {
        public Vector3f up() {
            return orientation.transform(new Vector3f(0f, 1f, 0f));
        }
    }

    public record TiltSettings(double smoothIn, double smoothOut, double holdTicks, double airborneHoldTicks,
                               boolean predictLanding, double maxAngle, double multiplier,
                               boolean rotateCamera, boolean turnWithDeck, boolean sticky) {
        public static final TiltSettings DEFAULT = new TiltSettings(
                1.7, 4.0, 5.0, 60.0, true, Math.toDegrees(Math.acos(0.8)), 1.0, true, true, false);

        public TiltSettings {
            smoothIn = fit(smoothIn, 0.0, 9999.0, 1.7);
            smoothOut = fit(smoothOut, 0.0, 9999.0, 4.0);
            holdTicks = fit(holdTicks, 0.0, 200.0, 5.0);
            airborneHoldTicks = fit(airborneHoldTicks, 0.0, 400.0, 60.0);
            maxAngle = fit(maxAngle, 0.0, 180.0, Math.toDegrees(Math.acos(0.8)));
            multiplier = fit(multiplier, 0.0, 1.0, 1.0);
        }

        public TiltSettings withSmoothIn(double smoothIn) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withSmoothOut(double smoothOut) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withHoldTicks(double holdTicks) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withAirborneHoldTicks(double airborneHoldTicks) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withPredictLanding(boolean predictLanding) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withMaxAngle(double maxAngle) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withMultiplier(double multiplier) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withRotateCamera(boolean rotateCamera) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withTurnWithDeck(boolean turnWithDeck) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        public TiltSettings withSticky(boolean sticky) {
            return new TiltSettings(smoothIn, smoothOut, holdTicks, airborneHoldTicks, predictLanding,
                    maxAngle, multiplier, rotateCamera, turnWithDeck, sticky);
        }

        private static double fit(double value, double min, double max, double fallback) {
            return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        }
    }

    public record BootsSettings(double reach, double leanStart, double ledgeHeight, double grip, double turnTime) {
        public static final BootsSettings DEFAULT = new BootsSettings(0.75, 0.30, 3.0, 1.0, 1.0);

        public BootsSettings {
            reach = fit(reach, 0.1, 4.0, 0.75);
            leanStart = fit(leanStart, 0.0, 0.5, 0.30);
            ledgeHeight = fit(ledgeHeight, 1.0, 6.0, 3.0);
            grip = fit(grip, 0.25, 6.0, 1.0);
            turnTime = fit(turnTime, 0.0, 20.0, 1.0);
        }

        public BootsSettings withReach(double reach) {
            return new BootsSettings(reach, leanStart, ledgeHeight, grip, turnTime);
        }

        public BootsSettings withLeanStart(double leanStart) {
            return new BootsSettings(reach, leanStart, ledgeHeight, grip, turnTime);
        }

        public BootsSettings withLedgeHeight(double ledgeHeight) {
            return new BootsSettings(reach, leanStart, ledgeHeight, grip, turnTime);
        }

        public BootsSettings withGrip(double grip) {
            return new BootsSettings(reach, leanStart, ledgeHeight, grip, turnTime);
        }

        public BootsSettings withTurnTime(double turnTime) {
            return new BootsSettings(reach, leanStart, ledgeHeight, grip, turnTime);
        }

        private static double fit(double value, double min, double max, double fallback) {
            return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        }
    }
}
