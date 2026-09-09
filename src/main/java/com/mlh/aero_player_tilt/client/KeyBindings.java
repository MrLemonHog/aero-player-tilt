package com.mlh.aero_player_tilt.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mlh.aero_player_tilt.client.config.Config;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.aero_player_tilt.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.category.aero_player_tilt"
    );

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.aero_player_tilt.open_config",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.category.aero_player_tilt"
    );

    public static void applyKey(KeyMapping mapping, String keyName) {
        InputConstants.Key key = keyName.equals("key.unknown") || keyName.isEmpty()
                ? InputConstants.UNKNOWN
                : InputConstants.getKey(keyName);
        mapping.setKey(key);
        KeyMapping.resetMapping();
    }

    public static void saveToConfig() {
        syncFromMappings();
    }

    public static void syncFromMappings() {
        Config.TOGGLE_KEY.set(TOGGLE.getKey().getName());
        Config.OPEN_CONFIG_KEY.set(OPEN_CONFIG.getKey().getName());
    }
}
