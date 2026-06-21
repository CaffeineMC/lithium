package net.caffeinemc.mods.lithium.common.shapes.offset_operations;

import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeSimpleCube;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import static net.minecraft.core.Direction.Axis.*;

public class LithiumOffsetShapes {

    public static double axisChoose(Direction.Axis axis, double offsetX, double offsetY, double offsetZ) {
        if (axis == Direction.Axis.X) {
            return offsetX;
        }
        if (axis == Direction.Axis.Y) {
            return offsetY;
        }
        return offsetZ;
    }

    //Copied from VoxelShapesMatchesAnywhere, adapted to work with AABB and offset VoxelShapes without allocations
    public static boolean joinIsNotEmpty(final VoxelShape firstWithoutOffset, double offsetX, double offsetY, double offsetZ, AABB aabb, @Nullable VoxelShape aabbAsShape, final BooleanOp op) {
        final boolean firstEmpty = firstWithoutOffset.isEmpty();
        if (firstEmpty) {
            return op.apply(false, true);
        } else {
            boolean firstOnlyMatters = op.apply(true, false);
            boolean secondOnlyMatters = op.apply(false, true);

            for (Direction.Axis axis : AxisCycle.AXIS_VALUES) {
                double axisOffset = axisChoose(axis, offsetX, offsetY, offsetZ);
                if (firstWithoutOffset.max(axis) + axisOffset < aabb.min(axis) - 1.0E-7) {
                    return firstOnlyMatters || secondOnlyMatters;
                }

                if (aabb.max(axis) < firstWithoutOffset.min(axis) + axisOffset - 1.0E-7) {
                    return firstOnlyMatters || secondOnlyMatters;
                }
            }

            boolean acceptOtherShapeAlone = op.apply(true, false);
            boolean acceptCuboidAlone = op.apply(false, true);
            if (firstWithoutOffset instanceof VoxelShapeSimpleCube) {
                if (!((VoxelShapeSimpleCube) firstWithoutOffset).isTiny) {
                    if (op.apply(true, true)) {
                        if (intersects((VoxelShapeSimpleCube) firstWithoutOffset, offsetX, offsetY, offsetZ, aabb)) {
                            return true;
                        } else {
                            return acceptOtherShapeAlone || acceptCuboidAlone;
                        }
                    } else if (acceptOtherShapeAlone && aExceedsHullOfB(firstWithoutOffset, offsetX, offsetY, offsetZ, aabb)) {
                        return true;
                    } else {
                        return acceptCuboidAlone && aExceedsHullOfB(aabb, firstWithoutOffset, offsetX, offsetY, offsetZ);
                    }
                }
            } else {
                if (!isTiny(firstWithoutOffset)) {
                    if (acceptOtherShapeAlone) {
                        if (aExceedsHullOfB(firstWithoutOffset, offsetX, offsetY, offsetZ, aabb)) {
                            return true;
                        } else if (op == BooleanOp.ONLY_FIRST) {
                            return false;
                        }
                    }
                    if (acceptCuboidAlone) {
                        if (aExceedsHullOfB(aabb, firstWithoutOffset, offsetX, offsetY, offsetZ)) {
                            return true;
                        }
                    }

                    boolean acceptAnd = op.apply(true, true);
                    DiscreteVoxelShape otherVoxelSet = firstWithoutOffset.shape;
                    int xMax = otherVoxelSet.lastFull(X);
                    int yMin = otherVoxelSet.firstFull(Y);
                    int yMax = otherVoxelSet.lastFull(Y);
                    int zMin = otherVoxelSet.firstFull(Z);
                    int zMax = otherVoxelSet.lastFull(Z);

                    double simpleCubeMaxX = aabb.maxX;
                    double simpleCubeMinX = aabb.minX;
                    double simpleCubeMaxY = aabb.maxY;
                    double simpleCubeMinY = aabb.minY;
                    double simpleCubeMaxZ = aabb.maxZ;
                    double simpleCubeMinZ = aabb.minZ;
                    for (int x = otherVoxelSet.firstFull(X); x < xMax; x++) {
                        if (intersectsSingleAxis(simpleCubeMinX, simpleCubeMaxX, firstWithoutOffset.get(X, x) + offsetX, firstWithoutOffset.get(X, x + 1) + offsetX)) {
                            for (int y = yMin; y < yMax; y++) {
                                if (intersectsSingleAxis(simpleCubeMinY, simpleCubeMaxY, firstWithoutOffset.get(Y, y) + offsetY, firstWithoutOffset.get(Y, y + 1) + offsetY)) {
                                    for (int z = zMin; z < zMax; z++) {
                                        if (intersectsSingleAxis(simpleCubeMinZ, simpleCubeMaxZ, firstWithoutOffset.get(Z, z) + offsetZ, firstWithoutOffset.get(Z, z + 1) + offsetZ)) {
                                            boolean coveredByOther = otherVoxelSet.isFullWide(x, y, z);
                                            if (acceptAnd && coveredByOther || acceptCuboidAlone && !coveredByOther) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return false;
                }
            }
            //Allocating fallback to vanilla only in case shape is non-empty and tiny (< 3e-7) on at least one axis
            VoxelShape first = firstWithoutOffset.move(offsetX, offsetY, offsetZ);
            if (aabbAsShape == null) {
                aabbAsShape = Shapes.create(aabb);
            }
            return Shapes.joinIsNotEmpty(first, aabbAsShape, op);
        }
    }

    private static boolean isTiny(VoxelShape shapeA) {
        return shapeA.min(X) > shapeA.max(X) - 3e-7 ||
                shapeA.min(Y) > shapeA.max(Y) - 3e-7 ||
                shapeA.min(Z) > shapeA.max(Z) - 3e-7;
    }

    private static boolean intersectsSingleAxis(double aMin, double aMax, double bMin, double bMax) {
        return aMin < bMax - 1e-7 && bMin < aMax - 1e-7;
    }


    private static boolean intersects(VoxelShapeSimpleCube a, double offsetX, double offsetY, double offsetZ, @UnknownNullability AABB b) {
        return a.min(X) + offsetX < b.maxX - 1e-7 && b.minX < a.max(X) + offsetX - 1e-7 &&
                a.min(Y) + offsetY < b.maxY - 1e-7 && b.minY < a.max(Y) + offsetY - 1e-7 &&
                a.min(Z) + offsetZ < b.maxZ - 1e-7 && b.minZ < a.max(Z) + offsetZ - 1e-7;
    }

    private static boolean aExceedsHullOfB(AABB a, VoxelShape b, double offsetX, double offsetY, double offsetZ) {
        return a.minX < b.min(X) + offsetX - 1e-7 || b.max(X) + offsetX < a.maxX - 1e-7 ||
                a.minY < b.min(Y) + offsetY - 1e-7 || b.max(Y) + offsetY < a.maxY - 1e-7 ||
                a.minZ < b.min(Z) + offsetZ - 1e-7 || b.max(Z) + offsetZ < a.maxZ - 1e-7;
    }

    private static boolean aExceedsHullOfB(VoxelShape a, double offsetX, double offsetY, double offsetZ, @UnknownNullability AABB b) {
        return a.min(X) + offsetX < b.minX - 1e-7 || b.maxX < a.max(X) + offsetX - 1e-7 ||
                a.min(Y) + offsetY < b.minY - 1e-7 || b.maxY < a.max(Y) + offsetY - 1e-7 ||
                a.min(Z) + offsetZ < b.minZ - 1e-7 || b.maxZ < a.max(Z) + offsetZ - 1e-7;
    }

}
