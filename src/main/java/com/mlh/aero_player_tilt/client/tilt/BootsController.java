package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.api.PlayerTiltApi;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.utils.BootsSurface;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import com.mlh.aero_player_tilt.tilt.TiltPolicy;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    private static boolean leapt;

    private static boolean adrift;

    private static final double KEEP_RANGE = 8.0;

    private static final double MAGNET_RANGE = 2.5;

    private record Rim(Direction from, double level, double[] lines) {
        static Rim of(Direction from, double level) {
            double[] lines = new double[6];
            java.util.Arrays.fill(lines, Double.NaN);
            return new Rim(from, level, lines);
        }

        boolean empty() {
            for (double line : lines) if (!Double.isNaN(line)) return false;
            return true;
        }

        double past(Vector3d feet, Direction to) {
            double line = lines[to.get3DDataValue()];
            return Double.isNaN(line) ? Double.NEGATIVE_INFINITY : along(feet, to) - line;
        }

        boolean beyond(Vector3d feet) {
            for (Direction to : Direction.values()) if (past(feet, to) > 0.0) return true;
            return false;
        }

        @Nullable
        Direction ahead(Vector3d feet) {
            Direction best = null;
            double most = 0.0;
            for (Direction to : Direction.values()) {
                double past = past(feet, to);
                if (past > most) {
                    most = past;
                    best = to;
                }
            }
            return best;
        }

        Vector3d lean(Vector3d feet, double radius, boolean hold) {
            double above = along(feet, from) - level;
            if (hold && !beyond(feet)) above = Math.min(above, 0.0);

            Vector3d lean = face(from).mul(Math.max(0.0, above + radius));
            for (Direction to : Direction.values()) {
                double past = past(feet, to);
                if (past + radius > 0.0) lean.fma(past + radius, face(to));
            }
            return lean;
        }

        double off(Vector3d feet, double radius) {
            return lean(feet, radius, false).length();
        }

        Rim flip(Direction to) {
            Rim flipped = of(to, lines[to.get3DDataValue()]);
            for (Direction other : Direction.values()) {
                if (other.getAxis() == to.getAxis()) continue;
                flipped.lines[other.get3DDataValue()] = lines[other.get3DDataValue()];
            }
            flipped.lines[from.get3DDataValue()] = level;
            return flipped;
        }
    }

    @Nullable private static Rim rim;

    private static float rolling;

    private static String rolled = "-";

    @Nullable private static Vector3d gap;

    private static final float ROLL_SETTLE = 10f;

    private static final double ROLL_SWITCH = Math.toRadians(48.0);

    private static final double MIN_ROLL = 0.05;

    private static final double FLOOR_DEPTH = 0.05;


    private static final double CLEAR = 2.0;

    private static final double TOUCH = 0.15;

    private static final double AIRBORNE_REACH = 2.0;

    private static final float MIN_HOLD_TICKS = 10f;

    private static final float GROUND_HOLD_TICKS = 2f;

    private static final float FACE_DWELL = 2f;

    private static final double LEAN_CAP = 0.30;

    private static final float LEAVING_TICKS = 15f;

    private static final double HOLD_MARGIN = Math.toRadians(5.0);

    private static final float STEEP_GRACE = 4f;

    private static float steepTicks;

    private static final double BASE_RATE = 30.0;

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

    public static boolean adrift() {
        return adrift && !attached;
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

        return flips() || (ClientPlayerTilt.settings().sticky()
                && TiltPolicy.allowsStickyTilt()
                && TiltPolicy.minNormalY() < 0.8);
    }

    public static boolean flips() {
        return (Config.flag(Config.MAGNETIC_BOOTS, false) || worn != null) && TiltPolicy.allowsMagneticBoots();
    }

    @Nullable
    private static PlayerTiltApi.BootsSettings worn;

    private static PlayerTiltApi.BootsSettings settings = PlayerTiltApi.BootsSettings.DEFAULT;

    private static PlayerTiltApi.BootsSettings configured() {
        return new PlayerTiltApi.BootsSettings(
                Config.value(Config.MAGNETIC_REACH, 0.75),
                Config.value(Config.MAGNETIC_LEAN, 0.30),
                Config.value(Config.MAGNETIC_DROP, 3.0),
                Config.value(Config.MAGNETIC_PULL, 1.0),
                Config.value(Config.MAGNETIC_SMOOTH, 1.0));
    }

    public static void tick(LocalPlayer player) {
        worn = com.mlh.aero_player_tilt.apiimpl.ApiRegistry.bootsFor(player);
        settings = worn != null ? worn : configured();
        com.mlh.aero_player_tilt.tilt.Boots.grip(settings.grip());

        boolean jumped = jumpPressed(player);

        if (player.onGround()) {
            leapt = attached && jumping(player);
            adrift = false;
        }

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

        if (attached && jumped && leapt && flips() && !player.onGround()) {
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
        roomToTurn(player);
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

        if (missTicks > holdTicks(player)
                || (flips() && !player.onGround() && !anchored(player))) {
            release();
            adrift = !player.onGround();
            return false;
        }

        return true;
    }

    private static boolean anchored(LocalPlayer player) {
        ClientSubLevel held = deck();
        if (held == null || support == null) return false;

        double size = com.mlh.aero_player_tilt.client.utils.BodyScale.of(player);
        Quaterniond pose = new Quaterniond(held.logicalPose().orientation());

        Vec3 feet = player.position();
        Vector3d up = up();
        double half = player.getBbHeight() * 0.5;
        Vec3 middle = up == null ? player.getBoundingBox().getCenter()
                : feet.add(up.x * half, up.y * half, up.z * half);

        if (towards(player, held, pose, feet, middle, support, KEEP_RANGE * size) < NO_HIT) {
            return true;
        }

        Direction nearest = null;
        double best = MAGNET_RANGE * size;

        for (Direction face : Direction.values()) {
            if (face == support) continue;

            double distance = towards(player, held, pose, feet, middle, face, best);
            if (distance < best && canStand(player, face)) {
                best = distance;
                nearest = face;
            }
        }

        if (nearest == null) return false;

        commit(nearest);
        aimAt(face(nearest));

        Vector3d out = pose.transform(face(nearest)).normalize();
        Vec3 motion = player.getDeltaMovement();
        double away = motion.x * out.x + motion.y * out.y + motion.z * out.z;
        if (away > 0.0) player.setDeltaMovement(motion.subtract(out.x * away, out.y * away, out.z * away));

        return true;
    }

    private static final double NO_HIT = Double.MAX_VALUE;

    private static double towards(LocalPlayer player, ClientSubLevel held, Quaterniond pose,
                                  Vec3 feet, Vec3 middle, Direction face, double range) {
        Vector3d in = pose.transform(face(face)).normalize().negate();

        return Math.min(cast(player, held, feet, in, face, range),
                cast(player, held, middle, in, face, range));
    }

    private static double cast(LocalPlayer player, ClientSubLevel held, Vec3 from, Vector3d in,
                               Direction face, double range) {
        Vec3 to = from.add(in.x * range, in.y * range, in.z * range);

        BlockHitResult hit = player.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != face) return NO_HIT;

        dev.ryanhcode.sable.sublevel.SubLevel space =
                dev.ryanhcode.sable.Sable.HELPER.getContaining(player.level(), hit.getLocation());
        if (space == null || !held.getUniqueId().equals(space.getUniqueId())) return NO_HIT;
        if (!com.mlh.aero_player_tilt.apiimpl.ApiRegistry.bootsGrip(player,
                player.level().getBlockState(hit.getBlockPos()), hit.getBlockPos(), face)) {
            return NO_HIT;
        }

        Vector3d landed = held.logicalPose().transformPosition(new Vector3d(
                hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));

        return landed.distance(from.x, from.y, from.z);
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

        Vector3d rolled = flips() && support != null ? roll(player) : null;
        if (rolled != null) {
            missTicks = 0f;
            steepTicks = 0f;
            aimAt(rolled);
            return true;
        }

        rim = null;
        gap = null;

        Direction on = settleFace(player, contact, best, allowedFace);
        if (on == null) return lose(player);

        missTicks = 0f;
        steepTicks = 0f;

        aimAt(lean(contact, on, allowedFace,
                com.mlh.aero_player_tilt.client.utils.BodyScale.of(player)));
        return true;
    }

    @Nullable
    private static Vector3d roll(LocalPlayer player) {
        ClientSubLevel held = deck();
        if (held == null || support == null) return null;

        Level plot = held.getLevel();
        double scale = Math.max(1.0e-3, held.logicalPose().scale().x());
        double size = com.mlh.aero_player_tilt.client.utils.BodyScale.of(player) / scale;
        double radius = Math.max(MIN_ROLL, settings.leanStart() * size);

        Vector3d feet = held.logicalPose().transformPositionInverse(
                new Vector3d(player.getX(), player.getY(), player.getZ()));

        Vector3d n = face(support);
        boolean beyond = rim != null && rim.from() == support && rim.beyond(feet);
        double drop = settings.ledgeHeight() * size;
        AABB floor = floorUnder(plot, feet, n, beyond ? TOUCH : Math.max(CLEAR * size, drop + 1.5 * size));

        if (floor != null) {
            double level = reach(floor, support);
            Vector3d base = new Vector3d(feet).fma(level - along(feet, support), n);
            Rim found = Rim.of(support, level);

            for (Direction to : Direction.values()) {
                if (to.getAxis() == support.getAxis()) continue;
                found.lines()[to.get3DDataValue()] =
                        rimAhead(plot, base, floor, support, to, radius, drop + FLOOR_DEPTH);
            }

            rim = found.empty() ? null : found;
            gap = null;
            rolled = "face";
            if (rim == null) return n;
        } else if (rim == null || rim.from() != support || rim.off(feet, radius) > radius * 3.0 + 1.0) {
            gap = null;
            rolled = "lost";
            return null;
        } else {
            rolled = "rim";
        }

        rolling = player.onGround() ? 0f : Math.max(0f, rolling - 1f);
        boolean hold = rolling <= 0f;

        gap = null;

        Vector3d lean = rim.lean(feet, radius, hold);
        if (lean.lengthSquared() < 1.0e-12) return n;
        lean.normalize();

        if (lean.dot(n) < Math.cos(ROLL_SWITCH)) {
            Direction to = rim.ahead(feet);

            if (to != null && fits(player, held, plot, feet, to)) {
                turnMotion(player, held, support, to);
                commit(to);
                rim = rim.flip(to);
                rolling = ROLL_SETTLE;
                rolled = "turn";
                gap = null;
                return rim.lean(feet, radius, false).normalize();
            }

            rolled = to == null ? "cap" : "blocked";
            if (to != null && player.onGround()) {
                rim = null;
                gap = null;
                return null;
            }

            gap = floor == null && to != null ? new Vector3d(lean) : null;

            Vector3d side = new Vector3d(lean).fma(-lean.dot(n), n);
            if (side.lengthSquared() < 1.0e-12) return n;
            side.normalize();
            return new Vector3d(n).mul(Math.cos(ROLL_SWITCH)).fma(Math.sin(ROLL_SWITCH), side);
        }

        return lean;
    }

    @Nullable
    private static AABB floorUnder(Level plot, Vector3d feet, Vector3d up, double depth) {
        Vector3d at = new Vector3d();

        for (double down = FLOOR_DEPTH; down <= depth; down += 0.0625) {
            AABB box = box(plot, at.set(feet).fma(-down, up));
            if (box != null) return box;
        }

        return null;
    }

    private static boolean canStand(LocalPlayer player, Direction face) {
        ClientSubLevel held = deck();
        if (held == null) return true;

        Vector3d feet = held.logicalPose().transformPositionInverse(
                new Vector3d(player.getX(), player.getY(), player.getZ()));
        return fits(player, held, held.getLevel(), feet, face);
    }

    private static boolean fits(LocalPlayer player, ClientSubLevel held, Level plot, Vector3d feet,
                                Direction face) {
        double scale = Math.max(1.0e-3, held.logicalPose().scale().x());
        double height = player.getBbHeight() / scale;
        double half = player.getBbWidth() * 0.45 / scale;

        double[] min = {feet.x - half, feet.y - half, feet.z - half};
        double[] max = {feet.x + half, feet.y + half, feet.z + half};

        int axis = face.getAxis().ordinal();
        double base = axis == 0 ? feet.x : axis == 1 ? feet.y : feet.z;
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        min[axis] = positive ? base + 0.02 : base - height;
        max[axis] = positive ? base + height : base - 0.02;

        AABB room = new AABB(min[0], min[1], min[2], max[0], max[1], max[2]);

        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(room.minX, room.minY, room.minZ),
                BlockPos.containing(room.maxX, room.maxY, room.maxZ))) {
            VoxelShape shape = plot.getBlockState(pos).getCollisionShape(plot, pos);
            if (shape.isEmpty()) continue;

            for (AABB part : shape.toAabbs()) {
                if (part.move(pos).intersects(room)) return false;
            }
        }

        Vector3d up = new Quaterniond(held.logicalPose().orientation()).transform(face(face)).normalize();
        Vector3d world = held.logicalPose().transformPosition(new Vector3d(feet)).fma(0.02 * scale, up);
        return com.mlh.aero_player_tilt.tilt.TiltedPlayerBox.clearOfWorld(player, world, up,
                player.getBbWidth() * 0.9, player.getBbHeight() - 0.02 * scale);
    }

    private static void turnMotion(LocalPlayer player, ClientSubLevel held, Direction from, Direction to) {
        Quaterniond pose = new Quaterniond(held.logicalPose().orientation());
        Vector3d was = pose.transform(face(from)).normalize();
        Vector3d now = pose.transform(face(to)).normalize();

        Vec3 motion = player.getDeltaMovement();
        Vector3d velocity = new Vector3d(motion.x, motion.y, motion.z);
        double intoWas = velocity.dot(was);
        if (intoWas < 0.0) velocity.fma(-intoWas, was);
        double intoNow = velocity.dot(now);
        if (intoNow < 0.0) velocity.fma(-intoNow, now);
        double press = Math.max(0.0, -Math.min(intoWas, intoNow));

        Vector3d turned = new Quaterniond().rotationTo(was, now).transform(velocity).fma(-press, now);

        player.setDeltaMovement(turned.x, turned.y, turned.z);
    }

    private static double rimAhead(Level plot, Vector3d feet, AABB floor, Direction on, Direction to,
                                   double radius, double drop) {
        double level = reach(floor, on);
        double start = along(feet, to);
        Vector3d probe = new Vector3d(feet).fma(-FLOOR_DEPTH, face(on));

        AABB box = floor;
        for (int step = 0; step < 8; step++) {
            double line = reach(box, to);
            if (line - start > radius) return Double.NaN;

            probe.fma(line - along(probe, to) + FLOOR_DEPTH * 0.5, face(to));

            AABB next = box(plot, probe);
            if (next == null) {
                Vector3d over = new Vector3d(probe).fma(FLOOR_DEPTH * 2.0, face(on));
                if (box(plot, over) != null) return Double.NaN;
                return floorBelow(plot, probe, face(on), drop) ? Double.NaN : line;
            }

            if (reach(next, on) > level + 1.0e-3) return Double.NaN;
            box = next;
        }

        return Double.NaN;
    }

    private static boolean floorBelow(Level plot, Vector3d from, Vector3d up, double depth) {
        double step = Math.max(0.125, depth / 32.0);
        Vector3d at = new Vector3d();

        for (double down = 0.0; down <= depth; down += step) {
            if (box(plot, at.set(from).fma(-down, up)) != null) return true;
        }

        return false;
    }

    @Nullable
    private static AABB box(Level plot, Vector3d at) {
        BlockPos pos = BlockPos.containing(at.x, at.y, at.z);
        VoxelShape shape = plot.getBlockState(pos).getCollisionShape(plot, pos);
        if (shape.isEmpty()) return null;

        AABB box = shape.bounds().move(pos);
        return box.minX <= at.x && at.x <= box.maxX
                && box.minY <= at.y && at.y <= box.maxY
                && box.minZ <= at.z && at.z <= box.maxZ ? box : null;
    }

    private static double reach(AABB box, Direction to) {
        return to.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.max(to.getAxis()) : -box.min(to.getAxis());
    }

    private static double along(Vector3d at, Direction to) {
        return at.x * to.getStepX() + at.y * to.getStepY() + at.z * to.getStepZ();
    }

    private static Vector3d lean(BootsSurface.Contact contact, Direction on,
                                 Predicate<Direction> allowed, double size) {
        if (leavingTicks > 0f) leavingTicks -= 1f;
        else leaving = null;

        Vector3d normal = face(on);

        double start = settings.leanStart() * size;
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
    private static Direction settleFace(LocalPlayer player, BootsSurface.Contact contact, Direction best,
                                        Predicate<Direction> allowed) {
        if (support == null) {
            commit(best);
            return support;
        }

        if (!allowed.test(support) || contact.weight(support) <= 0.0) {
            if (!flips()) return null;

            if (canStand(player, best)) commit(best);
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
        if (challengeTicks < FACE_DWELL || !canStand(player, best)) return support;

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
    public static Vector3d pullUp() {
        ClientSubLevel held = deck();
        if (!attached || held == null || gap == null) return supportUp();

        return new Quaterniond(held.logicalPose().orientation()).transform(new Vector3d(gap)).normalize();
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
        com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.hold(held, partialTick);
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
                "%s roll=%s rate=%.2f gap=%.2f lean=%.1f edge=%s deck=%.3f local=%.3f"
                        + " slide=%.5f idle=%.3f",
                support == null ? "?" : support.getName(), rolled,
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
            return com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.steadyTilt(
                    new Quaterniond(BodyTiltController.getRawTilt()));
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

    public static void letGo() {
        release();
        waitForLanding = true;
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
        leapt = false;
        rim = null;
        gap = null;
        rolling = 0f;
        frame.identity();
        framePrev.identity();
        target.identity();
    }

    private static void grab(LocalPlayer player, ClientSubLevel found) {
        deck = found;
        deckId = found.getUniqueId();
        attached = true;
        adrift = false;
        missTicks = 0f;
        steepTicks = 0f;
        placed = false;
        idle = 0.0;
        idleWarned = false;
        targetNormal = null;
        support = null;
        rim = null;
        gap = null;
        challenger = null;
        challengeTicks = 0f;
        leaving = null;
        leavingTicks = 0f;

        Quaterniond pose = new Quaterniond(found.logicalPose().orientation());
        Quaterniond current = com.mlh.aero_player_tilt.client.compat.PhysicsModCompat.steadyTilt(
                new Quaterniond(BodyTiltController.getRawTilt()));

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
        double smooth = settings.turnTime();

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

    private static void roomToTurn(LocalPlayer player) {
        ClientSubLevel held = deck();
        if (held == null || frame.equals(framePrev, 1.0e-9)) return;

        Quaterniond pose = new Quaterniond(held.logicalPose().orientation());
        double before = com.mlh.aero_player_tilt.tilt.TiltedPlayerBox.worldDepth(
                player, new Quaterniond(pose).mul(framePrev), held);
        if (com.mlh.aero_player_tilt.tilt.TiltedPlayerBox.worldDepth(
                player, new Quaterniond(pose).mul(frame), held) <= before + 1.0e-4) return;

        Quaterniond wanted = new Quaterniond(frame);
        double low = 0.0;
        double high = 1.0;
        for (int i = 0; i < 5; i++) {
            double mid = (low + high) / 2.0;
            Quaterniond tried = new Quaterniond(framePrev).slerp(wanted, mid).normalize();
            if (com.mlh.aero_player_tilt.tilt.TiltedPlayerBox.worldDepth(
                    player, new Quaterniond(pose).mul(tried), held) <= before + 1.0e-4) low = mid;
            else high = mid;
        }

        frame.set(framePrev).slerp(wanted, low).normalize();
        rate = 0.0;
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
        double base = settings.reach();
        double reach = player.onGround() ? base : base + AIRBORNE_REACH;
        return reach * com.mlh.aero_player_tilt.client.utils.BodyScale.of(player);
    }

    private static float holdTicks(LocalPlayer player) {
        if (!flips() || player.onGround()) {
            return Math.max(GROUND_HOLD_TICKS, (float) ClientPlayerTilt.settings().holdTicks());
        }

        return Math.max(MIN_HOLD_TICKS, (float) ClientPlayerTilt.settings().airborneHoldTicks());
    }

    private static boolean allowed(@Nullable LocalPlayer player) {
        if (player == null || !enabled()) return false;
        if (!TiltPolicy.playerTilt()) return false;
        if (!BodyTiltController.shouldComputeTilt(player)) return false;
        if (ClientPlayerTilt.apiDrivesBody()) return false;

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
            return waitForLanding ? "let go" : adrift ? "adrift" : "off";
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
