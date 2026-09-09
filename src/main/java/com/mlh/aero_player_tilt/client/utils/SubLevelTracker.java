package com.mlh.aero_player_tilt.client.utils;

import com.mlh.aero_player_tilt.client.config.Config;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;

import javax.annotation.Nullable;

public final class SubLevelTracker {
    private SubLevelTracker() {}

    private static @Nullable ClientSubLevel cachedSubLevel = null;

    private static @Nullable ClientSubLevel lastDeck = null;

    private static @Nullable ClientSubLevel alive(@Nullable ClientSubLevel deck) {
        return deck != null && !deck.isRemoved() ? deck : null;
    }

    public static @Nullable ClientSubLevel getClientSubLevel(LocalPlayer player) {
        cachedSubLevel = alive(cachedSubLevel);
        lastDeck = alive(lastDeck);

        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(player);

        if (player.getAbilities().flying && Config.DISABLE_ON_FLYING.get() ) {
            cachedSubLevel = null;
            return null;
        }

        if (subLevel != null) {
            cachedSubLevel = subLevel instanceof ClientSubLevel csl ? csl : null;
            if (cachedSubLevel != null) lastDeck = cachedSubLevel;
            return cachedSubLevel;
        }

        return cachedSubLevel;
    }

    public static boolean vetoed(LocalPlayer player) {
        return player.getAbilities().flying && Config.DISABLE_ON_FLYING.get();
    }

    public static @Nullable ClientSubLevel deckOf(net.minecraft.world.entity.Entity entity) {
        LocalPlayer self = net.minecraft.client.Minecraft.getInstance().player;
        if (self == null || self != entity) return null;

        ClientSubLevel standing = StandingDeck.current();
        if (standing != null) return standing;

        SubLevel live = Sable.HELPER.getTrackingOrVehicleSubLevel(self);
        if (live instanceof ClientSubLevel csl && !csl.isRemoved()) return csl;

        ClientSubLevel cached = alive(cachedSubLevel);
        if (cached != null) return cached;

        return alive(lastDeck);
    }

    public static void invalidateCache() {
        cachedSubLevel = null;
    }

    public static void forgetDeck() {
        cachedSubLevel = null;
        lastDeck = null;
        StandingDeck.forget();
        DeckZone.forget();
    }
}
