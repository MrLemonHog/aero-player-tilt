package com.mlh.aero_player_tilt.tilt;

import net.minecraft.world.entity.Entity;

public final class StepUpBudget {
    private StepUpBudget() {}

    private static double clientSpent;
    private static double serverSpent;

    public static void reset(boolean clientSide) {
        if (clientSide) clientSpent = 0.0;
        else serverSpent = 0.0;
    }

    public static float remaining(Entity entity) {
        double spent = entity.level().isClientSide ? clientSpent : serverSpent;
        return (float) Math.max(0.0, entity.maxUpStep() - spent);
    }

    public static void spend(Entity entity, double height) {
        if (!(height > 0.0)) return;

        if (entity.level().isClientSide) clientSpent += height;
        else serverSpent += height;
    }
}
