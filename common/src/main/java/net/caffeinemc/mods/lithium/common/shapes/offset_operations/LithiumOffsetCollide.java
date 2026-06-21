package net.caffeinemc.mods.lithium.common.shapes.offset_operations;

import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LithiumOffsetCollide {
    private static final double POSITIVE_EPSILON = 1.0E-7D;
    private static final double NEGATIVE_EPSILON = -1.0E-7D;

    public static int axisChoose(Direction.Axis axis, int offsetX, int offsetY, int offsetZ) {
        if (axis == Direction.Axis.X) {
            return offsetX;
        }
        if (axis == Direction.Axis.Y) {
            return offsetY;
        }
        return offsetZ;
    }

    public static double collide(VoxelShape shape, int offsetX, int offsetY, int offsetZ, Direction.Axis axis, AABB moving, double distance) {
        //[VanillaCopy] adapted for non-allocating offset collisions
        return collideXAsOffsetShape(shape, offsetX, offsetY, offsetZ, AxisCycle.between(axis, Direction.Axis.X), moving, distance);
    }

    /**
     * Includes code from VoxelShapeMixin
     * Original author: JellySquid
     * Original optimization: Use optimized implementation which delays searching for coordinates as long as possible
     * Modified for alloc-free offset collisions.
     *
     * @author 2No2Name
     */
    protected static double collideXAsOffsetShape(VoxelShape voxelShape, int offset1, int offset2, int offset3, AxisCycle cycleDirection, AABB box, double maxDist) {

        if (voxelShape.isEmpty()) {
            return maxDist;
        }

        if (Math.abs(maxDist) < POSITIVE_EPSILON) {
            return 0.0D;
        }

        AxisCycle cycle = cycleDirection.inverse();

        Direction.Axis axisX = cycle.cycle(Direction.Axis.X);
        Direction.Axis axisY = cycle.cycle(Direction.Axis.Y);
        Direction.Axis axisZ = cycle.cycle(Direction.Axis.Z);
        //From here on X,Y,Z mean whatever axis these axis variables represent

        int offsetX = axisChoose(axisX, offset1, offset2, offset3);
        int offsetY = axisChoose(axisY, offset1, offset2, offset3);
        int offsetZ = axisChoose(axisZ, offset1, offset2, offset3);


        int minY = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        int x, y, z;

        double dist;

        if (maxDist > 0.0D) {
            double max = box.max(axisX);
            int maxIdx = findIndexWithOffset(voxelShape, offsetX, axisX, max - POSITIVE_EPSILON);

            int maxX = voxelShape.shape.getSize(axisX);

            for (x = maxIdx + 1; x < maxX; ++x) {
                minY = minY == Integer.MIN_VALUE ? Math.max(0, findIndexWithOffset(voxelShape, offsetY, axisY, box.min(axisY) + POSITIVE_EPSILON)) : minY;
                maxY = maxY == Integer.MIN_VALUE ? Math.min(voxelShape.shape.getSize(axisY), findIndexWithOffset(voxelShape, offsetY, axisY, box.max(axisY) - POSITIVE_EPSILON) + 1) : maxY;

                for (y = minY; y < maxY; ++y) {
                    minZ = minZ == Integer.MIN_VALUE ? Math.max(0, findIndexWithOffset(voxelShape, offsetZ, axisZ, box.min(axisZ) + POSITIVE_EPSILON)) : minZ;
                    maxZ = maxZ == Integer.MIN_VALUE ? Math.min(voxelShape.shape.getSize(axisZ), findIndexWithOffset(voxelShape, offsetZ, axisZ, box.max(axisZ) - POSITIVE_EPSILON) + 1) : maxZ;

                    for (z = minZ; z < maxZ; ++z) {
                        if (voxelShape.shape.isFullWide(cycle, x, y, z)) {
                            dist = voxelShape.get(axisX, x) + offsetX - max;

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
            int minIdx = findIndexWithOffset(voxelShape, offsetX, axisX, min + POSITIVE_EPSILON);

            for (x = minIdx - 1; x >= 0; --x) {
                minY = minY == Integer.MIN_VALUE ? Math.max(0, findIndexWithOffset(voxelShape, offsetY, axisY, box.min(axisY) + POSITIVE_EPSILON)) : minY;
                maxY = maxY == Integer.MIN_VALUE ? Math.min(voxelShape.shape.getSize(axisY), findIndexWithOffset(voxelShape, offsetY, axisY, box.max(axisY) - POSITIVE_EPSILON) + 1) : maxY;

                for (y = minY; y < maxY; ++y) {
                    minZ = minZ == Integer.MIN_VALUE ? Math.max(0, findIndexWithOffset(voxelShape, offsetZ, axisZ, box.min(axisZ) + POSITIVE_EPSILON)) : minZ;
                    maxZ = maxZ == Integer.MIN_VALUE ? Math.min(voxelShape.shape.getSize(axisZ), findIndexWithOffset(voxelShape, offsetZ, axisZ, box.max(axisZ) - POSITIVE_EPSILON) + 1) : maxZ;

                    for (z = minZ; z < maxZ; ++z) {
                        if (voxelShape.shape.isFullWide(cycle, x, y, z)) {
                            dist = voxelShape.get(axisX, x + 1) + offsetX - min;

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

    public static int findIndexWithOffset(VoxelShape voxelShape, int offset, Direction.Axis axis, double coord) {
        int size = voxelShape.shape.getSize(axis);

        int start = 0;
        int len = size + 1 - start;

        while (len > 0) {
            int half = len / 2;
            int middle = start + half;
            //VoxelShape.get(Axis, int) is non-allocating given other lithium mixins being applied - unlike equivalent VoxelShape.getCoords(Axis).getDouble(int)
            if (middle >= 0 && (middle > size || coord < voxelShape.get(axis, middle) + offset)) {
                len = half;
            } else {
                start = middle + 1;
                len -= half + 1;
            }
        }

        return start - 1;
    }

}
