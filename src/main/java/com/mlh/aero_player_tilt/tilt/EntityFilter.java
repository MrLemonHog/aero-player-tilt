package com.mlh.aero_player_tilt.tilt;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityFilter {
    private EntityFilter() {}

    public enum Mode { BLACKLIST, WHITELIST }

    private static final Verdicts MOBS = new Verdicts();

    private static final Verdicts ITEMS = new Verdicts();

    public static boolean allows(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return TiltPolicy.tiltItems() && ITEMS.allows(TiltPolicy.itemListMode(), TiltPolicy.itemList(),
                    item.getItem().getItemHolder(), Registries.ITEM);
        }
        if (entity instanceof LivingEntity) {
            return TiltPolicy.tiltMobs() && MOBS.allows(TiltPolicy.mobListMode(), TiltPolicy.mobList(),
                    entity.getType().builtInRegistryHolder(), Registries.ENTITY_TYPE);
        }
        return false;
    }

    public static void forget() {
        MOBS.state = null;
        ITEMS.state = null;
    }

    private record State(Mode mode, List<? extends String> list, Map<Object, Boolean> cache) {}

    private static final class Verdicts {
        @Nullable
        private volatile State state;

        <T> boolean allows(Mode mode, List<? extends String> list, Holder<T> holder,
                           ResourceKey<? extends Registry<T>> registry) {
            if (mode == Mode.BLACKLIST && list.isEmpty()) return true;

            State current = state;
            if (current == null || current.mode() != mode || current.list() != list) {
                current = new State(mode, list, new ConcurrentHashMap<>());
                state = current;
            }

            return current.cache().computeIfAbsent(holder.value(),
                    key -> listed(list, holder, registry) == (mode == Mode.WHITELIST));
        }
    }

    private static <T> boolean listed(List<? extends String> list, Holder<T> holder,
                                      ResourceKey<? extends Registry<T>> registry) {
        ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);

        for (String raw : list) {
            String entry = raw.trim();
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && holder.is(TagKey.create(registry, tag))) return true;
            } else if (id != null && entry.endsWith(":*")) {
                if (id.getNamespace().equals(entry.substring(0, entry.length() - 2))) return true;
            } else if (id != null && id.equals(ResourceLocation.tryParse(entry))) {
                return true;
            }
        }
        return false;
    }
}
