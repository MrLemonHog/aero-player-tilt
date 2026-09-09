package com.mlh.aero_player_tilt.tilt;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;

import javax.annotation.Nullable;
import java.util.UUID;

public final class DeckFrame {
    private DeckFrame() {}

    @Nullable
    public static SubLevel forBox(@Nullable Entity entity) {
        if (entity == null) return null;

        if (entity.level().isClientSide) {
            SubLevel measured = com.mlh.aero_player_tilt.client.utils.SubLevelTracker.deckOf(entity);
            if (measured != null) return measured;
        } else if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            SubLevel stamped = byId(player.level(),
                    com.mlh.aero_player_tilt.ServerTiltStore.bodyDeckId(player.getUUID()));
            if (stamped != null) return stamped;
        }

        return of(entity);
    }

    public static Quaterniond orientationAt(SubLevel deck, float partialTicks, Quaterniond dest) {
        dest.set(deck.lastPose().orientation());
        if (partialTicks <= 0.0f) return dest;
        if (partialTicks >= 1.0f) return dest.set(deck.logicalPose().orientation());
        return dest.slerp(deck.logicalPose().orientation(), partialTicks);
    }

    @Nullable
    public static SubLevel byId(@Nullable net.minecraft.world.level.Level level, @Nullable UUID id) {
        if (level == null || id == null) return null;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;

        SubLevel deck = container.getSubLevel(id);
        return deck == null || deck.isRemoved() ? null : deck;
    }

    @Nullable
    public static SubLevel of(@Nullable Entity entity) {
        if (entity == null) return null;

        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(entity);
        if (tracking != null && !tracking.isRemoved()) return tracking;

        if (SubLevelContainer.getContainer(entity.level()) == null) return null;

        SubLevel remembered = Sable.HELPER.getLastTrackingSubLevel(entity);
        return remembered == null || remembered.isRemoved() ? null : remembered;
    }
}
