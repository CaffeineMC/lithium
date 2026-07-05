package net.caffeinemc.mods.lithium.common.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.caffeinemc.mods.lithium.common.shapes.lists.OffsetFractionalDoubleList;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class VoxelShapeAlignedCuboidOffset extends VoxelShapeAlignedCuboid {
    //keep track on how much the voxelSet was offset. minX,maxX,minY are stored offset already
    //This is only required to calculate the position of the walls inside the VoxelShape.
    //For shapes that are not offset the alignment is 0, but for offset shapes the walls move together with the shape.
    private final double xOffset, yOffset, zOffset;
    //instead of keeping those variables, equivalent information can probably be recovered from minX, minY, minZ (which are 1/8th of a block aligned), but possibly with additional floating point error

    public VoxelShapeAlignedCuboidOffset(VoxelShapeAlignedCuboid originalShape, DiscreteVoxelShape voxels, double xOffset, double yOffset, double zOffset) {
        super(voxels, originalShape.minX + xOffset, originalShape.minY + yOffset, originalShape.minZ + zOffset, originalShape.maxX + xOffset, originalShape.maxY + yOffset, originalShape.maxZ + zOffset, originalShape.xyzResolution);

        if (originalShape instanceof VoxelShapeAlignedCuboidOffset) {
            this.xOffset = ((VoxelShapeAlignedCuboidOffset) originalShape).xOffset + xOffset; //TODO the float addition here can cause non-vanilla floating point errors
            this.yOffset = ((VoxelShapeAlignedCuboidOffset) originalShape).yOffset + yOffset; // Float non-associativity technically causes an issue here
            this.zOffset = ((VoxelShapeAlignedCuboidOffset) originalShape).zOffset + zOffset; // In practice, this is likely not a problem
        } else {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.zOffset = zOffset;
        }
    }

    @Override
    public double collideX(AxisCycle cycleDirection, AABB moving, double maxDist) {
        if (Math.abs(maxDist) < EPSILON) {
            return 0.0D;
        }

        return switch (cycleDirection) {
            case NONE ->
                    limitMovement(maxDist, this.getXSegments(), this.xOffset, moving.minX, moving.maxX, moving.minY, moving.maxY, moving.minZ, moving.maxZ, this.minX, this.maxX, this.minY, this.maxY, this.minZ, this.maxZ);
            case FORWARD ->
                    limitMovement(maxDist, this.getZSegments(), this.zOffset, moving.minZ, moving.maxZ, moving.minX, moving.maxX, moving.minY, moving.maxY, this.minZ, this.maxZ, this.minX, this.maxX, this.minY, this.maxY);
            case BACKWARD ->
                    limitMovement(maxDist, this.getYSegments(), this.yOffset, moving.minY, moving.maxY, moving.minZ, moving.maxZ, moving.minX, moving.maxX, this.minY, this.maxY, this.minZ, this.maxZ, this.minX, this.maxX);
        };
    }

    private static double limitMovement(double maxDist, int segmentsA, double offsetA, double bMinA, double bMaxA, double bMinB, double bMaxB, double bMinC, double bMaxC, double sMinA, double sMaxA, double sMinB, double sMaxB, double sMinC, double sMaxC) {
        double maxMovement = VoxelShapeAlignedCuboidOffset.limitMovement(maxDist, segmentsA, offsetA, sMinA, sMaxA, bMinA, bMaxA);
        if (maxMovement != maxDist && hasOverlapFIE(sMinB, sMaxB, bMinB, bMaxB) && hasOverlapFIE(sMinC, sMaxC, bMinC, bMaxC)) {
            return maxMovement;
        }
        return maxDist;
    }

    /**
     * Determine how far the movement is possible.
     * <p>
     * Assumption: No two walls of the voxelShape are super close to each other, super close could be defined as distance < 1e-5
     */
    private static double limitMovement(double maxDist, int segments, double shapeOffset, double sMin, double sMax, double bMin, double bMax) {
        double maxMovement;

        if (maxDist > 0.0D) {
            maxMovement = sMin - bMax;

            if (maxDist < maxMovement) {
                //outside the shape and still far enough away for no collision at all
                return maxDist;
            }
            double max = bMax - EPSILON; //EPSILON from VoxelShapes#collide
            //1. FindIndex return value allows iteration
            //2. newDistance check ("past the wall by more than 1e-7?") - permits the wall to push backwards by up to 1e-7
            if (!(max < sMin) || maxMovement < -1.0E-7) {
                //Far enough inside to not collide with outer wall
                if (segments == 1) {
                    //Shape has no inner walls
                    return maxDist;
                }
                int nextWallIndex = findIndex(max, shapeOffset, segments) + 1; // findIndex returns the lower wall, +1 as this is towards positive
                //The outermost walls (non-inner wall) only have collision if movement direction is towards the shape from the outside
                double wall = nextWallIndex / (double) segments + shapeOffset;
                //Only inner walls are double-sided in vanilla
                boolean isNotBackWall = wall < sMax - LARGE_EPSILON; //Assuming that no two walls are super close to each other
                if (isNotBackWall) {
                    double newMaxMovement = wall - bMax;
                    //1. FindIndex return value already checked, since we called the function
                    //2. newDistance check ("past the wall by more than 1e-7?") - permits the wall to push backwards by up to 1e-7
                    if (newMaxMovement < -1.0E-7) {
                        //Far enough inside to not collide with the inner wall
                        //Assuming that no two walls are super close to each other
                        return maxDist;
                    }
                    return Math.min(maxDist, newMaxMovement);
                }
                return maxDist;
            }
        } else {
            maxMovement = sMax - bMin;

            if (maxDist > maxMovement) {
                //outside the shape and still far enough away for no collision at all
                return maxDist;
            }
            double min = bMin + EPSILON;
            //1. FindIndex return value allows iteration. Note this also uses < and not <=, since findIndex includes the boundary in the upper interval
            //2. newDistance check ("past the wall by more than 1e-7?") - permits the wall to push backwards by up to 1e-7
            if (min < sMax || maxMovement > 1.0E-7) {
                if (segments == 1) {
                    return maxDist;
                }
                int nextWallIndex = findIndex(min, shapeOffset, segments); // findIndex returns the lower wall, no +1 here as this is towards negative
                double wall = nextWallIndex / (double) segments + shapeOffset;
                boolean isNotBackWall = wall > sMin + LARGE_EPSILON;
                if (isNotBackWall) {
                    double newMaxMovement = wall - bMin;
                    if (newMaxMovement > 1.0E-7) {
                        return maxDist;
                    }
                    return Math.max(maxDist, newMaxMovement);
                }
                return maxDist;
            }
        }
        //allow moving up to the shape but not into it. This also includes going backwards by at most EPSILON.
        return maxMovement;
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return switch (axis) {
            case X -> new OffsetFractionalDoubleList(this.getXSegments(), this.xOffset);
            case Y -> new OffsetFractionalDoubleList(this.getYSegments(), this.yOffset);
            case Z -> new OffsetFractionalDoubleList(this.getZSegments(), this.zOffset);
        };
    }

    @Override
    protected double get(Direction.Axis axis, int index) {
        return switch (axis) {
            case X -> this.xOffset + (double) index / (double) this.getXSegments();
            case Y -> this.yOffset + (double) index / (double) this.getYSegments();
            case Z -> this.zOffset + (double) index / (double) this.getZSegments();
        };
    }

    @Override
    public int findIndex(Direction.Axis axis, double coord) {
        return switch (axis) {
            case X -> findIndex(coord, this.xOffset, this.getXSegments());
            case Y -> findIndex(coord, this.yOffset, this.getYSegments());
            case Z -> findIndex(coord, this.zOffset, this.getZSegments());
        };
    }

    /**
     * Implemented like vanilla's {@link net.minecraft.world.phys.shapes.ArrayVoxelShape#findIndex(Direction.Axis, double)}
     */
    private static int findIndex(double coord, double shapeOffset, int segments) {
        //Add some epsilon to avoid underestimating the index here.
        int index = Mth.floor((coord - shapeOffset + LARGE_EPSILON) * (double) segments);

        //Perform the vanilla check from the binary search once, since index could be slightly over the boundary due to floating point rounding error (and added some epsilon)
        double boundary = index / (double) segments + shapeOffset;
        if (coord < boundary) {
            index--;
        }
        //No need to check underestimated index, since some epsilon was added above to avoid underestimation.

        return Mth.clamp(index, -1, segments);
    }
}
