package com.mlh.aero_player_tilt.mixins.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundEngine.class)
public abstract class ListenerTiltMixin {
    @WrapOperation(method = "updateSource",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getLookVector()Lorg/joml/Vector3f;"))
    private Vector3f aero$lookAsDrawn(Camera camera, Operation<Vector3f> original) {
        return new Vector3f(0f, 0f, -1f).rotate(camera.rotation());
    }

    @WrapOperation(method = "updateSource",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getUpVector()Lorg/joml/Vector3f;"))
    private Vector3f aero$upAsDrawn(Camera camera, Operation<Vector3f> original) {
        return new Vector3f(0f, 1f, 0f).rotate(camera.rotation());
    }
}
