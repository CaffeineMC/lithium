package net.caffeinemc.mods.lithium.mixin.shapes.alloc;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CubeVoxelShape.class)
public abstract class CubeVoxelShapeMixin extends VoxelShape {

    protected CubeVoxelShapeMixin(DiscreteVoxelShape shape) {
        super(shape);
    }

    @Override
    public double get(final Direction.@NonNull Axis axis, final int index) {
        return (double) index / this.shape.getSize(axis);
    }
}
