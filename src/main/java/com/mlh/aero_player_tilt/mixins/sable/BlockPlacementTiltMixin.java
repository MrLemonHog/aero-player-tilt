package com.mlh.aero_player_tilt.mixins.sable;

import com.llamalad7.mixinextras.sugar.Local;
import com.mlh.aero_player_tilt.tilt.TiltedPlayerBox;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityGetter.class, priority = 1400)
public interface BlockPlacementTiltMixin {
    @Redirect(method = "isUnobstructed(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/shapes/VoxelShape;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty("
                            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
                            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
                            + "Lnet/minecraft/world/phys/shapes/BooleanOp;)Z",
                    ordinal = 0))
    private static boolean aeroCamSync$tiltedWorldBox(VoxelShape shape, VoxelShape entityShape, BooleanOp op,
                                                      @Local(ordinal = 1) Entity entity) {
        Boolean tilted = TiltedPlayerBox.intersects(entity, shape, null);
        boolean vanilla = Shapes.joinIsNotEmpty(shape, entityShape, op);
        TiltedPlayerBox.logDecision("world", entity, shape, vanilla, tilted);
        return tilted != null ? tilted : vanilla;
    }

    @Redirect(method = "isUnobstructed(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/shapes/VoxelShape;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty("
                            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
                            + "Lnet/minecraft/world/phys/shapes/VoxelShape;"
                            + "Lnet/minecraft/world/phys/shapes/BooleanOp;)Z",
                    ordinal = 1))
    private static boolean aeroCamSync$tiltedSubLevelBox(VoxelShape shape, VoxelShape entityShape, BooleanOp op,
                                                         @Local(ordinal = 1) Entity entity,
                                                         @Local(ordinal = 0) SubLevel subLevel) {
        Boolean tilted = TiltedPlayerBox.intersects(entity, shape, subLevel.logicalPose());
        boolean vanilla = Shapes.joinIsNotEmpty(shape, entityShape, op);
        TiltedPlayerBox.logDecision("sub", entity, shape, vanilla, tilted);
        return tilted != null ? tilted : vanilla;
    }
}
