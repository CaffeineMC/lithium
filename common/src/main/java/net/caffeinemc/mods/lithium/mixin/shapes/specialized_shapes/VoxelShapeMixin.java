package net.caffeinemc.mods.lithium.mixin.shapes.specialized_shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Implement faster methods for determining penetration during collision resolution.
 */
@Mixin(VoxelShape.class)
public abstract class VoxelShapeMixin {
    private static final double POSITIVE_EPSILON = 1.0E-7D;
    private static final double NEGATIVE_EPSILON = -1.0E-7D;

    @Shadow
    @Final
    public DiscreteVoxelShape shape;

    @Shadow
    public abstract boolean isEmpty();

    @Shadow
    protected abstract double get(Direction.Axis axis, int index);

    @Shadow
    public abstract DoubleList getCoords(Direction.Axis axis);

    /**
     * @reason Use optimized implementation which delays searching for coordinates as long as possible
     * @author JellySquid
     */
    @Overwrite
    public double collideX(AxisCycle cycleDirection, AABB box, double maxDist) {
        if (this.isEmpty()) {
            return maxDist;
        }

        if (Math.abs(maxDist) < POSITIVE_EPSILON) {
            return 0.0D;
        }

        AxisCycle cycle = cycleDirection.inverse();

        Direction.Axis axisX = cycle.cycle(Direction.Axis.X);
        Direction.Axis axisY = cycle.cycle(Direction.Axis.Y);
        Direction.Axis axisZ = cycle.cycle(Direction.Axis.Z);

        int minY = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        int x, y, z;

        double dist;

        if (maxDist > 0.0D) {
            double max = box.max(axisX);
            int maxIdx = this.findIndex(axisX, max - POSITIVE_EPSILON);

            int maxX = this.shape.getSize(axisX);

            for (x = maxIdx + 1; x < maxX; ++x) {
                minY = minY == Integer.MIN_VALUE ? Math.max(0, this.findIndex(axisY, box.min(axisY) + POSITIVE_EPSILON)) : minY;
                maxY = maxY == Integer.MIN_VALUE ? Math.min(this.shape.getSize(axisY), this.findIndex(axisY, box.max(axisY) - POSITIVE_EPSILON) + 1) : maxY;

                for (y = minY; y < maxY; ++y) {
                    minZ = minZ == Integer.MIN_VALUE ? Math.max(0, this.findIndex(axisZ, box.min(axisZ) + POSITIVE_EPSILON)) : minZ;
                    maxZ = maxZ == Integer.MIN_VALUE ? Math.min(this.shape.getSize(axisZ), this.findIndex(axisZ, box.max(axisZ) - POSITIVE_EPSILON) + 1) : maxZ;

                    for (z = minZ; z < maxZ; ++z) {
                        if (this.shape.isFullWide(cycle, x, y, z)) {
                            dist = this.get(axisX, x) - max;

                            if (dist >= NEGATIVE_EPSILON) {
                                maxDist = Math.min(maxDist, dist);
                            }

                            return maxDist;
                        }
                    }
                }
            }
        } else if (maxDist < 0.0D) {
            double min = box.min(axisX);
            int minIdx = this.findIndex(axisX, min + POSITIVE_EPSILON);

            for (x = minIdx - 1; x >= 0; --x) {
                minY = minY == Integer.MIN_VALUE ? Math.max(0, this.findIndex(axisY, box.min(axisY) + POSITIVE_EPSILON)) : minY;
                maxY = maxY == Integer.MIN_VALUE ? Math.min(this.shape.getSize(axisY), this.findIndex(axisY, box.max(axisY) - POSITIVE_EPSILON) + 1) : maxY;

                for (y = minY; y < maxY; ++y) {
                    minZ = minZ == Integer.MIN_VALUE ? Math.max(0, this.findIndex(axisZ, box.min(axisZ) + POSITIVE_EPSILON)) : minZ;
                    maxZ = maxZ == Integer.MIN_VALUE ? Math.min(this.shape.getSize(axisZ), this.findIndex(axisZ, box.max(axisZ) - POSITIVE_EPSILON) + 1) : maxZ;

                    for (z = minZ; z < maxZ; ++z) {
                        if (this.shape.isFullWide(cycle, x, y, z)) {
                            dist = this.get(axisX, x + 1) - min;

                            if (dist <= POSITIVE_EPSILON) {
                                maxDist = Math.max(maxDist, dist);
                            }

                            return maxDist;
                        }
                    }
                }
            }
        }

        return maxDist;
    }

    /**
     * Inlines the lambda passed to MathHelper#binarySearch. Simplifies the implementation very slightly for additional
     * speed.
     *
     * @reason Use faster implementation
     * @author JellySquid
     */
    @Overwrite
    public int findIndex(Direction.Axis axis, double coord) {
        DoubleList list = this.getCoords(axis);

        int size = this.shape.getSize(axis);

        int start = 0;
        int len = size + 1 - start;

        while (len > 0) {
            int half = len / 2;
            int middle = start + half;

            if (middle >= 0 && (middle > size || coord < list.getDouble(middle))) {
                len = half;
            } else {
                start = middle + 1;
                len -= half + 1;
            }
        }

        return start - 1;
    }
}
