package net.caffeinemc.mods.lithium.mixin.shapes.optimized_matching;

import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeMatchesAnywhere;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Shapes.class)
public class ShapesMixin {
    @Inject(
            method = "joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z",
            at = @At(
                    shift = At.Shift.BEFORE,
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/shapes/VoxelShape;getCoords(Lnet/minecraft/core/Direction$Axis;)Lit/unimi/dsi/fastutil/doubles/DoubleList;",
                    ordinal = 0
            ),
            cancellable = true
    )
    private static void cuboidMatchesAnywhere(VoxelShape shapeA, VoxelShape shapeB, BooleanOp predicate, CallbackInfoReturnable<Boolean> cir) {
        int matchesAnywhere = VoxelShapeMatchesAnywhere.cuboidMatchesAnywhere(shapeA, shapeB, predicate);
        if (matchesAnywhere != -1) {
            cir.setReturnValue(matchesAnywhere != 0);
        }
    }
}