package net.caffeinemc.mods.lithium.common.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import static net.minecraft.core.Direction.Axis.*;

public class VoxelShapeMatchesAnywhere {

    public static int cuboidMatchesAnywhere(VoxelShape shapeA, VoxelShape shapeB, BooleanOp predicate) {
        //calling this method only if both shapes are not empty and have bounding box overlap

        if (shapeA instanceof VoxelShapeSimpleCube && shapeB instanceof VoxelShapeSimpleCube) {
            if (((VoxelShapeSimpleCube) shapeA).isTiny || ((VoxelShapeSimpleCube) shapeB).isTiny) {
                //vanilla fallback: Handling this special case would mean using the whole
                //pointPosition merging code in SimplePairList. A tiny shape can have very odd effects caused by
                //having three pointPositions within 2e-7 or even 1e-7. Vanilla merges the point positions
                //by always taking the most negative one and skipping those with less than 1e-7 distance to it.
                //The optimization partially relies on only having to check the previous point position, which is
                //not possible when 3 or more are within 2e-7 of another, as the previous point position could have
                //been skipped by the merging code.
                return -1;
            }
            //both shapes are simple cubes, matching two cubes anywhere is really simple. Also handle epsilon margins.
            if (predicate.apply(true, true)) {
                if (intersects((VoxelShapeSimpleCube) shapeA, (VoxelShapeSimpleCube) shapeB)) {
                    return 1;
                }
                return (predicate.apply(true, false) || predicate.apply(false, true)) ? 1 : 0;
            } else if (predicate.apply(true, false) &&
                    exceedsCube((VoxelShapeSimpleCube) shapeA, (VoxelShapeSimpleCube) shapeB)) {
                return 1;
            } else if (predicate.apply(false, true) &&
                    exceedsCube((VoxelShapeSimpleCube) shapeB, (VoxelShapeSimpleCube) shapeA)) {
                return 1;
            }
            return 0;
        }
        else if (shapeA instanceof VoxelShapeSimpleCube || shapeB instanceof VoxelShapeSimpleCube) {
            //only one of the two shapes is a simple cube, but there are still some shortcuts that can be taken
            VoxelShapeSimpleCube simpleCube = (VoxelShapeSimpleCube) (shapeA instanceof VoxelShapeSimpleCube ? shapeA : shapeB);
            VoxelShape otherShape = simpleCube == shapeA ? shapeB : shapeA;

            if (simpleCube.isTiny || isTiny(otherShape)) {
                //vanilla fallback, same reason as above
                return -1;
            }

            boolean acceptSimpleCubeAlone = predicate.apply(shapeA == simpleCube, shapeB == simpleCube);
            //test the area outside otherShape
            if (acceptSimpleCubeAlone && exceedsCube(simpleCube,
                    otherShape.min(X), otherShape.min(Y), otherShape.min(Z),
                    otherShape.max(X), otherShape.max(Y), otherShape.max(Z))) {
                return 1;
            }
            boolean acceptAnd = predicate.apply(true, true);
            boolean acceptOtherShapeAlone = predicate.apply(shapeA == otherShape, shapeB == otherShape);

            //test the area inside otherShape
            DiscreteVoxelShape voxelSet = otherShape.shape;
            DoubleList pointPositionsX = otherShape.getCoords(X);
            DoubleList pointPositionsY = otherShape.getCoords(Y);
            DoubleList pointPositionsZ = otherShape.getCoords(Z);

            int xMax = voxelSet.lastFull(X); // xMax <= pointPositionsX.size()
            int yMax = voxelSet.lastFull(Y);
            int zMax = voxelSet.lastFull(Z);

            //keep the cube positions in local vars to avoid looking them up all the time
            double simpleCubeMaxX = simpleCube.max(X);
            double simpleCubeMinX = simpleCube.min(X);
            double simpleCubeMaxY = simpleCube.max(Y);
            double simpleCubeMinY = simpleCube.min(Y);
            double simpleCubeMaxZ = simpleCube.max(Z);
            double simpleCubeMinZ = simpleCube.min(Z);

            //iterate over all entries of the VoxelSet
            for (int x = voxelSet.firstFull(X); x < xMax; x++) {
                //all of the positions of +1e-7 and -1e-7 and >, >=, <, <= are carefully chosen:
                //for example for the following line:                       >= here fails the test
                //                                        moving the - 1e-7 here to the other side of > as + 1e-7 fails the test
                boolean simpleCubeIntersectsXSlice = (simpleCubeMaxX - 1e-7 > pointPositionsX.getDouble(x) && simpleCubeMinX < pointPositionsX.getDouble(x + 1) - 1e-7);
                if (!acceptOtherShapeAlone && !simpleCubeIntersectsXSlice) {
                    //if we cannot return when the simple cube is not intersecting the area, skip forward
                    continue;
                }
                boolean xSliceExceedsCube = acceptOtherShapeAlone && !((simpleCubeMaxX >= pointPositionsX.getDouble(x + 1) - 1e-7 && simpleCubeMinX - 1e-7 <= pointPositionsX.getDouble(x)));
                for (int y = voxelSet.firstFull(Y); y < yMax; y++) {
                    boolean simpleCubeIntersectsYSlice = (simpleCubeMaxY - 1e-7 > pointPositionsY.getDouble(y) && simpleCubeMinY < pointPositionsY.getDouble(y + 1) - 1e-7);
                    if (!acceptOtherShapeAlone && !simpleCubeIntersectsYSlice) {
                        //if we cannot return when the simple cube is not intersecting the area, skip forward
                        continue;
                    }
                    boolean ySliceExceedsCube = acceptOtherShapeAlone && !((simpleCubeMaxY >= pointPositionsY.getDouble(y + 1) - 1e-7 && simpleCubeMinY - 1e-7 <= pointPositionsY.getDouble(y)));
                    for (int z = voxelSet.firstFull(Z); z < zMax; z++) {
                        boolean simpleCubeIntersectsZSlice = (simpleCubeMaxZ - 1e-7 > pointPositionsZ.getDouble(z) && simpleCubeMinZ < pointPositionsZ.getDouble(z + 1) - 1e-7);
                        if (!acceptOtherShapeAlone && !simpleCubeIntersectsZSlice) {
                            //if we cannot return when the simple cube is not intersecting the area, skip forward
                            continue;
                        }
                        boolean zSliceExceedsCube = acceptOtherShapeAlone && !((simpleCubeMaxZ >= pointPositionsZ.getDouble(z + 1) - 1e-7 && simpleCubeMinZ - 1e-7 <= pointPositionsZ.getDouble(z)));

                        boolean o = voxelSet.isFullWide(x, y, z);
                        boolean s = simpleCubeIntersectsXSlice && simpleCubeIntersectsYSlice && simpleCubeIntersectsZSlice;
                        if (acceptAnd && o && s || acceptSimpleCubeAlone && !o && s || acceptOtherShapeAlone && o && (xSliceExceedsCube || ySliceExceedsCube || zSliceExceedsCube)) {
                            return 1;
                        }
                    }
                }
            }
            return 0;
        }
        return -1;
    }

