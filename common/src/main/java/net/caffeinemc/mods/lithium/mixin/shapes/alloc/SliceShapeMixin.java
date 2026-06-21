package net.caffeinemc.mods.lithium.mixin.shapes.alloc;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;


@Mixin(SliceShape.class)
public abstract class SliceShapeMixin extends VoxelShape {


    @Shadow
    @Final
    private Direction.Axis axis;

    @Shadow
    @Final
    private VoxelShape delegate;

    protected SliceShapeMixin(DiscreteVoxelShape shape) {
        super(shape);
    }

    @Override
    public double get(final Direction.Axis axis, final int index) {
        if (axis == this.axis) {
            return index;
        }
        return this.delegate.get(axis, index);
    }

    @Override
    protected int findIndex(final Direction.Axis axis, final double coord) {
        if (axis == this.axis) {
            if (coord < this.get(axis, 1)) {
                if (coord < this.get(axis, 0)) {
                    return -1;
                } else {
                    return 0;
                }
            } else {
                return 1;
            }

        } else {
            return ((VoxelShapeInvoker) this.delegate).callFindIndex(axis, coord);
        }
    }
}
