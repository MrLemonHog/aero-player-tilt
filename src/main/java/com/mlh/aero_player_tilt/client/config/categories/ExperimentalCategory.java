package com.mlh.aero_player_tilt.client.config.categories;

import com.mlh.aero_player_tilt.ServerConfig;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.config.entries.*;
import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;
import com.mlh.aero_player_tilt.tilt.TiltPolicy;

public class ExperimentalCategory {
    public static ConfigCategory build() {
        ConfigCategory cat = new ConfigCategory("aero_player_tilt.configuration.experimental");

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.entityTilt.separator"));

        addEntityTilt(cat);

        return cat;
    }

    private static void addEntityTilt(ConfigCategory cat) {
        if (!TiltPolicy.serverRules()) {
            String fallback = net.minecraft.client.Minecraft.getInstance().level == null
                    ? "aero_player_tilt.configuration.entityTilt.menu"
                    : "aero_player_tilt.configuration.entityTilt.local";

            cat.add(new ToggleButtonEntry("aero_player_tilt.configuration.tiltMobs",
                    fallback, Config.TILT_MOBS));
            cat.add(new ToggleButtonEntry("aero_player_tilt.configuration.tiltItems",
                    fallback, Config.TILT_ITEMS));
            return;
        }

        boolean ours = net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer();
        String tooltip = ours
                ? "aero_player_tilt.configuration.entityTilt.world"
                : "aero_player_tilt.configuration.entityTilt.locked";

        cat.add(new ToggleButtonEntry("aero_player_tilt.configuration.tiltMobs",
                tooltip, ServerConfig.TILT_MOBS, !ours));
        cat.add(new ToggleButtonEntry("aero_player_tilt.configuration.tiltItems",
                tooltip, ServerConfig.TILT_ITEMS, !ours));
    }
}
