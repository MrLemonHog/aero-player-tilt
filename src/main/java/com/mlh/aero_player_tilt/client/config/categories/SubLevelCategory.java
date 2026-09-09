package com.mlh.aero_player_tilt.client.config.categories;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.config.entries.EnumEntry;
import com.mlh.aero_player_tilt.client.config.entries.SeparatorEntry;
import com.mlh.aero_player_tilt.client.config.entries.SliderEntry;
import com.mlh.aero_player_tilt.client.config.entries.ThresholdEntry;
import com.mlh.aero_player_tilt.client.config.entries.ToggleButtonEntry;
import com.mlh.aero_player_tilt.client.config.ui.ConfigCategory;

public class SubLevelCategory {
    public static ConfigCategory build() {
        ConfigCategory cat = new ConfigCategory("aero_player_tilt.configuration.sublevel");

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.raycast.footing"));

        cat.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.blendFooting",
                "aero_player_tilt.configuration.blendFooting.tooltip",
                Config.BLEND_FOOTING));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.footingGrip",
                "aero_player_tilt.configuration.footingGrip.tooltip",
                Config.FOOTING_GRIP,
                0.05, 2.0, 0.05,
                0.05, 4.0));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.footingMargin",
                "aero_player_tilt.configuration.footingMargin.tooltip",
                Config.FOOTING_MARGIN,
                0.0, 0.9, 0.05,
                0.0, 0.9));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.footingDwellTicks",
                "aero_player_tilt.configuration.footingDwellTicks.tooltip",
                Config.FOOTING_DWELL_TICKS,
                0.0, 20.0, 0.5,
                0.0, 60.0));

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.sublevel.walking"));

        cat.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.stickToDeck",
                "aero_player_tilt.configuration.stickToDeck.tooltip",
                Config.STICK_TO_DECK));

        cat.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.turnWithDeck",
                "aero_player_tilt.configuration.turnWithDeck.tooltip",
                Config.TURN_WITH_DECK));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.deckMomentum",
                "aero_player_tilt.configuration.deckMomentum.tooltip",
                Config.DECK_MOMENTUM,
                0.0, 3.0, 0.05,
                0.0, 3.0)
                .withNotice("aero_player_tilt.configuration.deckMomentum.surefooting",
                        () -> net.neoforged.fml.ModList.get().isLoaded("surefooting")
                                && Config.DECK_MOMENTUM.get() > 0.0));

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.sublevel.zone"));

        cat.add(new EnumEntry<>(
                "aero_player_tilt.configuration.zoneTilt",
                "aero_player_tilt.configuration.zoneTilt.tooltip",
                Config.ZONE_TILT,
                com.mlh.aero_player_tilt.tilt.ZoneTilt.values(),
                mode -> net.minecraft.network.chat.Component.translatable(mode.translationKey()),
                mode -> mode.translationKey() + ".tooltip")
                .withNotice("aero_player_tilt.configuration.zoneTilt.flyingConflict",
                        () -> Config.ZONE_TILT.get() != com.mlh.aero_player_tilt.tilt.ZoneTilt.OFF
                        && Config.DISABLE_ON_FLYING.get()));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.zoneReach",
                "aero_player_tilt.configuration.zoneReach.tooltip",
                Config.ZONE_REACH,
                0.0, 16.0, 0.5,
                0.0, 32.0));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.zoneLingerTicks",
                "aero_player_tilt.configuration.zoneLingerTicks.tooltip",
                Config.ZONE_LINGER_TICKS,
                0.0, 60.0, 1.0,
                0.0, 200.0));

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.experimental.landing"));

        cat.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.predictLanding",
                "aero_player_tilt.configuration.predictLanding.tooltip",
                Config.PREDICT_LANDING));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.predictHorizonTicks",
                "aero_player_tilt.configuration.predictHorizonTicks.tooltip",
                Config.PREDICT_HORIZON_TICKS,
                1, 100, 1,
                1, 200));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.landingLeadTicks",
                "aero_player_tilt.configuration.landingLeadTicks.tooltip",
                Config.LANDING_LEAD_TICKS,
                0.0, 10.0, 0.5,
                0.0, 20.0));

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.activation.leaving"));

        cat.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.dropCacheOnAllMiss",
                "aero_player_tilt.configuration.dropCacheOnAllMiss.tooltip",
                Config.DROP_CACHE_ON_ALL_MISS));

        cat.add(new ToggleButtonEntry(
                "aero_player_tilt.configuration.disableOnFlying",
                "aero_player_tilt.configuration.disableOnFlying.tooltip",
                Config.DISABLE_ON_FLYING)
                .withNotice("aero_player_tilt.configuration.disableOnFlying.zoneConflict",
                        () -> Config.ZONE_TILT.get() != com.mlh.aero_player_tilt.tilt.ZoneTilt.OFF
                        && Config.DISABLE_ON_FLYING.get()));

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.activation.physical"));

        cat.add(new ThresholdEntry(
                "aero_player_tilt.configuration.gateMass",
                "aero_player_tilt.configuration.gateMass.tooltip",
                Config.GATE_MASS_ENABLED, Config.GATE_MASS_MIN,
                0.0, 1000.0, 1.0, 0.0, 100_000_000.0));

        cat.add(new ThresholdEntry(
                "aero_player_tilt.configuration.gateBlocks",
                "aero_player_tilt.configuration.gateBlocks.tooltip",
                Config.GATE_BLOCKS_ENABLED, Config.GATE_BLOCKS_MIN,
                0, 1000, 1, 0, 100_000_000));

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.activation.size"));

        cat.add(new ThresholdEntry(
                "aero_player_tilt.configuration.gateLength",
                "aero_player_tilt.configuration.gateLength.tooltip",
                Config.GATE_LENGTH_ENABLED, Config.GATE_LENGTH_MIN,
                1, 64, 1, 1, 100_000));

        cat.add(new ThresholdEntry(
                "aero_player_tilt.configuration.gateHeight",
                "aero_player_tilt.configuration.gateHeight.tooltip",
                Config.GATE_HEIGHT_ENABLED, Config.GATE_HEIGHT_MIN,
                1, 64, 1, 1, 100_000));

        cat.add(new ThresholdEntry(
                "aero_player_tilt.configuration.gateWidth",
                "aero_player_tilt.configuration.gateWidth.tooltip",
                Config.GATE_WIDTH_ENABLED, Config.GATE_WIDTH_MIN,
                1, 64, 1, 1, 100_000));

        cat.add(new SeparatorEntry("aero_player_tilt.configuration.sublevel.floor"));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.count",
                "aero_player_tilt.configuration.count.tooltip",
                Config.RAYCAST_COUNT,
                1, 100, 1,
                1, 10000));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.radius",
                "aero_player_tilt.configuration.radius.tooltip",
                Config.RAYCAST_RADIUS,
                0.0, 2.0, 0.01,
                0.0, 2.0));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.downLength",
                "aero_player_tilt.configuration.downLength.tooltip",
                Config.RAYCAST_DOWN_LENGTH,
                0.1, 12.0, 0.1,
                0.1, 12.0));

        cat.add(new SliderEntry(
                "aero_player_tilt.configuration.upLength",
                "aero_player_tilt.configuration.upLength.tooltip",
                Config.RAYCAST_UP_LENGTH,
                -1.0, 1.0, 0.05,
                -1.0, 1.0));

        return cat;
    }
}
