package com.mlh.aero_player_tilt.client.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public final class RealCameraCompat {
    private RealCameraCompat() {}

    private static final boolean PRESENT =
            ModList.get() != null && ModList.get().isLoaded("realcamera");

    private static Method isActive;
    private static boolean resolved;

    public static boolean drawsSelf(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity == net.minecraft.client.Minecraft.getInstance().getCameraEntity() && active();
    }

    public static boolean active() {
        if (!PRESENT) return false;

        if (!resolved) {
            resolved = true;
            try {
                isActive = Class.forName("com.xtracr.realcamera.RealCameraCore").getMethod("isActive");
            } catch (ReflectiveOperationException | LinkageError e) {
                isActive = null;
            }
        }
        if (isActive == null) return false;

        try {
            return (boolean) isActive.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
