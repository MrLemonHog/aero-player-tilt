package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.utils.BootsSurface;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.TiltPolicy;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

public final class BootsController {
    private BootsController() {}

    private static final Quaterniond frame = new Quaterniond();
    private static final Quaterniond framePrev = new Quaterniond();

    private static final Quaterniond target = new Quaterniond();

    @Nullable private static Vector3d targetNormal;

    @Nullable private static ClientSubLevel deck;
    @Nullable private static UUID deckId;

    @Nullable private static Direction support;

    @Nullable private static Direction challenger;
    private static float challengeTicks;

    @Nullable private static Direction leaving;
    private static float leavingTicks;

    private static boolean attached;

    private static float missTicks;

    private static float airTicks;

    private static final float AIR_GRACE = 1f;

    private static boolean wasJumping;

    private static boolean waitForLanding;

    private static final double AIRBORNE_REACH = 2.0;

    private static final float MIN_HOLD_TICKS = 10f;

    private static final float GROUND_HOLD_TICKS = 2f;

    private static final float FACE_DWELL = 2f;

    private static final double LEAN_CAP = 0.30;

    private static final float LEAVING_TICKS = 15f;

    private static final double HOLD_MARGIN = Math.toRadians(5.0);

    private static final float STEEP_GRACE = 4f;

    private static float steepTicks;

    private static final double BASE_RATE = 14.0;

    private static double rate;

    private static final Quaterniond tracedFrame = new Quaterniond();
    private static final Quaterniond tracedPose = new Quaterniond();
    private static double tracedLocal;
    private static double tracedDeck;
    private static boolean traced;

    private static final double ANTIPODE = -0.9995;

    private static final double UPRIGHT = 0.7;

    private static double edge = BootsSurface.NO_EDGE;

    private static final Vector3d wereAt = new Vector3d();
    private static boolean placed;

    private static double slide;
    private static double idle;
    private static boolean idleWarned;

    private static final double IDLE_REPORT = 0.20;

    private static final double IDLE_SPEED = 0.03;

    public static boolean attached() {
        return attached;
    }

    @Nullable
    public static ClientSubLevel deck() {
        return deck != null && !deck.isRemoved() ? deck : null;
    }

    @Nullable
    public static UUID deckId() {
        return deckId;
    }

    public static boolean enabled() {
        if (!Config.isLoaded()) return false;

        return Config.flag(Config.MAGNETIC_BOOTS, false) || Config.flag(Config.GRIP_SLOPES, false);
    }

    public static boolean flips() {
        return Config.flag(Config.MAGNETIC_BOOTS, false);
    }

    public static void tick(LocalPlayer player) {
        boolean jumped = jumpPressed(player);

        if (!allowed(player)) {
            release();
            return;
        }

        if (attached && deck() == null) {
            release();
            return;
        }

        if (!flips()) {
            if (player.onGround()) airTicks = 0f;
            else airTicks += 1f;

            if (jumping(player) || airTicks > (attached ? AIR_GRACE : 0f)) {
                release();
                return;
            }
        }

        if (attached && jumped && flips() && !player.onGround()) {
            release();
            waitForLanding = true;
            return;
        }

        if (waitForLanding) {
            if (!player.onGround()) return;
            waitForLanding = false;
        }

        if (!attached) {
            dev.ryanhcode.sable.sublevel.SubLevel tracking =
                    dev.ryanhcode.sable.Sable.HELPER.getTrackingSubLevel(player);

            if (tracking == null || tracking.isRemoved()) return;
        }

        com.mlh.aero_player_tilt.client.debug.DebugRayRenderer.clear();

        BootsSurface.Contact contact = BootsSurface.probe(
                player, worldFrameNow(player), reach(player), flips(), attached ? deck : null);

        if (contact == null || contact.deck().isRemoved()) {
            if (!attached) return;
            if (!lose(player)) return;
        } else if (!settle(player, contact)) {
            return;
        }

        framePrev.set(frame);
        advance();
        publish();

        measureDrift(player);
    }

    private static void measureDrift(LocalPlayer player) {
        ClientSubLevel held = deck();
        if (!attached || held == null || support == null) {
            placed = false;
            return;
        }

        Vector3d now = held.logicalPose().transformPositionInverse(
                new Vector3d(player.getX(), player.getY(), player.getZ()));

        boolean pushing = Math.abs(player.xxa) > 1.0e-5f || Math.abs(player.zza) > 1.0e-5f;

        if (!placed) {
            wereAt.set(now);
            placed = true;
            return;
        }

        Vector3d moved = new Vector3d(now).sub(wereAt);
        wereAt.set(now);

        Vector3d normal = face(support);
        slide = new Vector3d(moved).fma(-moved.dot(normal), normal).length();

        if (pushing || !player.onGround() || player.isPassenger()) {
            idle = 0.0;
            idleWarned = false;
            return;
        }

        if (slide > IDLE_SPEED) {
            idle = 0.0;
            return;
        }

        idle += slide;

        if (idle > IDLE_REPORT && !idleWarned) {
            idleWarned = true;
            com.mlh.aero_player_tilt.client.debug.FrameTrace.mark("drift");
        }
    }

