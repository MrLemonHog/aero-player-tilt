package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.utils.ReplayCompat;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class ReplayAnchor {
    private ReplayAnchor() {}

    private static final Map<Integer, Anchor> ANCHORS = new HashMap<>();

    private static final double FOLLOW_FULLY = 0.12;

    private static final double SETTLE_MIN = 0.15;

    private static final double JITTER_MEMORY = 0.2;

    private static final double CLEAN = 0.02;

    private static final double SANE_PULL = 1.5;

    private static final double[] PHASES =
            {0.0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0};

    private static final double WORTH_SWITCHING = 0.85;

    private static final class Anchor {
        final SubLevel deck;

        final Vec3[] raw = new Vec3[PHASES.length];
        final double[] wander = new double[PHASES.length];

        int phase = PHASES.length - 1;

        Vec3 held;
        Vec3 heldBefore;

        Vec3 mark;
        int marked;
        double excursion;

        Anchor(SubLevel deck, Vec3[] samples) {
            this.deck = deck;
            System.arraycopy(samples, 0, this.raw, 0, samples.length);
            this.held = samples[samples.length - 1];
            this.heldBefore = this.held;
            this.mark = this.held;
        }
    }

    public static void tick(ClientLevel level) {
        if (!ReplayCompat.inReplay()) {
            if (!ANCHORS.isEmpty()) ANCHORS.clear();
            return;
        }

        for (Player player : level.players()) {
            SubLevel deck = deckOf(player);
            if (deck == null) {
                ANCHORS.remove(player.getId());
                continue;
            }

            Vec3[] samples = readAcrossTick(deck, player.position());

            Anchor anchor = ANCHORS.get(player.getId());
            if (anchor == null || anchor.deck != deck) {
                ANCHORS.put(player.getId(), new Anchor(deck, samples));
                continue;
            }

            int steadiest = 0;
            for (int i = 0; i < PHASES.length; i++) {
                anchor.wander[i] = ease(anchor.wander[i], samples[i].distanceTo(anchor.raw[i]));
                anchor.raw[i] = samples[i];

                if (anchor.wander[i] < anchor.wander[steadiest]) steadiest = i;
            }

            if (steadiest != anchor.phase
                    && anchor.wander[steadiest] < anchor.wander[anchor.phase] * WORTH_SWITCHING) {
                Vec3 world = poseAt(deck, PHASES[anchor.phase]).transformPosition(anchor.held);

                anchor.phase = steadiest;
                anchor.held = poseAt(deck, PHASES[steadiest]).transformPositionInverse(world);
            }

            Vec3 aim = samples[anchor.phase];

            anchor.heldBefore = anchor.held;
            anchor.held = anchor.held.add(aim.subtract(anchor.held).scale(settle(anchor.held, aim)));

            track(anchor);

            place(player, anchor);
        }

        ANCHORS.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    private static Vec3[] readAcrossTick(SubLevel deck, Vec3 position) {
        Vec3[] samples = new Vec3[PHASES.length];

        for (int i = 0; i < PHASES.length; i++) {
            samples[i] = poseAt(deck, PHASES[i]).transformPositionInverse(position);
        }

        return samples;
    }

    private static Pose3d poseAt(SubLevel deck, double phase) {
        Pose3d pose = new Pose3d(deck.lastPose());

        if (pose.rotationPoint().lengthSquared() <= 0.0) {
            pose.rotationPoint().set(deck.logicalPose().rotationPoint());
        }

        return phase <= 0.0 ? pose : pose.lerp(deck.logicalPose(), phase);
    }

    private static void track(Anchor anchor) {
        anchor.excursion = Math.max(anchor.excursion, anchor.held.distanceTo(anchor.mark));

        if (++anchor.marked >= 40) {
            anchor.marked = 0;
            anchor.mark = anchor.held;
            anchor.excursion = 0.0;
        }
    }

    private static void place(Player player, Anchor anchor) {
        Vec3 now = anchor.deck.logicalPose().transformPosition(anchor.held);

        if (now.distanceToSqr(player.position()) > SANE_PULL * SANE_PULL) return;

        Vec3 before = anchor.deck.lastPose().transformPosition(anchor.heldBefore);

        player.setPos(now);

        player.xOld = before.x;
        player.yOld = before.y;
        player.zOld = before.z;
        player.xo = before.x;
        player.yo = before.y;
        player.zo = before.z;
    }

    private static double settle(Vec3 held, Vec3 aim) {
        double step = held.distanceTo(aim);

        return Math.min(1.0, Math.max(SETTLE_MIN, step / FOLLOW_FULLY));
    }

    private static double ease(double average, double sample) {
        return average + (sample - average) * JITTER_MEMORY;
    }

    public static float deckSpeed(Entity entity) {
        Anchor anchor = anchorOf(entity);
        if (anchor == null) return -1f;

        return (float) anchor.held.distanceTo(anchor.heldBefore);
    }

    @Nullable
    public static Vec3 bodyMove(Entity entity) {
        Anchor anchor = anchorOf(entity);
        if (anchor == null) return null;

        Vec3 step = anchor.held.subtract(anchor.heldBefore);
        Vector3d world = anchor.deck.logicalPose().orientation()
                .transform(new Vector3d(step.x, step.y, step.z));

        return new Vec3(world.x, world.y, world.z);
    }

    private static double worstWander(Anchor anchor) {
        double worst = 0.0;
        for (double value : anchor.wander) worst = Math.max(worst, value);
        return worst;
    }

    public static void clear() {
        ANCHORS.clear();
    }

    public static String debug() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "-";

        StringBuilder out = new StringBuilder();

        for (Player player : mc.level.players()) {
            if (out.length() > 0) out.append(' ');
            out.append(player.getId()).append('=');

            SubLevel deck = deckOf(player);
            if (deck == null) {
                out.append("nodeck");
                continue;
            }

            boolean networked = player instanceof EntityStickExtension stick
                    && stick.sable$getPlotPosition() != null;
            out.append(networked ? "sable" : "derived");

            Anchor anchor = ANCHORS.get(player.getId());
            if (anchor == null) {
                out.append("/none");
                continue;
            }

            out.append(String.format(java.util.Locale.ROOT,
                    "/move%.3f/phase%.2f/wander%.3f/worst%.3f/walk%.3f%s",
                    anchor.held.distanceTo(anchor.heldBefore),
                    PHASES[anchor.phase],
                    anchor.wander[anchor.phase],
                    worstWander(anchor),
                    anchor.excursion,
                    anchor.wander[anchor.phase] <= CLEAN ? "/clean" : ""));
        }

        return out.length() == 0 ? "-" : out.toString();
    }

    @Nullable
    private static Anchor anchorOf(Entity entity) {
        if (ANCHORS.isEmpty() || !ReplayCompat.inReplay()) return null;

        Anchor anchor = ANCHORS.get(entity.getId());
        return anchor == null || anchor.deck.isRemoved() ? null : anchor;
    }

    @Nullable
    private static SubLevel deckOf(Player player) {
        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(player);
        if (tracking != null && !tracking.isRemoved()) return tracking;

        return DeckFrame.byId(Minecraft.getInstance().level, ClientPlayerTilt.deckIdOf(player.getId()));
    }
}
