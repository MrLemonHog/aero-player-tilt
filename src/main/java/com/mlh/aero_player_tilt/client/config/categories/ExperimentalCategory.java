package com.mlh.aero_player_tilt.client.config.categories;

import com.mlh.aero_player_tilt.ServerConfig;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.config.entries.*;
import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;
import com.mlh.aero_player_tilt.tilt.TiltPolicy;

public class ExperimentalCategory {
    public static ConfigCategory build() {
        ConfigCategory cat = new ConfigCategory("aero_player_tilt.configuration.experimental");

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.boots.separator"));

        cat.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.magneticBoots",
                "aero_player_tilt.configuration.magneticBoots.tooltip",
                Config.MAGNETIC_BOOTS));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.magneticReach",
                "aero_player_tilt.configuration.magneticReach.tooltip",
                Config.MAGNETIC_REACH,
                0.1, 2.0, 0.05,
                0.1, 4.0)
                .withVisibleWhen(() -> Config.MAGNETIC_BOOTS.get()));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.magneticLean",
                "aero_player_tilt.configuration.magneticLean.tooltip",
                Config.MAGNETIC_LEAN,
                0.0, 0.5, 0.01,
                0.0, 0.5)
                .withVisibleWhen(() -> Config.MAGNETIC_BOOTS.get()));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.magneticPull",
                "aero_player_tilt.configuration.magneticPull.tooltip",
                Config.MAGNETIC_PULL,
                0.25, 4.0, 0.05,
                0.25, 6.0)
                .withVisibleWhen(() -> Config.MAGNETIC_BOOTS.get()));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.magneticSmooth",
                "aero_player_tilt.configuration.magneticSmooth.tooltip",
                Config.MAGNETIC_SMOOTH,
                0.0, 6.0, 0.05,
                0.0, 20.0)
                .withVisibleWhen(() -> Config.MAGNETIC_BOOTS.get()));

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
