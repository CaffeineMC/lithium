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
            this.xOffset = ((VoxelShapeAlignedCuboidOffset) originalShape).xOffset + xOffset; //TODO the float addition here might cause non-vanilla floating point errors
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
                    limitMovement(maxDist, this.minX, this.maxX, this.getXSegments(), this.xOffset, moving.minX, moving.maxX, this.minY, moving.maxY, moving.minY, this.maxY, this.minZ, moving.maxZ, moving.minZ, this.maxZ);
            case FORWARD ->
                    limitMovement(maxDist, this.minZ, this.maxZ, this.getZSegments(), this.zOffset, moving.minZ, moving.maxZ, this.minX, moving.maxX, moving.minX, this.maxX, this.minY, moving.maxY, moving.minY, this.maxY);
            case BACKWARD ->
                    limitMovement(maxDist, this.minY, this.maxY, this.getYSegments(), this.yOffset, moving.minY, moving.maxY, this.minZ, moving.maxZ, moving.minZ, this.maxZ, this.minX, moving.maxX, moving.minX, this.maxX);
        };
    }

    private static double limitMovement(double maxDist, double sMinA, double sMaxA, int segmentsA, double offsetA, double bMinA, double bMaxA, double sMinB, double bMaxB, double bMinB, double sMaxB, double sMinC, double bMaxC, double bMinC, double sMaxC) {
        double maxMovement = VoxelShapeAlignedCuboidOffset.limitMovement(sMinA, sMaxA, segmentsA, offsetA, bMinA, bMaxA, maxDist);
        if ((maxMovement != maxDist) && hasOverlap(sMinB, sMaxB, bMinB, bMaxB) && hasOverlap(sMinC, sMaxC, bMinC, bMaxC)) {
            return maxMovement;
        }
        return maxDist;
    }

    /**
     * Determine how far the movement is possible.
     */
    private static double limitMovement(double aMin, double aMax, final int segmentsPerUnit, double shapeOffset, double bMin, double bMax, double maxDist) {
        double gap;

        if (maxDist > 0.0D) {
            gap = aMin - bMax;

            if (gap >= -EPSILON) {
                //outside the shape/within margin, move up to/back to boundary
                return Math.min(gap, maxDist);
            } else {
                //already far enough inside this shape to not collide with the surface
                if (segmentsPerUnit == 1) {
                    //no extra segments to collide with, because only one segment in total
                    return maxDist;
                }
                //extra segment walls / hitboxes inside this shape, evenly spaced out in 0..1 + shapeOffset
                //round to the next segment wall, but with epsilon margin like vanilla

                //using large epsilon and extra check here because +- shapeOffset can cause larger floating point errors
                int segment = Mth.ceil((bMax - LARGE_EPSILON - shapeOffset) * segmentsPerUnit);
                double wallPos = segment / (double) segmentsPerUnit + shapeOffset;
                if (wallPos < bMax - EPSILON) {
                    ++segment;
                    wallPos = segment / (double) segmentsPerUnit + shapeOffset;
                }
                //only use the wall when it is actually inside the shape, and not a border / outside the shape
                if (wallPos < aMax - LARGE_EPSILON) {
                    return Math.min(maxDist, wallPos - bMax);
                }
                return maxDist;
            }
        } else {
            //whole code again, just negated for the other direction
            gap = aMax - bMin;

            if (gap <= EPSILON) {
                //outside the shape/within margin, move up to/back to boundary
                return Math.max(gap, maxDist);
            } else {
                //already far enough inside this shape to not collide with the surface
                if (segmentsPerUnit == 1) {
                    //no extra segments to collide with, because only one segment in total
                    return maxDist;
                }
                //extra segment walls / hitboxes inside this shape, evenly spaced out in 0..1
                //round to the next segment wall, but with epsilon margin like vanilla

                //using large epsilon and extra check here because +- shapeOffset can cause larger floating point errors
                int segment = Mth.floor((bMin + LARGE_EPSILON - shapeOffset) * segmentsPerUnit);
                double wallPos = segment / (double) segmentsPerUnit + shapeOffset;
                if (wallPos > bMin + EPSILON) {
                    --segment;
                    wallPos = segment / (double) segmentsPerUnit + shapeOffset;
                }
                //only use the wall when it is actually inside the shape, and not a border / outside the shape
                if (wallPos > aMin + LARGE_EPSILON) {
                    return Math.max(maxDist, wallPos - bMin);
                }
                return maxDist;
            }
        }
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
    protected int findIndex(Direction.Axis axis, double coord) {
        int numSegments;
        coord = switch (axis) {
            case X -> (coord - this.xOffset) * (numSegments = this.getXSegments());
            case Y -> (coord - this.yOffset) * (numSegments = this.getYSegments());
            case Z -> (coord - this.zOffset) * (numSegments = this.getZSegments());
        };
        return Mth.clamp(Mth.floor(coord), -1, numSegments);
    }
}
