package shapes;

import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeCuboidDifference;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import util.TestUtils;

import java.util.List;
import java.util.Random;

/**
 * Simulates the shape operation sequence of vanilla jigsaw structure placement
 * ({@code JigsawPlacement.Placer#tryPlacingChildren}) and checks that the {@link VoxelShapeCuboidDifference}
 * fast paths produce exactly the same results as the vanilla voxel-based shape operations they replace.
 */
public class JigsawFreeSpaceTest {

    private static final boolean CAN_INVOKE_VANILLA_CODE = !TestUtils.IS_MIXIN_LOADED;

    private static final int CHAIN_COUNT = 12;
    private static final int PIECES_PER_CHAIN = 40;

    /**
     * Mirrors the candidate rejection query of the jigsaw_free_space mixin, including the vanilla fallback.
     */
    private static boolean lithiumPieceRejected(VoxelShape freeSpace, VoxelShape candidate) {
        if (freeSpace instanceof VoxelShapeCuboidDifference cuboidDifference) {
            int result = cuboidDifference.exceedsFreeSpace(candidate);
            if (result != VoxelShapeCuboidDifference.FALLBACK) {
                return result == VoxelShapeCuboidDifference.EXCEEDS;
            }
        }
        return Shapes.joinIsNotEmpty(VoxelShapeCuboidDifference.unwrap(freeSpace), VoxelShapeCuboidDifference.unwrap(candidate), BooleanOp.ONLY_SECOND);
    }

    /**
     * Mirrors the free space subtraction of the jigsaw_free_space mixin, including the vanilla fallback.
     */
    private static VoxelShape lithiumSubtract(VoxelShape freeSpace, VoxelShape placedPiece) {
        if (freeSpace instanceof VoxelShapeCuboidDifference cuboidDifference) {
            VoxelShape result = cuboidDifference.trySubtract(placedPiece);
            if (result != null) {
                return result;
            }
        }
        return Shapes.joinUnoptimized(VoxelShapeCuboidDifference.unwrap(freeSpace), VoxelShapeCuboidDifference.unwrap(placedPiece), BooleanOp.ONLY_FIRST);
    }

    private static void assertShapesEqual(VoxelShape expected, VoxelShape actual, String message) {
        Assertions.assertEquals(expected.isEmpty(), actual.isEmpty(), () -> message + " (isEmpty)");
        List<AABB> expectedBoxes = expected.toAabbs();
        List<AABB> actualBoxes = actual.toAabbs();
        Assertions.assertEquals(expectedBoxes, actualBoxes, () -> message + " (toAabbs)");
    }

    private static AABB randomPieceBox(Random random, int radius) {
        int sizeX = 1 + random.nextInt(8);
        int sizeY = 1 + random.nextInt(8);
        int sizeZ = 1 + random.nextInt(8);
        //also generate boxes that stick out of or lie outside the container
        int minX = random.nextInt(-radius - 4, radius + 5 - sizeX);
        int minY = random.nextInt(-radius - 4, radius + 5 - sizeY);
        int minZ = random.nextInt(-radius - 4, radius + 5 - sizeZ);
        return new AABB(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ);
    }

    /**
     * Runs one simulated placement: both free space representations start from the same initial shape and receive
     * the same query + subtraction sequence, checking equal results after every step.
     */
    private static void runPlacementChain(VoxelShape vanillaFreeSpace, VoxelShape lithiumFreeSpace, Random random, int radius, String testName) {
        for (int piece = 0; piece < PIECES_PER_CHAIN; piece++) {
            AABB pieceBox = randomPieceBox(random, radius);
            String message = testName + ", piece " + piece + ": " + pieceBox;
            //materializing the lithium shape replays the whole subtraction chain, so only check it occasionally
            boolean checkMaterialized = piece % 13 == 0 || piece == PIECES_PER_CHAIN - 1;

            VoxelShape deflatedCandidate = Shapes.create(pieceBox.deflate(0.25));
            boolean vanillaRejected = Shapes.joinIsNotEmpty(vanillaFreeSpace, deflatedCandidate, BooleanOp.ONLY_SECOND);
            boolean lithiumRejected = lithiumPieceRejected(lithiumFreeSpace, deflatedCandidate);
            Assertions.assertEquals(vanillaRejected, lithiumRejected, () -> message + " (rejection query)");

            if (checkMaterialized) {
                //vanilla code unaware of the replaced shape class must see the same shape (exercises the delegation
                //to the materialized shape, including direct DiscreteVoxelShape accesses inside joinIsNotEmpty)
                boolean lithiumRejectedVanillaPath = Shapes.joinIsNotEmpty(lithiumFreeSpace, deflatedCandidate, BooleanOp.ONLY_SECOND);
                Assertions.assertEquals(vanillaRejected, lithiumRejectedVanillaPath, () -> message + " (rejection query via vanilla code)");
            }

            if (!vanillaRejected) {
                VoxelShape placedPiece = Shapes.create(pieceBox);
                vanillaFreeSpace = Shapes.joinUnoptimized(vanillaFreeSpace, placedPiece, BooleanOp.ONLY_FIRST);
                lithiumFreeSpace = lithiumSubtract(lithiumFreeSpace, placedPiece);
                if (checkMaterialized) {
                    assertShapesEqual(vanillaFreeSpace, VoxelShapeCuboidDifference.unwrap(lithiumFreeSpace), message + " (free space after subtraction)");
                }
            }
        }
        assertShapesEqual(vanillaFreeSpace, VoxelShapeCuboidDifference.unwrap(lithiumFreeSpace), testName + " (final free space)");
    }

