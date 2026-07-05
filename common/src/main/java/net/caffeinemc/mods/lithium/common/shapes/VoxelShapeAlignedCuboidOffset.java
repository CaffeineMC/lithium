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
     */
    private static double limitMovement(double maxDist, int segments, double shapeOffset, double sMin, double sMax, double bMin, double bMax) {
        double maxMovement;

        if (maxDist > 0.0D) {
            maxMovement = aMin - bMax;

            if (maxMovement >= -EPSILON) {
                //outside the shape/within margin, move up to/back to boundary
                return Math.min(maxMovement, maxDist);
            } else {
                //already far enough inside this shape to not collide with the surface
                if (segments == 1) {
                    //no extra segments to collide with, because only one segment in total
                    return maxDist;
                }
                //extra segment walls / hitboxes inside this shape, evenly spaced out in 0..1 + shapeOffset
                //round to the next segment wall, but with epsilon margin like vanilla

                //using large epsilon and extra check here because +- shapeOffset can cause larger floating point errors
                int segment = Mth.ceil((bMax - LARGE_EPSILON - shapeOffset) * segments);
                double wallPos = segment / (double) segments + shapeOffset;
                if (wallPos < bMax - EPSILON) {
                    ++segment;
                    wallPos = segment / (double) segments + shapeOffset;
                }
                //only use the wall when it is actually inside the shape, and not a border / outside the shape
                if (wallPos < aMax - LARGE_EPSILON) {
                    return Math.min(maxDist, wallPos - bMax);
                }
                return maxDist;
            }
        } else {
            //whole code again, just negated for the other direction
            maxMovement = aMax - bMin;

            if (maxMovement <= EPSILON) {
                //outside the shape/within margin, move up to/back to boundary
                return Math.max(maxMovement, maxDist);
            } else {
                //already far enough inside this shape to not collide with the surface
                if (segments == 1) {
                    //no extra segments to collide with, because only one segment in total
                    return maxDist;
                }
                //extra segment walls / hitboxes inside this shape, evenly spaced out in 0..1
                //round to the next segment wall, but with epsilon margin like vanilla

                //using large epsilon and extra check here because +- shapeOffset can cause larger floating point errors
                int segment = Mth.floor((bMin + LARGE_EPSILON - shapeOffset) * segments);
                double wallPos = segment / (double) segments + shapeOffset;
                if (wallPos > bMin + EPSILON) {
                    --segment;
                    wallPos = segment / (double) segments + shapeOffset;
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
        return switch (axis) {
            case X -> findIndex(coord, this.xOffset, this.getXSegments());
            case Y -> findIndex(coord, this.yOffset, this.getYSegments());
            case Z -> findIndex(coord, this.zOffset, this.getZSegments());
        };
    }

    private static int findIndex(double coord, double offset, int segments) {
        return Mth.clamp(Mth.floor((coord - offset) * segments), -1, segments);
    }
}