    private static boolean lose(LocalPlayer player) {
        missTicks += 1f;

        if (missTicks > holdTicks(player)) {
            release();
            return false;
        }

        return true;
    }

    private static boolean settle(LocalPlayer player, BootsSurface.Contact contact) {
        ClientSubLevel found = contact.deck();

        if (attached && deckId != null && !deckId.equals(found.getUniqueId())) {
            attached = false;
        }

        Predicate<Direction> allowedFace = faceFilter(found);
        Direction best = contact.best(allowedFace);

        if (best == null) {
            if (!attached) return false;

            if (!flips()) {
                steepTicks += 1f;
                if (steepTicks > STEEP_GRACE) {
                    release();
                    return false;
                }
            }

            return lose(player);
        }

        if (!attached) grab(player, found);

        edge = contact.edge();

        Direction on = settleFace(contact, best, allowedFace);
        if (on == null) return lose(player);

        missTicks = 0f;
        steepTicks = 0f;

        aimAt(lean(contact, on, allowedFace));
        return true;
    }

    private static Vector3d lean(BootsSurface.Contact contact, Direction on,
                                 Predicate<Direction> allowed) {
        if (leavingTicks > 0f) leavingTicks -= 1f;
        else leaving = null;

        Vector3d normal = face(on);

        double start = Config.value(Config.MAGNETIC_LEAN, 0.30);
        if (start <= 0.0) return normal;

        Vector3d pull = new Vector3d();
        for (Direction other : Direction.values()) {
            if (other == on) continue;
            if (!allowed.test(other)) continue;

            double edgeOver = contact.edgeOver(other);
            if (edgeOver >= start) continue;

            double weight = Math.min(1.0, (start - edgeOver) / start);

            if (other == leaving) weight *= 1.0 - leavingTicks / LEAVING_TICKS;

            if (weight <= 0.0) continue;

            pull.fma(weight, face(other));
        }

        pull.fma(-pull.dot(normal), normal);

        double reached = pull.length();
        if (reached <= 1.0e-3) return normal;

        double lean = LEAN_CAP * Math.min(1.0, reached);

        pull.div(reached);

        return normal.mul(1.0 - lean).fma(lean, pull).normalize();
    }

    private static Predicate<Direction> faceFilter(ClientSubLevel found) {
        if (flips() || PlayerTilt.anyFace()) return direction -> true;

        Quaterniond pose = new Quaterniond(found.logicalPose().orientation());

        double limit = Math.min(1.0, Math.max(-1.0, PlayerTilt.walkableNormalY()));
        double keep = attached
                ? Math.cos(Math.min(Math.PI, Math.acos(limit) + HOLD_MARGIN))
                : limit;

        return direction -> pose.transform(face(direction)).normalize().y >= keep;
    }

    @Nullable
    private static Direction settleFace(BootsSurface.Contact contact, Direction best,
                                        Predicate<Direction> allowed) {
        if (support == null) {
            commit(best);
            return support;
        }

        if (!allowed.test(support) || contact.weight(support) <= 0.0) {
            if (!flips()) return null;

            commit(best);
            return support;
        }

        if (!flips()) return support;

        if (contact.centre() == support) {
            challenger = null;
            challengeTicks = 0f;
            return support;
        }

        if (best == support
                || contact.weight(best) <= contact.weight(support) + BootsSurface.FACE_MARGIN) {
            challenger = null;
            challengeTicks = 0f;
            return support;
        }

        if (best != challenger) {
            challenger = best;
            challengeTicks = 0f;
        }

        challengeTicks += 1f;
        if (challengeTicks < FACE_DWELL) return support;

        commit(best);
        return support;
    }

    private static void commit(Direction face) {
        if (support != null && support != face) {
            leaving = support;
            leavingTicks = LEAVING_TICKS;
        }

        support = face;
        challenger = null;
        challengeTicks = 0f;
    }

    @Nullable
    public static Vector3d supportUp() {
        ClientSubLevel held = deck();
        if (!attached || held == null || support == null) return null;

        return new Quaterniond(held.logicalPose().orientation())
                .transform(face(support)).normalize();
    }

