package net.caffeinemc.mods.lithium.mixin.alloc.shapes.slice;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Shapes.class)
public class ShapesMixin {

    @WrapOperation(
            method = "blockOccludes(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/core/Direction;)Z",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/core/Direction$Axis;I)Lnet/minecraft/world/phys/shapes/SliceShape;")
    )
    private static SliceShape getSliceShape(VoxelShape delegate, Direction.Axis axis, int point, Operation<SliceShape> original) {
        if (
                point == 0 && //The sliceShape should only have segment on that axis
                        delegate instanceof SliceShape sliceShape &&
                        ((SliceShapeAccessor) sliceShape).getAxis() == axis
        ) {
            return sliceShape;
        }
        return original.call(delegate, axis, point);
    }
}
