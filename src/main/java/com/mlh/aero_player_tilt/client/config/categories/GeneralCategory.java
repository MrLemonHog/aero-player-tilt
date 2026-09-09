package com.mlh.aero_player_tilt.client.config.categories;

import com.mlh.aero_player_tilt.client.KeyBindings;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.config.entries.*;
import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;

public class GeneralCategory {
    public static ConfigCategory build() {
        ConfigCategory general = new ConfigCategory("aero_player_tilt.configuration.general");

        general.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.enabled",
                "aero_player_tilt.configuration.enabled.tooltip",
                Config.MOD_ENABLED));

        general.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.playerTilt",
                "aero_player_tilt.configuration.playerTilt.tooltip",
                Config.PLAYER_TILT));

        addMaxTilt(general);

        addDeckGravity(general);

        general.add(new ThresholdEntry(
                "aero_player_tilt.configuration.tiltMultiplier",
                "aero_player_tilt.configuration.tiltMultiplier.tooltip",
                Config.USE_TILT_MULTIPLIER, Config.TILT_MULTIPLIER,
                0.0, 1.0, 0.05, 0.0, 1.0));

        general.add(new SeparatorEntry("aero_player_tilt.configuration.general.keys"));

        general.add(new KeyBindEntry(
                "aero_player_tilt.configuration.toggleKey",
                "aero_player_tilt.configuration.toggleKey.tooltip",
                KeyBindings.TOGGLE,
                Config.TOGGLE_KEY));

        general.add(new KeyBindEntry(
                "aero_player_tilt.configuration.openConfigKey",
                "aero_player_tilt.configuration.openConfigKey.tooltip",
                KeyBindings.OPEN_CONFIG,
                Config.OPEN_CONFIG_KEY));

        return general;
    }

    private static void addDeckGravity(ConfigCategory general) {
        boolean world = com.mlh.aero_player_tilt.tilt.TiltPolicy.serverRules();
        boolean ours = !world
                || net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer();

        String tooltip = !world
                ? "aero_player_tilt.configuration.deckGravity.menu"
                : (ours ? "aero_player_tilt.configuration.deckGravity.world"
                        : "aero_player_tilt.configuration.deckGravity.locked");

        general.add(new EnumEntry<>(
                "aero_player_tilt.configuration.deckGravity",
                tooltip,
                world ? com.mlh.aero_player_tilt.ServerConfig.DECK_GRAVITY : Config.DECK_GRAVITY,
                com.mlh.aero_player_tilt.tilt.DeckGravity.values(),
                mode -> net.minecraft.network.chat.Component.translatable(mode.translationKey()),
                mode -> mode.translationKey() + ".tooltip",
                !ours));
    }

    private static void addMaxTilt(ConfigCategory general) {
        boolean world = com.mlh.aero_player_tilt.tilt.TiltPolicy.enforcesMinNormalY();
        boolean ours = !world
                || net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer();

        String tooltip = !world
                ? "aero_player_tilt.configuration.minNormalY.tooltip"
                : (ours ? "aero_player_tilt.configuration.minNormalY.world"
                        : "aero_player_tilt.configuration.minNormalY.locked");

        general.add(new SliderEntry(
                "aero_player_tilt.configuration.minNormalY",
                tooltip,
                world ? com.mlh.aero_player_tilt.ServerConfig.MIN_NORMAL_Y : Config.MIN_NORMAL_Y,
                0.0, 1.0, 0.01,
                0.0, 1.0,
                !ours)
                .withReadout(value -> {
                    if (value <= 0.0) return "—";
                    if (value >= 1.0) return "0°";
                    return String.format(java.util.Locale.ROOT, "%.0f°",
                            Math.toDegrees(Math.acos(value)));
                }));
    }
}
