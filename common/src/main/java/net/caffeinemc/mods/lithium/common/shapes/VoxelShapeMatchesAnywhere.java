package net.caffeinemc.mods.lithium.common.shapes;

import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.core.Direction.Axis.*;

public class VoxelShapeMatchesAnywhere {

    //Note: When changing this, make sure to update LithiumOffsetShapes as well, as it copies/adapts this code!
    public static void cuboidMatchesAnywhere(VoxelShape shapeA, VoxelShape shapeB, BooleanOp predicate, CallbackInfoReturnable<Boolean> cir) {
        //calling this method only if both shapes are not empty and have bounding box overlap

        boolean acceptAAlone = predicate.apply(true, false);
        boolean acceptBAlone = predicate.apply(false, true);
        if (shapeA instanceof VoxelShapeSimpleCube && shapeB instanceof VoxelShapeSimpleCube) {
            if (((VoxelShapeSimpleCube) shapeA).isTiny || ((VoxelShapeSimpleCube) shapeB).isTiny) {
                //vanilla fallback: Handling this special case would mean using the whole
                //pointPosition merging code in SimplePairList. A tiny shape can have very odd effects caused by
                //having three pointPositions within 2e-7 or even 1e-7. Vanilla merges the point positions
                //by always taking the most negative one and skipping those with less than 1e-7 distance to it.
                //The optimization partially relies on only having to check the previous point position, which is
                //not possible when 3 or more are within 2e-7 of another, as the previous point position could have
                //been skipped by the merging code.
                return;
            }
            //both shapes are simple cubes, matching two cubes anywhere is really simple. Also handle epsilon margins.
            if (predicate.apply(true, true)) {
                if (intersects((VoxelShapeSimpleCube) shapeA, (VoxelShapeSimpleCube) shapeB)) {
                    cir.setReturnValue(true);
                    return;
                }
                cir.setReturnValue(acceptAAlone || acceptBAlone);
            } else if (acceptAAlone && aExceedsHullOfB(shapeA, shapeB)) {
                cir.setReturnValue(true);
                return;
            } else if (acceptBAlone && aExceedsHullOfB(shapeB, shapeA)) {
                cir.setReturnValue(true);
                return;
            }
            cir.setReturnValue(false);
        }
        else if (shapeA instanceof VoxelShapeSimpleCube || shapeB instanceof VoxelShapeSimpleCube) {
            //only one of the two shapes is a simple cube, but there are still some shortcuts that can be taken
            VoxelShapeSimpleCube cuboid;
            VoxelShape otherShape;
            boolean cuboidIsA = true;
            if (shapeA instanceof VoxelShapeSimpleCube) {
                cuboid = (VoxelShapeSimpleCube) shapeA;
                otherShape = shapeB;
            } else {
                cuboid = (VoxelShapeSimpleCube) shapeB;
                otherShape = shapeA;
                cuboidIsA = false;
            }


            if (cuboid.isTiny || isTiny(otherShape)) {
                //vanilla fallback, same reason as above
                return;
            }

            //One shape is a simple cuboid, and the outermost (no overestimate!) bounds of the other shape is known
            // Thus a quick check returns if there is one shape exceeds the other. However, it is still possible
            // for the cuboid to exceed the other shape after this.
            if (acceptAAlone) {
                if (aExceedsHullOfB(shapeA, shapeB)) {
                    cir.setReturnValue(true);
                    return;
                } else if (!cuboidIsA && predicate == BooleanOp.ONLY_FIRST) {
                    //Since the cuboid covers its whole hull, the other shape cannot exceed it without exceeding the hull
                    cir.setReturnValue(false);
                    return;
                }
            }
            if (acceptBAlone) {
                if (aExceedsHullOfB(shapeB, shapeA)) {
                    cir.setReturnValue(true);
                    return;
                } else if (cuboidIsA && predicate == BooleanOp.ONLY_SECOND) {
                    //Since the cuboid covers its whole hull, the other shape cannot exceed it without exceeding the hull
                    cir.setReturnValue(false);
                    return;
                }
            }
            //Now it is known that the other shape does not exceed the cuboid
            //While the cuboid only exceeds the other shape if it covers it somewhere it doesn't have its voxelSet set to true

            boolean acceptSimpleCubeAlone = cuboidIsA && acceptAAlone || !cuboidIsA && acceptBAlone;
            boolean acceptAnd = predicate.apply(true, true);

            //test the area inside otherShape
            DiscreteVoxelShape otherVoxelSet = otherShape.shape;

            int xMax = otherVoxelSet.lastFull(X); // xMax <= pointPositionsX.size()
            int yMin = otherVoxelSet.firstFull(Y);
            int yMax = otherVoxelSet.lastFull(Y);
            int zMin = otherVoxelSet.firstFull(Z);
            int zMax = otherVoxelSet.lastFull(Z);

            //keep the cube positions in local vars to avoid looking them up all the time
            double simpleCubeMaxX = cuboid.max(X);
            double simpleCubeMinX = cuboid.min(X);
            double simpleCubeMaxY = cuboid.max(Y);
            double simpleCubeMinY = cuboid.min(Y);
            double simpleCubeMaxZ = cuboid.max(Z);
            double simpleCubeMinZ = cuboid.min(Z);

            //iterate over all entries of the VoxelSet
            for (int x = otherVoxelSet.firstFull(X); x < xMax; x++) {
                if (intersectsSingleAxis(simpleCubeMinX, simpleCubeMaxX, otherShape.get(X, x), otherShape.get(X, x + 1))) {
                    for (int y = yMin; y < yMax; y++) {
                        if (intersectsSingleAxis(simpleCubeMinY, simpleCubeMaxY, otherShape.get(Y, y), otherShape.get(Y, y + 1))) {
                            for (int z = zMin; z < zMax; z++) {
                                if (intersectsSingleAxis(simpleCubeMinZ, simpleCubeMaxZ, otherShape.get(Z, z), otherShape.get(Z, z + 1))) {
                                    boolean coveredByOther = otherVoxelSet.isFullWide(x, y, z);
                                    if (acceptAnd && coveredByOther || acceptSimpleCubeAlone && !coveredByOther) {
                                        cir.setReturnValue(true);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            cir.setReturnValue(false);
        }
    }

    private static boolean isTiny(VoxelShape shapeA) {
        //avoid properties of IndirectMerger, really close point positions are subject to special merging behavior, as
        // multiple (>2) boundaries can be merged into a single one
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
     * This can be simplified to:
     * boolean keepBoth = (x < y - eps) || (y < x - eps);
     * <p>
     * When not keeping both boundaries, they fall into the same location logically, thus preventing exceeding or intersecting.
     * <p>
     * Note: The intersection test is symmetric (including floating point rounding and associativity issues)
     */
    private static boolean intersectsSingleAxis(double aMin, double aMax, double bMin, double bMax) {
        return aMin < bMax - 1e-7 && bMin < aMax - 1e-7;
    }

    public static boolean intersects(VoxelShapeSimpleCube a, VoxelShapeSimpleCube b) {
        return  a.min(X) < b.max(X) - 1e-7 && b.min(X) < a.max(X) - 1e-7 &&
                a.min(Y) < b.max(Y) - 1e-7 && b.min(Y) < a.max(Y) - 1e-7 &&
                a.min(Z) < b.max(Z) - 1e-7 && b.min(Z) < a.max(Z) - 1e-7;
    }

    private static boolean aExceedsHullOfB(VoxelShape a, VoxelShape b) {
        return a.min(X) < b.min(X) - 1e-7 || b.max(X) < a.max(X) - 1e-7 ||
                a.min(Y) < b.min(Y) - 1e-7 || b.max(Y) < a.max(Y) - 1e-7 ||
                a.min(Z) < b.min(Z) - 1e-7 || b.max(Z) < a.max(Z) - 1e-7;
    }
}