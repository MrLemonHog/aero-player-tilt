package com.mlh.aero_player_tilt.client.config.categories;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.config.entries.*;
import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;

public class MotionCategory {
    public static ConfigCategory build() {
        ConfigCategory motion = new ConfigCategory("aero_player_tilt.configuration.motion");

        motion.add(new SeparatorEntry("aero_player_tilt.configuration.motion.smoothing"));

        motion.add(new SliderEntry(
                "aero_player_tilt.configuration.smoothSpeed",
                "aero_player_tilt.configuration.smoothSpeed.tooltip",
                Config.SMOOTH_SPEED,
                0.0, 10.0, 0.05,
                0.0, 9999.0));

        motion.add(new SliderEntry(
                "aero_player_tilt.configuration.smoothSpeedExit",
                "aero_player_tilt.configuration.smoothSpeedExit.tooltip",
                Config.SMOOTH_SPEED_EXIT,
                0.0, 20.0, 0.05,
                0.0, 9999.0));

        motion.add(new SliderEntry(
                "aero_player_tilt.configuration.takeoverTicks",
                "aero_player_tilt.configuration.takeoverTicks.tooltip",
                Config.TAKEOVER_TICKS,
                0.0, 20.0, 0.5,
                0.0, 40.0));

        motion.add(new SeparatorEntry("aero_player_tilt.configuration.motion.others"));

        motion.add(new SliderEntry(
                "aero_player_tilt.configuration.remoteSmoothSpeed",
                "aero_player_tilt.configuration.remoteSmoothSpeed.tooltip",
                Config.REMOTE_SMOOTH_SPEED,
                0.0, 10.0, 0.05,
                0.0, 9999.0));

        motion.add(new SeparatorEntry("aero_player_tilt.configuration.motion.hold"));

        motion.add(new SliderEntry(
                "aero_player_tilt.configuration.holdTicks",
                "aero_player_tilt.configuration.holdTicks.tooltip",
                Config.HOLD_TICKS,
                0.0, 40.0, 0.5,
                0.0, 200.0));

        motion.add(new SliderEntry(
                "aero_player_tilt.configuration.airborneHoldTicks",
                "aero_player_tilt.configuration.airborneHoldTicks.tooltip",
                Config.AIRBORNE_HOLD_TICKS,
                0.0, 120.0, 1.0,
                0.0, 400.0));

        motion.add(new SliderEntry(
                "aero_player_tilt.configuration.deckCarryTicks",
                "aero_player_tilt.configuration.deckCarryTicks.tooltip",
                Config.DECK_CARRY_TICKS,
                0.0, 60.0, 1.0,
                0.0, 200.0));

        return motion;
    }
}
