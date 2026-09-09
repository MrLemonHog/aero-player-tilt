package com.mlh.aero_player_tilt.client.utils;

import com.mlh.aero_player_tilt.AcsBridge;
import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.playsi.aero_cam_sync.api.AcsClientState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class FirstPersonCompat {
    private FirstPersonCompat() {}

    private static final String API = "dev.tr7zw.firstperson.api.FirstPersonAPI";
    private static final String OFFSET_HANDLER = "dev.tr7zw.firstperson.api.PlayerOffsetHandler";

    private static final boolean PRESENT =
            ModList.get() != null && ModList.get().isLoaded("firstperson");

    private static boolean resolved = false;
    private static Method isRenderingPlayerMethod;
    private static boolean usable = false;

    private static boolean installed = false;

    public static boolean isLoaded() {
        return PRESENT;
    }

    public static boolean isRenderingFirstPersonBody() {
        if (!PRESENT) return false;
        ensureResolved();
        if (!usable) return false;
        try {
            return (boolean) isRenderingPlayerMethod.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean leanedBySable(Player player, float partialTick) {
        return drawnByThisMod(player, partialTick) != null;
    }

    public static void install() {
        if (!PRESENT || installed) return;
        installed = true;

        ClassLoader loader = FirstPersonCompat.class.getClassLoader();
        try {
            Class<?> api = Class.forName(API, false, loader);
            Class<?> handler = Class.forName(OFFSET_HANDLER, false, loader);

            Object offsets = Proxy.newProxyInstance(
                    loader, new Class<?>[]{handler}, new OffsetHandler());

            api.getMethod("registerPlayerHandler", Object.class).invoke(null, offsets);

            AeroPlayerTilt.LOGGER.info("[AeroPlayerTilt] First Person Model found - "
                    + "the first person body will follow the lean.");
        } catch (Throwable t) {
            AeroPlayerTilt.LOGGER.warn("[AeroPlayerTilt] First Person Model is loaded but its "
                    + "offset API did not answer; the first person body will keep an upright "
                    + "offset in a leaning view.", t);
        }
    }

    private static final class OffsetHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "applyOffset" -> lean(args);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "aero_player_tilt:first_person_offset";
                default -> null;
            };
        }
    }

    private static Vec3 lean(Object[] args) {
        Vec3 offset = (Vec3) args[3];
        if (offset == null || offset.lengthSqr() < 1.0e-12) return offset;
        if (!Config.MOD_ENABLED.get()) return offset;

        if (!(args[0] instanceof Player player)) return offset;
        float partialTick = (Float) args[1];

        Quaterniond lean = drawnByThisMod(player, partialTick);

        if (lean == null) {
            AcsClientState client = acsFrame(player, partialTick);
            Quaternionf acs = client == null ? null : acsLean(client);
            if (acs == null) return offset;
            lean = new Quaterniond(acs);
        }

        Vector3d leaned = lean.transform(new Vector3d(offset.x, offset.y, offset.z));
        return new Vec3(leaned.x, leaned.y, leaned.z);
    }

    @Nullable
    private static Quaterniond drawnByThisMod(Entity entity, float partialTick) {
        Quaterniond ours = PlayerTilt.getRenderOrientation(entity, partialTick);
        return ours != null && PlayerTilt.isMeaningful(ours.w) ? ours : null;
    }

    @Nullable
    private static AcsClientState acsFrame(Player entity, float partialTick) {
        var state = AcsBridge.ACS.state(entity, partialTick);
        return state.tiltApplied() ? state.client() : null;
    }

    @Nullable
    private static Quaternionf acsLean(AcsClientState client) {
        Quaternionf lean = client.cameraRot().mul(client.vanillaCameraRot().invert());
        return PlayerTilt.isMeaningful(lean.w()) ? lean.normalize() : null;
    }

    private static void ensureResolved() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> api = Class.forName(API, false, FirstPersonCompat.class.getClassLoader());
            isRenderingPlayerMethod = api.getMethod("isRenderingPlayer");
            usable = true;
        } catch (Throwable ignored) {
            usable = false;
        }
    }
}
