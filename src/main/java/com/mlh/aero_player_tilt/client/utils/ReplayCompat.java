package com.mlh.aero_player_tilt.client.utils;

import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReplayCompat {
    private ReplayCompat() {}

    private static final String FLASHBACK_ID = "flashback";
    private static final String FLASHBACK = "com.moulberry.flashback.Flashback";
    private static final String REPLAYMOD = "com.replaymod.replay.ReplayModReplay";

    private static boolean resolved = false;

    @Nullable private static Method flashbackInReplay;
    @Nullable private static Field flashbackRecorder;

    @Nullable private static Field replayModInstance;
    @Nullable private static Method replayModHandler;

    public static boolean inReplay() {
        resolve();

        return flashbackInReplay() || replayModPlaying();
    }

    public static boolean recording() {
        resolve();

        if (flashbackRecorder == null) return false;
        try {
            return flashbackRecorder.get(null) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean flashbackInReplay() {
        if (flashbackInReplay == null) return false;
        try {
            return Boolean.TRUE.equals(flashbackInReplay.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean replayModPlaying() {
        if (replayModInstance == null || replayModHandler == null) return false;
        try {
            Object mod = replayModInstance.get(null);
            return mod != null && replayModHandler.invoke(mod) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;

        Class<?> flashback = loaded(FLASHBACK_ID) ? probe(FLASHBACK) : null;
        if (flashback != null) {
            try {
                flashbackInReplay = flashback.getMethod("isInReplay");
                flashbackRecorder = flashback.getField("RECORDER");
            } catch (Throwable ignored) {
                flashbackInReplay = null;
                flashbackRecorder = null;
            }
        }

        Class<?> replayMod = probe(REPLAYMOD);
        if (replayMod != null) {
            try {
                replayModInstance = replayMod.getField("instance");
                replayModHandler = replayMod.getMethod("getReplayHandler");
            } catch (Throwable ignored) {
                replayModInstance = null;
                replayModHandler = null;
            }
        }
    }

    private static boolean loaded(String modId) {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded(modId);
    }

    @Nullable
    private static Class<?> probe(String className) {
        try {
            return Class.forName(className, false, ReplayCompat.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