    private static Vector3d face(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static void publish() {
        ClientSubLevel held = deck();
        if (!attached || held == null) return;

        Quaterniond pose = new Quaterniond(held.logicalPose().orientation());
        Quaterniond world = new Quaterniond(pose).mul(frame).normalize();

        BodyTiltController.driveFromBoots(
                new Quaternionf((float) world.x, (float) world.y, (float) world.z, (float) world.w),
                new Quaternionf((float) pose.x, (float) pose.y, (float) pose.z, (float) pose.w),
                held.getUniqueId());
    }

    public static boolean render(float partialTick) {
        if (!attached) return false;

        ClientSubLevel held = deck();
        if (held == null) {
            release();
            return false;
        }

        Quaterniond shown = new Quaterniond(framePrev).slerp(frame, partialTick).normalize();

        Quaterniond pose = new Quaterniond(held.renderPose(partialTick).orientation());
        Quaterniond world = new Quaterniond(pose).mul(shown).normalize();

        note(pose, shown);

        BodyTiltController.driveFromBoots(
                new Quaternionf((float) world.x, (float) world.y, (float) world.z, (float) world.w),
                new Quaternionf((float) pose.x, (float) pose.y, (float) pose.z, (float) pose.w),
                held.getUniqueId());

        return true;
    }

    private static void note(Quaterniond pose, Quaterniond shown) {
        if (traced) {
            tracedDeck = between(tracedPose, pose);
            tracedLocal = between(tracedFrame, shown);
        }

        tracedPose.set(pose);
        tracedFrame.set(shown);
        traced = true;
    }

    private static double between(Quaterniond from, Quaterniond to) {
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0,
                Math.abs(new Quaterniond(from).conjugate().mul(to).normalize().w))));
    }

    public static String trace() {
        if (!attached) return "off";

        return String.format(Locale.ROOT,
                "%s rate=%.2f gap=%.2f lean=%.1f edge=%s deck=%.3f local=%.3f"
                        + " slide=%.5f idle=%.3f",
                support == null ? "?" : support.getName(),
                rate,
                between(frame, target),
                leanDegrees(),
                edge == BootsSurface.NO_EDGE ? "-" : String.format(Locale.ROOT, "%.3f", edge),
                tracedDeck, tracedLocal, slide, idle);
    }

    private static double leanDegrees() {
        Vector3d body = up();
        Vector3d hold = supportUp();
        if (body == null || hold == null) return 0.0;

        return Math.toDegrees(Math.acos(Math.min(1.0, Math.max(-1.0, body.dot(hold)))));
    }

    public static Quaterniond worldFrameNow(LocalPlayer player) {
        ClientSubLevel held = deck();

        if (!attached || held == null) {
            return new Quaterniond(BodyTiltController.getRawTilt());
        }

        return new Quaterniond(held.logicalPose().orientation()).mul(frame).normalize();
    }

    public static void carry(LocalPlayer player) {
        if (!attached) return;

        ClientSubLevel held = deck();
        if (held == null) return;

        Vector3d up = supportUp();
        if (up == null) up = up();
        if (up == null) return;

        if (up.y < UPRIGHT) player.resetFallDistance();

        if (!player.onGround() || player.getVehicle() != null) return;

        Quaterniond delta = new Quaterniond(held.logicalPose().orientation())
                .mul(new Quaterniond(held.lastPose().orientation()).conjugate())
                .normalize();

        net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();
        Vector3d velocity = new Vector3d(motion.x, motion.y, motion.z);

        Vector3d was = new Quaterniond(delta).conjugate().transform(new Vector3d(up));

        double into = velocity.dot(was);
        Vector3d stride = new Vector3d(velocity).fma(-into, was);

        double strength = Config.value(Config.DECK_MOMENTUM, 1.0);
        if (strength > 0.0) {
            (strength == 1.0 ? delta : new Quaterniond().slerp(delta, strength).normalize())
                    .transform(stride);
        }

        stride.fma(-stride.dot(up), up).fma(into, up);

        player.setDeltaMovement(stride.x, stride.y, stride.z);
    }

    @Nullable
    public static Vector3d up() {
        ClientSubLevel held = deck();
        if (!attached || held == null) return null;

        return new Quaterniond(held.logicalPose().orientation()).mul(frame)
                .transform(new Vector3d(0.0, 1.0, 0.0)).normalize();
    }

    public static void release() {
        boolean held = attached;

        forget();

        if (held) BodyTiltController.bootsReleased();
    }

    public static void forget() {
        attached = false;
        deck = null;
        deckId = null;
        targetNormal = null;
        support = null;
        challenger = null;
        challengeTicks = 0f;
        leaving = null;
        leavingTicks = 0f;
        missTicks = 0f;
        airTicks = 0f;
        steepTicks = 0f;
        edge = BootsSurface.NO_EDGE;
        rate = 0.0;
        traced = false;
        placed = false;
        slide = 0.0;
        idle = 0.0;
        idleWarned = false;
        waitForLanding = false;
        frame.identity();
        framePrev.identity();
        target.identity();
    }

    private static void grab(LocalPlayer player, ClientSubLevel found) {
        deck = found;
        deckId = found.getUniqueId();
        attached = true;
        missTicks = 0f;
        steepTicks = 0f;
        placed = false;
        idle = 0.0;
        idleWarned = false;
        targetNormal = null;
        support = null;
        challenger = null;
        challengeTicks = 0f;
        leaving = null;
        leavingTicks = 0f;

        Quaterniond pose = new Quaterniond(found.logicalPose().orientation());
        Quaterniond current = new Quaterniond(BodyTiltController.getRawTilt());

        frame.set(pose.conjugate().mul(current)).normalize();
        framePrev.set(frame);
        target.set(frame);
        rate = 0.0;
    }

    private static void aimAt(Vector3d normalLocal) {
        if (targetNormal == null) {
            targetNormal = new Vector3d(normalLocal);

            Vector3d up = target.transform(new Vector3d(0.0, 1.0, 0.0)).normalize();
            target.premul(transport(up, normalLocal, target)).normalize();
            return;
        }

        if (targetNormal.dot(normalLocal) > 0.999999) return;

        target.premul(transport(targetNormal, normalLocal, target)).normalize();
        targetNormal.set(normalLocal);
    }

    private static Quaterniond transport(Vector3d from, Vector3d to, Quaterniond current) {
        if (from.dot(to) < ANTIPODE) {
            Vector3d axis = current.transform(new Vector3d(0.0, 0.0, 1.0)).normalize();
            return new Quaterniond().fromAxisAngleRad(axis.x, axis.y, axis.z, Math.PI);
        }

        return new Quaterniond().rotationTo(from.x, from.y, from.z, to.x, to.y, to.z);
    }

    private static void advance() {
        double smooth = Config.value(Config.MAGNETIC_SMOOTH, 1.0);

        if (smooth <= 0.0) {
            frame.set(target);
            rate = 0.0;
            return;
        }

        double gap = between(frame, target);
        if (gap < 1.0e-4) {
            rate = 0.0;
            return;
        }

        double chase = 1.0 - Math.pow(0.5, 1.0 / smooth);
        double accel = 1.0 - Math.pow(0.5, 1.0 / (smooth * 2.0));

        double wanted = Math.min(BASE_RATE / smooth, gap * chase);
        rate += (wanted - rate) * accel;

        double turn = Math.min(rate, gap * chase);
        if (turn <= 0.0) return;

        frame.slerp(target, turn / gap).normalize();
    }

    private static boolean jumping(LocalPlayer player) {
        return player.input != null && player.input.jumping;
    }

    private static boolean jumpPressed(LocalPlayer player) {
        boolean held = jumping(player);
        boolean pressed = held && !wasJumping;
        wasJumping = held;
        return pressed;
    }

    private static double reach(LocalPlayer player) {
        double base = Config.value(Config.MAGNETIC_REACH, 0.75);
        return player.onGround() ? base : base + AIRBORNE_REACH;
    }

    private static float holdTicks(LocalPlayer player) {
        if (!flips() || player.onGround()) {
            return Math.max(GROUND_HOLD_TICKS, (float) Config.value(Config.HOLD_TICKS, 5.0));
        }

        return Math.max(MIN_HOLD_TICKS, (float) Config.value(Config.AIRBORNE_HOLD_TICKS, 60.0));
    }

    private static boolean allowed(@Nullable LocalPlayer player) {
        if (player == null || !enabled()) return false;
        if (!TiltPolicy.playerTilt()) return false;
        if (!BodyTiltController.shouldComputeTilt(player)) return false;

        if (player.isSpectator() || player.isSleeping() || player.isPassenger()) return false;
        if (player.getAbilities().flying || player.isFallFlying()) return false;

        return true;
    }

    @Nullable
    public static ClientSubLevel standing() {
        return attached ? deck() : null;
    }

    public static String debug() {
        if (!attached) {
            if (!enabled()) return "-";
            return waitForLanding ? "let go" : "off";
        }

        Vector3d up = up();

        Vector3d hold = supportUp();

        return String.format(Locale.ROOT, "%s/%s%s/lean %.0f/edge %s/drift %.2f",
                flips() ? "boots" : "grip",
                support == null ? "?" : support.getName(),
                rate > 1.0 ? "*" : "",
                up == null || hold == null ? 0.0
                        : Math.toDegrees(Math.acos(Math.min(1.0, up.dot(hold)))),
                edge == BootsSurface.NO_EDGE
                        ? "-" : String.format(Locale.ROOT, "%.2f", edge),
                idle);
    }
}