    private static boolean isTiny(VoxelShape shapeA) {
        //avoid properties of SimplePairList, really close point positions are subject to special merging behavior
        return shapeA.min(X) > shapeA.max(X) - 3e-7 ||
                shapeA.min(Y) > shapeA.max(Y) - 3e-7 ||
                shapeA.min(Z) > shapeA.max(Z) - 3e-7;
    }

    /**
     * {@link net.minecraft.world.phys.shapes.IndirectMerger} merges the two VoxelShapes' lattice boundaries with 1e-7
     * margins. When two boundaries are very close to each other, it removes one of the two.
     * The merge logic is, given boundary x from the first shape and y from the second shape:
     * <p>
     * IF x < y + eps
     *     IF x < y - eps
     *         NO MERGE
     *     ELSE
     *         MERGE
     * ELSE
     *     IF y < x - eps
     *         NO MERGE
     *     ELSE
     *         MERGE
     * <p>
     * In total:
     * boolean keepBoth = ((x < y + eps) && (x < y - eps)) || ((y + eps <= x) && (y < x - eps));
     * This can be simplified to (checked with z3):
     * boolean keepBoth = (x < y - eps) || (y < x - eps);
     * <p>
     * When not keeping both boundaries, they fall into the same location logically, thus preventing exceeding or intersecting.
     * <p>
     * Luckily, this means that the resulting methods are symmetric regarding the two VoxelShape arguments
     */
    private static boolean exceedsCube(VoxelShapeSimpleCube a, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return  a.min(X) < minX - 1e-7 || maxX < a.max(X) - 1e-7 ||
                a.min(Y) < minY - 1e-7 || maxY < a.max(Y) - 1e-7 ||
                a.min(Z) < minZ - 1e-7 || maxZ < a.max(Z) - 1e-7;
    }

    private static boolean exceedsCube(VoxelShapeSimpleCube a, VoxelShapeSimpleCube b) {
        return a.min(X) < b.min(X) - 1e-7 || b.max(X) < a.max(X) - 1e-7 ||
                a.min(Y) < b.min(Y) - 1e-7 || b.max(Y) < a.max(Y) - 1e-7 ||
                a.min(Z) < b.min(Z) - 1e-7 || b.max(Z) < a.max(Z) - 1e-7;
    }

    private static boolean intersects(VoxelShapeSimpleCube a, VoxelShapeSimpleCube b) {
        return  a.min(X) < b.max(X) - 1e-7 && b.min(X) < a.max(X) - 1e-7 &&
                a.min(Y) < b.max(Y) - 1e-7 && b.min(Y) < a.max(Y) - 1e-7 &&
                a.min(Z) < b.max(Z) - 1e-7 && b.min(Z) < a.max(Z) - 1e-7;
    }
}