    @Test
    void testSingleCuboidFreeSpace() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        Random random = new Random(TestUtils.SEED);
        for (int chain = 0; chain < CHAIN_COUNT; chain++) {
            int radius = 8 + random.nextInt(9);
            AABB container = new AABB(-radius, -radius, -radius, radius, radius, radius);

            //like the piece interior free space: Shapes.create(AABB.of(pieceBoundingBox))
            VoxelShape lithiumFreeSpace = VoxelShapeCuboidDifference.tryCreate(container);
            Assertions.assertNotNull(lithiumFreeSpace, "aligned container must take the fast path");

            runPlacementChain(Shapes.create(container), lithiumFreeSpace, random, radius, "testSingleCuboidFreeSpace, chain " + chain);
        }
    }

    @Test
    void testConvertedFreeSpace() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        Random random = new Random(TestUtils.SEED + 1);
        for (int chain = 0; chain < CHAIN_COUNT; chain++) {
            int radius = 8 + random.nextInt(9);
            AABB container = new AABB(-radius, -radius, -radius, radius, radius, radius);
            AABB startPieceBox = randomPieceBox(random, radius - 4);

            //like the initial jigsaw free space: max range box minus the start piece's box
            VoxelShape initialFreeSpace = Shapes.join(Shapes.create(container), Shapes.create(startPieceBox), BooleanOp.ONLY_FIRST);
            VoxelShape lithiumFreeSpace = VoxelShapeCuboidDifference.tryConvert(initialFreeSpace);
            Assertions.assertInstanceOf(VoxelShapeCuboidDifference.class, lithiumFreeSpace, "aligned initial free space must be converted");
            assertShapesEqual(initialFreeSpace, VoxelShapeCuboidDifference.unwrap(lithiumFreeSpace), "converted free space");

            runPlacementChain(initialFreeSpace, lithiumFreeSpace, random, radius, "testConvertedFreeSpace, chain " + chain);
        }
    }

    @Test
    void testSharedListDivergence() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        //vanilla keeps the free space of the parent piece around and reuses it for multiple children,
        //so subtracting from the same instance twice (diverging chains) must not corrupt either chain
        AABB container = new AABB(-16, -16, -16, 16, 16, 16);
        VoxelShape vanillaBase = Shapes.create(container);
        VoxelShapeCuboidDifference lithiumBase = VoxelShapeCuboidDifference.tryCreate(container);
        Assertions.assertNotNull(lithiumBase);

        VoxelShape vanillaCommon = Shapes.joinUnoptimized(vanillaBase, Shapes.create(new AABB(-8, -8, -8, -2, -2, -2)), BooleanOp.ONLY_FIRST);
        VoxelShape lithiumCommon = lithiumSubtract(lithiumBase, Shapes.create(new AABB(-8, -8, -8, -2, -2, -2)));

        VoxelShape vanillaBranchA = Shapes.joinUnoptimized(vanillaCommon, Shapes.create(new AABB(0, 0, 0, 4, 4, 4)), BooleanOp.ONLY_FIRST);
        VoxelShape lithiumBranchA = lithiumSubtract(lithiumCommon, Shapes.create(new AABB(0, 0, 0, 4, 4, 4)));
        VoxelShape vanillaBranchB = Shapes.joinUnoptimized(vanillaCommon, Shapes.create(new AABB(5, 5, 5, 9, 9, 9)), BooleanOp.ONLY_FIRST);
        VoxelShape lithiumBranchB = lithiumSubtract(lithiumCommon, Shapes.create(new AABB(5, 5, 5, 9, 9, 9)));

        //branch A must not see branch B's subtraction and vice versa
        VoxelShape candidateInB = Shapes.create(new AABB(5, 5, 5, 9, 9, 9).deflate(0.25));
        Assertions.assertFalse(lithiumPieceRejected(lithiumBranchA, candidateInB), "branch A must not contain branch B's subtraction");
        VoxelShape candidateInA = Shapes.create(new AABB(0, 0, 0, 4, 4, 4).deflate(0.25));
        Assertions.assertFalse(lithiumPieceRejected(lithiumBranchB, candidateInA), "branch B must not contain branch A's subtraction");
        Assertions.assertTrue(lithiumPieceRejected(lithiumBranchA, candidateInA), "branch A must contain its own subtraction");
        Assertions.assertTrue(lithiumPieceRejected(lithiumBranchB, candidateInB), "branch B must contain its own subtraction");

        assertShapesEqual(vanillaBranchA, VoxelShapeCuboidDifference.unwrap(lithiumBranchA), "branch A");
        assertShapesEqual(vanillaBranchB, VoxelShapeCuboidDifference.unwrap(lithiumBranchB), "branch B");
        assertShapesEqual(vanillaCommon, VoxelShapeCuboidDifference.unwrap(lithiumCommon), "common chain after divergence");
    }

    @Test
    void testBoundaryTouchingCandidates() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        AABB container = new AABB(0, 0, 0, 16, 16, 16);
        VoxelShape vanillaFreeSpace = Shapes.create(container);
        VoxelShapeCuboidDifference lithiumFreeSpace = VoxelShapeCuboidDifference.tryCreate(container);
        Assertions.assertNotNull(lithiumFreeSpace);

        AABB[] candidates = new AABB[]{
                new AABB(0, 0, 0, 16, 16, 16), //exactly the container
                new AABB(0, 0, 0, 16, 16, 16).deflate(0.25), //the container after deflation
                new AABB(0, 0, 0, 4, 4, 4), //touching the min corner
                new AABB(12, 12, 12, 16, 16, 16), //touching the max corner
                new AABB(-0.25, 0, 0, 4, 4, 4), //sticking out by a quarter block
                new AABB(12, 12, 12, 16.25, 16, 16), //sticking out by a quarter block
                new AABB(-4, -4, -4, 0, 0, 0), //outside, sharing only the min corner point
                new AABB(16, 16, 16, 20, 20, 20), //outside, sharing only the max corner point
                new AABB(-8, -8, -8, -4, -4, -4), //fully outside
        };
        for (AABB candidateBox : candidates) {
            VoxelShape candidate = Shapes.create(candidateBox);
            boolean vanillaRejected = Shapes.joinIsNotEmpty(vanillaFreeSpace, candidate, BooleanOp.ONLY_SECOND);
            Assertions.assertEquals(vanillaRejected, lithiumPieceRejected(lithiumFreeSpace, candidate), "candidate " + candidateBox);
        }

        //subtract a piece and test candidates touching the subtracted piece
        AABB placedBox = new AABB(4, 4, 4, 8, 8, 8);
        VoxelShape vanillaSubtracted = Shapes.joinUnoptimized(vanillaFreeSpace, Shapes.create(placedBox), BooleanOp.ONLY_FIRST);
        VoxelShape lithiumSubtracted = lithiumSubtract(lithiumFreeSpace, Shapes.create(placedBox));
        AABB[] candidatesAfterSubtraction = new AABB[]{
                new AABB(8, 4, 4, 12, 8, 8), //sharing a face with the subtracted piece
                new AABB(8.25, 4, 4, 12, 8, 8), //a quarter block away
                new AABB(7.75, 4, 4, 12, 8, 8), //overlapping by a quarter block
                new AABB(8, 8, 8, 12, 12, 12), //sharing only a corner point
                new AABB(4, 4, 4, 8, 8, 8), //exactly the subtracted piece
                new AABB(5, 5, 5, 7, 7, 7), //fully inside the subtracted piece
        };
        for (AABB candidateBox : candidatesAfterSubtraction) {
            VoxelShape candidate = Shapes.create(candidateBox);
            boolean vanillaRejected = Shapes.joinIsNotEmpty(vanillaSubtracted, candidate, BooleanOp.ONLY_SECOND);
            Assertions.assertEquals(vanillaRejected, lithiumPieceRejected(lithiumSubtracted, candidate), "candidate after subtraction " + candidateBox);
        }
    }

    @Test
    void testFullyCoveredFreeSpace() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        AABB container = new AABB(0, 0, 0, 8, 8, 8);
        VoxelShape vanillaFreeSpace = Shapes.create(container);
        VoxelShape lithiumFreeSpace = VoxelShapeCuboidDifference.tryCreate(container);
        Assertions.assertNotNull(lithiumFreeSpace);

        //cover the entire container with two pieces
        for (AABB placedBox : new AABB[]{new AABB(0, 0, 0, 8, 4, 8), new AABB(0, 4, 0, 8, 8, 8)}) {
            VoxelShape placedPiece = Shapes.create(placedBox);
            vanillaFreeSpace = Shapes.joinUnoptimized(vanillaFreeSpace, placedPiece, BooleanOp.ONLY_FIRST);
            lithiumFreeSpace = lithiumSubtract(lithiumFreeSpace, placedPiece);
        }
        assertShapesEqual(vanillaFreeSpace, VoxelShapeCuboidDifference.unwrap(lithiumFreeSpace), "fully covered free space");

        VoxelShape candidate = Shapes.create(new AABB(2, 2, 2, 4, 4, 4).deflate(0.25));
        boolean vanillaRejected = Shapes.joinIsNotEmpty(vanillaFreeSpace, candidate, BooleanOp.ONLY_SECOND);
        Assertions.assertTrue(vanillaRejected);
        Assertions.assertEquals(vanillaRejected, lithiumPieceRejected(lithiumFreeSpace, candidate), "candidate in fully covered free space");
    }

    @Test
    void testUnalignedFallback() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        //unaligned coordinates must never take the fast paths
        AABB unalignedContainer = new AABB(0.1, 0, 0, 16, 16, 16);
        Assertions.assertNull(VoxelShapeCuboidDifference.tryCreate(unalignedContainer), "unaligned container must not take the fast path");
        VoxelShape unalignedShape = Shapes.create(unalignedContainer);
        Assertions.assertSame(unalignedShape, VoxelShapeCuboidDifference.tryConvert(unalignedShape), "unaligned free space must not be converted");

        AABB hugeContainer = new AABB(0, 0, 0, 2.0E9, 16, 16);
        Assertions.assertNull(VoxelShapeCuboidDifference.tryCreate(hugeContainer), "huge container must not take the fast path");

        //unaligned candidates fall back to vanilla behavior on an aligned free space
        AABB container = new AABB(0, 0, 0, 16, 16, 16);
        VoxelShapeCuboidDifference lithiumFreeSpace = VoxelShapeCuboidDifference.tryCreate(container);
        Assertions.assertNotNull(lithiumFreeSpace);
        VoxelShape vanillaFreeSpace = Shapes.create(container);

        AABB unalignedCandidateBox = new AABB(0.1, 0.1, 0.1, 4, 4, 4);
        VoxelShape unalignedCandidate = Shapes.create(unalignedCandidateBox);
        Assertions.assertEquals(VoxelShapeCuboidDifference.FALLBACK, lithiumFreeSpace.exceedsFreeSpace(unalignedCandidate));
        boolean vanillaRejected = Shapes.joinIsNotEmpty(vanillaFreeSpace, unalignedCandidate, BooleanOp.ONLY_SECOND);
        Assertions.assertEquals(vanillaRejected, lithiumPieceRejected(lithiumFreeSpace, unalignedCandidate), "unaligned candidate fallback");

        Assertions.assertNull(lithiumFreeSpace.trySubtract(unalignedCandidate), "unaligned piece must not take the subtraction fast path");
        VoxelShape vanillaSubtracted = Shapes.joinUnoptimized(vanillaFreeSpace, unalignedCandidate, BooleanOp.ONLY_FIRST);
        VoxelShape lithiumSubtracted = lithiumSubtract(lithiumFreeSpace, unalignedCandidate);
        assertShapesEqual(vanillaSubtracted, VoxelShapeCuboidDifference.unwrap(lithiumSubtracted), "unaligned piece subtraction fallback");
    }
}
