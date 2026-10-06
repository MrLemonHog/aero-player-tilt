package com.mlh.aero_player_tilt.client.utils;

import net.minecraft.world.entity.Entity;

public final class BodyScale {
    private BodyScale() {}

    private static final double VANILLA_WIDTH = 0.6;

    public static double of(Entity entity) {
        double scale = entity.getBbWidth() / VANILLA_WIDTH;
        return scale > 1.0e-6 ? scale : 1.0;
    }
}
