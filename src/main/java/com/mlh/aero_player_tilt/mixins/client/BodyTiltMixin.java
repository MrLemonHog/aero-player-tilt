package com.mlh.aero_player_tilt.mixins.client;

import com.mlh.aero_player_tilt.AcsBridge;
import com.mlh.aero_player_tilt.client.config.Config;
import com.mlh.aero_player_tilt.client.debug.DebugRayRenderer;
import com.mlh.aero_player_tilt.client.tilt.BodyTiltController;
import com.mlh.aero_player_tilt.client.utils.StandingDeck;
import com.mlh.aero_player_tilt.client.utils.SubLevelTracker;
import com.mlh.aero_player_tilt.client.utils.SurfaceRaycaster;
import com.mlh.aero_player_tilt.tilt.DeckFrame;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 900)
public abstract class BodyTiltMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void aero$updateBodyTilt(
            BlockGetter level, Entity entity,
            boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        if ((Object) this != mc.gameRenderer.getMainCamera()) return;

        if (!com.mlh.aero_player_tilt.client.tilt.BootsController.attached()) {
            DebugRayRenderer.clear();
        }

        com.mlh.aero_player_tilt.client.tilt.ClientPlayerTilt.advanceRemote(
                mc.getTimer().getRealtimeDeltaTicks(), partialTick);

        BodyTiltController.tickApplyState();

        if (mc.player == null) return;

        com.mlh.aero_player_tilt.client.tilt.BedRenderDiagnostics.report(mc.player, partialTick);

        if (!BodyTiltController.shouldComputeTilt(mc.player)) {
            StandingDeck.forget();
            com.mlh.aero_player_tilt.client.utils.DeckZone.forget();
            com.mlh.aero_player_tilt.client.tilt.BootsController.release();
            return;
        }

        float deltaTime = mc.getTimer().getRealtimeDeltaTicks();

        if (com.mlh.aero_player_tilt.client.tilt.BootsController.render(partialTick)) {
            StandingDeck.forget();
            com.mlh.aero_player_tilt.client.utils.DeckZone.forget();
            return;
        }

        boolean suppressed = AcsBridge.ACS.isSuppressed();

        ClientSubLevel tracked = SubLevelTracker.getClientSubLevel(mc.player);

        boolean blend = Config.flag(Config.BLEND_FOOTING, true);

        SurfaceRaycaster.Floor zone = com.mlh.aero_player_tilt.client.utils.DeckZone.survey(
                mc.player, partialTick, deltaTime,
                !suppressed && !SubLevelTracker.vetoed(mc.player));

        boolean fromZone = zone.normal() != null
                && com.mlh.aero_player_tilt.client.utils.DeckZone.always();

        SurfaceRaycaster.Floor floor = fromZone ? zone
                : (!suppressed && tracked != null
                        ? SurfaceRaycaster.survey(mc.player, partialTick, blend ? null : tracked)
                        : SurfaceRaycaster.Floor.NONE);

        if (!fromZone && floor.normal() == null && zone.normal() != null) {
            floor = zone;
            fromZone = true;
        }

        ClientSubLevel standing;
        if (blend || fromZone) {
            standing = StandingDeck.commit(floor, deltaTime);
        } else {
            StandingDeck.forget();
            standing = tracked;
        }

        Vector3f surfaceNormal = standing == null ? null : floor.normalOf(standing);

        if (surfaceNormal == null && !suppressed && tracked != null
                && Config.DROP_CACHE_ON_ALL_MISS.get()) {
            SubLevelTracker.invalidateCache();
        }

        SubLevel tracking = Sable.HELPER.getTrackingSubLevel(mc.player);

        ClientSubLevel deck = standing != null ? standing
                : (tracked != null ? tracked
                : (DeckFrame.of(mc.player) instanceof ClientSubLevel aboard ? aboard : null));
        Quaternionf shipRotation = deck != null
                ? new Quaternionf(deck.renderPose(partialTick).orientation())
                : null;

        com.mlh.aero_player_tilt.client.tilt.TiltPrediction.Landing landing = suppressed
                ? null
                : com.mlh.aero_player_tilt.client.tilt.TiltPrediction.predict(mc.player);

        boolean airborneOverDeck = !suppressed
                && Config.flag(Config.PREDICT_LANDING, true)
                && !mc.player.onGround()
                && (tracking != null || deck != null);

        boolean hold = suppressed || (surfaceNormal == null && tracking != null) || airborneOverDeck;

        BodyTiltController.updateBodyTilt(surfaceNormal, deltaTime, hold, shipRotation,
                deck != null ? deck.getUniqueId() : null, landing, airborneOverDeck);

        com.mlh.aero_player_tilt.client.debug.FrameTrace.body(
                floor, surfaceNormal, hold, airborneOverDeck, tracked, standing);
    }
}
