package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.utils.StandingDeck;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;

public final class DeckTurn {
    private DeckTurn() {}

    private static final Quaterniond LAST = new Quaterniond();

    @Nullable private static UUID lastDeck;

    private static String state = "-";
    private static double applied;
    private static double deckWorld;

    private static double pending;

    public static void follow(@Nullable LocalPlayer player) {
        if (!Config.flag(Config.TURN_WITH_DECK, true)) {
            standDown("off");
            return;
        }

        if (player == null || player.getVehicle() != null || !PlayerTilt.isTilted(player)) {
            standDown("sable");
            return;
        }

        if (com.mlh.aero_player_tilt.tilt.Boots.holding(player)) {
            standDown("boots");
            return;
        }

        ClientSubLevel deck = deckOf(player);
        if (deck == null) {
            standDown("nodeck");
            return;
        }

        Quaterniond tilt = PlayerTilt.getOrientation(player, 1.0f);
        if (tilt == null) {
            standDown("notilt");
            return;
        }

        Quaterniondc current = deck.renderPose().orientation();

        if (lastDeck == null || !lastDeck.equals(deck.getUniqueId())) {
            LAST.set(current);
            lastDeck = deck.getUniqueId();
            state = "seed";
            return;
        }

        Quaterniond world = current.div(LAST, new Quaterniond());

        Quaterniond inverseTilt = new Quaterniond(tilt).conjugate();
        Quaterniond currentLocal = current.premul(inverseTilt, new Quaterniond());
        Quaterniond lastLocal = LAST.premul(inverseTilt, new Quaterniond());
        Quaterniond relative = currentLocal.div(lastLocal, new Quaterniond());

        LAST.set(current);
        state = "on";

        if (Math.abs(relative.w) < 1.0e-9 || Math.abs(world.w) < 1.0e-9) return;

        double delta = Math.toDegrees(2.0 * relative.y / relative.w);
        if (!Double.isFinite(delta) || delta == 0.0) return;

        applied += delta;
        pending += delta;
        deckWorld += Math.toDegrees(2.0 * world.y / world.w);

        float step = (float) delta;
        player.yBodyRot -= step;
        player.yBodyRotO -= step;
        player.yHeadRot -= step;
        player.yHeadRotO -= step;
        player.setYRot(player.getYRot() - step);
        player.yRotO -= step;
    }

    private static void standDown(String why) {
        lastDeck = null;
        pending = 0.0;
        state = why;
    }

    public static void carryMomentum(@Nullable LocalPlayer player) {
        double turn = pending;
        pending = 0.0;

        if (player == null || turn == 0.0 || !Double.isFinite(turn)) return;
        if (!Config.flag(Config.TURN_WITH_DECK, true)) return;

        double strength = Config.value(Config.DECK_MOMENTUM, 1.0);
        if (strength <= 0.0) return;

        if (!player.onGround() || player.getAbilities().flying) return;
        if (player.getVehicle() != null || !PlayerTilt.isTilted(player)) return;
        if (com.mlh.aero_player_tilt.tilt.Boots.holding(player)) return;

        net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
        if (motion.x == 0.0 && motion.z == 0.0) return;

        double angle = Math.toRadians(turn * strength);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        player.setDeltaMovement(
                cos * motion.x + sin * motion.z,
                motion.y,
                cos * motion.z - sin * motion.x);
    }

    @Nullable
    private static ClientSubLevel deckOf(LocalPlayer player) {
        ClientSubLevel standing = StandingDeck.current();
        if (standing != null) return standing;

        SubLevel tracked = Sable.HELPER.getTrackingSubLevel(player);
        return tracked instanceof ClientSubLevel client && !client.isRemoved() ? client : null;
    }

    public static String debug() {
        String out = String.format(Locale.ROOT, "%s/%.1f/%.1f", state, applied, deckWorld);
        applied = 0.0;
        deckWorld = 0.0;
        return out;
    }
}
