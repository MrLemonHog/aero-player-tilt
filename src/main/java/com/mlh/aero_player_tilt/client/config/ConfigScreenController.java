package com.mlh.aero_player_tilt.client.config;

import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;

import java.util.List;

public class ConfigScreenController {
    private final List<ConfigCategory> categories;

    public ConfigScreenController(List<ConfigCategory> categories) {
        this.categories = categories;
    }

    public void snapshotAll() {
        for (ConfigCategory cat : categories)
            for (var e : cat.entries())
                e.saveSnapshot();
    }

    public void restoreAll() {
        for (ConfigCategory cat : categories)
            for (var e : cat.entries())
                e.restoreSnapshot();
    }
}
