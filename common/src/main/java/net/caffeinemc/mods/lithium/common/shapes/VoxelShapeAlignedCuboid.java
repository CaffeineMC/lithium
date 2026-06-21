package net.caffeinemc.mods.lithium.common.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CubePointRange;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An efficient implementation of {@link VoxelShape} for a shape with one simple cuboid.
 * This is an alternative to VoxelShapeSimpleCube with extra hitboxes inside.
 * Vanilla has extra hitboxes at steps of 1/8th or 1/4th of a block depending on the exact coordinates of the shape.
 * We are mimicking the effect on collisions here, as otherwise some contraptions would not behave like vanilla.
 *
 * @author 2No2Name
 */
public class VoxelShapeAlignedCuboid extends VoxelShapeSimpleCube {
    //EPSILON for use in cases where it must be a lot smaller than 1/256 and larger than EPSILON
    static final double LARGE_EPSILON = 10 * EPSILON;

    //In bit-aligned shapes the bitset adds segments are between minX/Y/Z and maxX/Y/Z.
    //Segments all have the same size. There is an additional collision box between two adjacent segments (if both are inside the shape)
    protected final byte xyzResolution;

    public VoxelShapeAlignedCuboid(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int xRes, int yRes, int zRes) {
        super(new CuboidVoxelSet(1 << xRes, 1 << yRes, 1 << zRes, minX, minY, minZ, maxX, maxY, maxZ), minX, minY, minZ, maxX, maxY, maxZ);

        if (xRes > 3 || yRes > 3 || zRes > 3 || xRes < 0 || yRes < 0 || zRes < 0) {
            throw new IllegalArgumentException("Resolution must be between 0 and 3");
        }

        this.xyzResolution = (byte) (xRes << 4 | yRes << 2 | zRes);
    }

    /**
     * Constructor for use in offset() calls.
     */
    public VoxelShapeAlignedCuboid(DiscreteVoxelShape voxels, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, byte xyzResolution) {
        super(voxels, minX, minY, minZ, maxX, maxY, maxZ);
        this.xyzResolution = xyzResolution;
    }

    @Override
    public VoxelShape move(double x, double y, double z) {
        if (x == 0 && y == 0 && z == 0) {
            return this;
        }
        return new VoxelShapeAlignedCuboidOffset(this, this.shape, x, y, z);
    }


    @Override
    public double collideX(AxisCycle cycleDirection, AABB moving, double maxDist) {
        if (Math.abs(maxDist) < EPSILON) {
            return 0.0D;
        }

        return switch (cycleDirection) {
            case NONE ->
                    limitMovement(maxDist, this.getXSegments(), moving.minX, moving.maxX, moving.minY, moving.maxY, moving.minZ, moving.maxZ, this.minX, this.maxX, this.minY, this.maxY, this.minZ, this.maxZ);
            case FORWARD ->
                    limitMovement(maxDist, this.getZSegments(), moving.minZ, moving.maxZ, moving.minX, moving.maxX, moving.minY, moving.maxY, this.minZ, this.maxZ, this.minX, this.maxX, this.minY, this.maxY);
            case BACKWARD ->
                    limitMovement(maxDist, this.getYSegments(), moving.minY, moving.maxY, moving.minZ, moving.maxZ, moving.minX, moving.maxX, this.minY, this.maxY, this.minZ, this.maxZ, this.minX, this.maxX);
        };
    }

    private static double limitMovement(double maxDist, int segmentsA, double bMinA, double bMaxA, double bMinB, double bMaxB, double bMinC, double bMaxC, double sMinA, double sMaxA, double sMinB, double sMaxB, double sMinC, double sMaxC) {
        double maxMovement = VoxelShapeAlignedCuboid.limitMovement(maxDist, segmentsA, sMinA, sMaxA, bMinA, bMaxA);
        if (maxMovement != maxDist && hasOverlapFIE(sMinB, sMaxB, bMinB, bMaxB) && hasOverlapFIE(sMinC, sMaxC, bMinC, bMaxC)) {
            return maxMovement;
        }
        return maxDist;
    }

