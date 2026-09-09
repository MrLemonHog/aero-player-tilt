package com.mlh.aero_player_tilt.client.utils;

import com.mlh.aero_player_tilt.client.config.Config;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public final class StandingDeck {
    private StandingDeck() {}

    private static double margin() {
        return Config.value(Config.FOOTING_MARGIN, 0.20);
    }

    private static float dwellTicks() {
        return (float) Config.value(Config.FOOTING_DWELL_TICKS, 4.0);
    }

    private static final float LAPSE_RATE = 2.0f;

    @Nullable private static ClientSubLevel standing;

    @Nullable private static UUID challenger;
    private static float challengerTicks;

    private static double heldShare;
    private static double rivalShare;

    @Nullable
    public static ClientSubLevel commit(SurfaceRaycaster.Floor floor, float deltaTicks) {
        if (standing != null && standing.isRemoved()) standing = null;

        SurfaceRaycaster.Patch held = floor.patchOf(standing);
        SurfaceRaycaster.Patch best = floor.strongest();

        if (held == null) {
            standing = best == null ? null : best.deck();
            challenger = null;
            challengerTicks = 0f;
            heldShare = best == null ? 0.0 : floor.share(best.weight());
            rivalShare = 0.0;
            return standing;
        }

        standing = held.deck();
        heldShare = floor.share(held.weight());

        if (best == null || best.deck().getUniqueId().equals(standing.getUniqueId())) {
            challenger = null;
            challengerTicks = 0f;
            rivalShare = 0.0;
            return standing;
        }

        rivalShare = floor.share(best.weight());

        if (rivalShare < heldShare + margin()) {
            challengerTicks = Math.max(0f, challengerTicks - deltaTicks * LAPSE_RATE);
            return standing;
        }

        if (!best.deck().getUniqueId().equals(challenger)) {
            challenger = best.deck().getUniqueId();
            challengerTicks = 0f;
        }

        challengerTicks += deltaTicks;
        if (challengerTicks < dwellTicks()) return standing;

        standing = best.deck();
        heldShare = rivalShare;
        rivalShare = 0.0;
        challenger = null;
        challengerTicks = 0f;
        return standing;
    }

    @Nullable
    public static ClientSubLevel current() {
        if (standing != null && standing.isRemoved()) standing = null;
        return standing;
    }

    public static void forget() {
        standing = null;
        challenger = null;
        challengerTicks = 0f;
        heldShare = 0.0;
        rivalShare = 0.0;
    }

    public static String debug() {
        if (standing == null) return "-";

        String seat = String.format(Locale.ROOT, "%s %.2f",
                standing.getUniqueId().toString().substring(0, 4), heldShare);

        if (challenger == null) return seat;

        return String.format(Locale.ROOT, "%s < %s %.2f [%.0f/%.0f]",
                seat, challenger.toString().substring(0, 4), rivalShare,
                challengerTicks, dwellTicks());
    }
}
