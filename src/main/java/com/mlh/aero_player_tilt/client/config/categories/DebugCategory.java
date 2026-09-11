package com.mlh.aero_player_tilt.client.config.categories;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.config.entries.*;
import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;

public class DebugCategory {
    public static ConfigCategory build() {
        ConfigCategory debug = new ConfigCategory("aero_player_tilt.configuration.debug");

        debug.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.rays",
                "aero_player_tilt.configuration.rays.tooltip",
                Config.DEBUG_RAYS));

        debug.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.debugMessages",
                "aero_player_tilt.configuration.debugMessages.tooltip",
                Config.DEBUG_MESSAGES));

        debug.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.frameTrace",
                "aero_player_tilt.configuration.frameTrace.tooltip",
                Config.DEBUG_FRAME_TRACE)
                .withVisibleWhen(() -> Config.DEBUG_MESSAGES.get()));

        debug.add(new SliderEntry(
                "aero_player_tilt.configuration.frameTraceJump",
                "aero_player_tilt.configuration.frameTraceJump.tooltip",
                Config.DEBUG_FRAME_TRACE_JUMP,
                0.05, 5.0, 0.05,
                0.05, 45.0)
                .withVisibleWhen(() -> Config.DEBUG_MESSAGES.get()
                        && Config.DEBUG_FRAME_TRACE.get()));

        debug.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.tiltSyncPanel",
                "aero_player_tilt.configuration.tiltSyncPanel.tooltip",
                Config.DEBUG_TILT_SYNC));

        return debug;
    }
}
