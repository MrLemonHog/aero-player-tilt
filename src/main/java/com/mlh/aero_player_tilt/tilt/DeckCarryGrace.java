package com.mlh.aero_player_tilt.tilt;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class DeckCarryGrace {
    private DeckCarryGrace() {}

    private static int carryTicks() {
        return (int) Math.round(
                com.mlh.aero_player_tilt.client.config.Config.value(
                        com.mlh.aero_player_tilt.client.config.Config.DECK_CARRY_TICKS, 20.0));
    }

    private static final Map<Entity, Integer> LAST_SUPPORTED =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void supported(Entity entity) {
        LAST_SUPPORTED.put(entity, entity.tickCount);
    }

    public static void touched(Entity entity) {
        LAST_SUPPORTED.putIfAbsent(entity, entity.tickCount);
    }

    public static void separated(Entity entity) {
        LAST_SUPPORTED.remove(entity);
    }

    public static boolean supportedWithin(Entity entity, int ticks) {
        Integer last = LAST_SUPPORTED.get(entity);
        if (last == null) return false;

        int age = entity.tickCount - last;
        return age >= 0 && age <= ticks;
    }

    private static final int FOOTING_TICKS = 2;

    private record Footing(UUID deck, int tick) {}

    private static final Map<Entity, Footing> FOOTED =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void footing(Entity entity, @Nullable UUID deckId) {
        if (deckId == null) {
            FOOTED.remove(entity);
            return;
        }

        FOOTED.put(entity, new Footing(deckId, entity.tickCount));
    }

    public static boolean footedOn(Entity entity, UUID deckId) {
        Footing footing = FOOTED.get(entity);
        if (footing == null || !footing.deck().equals(deckId)) return false;

        int age = entity.tickCount - footing.tick();
        return age >= 0 && age <= FOOTING_TICKS;
    }

    public static boolean supportedRecently(Entity entity) {
        Integer last = LAST_SUPPORTED.get(entity);
        if (last == null) return false;

        int age = entity.tickCount - last;
        return age >= 0 && age <= carryTicks();
    }
}
