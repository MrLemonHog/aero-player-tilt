package com.mlh.aero_player_tilt.client.tilt;

import com.mlh.aero_player_tilt.AeroPlayerTilt;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.tilt.PlayerTilt;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public final class TiltDiagnostics {
    private TiltDiagnostics() {}

    private static int ticks;

    private static final StringBuilder FITS = new StringBuilder();

    private static final StringBuilder MTVS = new StringBuilder();

    private static final StringBuilder INSIDES = new StringBuilder();

    private static final StringBuilder CLIMBS = new StringBuilder();

    private static volatile String sableReason = "-";
    private static volatile double sableAngle;
    private static final java.util.concurrent.atomic.AtomicInteger SABLE_CALLS =
            new java.util.concurrent.atomic.AtomicInteger();

    public static void recordSableOrientation(String reason, double angleDegrees) {
        if (!Config.isLoaded() || !Config.DEBUG_MESSAGES.get()) return;
        sableReason = reason;
        sableAngle = angleDegrees;
        SABLE_CALLS.incrementAndGet();
    }

    private static String takeSableDecision() {
        return sableReason + "/" + fmt(sableAngle) + "/" + SABLE_CALLS.getAndSet(0);
    }

    private static volatile String lightProbe = "-";

    public static void recordLightProbe(net.minecraft.world.entity.Entity entity,
                                        Vec3 upright, Vec3 head, Vec3 chosen, int step) {
        if (!Config.isLoaded() || !Config.DEBUG_MESSAGES.get()) return;
        if (!(entity instanceof LocalPlayer)) return;

        net.minecraft.world.level.Level level = entity.level();
        net.minecraft.core.BlockPos worldPos = net.minecraft.core.BlockPos.containing(chosen);

        StringBuilder line = new StringBuilder();
        line.append("step=").append(step)
                .append(" lean=").append(fmt(head.subtract(upright).horizontalDistance()))
                .append(" at=").append(worldPos.getX()).append(',')
                .append(worldPos.getY()).append(',').append(worldPos.getZ())
                .append(" world=")
                .append(level.getBlockState(worldPos).isSolidRender(level, worldPos) ? "SOLID" : "air")
                .append('/').append(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, worldPos))
                .append('/').append(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, worldPos));

        Vector3d probe = new Vector3d(chosen.x, chosen.y, chosen.z);
        Vector3d local = new Vector3d();

        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(
                level, new dev.ryanhcode.sable.companion.math.BoundingBox3d(worldPos))) {
            if (!(subLevel instanceof ClientSubLevel clientSubLevel)) continue;

            clientSubLevel.renderPose().transformPositionInverse(probe, local);
            net.minecraft.core.BlockPos localPos =
                    net.minecraft.core.BlockPos.containing(local.x, local.y, local.z);

            net.minecraft.world.level.Level subLevelLevel = subLevel.getLevel();
            int subBlock = subLevelLevel.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, localPos);
            int subSky = subLevelLevel.getBrightness(net.minecraft.world.level.LightLayer.SKY, localPos);

            line.append(" sub=")
                    .append(subLevelLevel.getBlockState(localPos).isSolidRender(subLevelLevel, localPos)
                            ? "SOLID" : "air")
                    .append('/').append(subBlock)
                    .append('/').append(subSky)
                    .append('/').append(clientSubLevel.scaleSkyLight(subSky));
        }

        net.minecraft.core.BlockPos headPos = net.minecraft.core.BlockPos.containing(head);
        Vector3d headCentre = new Vector3d(
                headPos.getX() + 0.5, headPos.getY() + 0.5, headPos.getZ() + 0.5);
        Vector3d headLocal = new Vector3d();

        for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(
                level, new dev.ryanhcode.sable.companion.math.BoundingBox3d(headPos))) {
            if (!(subLevel instanceof ClientSubLevel clientSubLevel)) continue;

            clientSubLevel.renderPose().transformPositionInverse(headCentre, headLocal);
            net.minecraft.core.BlockPos localPos =
                    net.minecraft.core.BlockPos.containing(headLocal.x, headLocal.y, headLocal.z);

            net.minecraft.world.level.Level subLevelLevel = subLevel.getLevel();
            int subSky = subLevelLevel.getBrightness(net.minecraft.world.level.LightLayer.SKY, localPos);

            line.append(" headCentre=")
                    .append(subLevelLevel.getBlockState(localPos).isSolidRender(subLevelLevel, localPos)
                            ? "SOLID" : "air")
                    .append('/').append(subLevelLevel.getBrightness(
                            net.minecraft.world.level.LightLayer.BLOCK, localPos))
                    .append('/').append(subSky)
                    .append('/').append(clientSubLevel.scaleSkyLight(subSky));
        }

        Vec3 eye = entity.getEyePosition(1.0f);
        Vec3 rebased = Sable.HELPER.getEyePositionInterpolated(entity, 1.0f)
                .add(chosen.subtract(eye));
        net.minecraft.core.BlockPos rebasedPos = net.minecraft.core.BlockPos.containing(rebased);

        line.append(" sable=").append(rebasedPos.getX()).append(',')
                .append(rebasedPos.getY()).append(',').append(rebasedPos.getZ())
                .append('/').append(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, rebasedPos))
                .append('/').append(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, rebasedPos))
                .append(" off=").append(fmt(rebased.distanceTo(chosen)));

        lightProbe = line.toString();
    }

    public static void recordMtv(String branch, double x, double y, double z) {
        if (!Config.isLoaded()) return;
        if (!Config.DEBUG_MESSAGES.get()
                && !com.mlh.aero_player_tilt.client.debug.FallTrace.enabled()) return;

        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0e-9) return;

        String entry = branch + ":" + fmt(x / length) + "/" + fmt(y / length) + "/" + fmt(z / length)
                + "@" + String.format(java.util.Locale.ROOT, "%.4f", length);
        if (MTVS.indexOf(entry) >= 0) return;
        if (MTVS.length() > 0) MTVS.append(' ');
        MTVS.append(entry);
    }

    public static void recordFit(net.minecraft.world.entity.player.Player player,
                                 net.minecraft.world.entity.Pose pose,
                                 boolean worldFits, boolean subLevelHits) {
        recordFit(player, pose, worldFits, subLevelHits, null);
    }

    public static void recordFit(net.minecraft.world.entity.player.Player player,
                                 net.minecraft.world.entity.Pose pose,
                                 boolean worldFits, boolean subLevelHits,
                                 @javax.annotation.Nullable Vector3d blocker) {
        if (!Config.isLoaded() || !Config.DEBUG_MESSAGES.get()) return;
        if (!(player instanceof LocalPlayer)) return;

        String entry = pose + "=" + (worldFits ? (subLevelHits ? "SUB" : "ok") : "WORLD");
        if (blocker != null) {
            entry += String.format(java.util.Locale.ROOT, "(y%+.3f xz%.2f/%.2f)",
                    blocker.y, blocker.x, blocker.z);
        }
        if (FITS.indexOf(entry) >= 0) return;
        if (FITS.length() > 0) FITS.append(' ');
        FITS.append(entry);
    }

    public static void recordInside(net.minecraft.world.entity.Entity entity,
                                    net.minecraft.world.level.Level level,
                                    net.minecraft.core.BlockPos pos,
                                    boolean subLevel, boolean reached) {
        if (!Config.isLoaded() || !Config.DEBUG_MESSAGES.get()) return;
        if (!(entity instanceof LocalPlayer)) return;

        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        String entry = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock()).getPath()
                + "@" + (subLevel ? "sub" : "world")
                + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + ":" + (reached ? "kept" : "cut");

        if (INSIDES.length() > 512) return;
        if (INSIDES.indexOf(entry) >= 0) return;
        if (INSIDES.length() > 0) INSIDES.append(' ');
        INSIDES.append(entry);
    }

    public static void recordClimb(net.minecraft.world.entity.LivingEntity entity,
                                   boolean asked, boolean horizontalCollision, boolean jumping,
                                   float strafe, float forward) {
        if (!Config.isLoaded() || !Config.DEBUG_MESSAGES.get()) return;
        if (!(entity instanceof LocalPlayer)) return;

        String entry = (asked ? "rise:" : "held:")
                + (horizontalCollision ? (jumping ? "hcol+jump" : "hcol") : "jump")
                + String.format(java.util.Locale.ROOT, " in=%.2f/%.2f", strafe, forward);

        if (CLIMBS.length() > 512) return;
        if (CLIMBS.indexOf(entry) >= 0) return;
        if (CLIMBS.length() > 0) CLIMBS.append(' ');
        CLIMBS.append(entry);
    }

    private static String carry(LocalPlayer player) {
        if (!(player instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision
                .EntityMovementExtension movement)) {
            return "n/a";
        }

        boolean tracked = movement.sable$getTrackingSubLevel() != null;

        var info = movement.sable$getCollisionInfo();
        Vec3 inherited = info == null ? null : info.inheritedMotion;
        if (inherited == null) return tracked ? "yes/0.00" : "no";

        return (tracked ? "yes/" : "no/") + fmt(inherited.length());
    }

    public static String takeMtvs() {
        if (MTVS.length() == 0) return "-";

        String out = MTVS.toString();
        MTVS.setLength(0);
        return out;
    }

    private static String packedLight(LocalPlayer player) {
        try {
            var dispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
            int packed = com.mlh.aero_player_tilt.AcsBridge.ACS.withVanillaEye(
                    () -> dispatcher.getRenderer(player).getPackedLightCoords(player, 1.0f));
            return net.minecraft.client.renderer.LightTexture.block(packed)
                    + "/" + net.minecraft.client.renderer.LightTexture.sky(packed)
                    + " raw=" + Integer.toHexString(packed);
        } catch (Throwable t) {
            return "n/a";
        }
    }

    public static void tick(LocalPlayer player) {
        com.mlh.aero_player_tilt.client.debug.FallTrace.tick(player);

        if (!Config.isLoaded() || !Config.DEBUG_MESSAGES.get()) return;

        trackJump(player);

        if (++ticks % 10 != 0) return;

        boolean replay = com.mlh.aero_player_tilt.client.utils.ReplayCompat.inReplay();

        AeroPlayerTilt.LOGGER.info("[tilt/remote] replay={} {}", replay, ClientPlayerTilt.debugRemote());

        String packed = packedLight(player);
        AeroPlayerTilt.LOGGER.info("[tilt/light] {} packed={}", lightProbe, packed);

        if (replay) {
            AeroPlayerTilt.LOGGER.info("[tilt/anchor] {}", ReplayAnchor.debug());
        }

        SubLevel tracking = com.mlh.aero_player_tilt.tilt.DeckFrame.forBox(player);
        if (!(tracking instanceof ClientSubLevel clientSubLevel)) return;

        Quaterniond tilt = PlayerTilt.getOrientation(player, 1.0f);
        if (tilt == null) return;

        Quaterniondc ship = clientSubLevel.renderPose(1.0f).orientation();

        Quaterniond box = com.mlh.aero_player_tilt.tilt.BoxOrientation.forBox(
                tilt, clientSubLevel.renderPose(1.0f), new Quaterniond());

        double upErr = angleBetween(axis(box, 0, 1, 0), axis(ship, 0, 1, 0));
        double wallErr = horizontalMismatch(box, ship);

        Vector3d localUp = ship.transformInverse(axis(box, 0, 1, 0), new Vector3d()).normalize();
        String face = Math.round(localUp.x) + "/" + Math.round(localUp.y) + "/" + Math.round(localUp.z);

        Vec3 dm = player.getDeltaMovement();

        String fits = FITS.length() == 0 ? "-" : FITS.toString();
        FITS.setLength(0);
        String mtvs = MTVS.length() == 0 ? "-" : MTVS.toString();
        MTVS.setLength(0);
        String insides = INSIDES.length() == 0 ? "-" : INSIDES.toString();
        INSIDES.setLength(0);
        String climbs = CLIMBS.length() == 0 ? "-" : CLIMBS.toString();
        CLIMBS.setLength(0);

        Vec3 pos = player.position();
        String moved = fmt(pos.x - lastX) + "/" + fmt(pos.y - lastY) + "/" + fmt(pos.z - lastZ);
        lastX = pos.x; lastY = pos.y; lastZ = pos.z;

        String local = localDrift(clientSubLevel, pos);

        AeroPlayerTilt.LOGGER.info(
                "[tilt] upErr={} wallErr={} face={} tilt={} dm=({}, {}, {}) onGround={} horizCol={} pose={}"
                        + " shift={} crouch={} moved={} local={} carry={} turn={} predict={} fits[{}] mtv[{}] inside[{}] climb[{}]",
                fmt(upErr), fmt(wallErr), face, fmt(angleOf(tilt)),
                fmt(dm.x), fmt(dm.y), fmt(dm.z),
                player.onGround(), player.horizontalCollision, player.getPose(),
                player.isShiftKeyDown(), player.isCrouching(), moved, local, carry(player),
                DeckTurn.debug(), landing(player), fits, mtvs, insides, climbs);

        logCameraAnchor(player, tilt);
        logLookAxes(player, tilt);
    }

    private static String landing(LocalPlayer player) {
        TiltPrediction.Landing landing = TiltPrediction.current();
        if (landing == null) return "-";

        double angle = Math.toDegrees(Math.acos(Math.min(1.0, landing.normal().y)));
        return fmt(landing.ticks()) + "t/" + fmt(angle);
    }

    private static double lastX, lastY, lastZ;

    private static java.util.UUID anchorDeck;
    private static final Vector3d anchorLocal = new Vector3d();

    private static String localDrift(ClientSubLevel deck, Vec3 pos) {
        Vector3d local = deck.logicalPose().transformPositionInverse(
                new Vector3d(pos.x, pos.y, pos.z), new Vector3d());

        java.util.UUID id = deck.getUniqueId();
        if (!id.equals(anchorDeck)) {
            anchorDeck = id;
            anchorLocal.set(local);
        }

        return String.format(java.util.Locale.ROOT, "(%+.4f, %+.4f)",
                local.x - anchorLocal.x, local.z - anchorLocal.z);
    }

    private static void logCameraAnchor(LocalPlayer player, Quaterniond tilt) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();

        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
        double eyeHeight = player.getEyeHeight();

        Vec3 modelFeet = com.mlh.aero_player_tilt.AcsBridge.ACS
                .withVanillaEye(() -> Sable.HELPER.getEyePositionInterpolated(player, pt))
                .subtract(0.0, eyeHeight, 0.0);
        Vector3d head = tilt.transform(new Vector3d(0.0, eyeHeight, 0.0));
        Vec3 modelHead = modelFeet.add(head.x, head.y, head.z);

        Vec3 camPos = camera.getPosition();

        Vec3 anchor = camPos;
        if (camera.isDetached()) {
            Vec3 forward = cameraForward(camera);
            anchor = camPos.add(forward.scale(camPos.distanceTo(modelHead)));
        }

        Vec3 vanillaEye = player.getPosition(pt).add(0.0, eyeHeight, 0.0);

        Vec3 pickEye = Sable.HELPER.getEyePositionInterpolated(player, pt);

        com.playsi.aero_cam_sync.api.AcsState state =
                com.mlh.aero_player_tilt.AcsBridge.ACS.state(player, pt);
        org.joml.Quaternionf acsTilt = state.posTilt();
        double acsAngle = acsTilt == null ? 0.0 : angleOf(new Quaterniond(acsTilt));
        float acsScale = state.client() == null ? Float.NaN : state.client().tiltScale();

        AeroPlayerTilt.LOGGER.info(
                "[tilt/cam] view={} anchorErr={} pickEyeErr={} interpErr={} pivotErr={} acsMoves={} acsTilt={} acsScale={}"
                        + " cam=({}, {}, {}) head=({}, {}, {})",
                camera.isDetached() ? "3rd" : "1st",
                fmt(anchor.distanceTo(modelHead)),
                fmt(pickEye.distanceTo(modelHead)),
                fmt(vanillaEye.distanceTo(modelFeet.add(0.0, eyeHeight, 0.0))),
                fmt(head.distance(0.0, eyeHeight, 0.0)),
                state.modEnabled(), fmt(acsAngle), fmt(acsScale),
                fmt(camPos.x), fmt(camPos.y), fmt(camPos.z),
                fmt(modelHead.x), fmt(modelHead.y), fmt(modelHead.z));
    }

    private static boolean wasOnGround = true;
    private static boolean airborne;
    private static double jumpStartY, jumpPeakY, jumpImpulseY, jumpImpulseUp, jumpTiltAngle;
    private static double jumpPeakDeck, jumpRun;
    private static final Vector3d JUMP_START = new Vector3d();
    private static final Vector3d JUMP_UP = new Vector3d();

    private static int jumpTrackLost;

    private static final StringBuilder JUMP_POSES = new StringBuilder();

    private static double jumpUpDiff;

    private static String jumpMismatch = "-";
    private static String lastMismatch = "-";

    private static void trackJump(LocalPlayer player) {
        boolean onGround = player.onGround();
        Vec3 dm = player.getDeltaMovement();

        if (wasOnGround && !onGround) {
            airborne = true;
            jumpStartY = player.getY();
            jumpPeakY = jumpStartY;
            jumpImpulseY = dm.y;

            Quaterniond tilt = PlayerTilt.getOrientation(player, 1.0f);
            Vector3d up = tilt == null
                    ? new Vector3d(0.0, 1.0, 0.0)
                    : tilt.transform(new Vector3d(0.0, 1.0, 0.0));
            jumpImpulseUp = up.x * dm.x + up.y * dm.y + up.z * dm.z;
            jumpTiltAngle = tilt == null ? 0.0 : angleOf(tilt);

            JUMP_UP.set(up);
            JUMP_START.set(player.getX(), player.getY(), player.getZ());
            jumpPeakDeck = 0.0;
            jumpTrackLost = 0;
            jumpUpDiff = 0.0;
            jumpMismatch = "-";
            JUMP_POSES.setLength(0);

            jumpRun = -(up.x * dm.x + up.z * dm.z);
        }

        if (airborne) {
            jumpPeakY = Math.max(jumpPeakY, player.getY());

            if (Sable.HELPER.getTrackingSubLevel(player) == null) jumpTrackLost++;

            double upDiff = serverUpMismatch(player);
            if (upDiff > jumpUpDiff) {
                jumpUpDiff = upDiff;
                jumpMismatch = lastMismatch;
            }

            String pose = player.getPose().toString();
            if (JUMP_POSES.indexOf(pose) < 0) {
                if (JUMP_POSES.length() > 0) JUMP_POSES.append('>');
                JUMP_POSES.append(pose);
            }

            double deck = JUMP_UP.x * (player.getX() - JUMP_START.x)
                    + JUMP_UP.y * (player.getY() - JUMP_START.y)
                    + JUMP_UP.z * (player.getZ() - JUMP_START.z);
            jumpPeakDeck = Math.max(jumpPeakDeck, deck);

            if (onGround) {
                airborne = false;
                AeroPlayerTilt.LOGGER.info(
                        "[jump] deck={} world={} run={} dmY0={} alongUp0={} tilt={} trackLost={} upDiff={} poses={} mism[{}]",
                        fmt(jumpPeakDeck), fmt(jumpPeakY - jumpStartY), fmt(jumpRun),
                        fmt(jumpImpulseY), fmt(jumpImpulseUp), fmt(jumpTiltAngle),
                        jumpTrackLost, fmt(jumpUpDiff),
                        JUMP_POSES.length() == 0 ? "-" : JUMP_POSES.toString(),
                        jumpMismatch);
            }
        }

        wasOnGround = onGround;
    }

    private static double serverUpMismatch(LocalPlayer player) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (!mc.hasSingleplayerServer()) return 0.0;

        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return 0.0;

        net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
        if (sp == null) return 0.0;

        Quaterniond serverTilt = PlayerTilt.getOrientation(sp, 1.0f);
        Quaterniond clientTilt = PlayerTilt.getOrientation(player, 1.0f);
        if (serverTilt == null || clientTilt == null) return 0.0;

        lastMismatch = "c=" + describeSide(player, clientTilt, false)
                + " s=" + describeSide(sp, serverTilt, true);

        return angleBetween(axis(serverTilt, 0, 1, 0), axis(clientTilt, 0, 1, 0));
    }

    private static String describeSide(net.minecraft.world.entity.Entity entity,
                                       Quaterniond tilt, boolean logical) {
        dev.ryanhcode.sable.sublevel.SubLevel deck =
                com.mlh.aero_player_tilt.tilt.DeckFrame.of(entity);
        boolean live = Sable.HELPER.getTrackingSubLevel(entity) != null;

        String ship = "-";
        if (deck != null) {
            Quaterniondc orientation = logical
                    ? deck.logicalPose().orientation()
                    : (deck instanceof ClientSubLevel client
                            ? client.renderPose(1.0f).orientation()
                            : deck.logicalPose().orientation());
            ship = fmt(angleOf(orientation));
        }

        return fmt(angleOf(tilt)) + "/deck" + (deck != null ? 1 : 0)
                + "/track" + (live ? 1 : 0) + "/ship" + ship;
    }

    private static void logLookAxes(LocalPlayer player, Quaterniond tilt) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);

        double f = Math.toRadians(player.getViewXRot(pt));
        double g = Math.toRadians(-player.getViewYRot(pt));
        Vector3d vanilla = new Vector3d(Math.sin(g) * Math.cos(f), -Math.sin(f), Math.cos(g) * Math.cos(f));

        Vec3 aim = player.getViewVector(pt);
        Vector3d aimVec = new Vector3d(aim.x, aim.y, aim.z);

        Vec3 look = cameraForward(camera);
        Vector3d camVec = new Vector3d(look.x, look.y, look.z);

        org.joml.Vector3f stale = camera.getLookVector();

        com.playsi.aero_cam_sync.api.AcsState state =
                com.mlh.aero_player_tilt.AcsBridge.ACS.state(player, pt);

        AeroPlayerTilt.LOGGER.info(
                "[tilt/look] view={} tilt={} sable[{}] camOff={} aimOff={} camVsAim={} staleFwd={} suppressed={} by={}",
                camera.isDetached() ? "3rd" : "1st",
                fmt(angleOf(tilt)),
                takeSableDecision(),
                fmt(angleBetween(new Vector3d(camVec), new Vector3d(vanilla))),
                fmt(angleBetween(new Vector3d(aimVec), new Vector3d(vanilla))),
                fmt(angleBetween(new Vector3d(camVec), new Vector3d(aimVec))),
                fmt(angleBetween(new Vector3d(stale.x, stale.y, stale.z), new Vector3d(camVec))),
                state.suppressed(), state.suppressedBy());
    }

    private static Vec3 cameraForward(net.minecraft.client.Camera camera) {
        org.joml.Vector3f v = camera.rotation().transform(new org.joml.Vector3f(0.0f, 0.0f, -1.0f));
        return new Vec3(v.x, v.y, v.z);
    }

    private static Vector3d axis(Quaterniondc q, double x, double y, double z) {
        return q.transform(new Vector3d(x, y, z));
    }

    private static double angleBetween(Vector3d a, Vector3d b) {
        double dot = Math.max(-1.0, Math.min(1.0, a.normalize().dot(b.normalize())));
        return Math.toDegrees(Math.acos(dot));
    }

    private static double angleOf(Quaterniondc q) {
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(q.w()))));
    }

    private static double horizontalMismatch(Quaterniondc box, Quaterniondc ship) {
        Vector3d boxX = axis(box, 1, 0, 0);
        double toShipX = angleBetween(new Vector3d(boxX), axis(ship, 1, 0, 0));
        double toShipZ = angleBetween(new Vector3d(boxX), axis(ship, 0, 0, 1));

        toShipX = Math.min(toShipX, 180.0 - toShipX);
        toShipZ = Math.min(toShipZ, 180.0 - toShipZ);
        return Math.min(toShipX, toShipZ);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
