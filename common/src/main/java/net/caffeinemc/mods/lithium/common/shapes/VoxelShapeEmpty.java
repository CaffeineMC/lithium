package net.caffeinemc.mods.lithium.common.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An efficient implementation of {@link VoxelShape} for a shape with no vertices. Vanilla normally represents this
 * case with an empty {@link net.minecraft.world.phys.shapes.CubeVoxelShape}, but since there is no data the return values
 * here will always be constant. This allows a lot of unnecessary code to be eliminated that would otherwise try to
 * iterate over sets of empty voxels/vertices.
 */
public class VoxelShapeEmpty extends VoxelShape implements VoxelShapeCaster {
    private static final DoubleList EMPTY_LIST = DoubleArrayList.wrap(new double[] { 0.0D }); //Vanilla also has 0.0, so merging behavior is not modified

    public VoxelShapeEmpty(DiscreteVoxelShape voxels) {
        super(voxels);
    }

    @Override
    protected double collideX(AxisCycle axisCycle, AABB box, double maxDist) {
        return maxDist;
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return EMPTY_LIST;
    }

    @Override
    public double min(Direction.Axis axis) {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public double max(Direction.Axis axis) {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean intersectsJNE(AABB box, double blockX, double blockY, double blockZ) {
        return false;
    }
}