    private static double limitMovement(double maxDist, int segments, double sMin, double sMax, double bMin, double bMax) {
        double maxMovement;

        if (maxDist > 0.0D) {
            maxMovement = sMin - bMax;

            if (maxDist < maxMovement) {
                //outside the shape and still far enough away for no collision at all
                return maxDist;
            }
            double max = bMax - EPSILON;
            if (!(max < sMin)) {
                //already far enough inside this shape to not collide with the surface
                //Vanilla: Shrink box by EPSILON, then use coord < voxelShapeBoundary as boundary

                //Now the extra inner walls (due to segments) have to checked
                if (segments == 1) {
                    return maxDist;
                }
                int nextWallIndex = findIndex(max, segments) + 1; // findIndex returns the lower wall, +1 as this is towards positive
                //The outermost walls (non-inner wall) only have collision if movement direction is towards the shape from the outside
                double wall = nextWallIndex / (double) segments;
                boolean isNotBackWall = wall < sMax - LARGE_EPSILON;
                if (isNotBackWall) {
                    return Math.min(maxDist, wall - bMax);
                }
                return maxDist;
            }
            //allow moving up to the shape but not into it. This also includes going backwards by at most EPSILON.
        } else {
            maxMovement = sMax - bMin;

            if (maxDist > maxMovement) {
                //outside the shape and still far enough away for no collision at all
                return maxDist;
            }
            double min = bMin + EPSILON;
            if (min < sMax) {
                //already far enough inside this shape to not collide with the surface
                //Vanilla: Shrink box by EPSILON, then use coord < voxelShapeBoundary as boundary

                //Now the extra inner walls (due to segments) have to checked
                if (segments == 1) {
                    return maxDist;
                }
                int nextWallIndex = findIndex(min, segments); // findIndex returns the lower wall, no +1 as this is towards negative
                //The outermost walls (non-inner wall) only have collision if movement direction is towards the shape from the outside
                double wall = nextWallIndex / (double) segments;
                boolean isNotBackWall = wall > sMin + LARGE_EPSILON; //Wall #0 is the negative outer wall
                if (isNotBackWall) {
                    return Math.max(maxDist, wall - bMin);
                }
                return maxDist;
            }
            //allow moving up to the shape but not into it. This also includes going backwards by at most EPSILON.
        }
        return maxMovement;
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return switch (axis) {
            case X -> new CubePointRange(this.getXSegments());
            case Y -> new CubePointRange(this.getYSegments());
            case Z -> new CubePointRange(this.getZSegments());
        };
    }

    @Override
    public double get(Direction.Axis axis, int index) {
        return switch (axis) {
            case X -> (double) index / (double) this.getXSegments();
            case Y -> (double) index / (double) this.getYSegments();
            case Z -> (double) index / (double) this.getZSegments();
        };
    }

    @Override
    protected int findIndex(Direction.Axis axis, double coord) {
        int segments = switch (axis) {
            case X -> this.getXSegments();
            case Y -> this.getYSegments();
            case Z -> this.getZSegments();
        };
        return findIndex(coord, segments);
    }

    private static int findIndex(double coord, int segments) {
        //Add some epsilon to avoid underestimating the index here.
        int index = Mth.floor((coord + EPSILON) * (double) segments);

        //Perform the vanilla check from the binary search once, since index could be slightly over the boundary due to floating point rounding error (and added some epsilon)
        double boundary = index / (double) segments;
        if (coord < boundary) {
            index--;
        }
        //No need to check underestimated index, since some epsilon was added above to avoid underestimation.

        return Mth.clamp(index, -1, segments);
    }

    protected int getXSegments() {
        return 1 << (this.xyzResolution >>> 4);
    }

    protected int getYSegments() {
        return 1 << (this.xyzResolution >>> 2 & 3);
    }

    protected int getZSegments() {
        return 1 << (this.xyzResolution & 3);
    }
}
