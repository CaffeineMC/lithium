package shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeAlignedCuboid;
import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeSimpleCube;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import util.DoubleUtils;
import util.TestUtils;

import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.DoublePredicate;
import java.util.function.Function;

public class RandomizedAABBMoveAgainstCubeTest {


    @Test
    void testMoveAgainstSimpleCube() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape originCube = shapeProvider.apply(position);
            AABB aabb = new AABB(position.x(), position.y() + 2, position.z(), position.x() + 1, position.y() + 3, position.z() + 1);
            TestUtils.assertEquals(1 + position.y() - aabb.minY, () -> originCube.collide(Direction.Axis.Y, aabb, -5), position, "testMoveAgainstSimpleCube");
        }, new Random(TestUtils.SEED));
    }

    @SuppressWarnings("SameParameterValue")
    private static double getDecisionBoundaryForNegativeMovementPushedBackwards(double pos) {
        DoublePredicate collideCondition = b -> 1.0E-7 >= pos - b && b + 1e-7 >= pos;
        double ulp = 2 * Math.max(Math.ulp(pos), Math.ulp(1e-7));
        double lowerFalse = pos - ulp - (1e-7 + ulp);
        double upperTrue = pos;
        return DoubleUtils.getFirstTrue(collideCondition, lowerFalse, upperTrue);
    }

    @SuppressWarnings("SameParameterValue")
    private static double getDecisionBoundaryForPositiveMovementPushedBackwards(double pos) {
        DoublePredicate collideCondition = b -> -1.0E-7 <= pos - b && b - 1e-7 < pos;
        double ulp = 2 * Math.max(Math.ulp(pos), Math.ulp(1e-7));
        double lowerTrue = pos - ulp;
        double upperFalse = pos + ulp + (1e-7 + ulp);
        return DoubleUtils.getLastTrue(collideCondition, lowerTrue, upperFalse);
    }

    private static void forEachRandomPosition(BiConsumer<Vec3, Function<Vec3, VoxelShape>> consumer, Random random) {
        VoxelShape originBlock = Shapes.block();
        forEachRandomPositionWithoutInnerWalls(consumer, random, originBlock);
        forEachRandomPositionWithInnerWalls(consumer, random, originBlock);
    }

    private static void forEachRandomPositionWithInnerWalls(BiConsumer<Vec3, Function<Vec3, VoxelShape>> consumer, Random random, VoxelShape originBlock) {
        for (int resolutionBits = 0; resolutionBits <= 3; resolutionBits++) {
            if (originBlock instanceof VoxelShapeSimpleCube) {
                //Custom lithium shapes are in use
                originBlock = new VoxelShapeAlignedCuboid(0, 0, 0, 1, 1, 1, resolutionBits, resolutionBits, resolutionBits);
            } else {
                //Vanilla shapes are in use
                int xSize = 1 << resolutionBits;
                int ySize = 1 << resolutionBits;
                int zSize = 1 << resolutionBits;
                BitSetDiscreteVoxelShape voxelSet = BitSetDiscreteVoxelShape.withFilledBounds(xSize, ySize, zSize, 0, 0, 0, xSize, ySize, zSize);
                originBlock = new CubeVoxelShape(voxelSet);
            }
            forEachRandomPositionWithoutInnerWalls(consumer, random, originBlock);
        }
    }

    private static void forEachRandomPositionWithoutInnerWalls(BiConsumer<Vec3, Function<Vec3, VoxelShape>> consumer, Random random, VoxelShape originBlock) {
        Function<Vec3, VoxelShape> voxelShapeProducer = vec3 -> {
            if (vec3.x() == 0 && vec3.y() == 0 && vec3.z() == 0) {
                return originBlock;
            }
            return originBlock.move(vec3.x(), vec3.y(), vec3.z());
        };
        consumer.accept(new Vec3(0, 0, 0), voxelShapeProducer);

        Random random2 = new Random(random.nextLong());
        for (int i = 0; i < TestUtils.RANDOM_COORDINATE_COUNT; i++) {
            double x = randomCoordinate(random2);
            double y = randomCoordinate(random2);
            double z = randomCoordinate(random2);
            consumer.accept(new Vec3(x, y, z), voxelShapeProducer);
        }

        random2 = new Random(random.nextLong());
        for (int magnitude = 2; magnitude >= -1074; magnitude--) {
            double factor = Math.pow(2, magnitude);
            for (int i = 0; i < TestUtils.RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT; i++) {
                double x = factor * random2.nextDouble();
                double y = factor * random2.nextDouble();
                double z = factor * random2.nextDouble();
                consumer.accept(new Vec3(x, y, z), voxelShapeProducer);
            }
        }
    }

    private static double randomCoordinate(Random random) {
        return random.nextInt(-30_000_000 + 1, 30_000_000) - random.nextDouble();
    }

    @Test
    void testMoveNegativeBackwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            double decisionBoundary = getDecisionBoundaryForNegativeMovementPushedBackwards(position.y() + 1);
            AABB aabb = new AABB(position.x(), decisionBoundary, position.z(), position.x() + 1, position.y() + 1 + 3, position.z() + 1);
            TestUtils.assertEquals(position.y() + 1 - aabb.minY, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveNegativeBackwards");
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMoveNegativeBarelyForwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            double decisionBoundary = getDecisionBoundaryForNegativeMovementPushedBackwards(position.y() + 1);
            AABB aabb = new AABB(position.x(), Math.nextDown(decisionBoundary), position.z(), position.x() + 1, position.y() + 1 + 3, position.z() + 1);
            TestUtils.assertEquals(-0.05, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveNegativeBarelyForwards");
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMoveNegativeAgainstInnerWall() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            DoubleList coords = cubeBelow.getCoords(Direction.Axis.Y);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);
                boolean isLastWall = i == 0;

                AABB aabb = new AABB(position.x(), coord + 0.01, position.z(), position.x() + 1, position.y() + 3, position.z() + 1);
                double expected = isLastWall ? -5 : coord - aabb.minY;
                TestUtils.assertEquals(expected, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -5), position, "testMoveNegativeAgainstInnerWall");
            }
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMoveNegativeAgainstInnerWallBackwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            DoubleList coords = cubeBelow.getCoords(Direction.Axis.Y);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);
                boolean isLastWall = i == 0;
                double decisionBoundary = getDecisionBoundaryForNegativeMovementPushedBackwards(coord);
                AABB aabb = new AABB(position.x(), decisionBoundary, position.z(), position.x() + 1, position.y() + 1 + 3, position.z() + 1);
                double expected = isLastWall ? -0.05 : coord - aabb.minY;
                TestUtils.assertEquals(expected, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveNegativeAgainstInnerWallBackwards");
            }
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMoveNegativeAgainstInnerWallBarelyForwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            DoubleList coords = cubeBelow.getCoords(Direction.Axis.Y);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);
                double decisionBoundary = getDecisionBoundaryForNegativeMovementPushedBackwards(coord);
                AABB aabb = new AABB(position.x(), Math.nextDown(decisionBoundary), position.z(), position.x() + 1, position.y() + 1 + 3, position.z() + 1);
                TestUtils.assertEquals(-0.05, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveNegativeAgainstInnerWallBarelyForwards");
            }
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMovePositiveAgainstInnerWall() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeAbove = shapeProvider.apply(position);
            DoubleList coords = cubeAbove.getCoords(Direction.Axis.Y);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);
                boolean isLastWall = i == coords.size() - 1;

                AABB aabb = new AABB(position.x(), position.y() - 3, position.z(), position.x() + 1, coord - 0.01, position.z() + 1);
                double expected = isLastWall ? 5 : coord - aabb.maxY;
                TestUtils.assertEquals(expected, () -> cubeAbove.collide(Direction.Axis.Y, aabb, 5), position, "testMovePositiveAgainstInnerWall");
            }
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMovePositiveAgainstInnerWallBackwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeAbove = shapeProvider.apply(position);
            DoubleList coords = cubeAbove.getCoords(Direction.Axis.Y);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);
                boolean isLastWall = i == coords.size() - 1;
                double decisionBoundary = getDecisionBoundaryForPositiveMovementPushedBackwards(coord);
                AABB aabb = new AABB(position.x(), position.y() - 3, position.z(), position.x() + 1, decisionBoundary, position.z() + 1);
                double expected = isLastWall ? 0.05 : coord - aabb.maxY;
                TestUtils.assertEquals(expected, () -> cubeAbove.collide(Direction.Axis.Y, aabb, 0.05), position, "testMovePositiveAgainstInnerWallBackwards");
            }
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMovePositiveAgainstInnerWallBarelyForwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeAbove = shapeProvider.apply(position);
            DoubleList coords = cubeAbove.getCoords(Direction.Axis.Y);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);
                double decisionBoundary = getDecisionBoundaryForPositiveMovementPushedBackwards(coord);
                AABB aabb = new AABB(position.x(), position.y() - 3, position.z(), position.x() + 1, Math.nextUp(decisionBoundary), position.z() + 1);
                TestUtils.assertEquals(0.05, () -> cubeAbove.collide(Direction.Axis.Y, aabb, 0.05), position, "testMovePositiveAgainstInnerWallBarelyForwards");
            }
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMovePositiveBackwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeAbove = shapeProvider.apply(position);
            double decisionBoundary = getDecisionBoundaryForPositiveMovementPushedBackwards(position.y());
            AABB aabb = new AABB(position.x(), position.y() - 3, position.z(), position.x() + 1, decisionBoundary, position.z() + 1);
            TestUtils.assertEquals(position.y() - aabb.maxY, () -> cubeAbove.collide(Direction.Axis.Y, aabb, 0.05), position, "testMovePositiveBackwards");
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMovePositiveBarelyForwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeAbove = shapeProvider.apply(position);
            double decisionBoundary = getDecisionBoundaryForPositiveMovementPushedBackwards(position.y());
            AABB aabb = new AABB(position.x(), position.y() - 3, position.z(), position.x() + 1, Math.nextUp(decisionBoundary), position.z() + 1);
            TestUtils.assertEquals(0.05, () -> cubeAbove.collide(Direction.Axis.Y, aabb, 0.05), position, "testMovePositiveBarelyForwards");
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testMoveBackwardsTwice() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            double shapeYOffset2 = position.y() + 2;
            VoxelShape cubeAbove = shapeProvider.apply(position.with(Direction.Axis.Y, shapeYOffset2));

            double decisionBoundary = getDecisionBoundaryForNegativeMovementPushedBackwards(position.y() + 1);
            double decisionBoundary2 = getDecisionBoundaryForPositiveMovementPushedBackwards(shapeYOffset2);

            AABB aabb = new AABB(position.x(), decisionBoundary, position.z(), position.x() + 1, decisionBoundary2, position.z() + 1);
            double expected = 1 + position.y() - aabb.minY;
            double yMovement = TestUtils.assertEquals(expected, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveBackwardsTwice(A)");

            double expected2 = shapeYOffset2 - aabb.maxY;
            if (Math.abs(expected) < 1e-7) {
                expected2 = 0.0;
            }
            TestUtils.assertEquals(expected2, () -> cubeAbove.collide(Direction.Axis.Y, aabb, yMovement), position, "testMoveBackwardsTwice(B)");

        }, new Random(TestUtils.SEED));
    }

    @Test
    void testSwappedOrderBreaksMoveBackwardsTwice() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            double shapeYOffset2 = position.y() + 2;
            VoxelShape cubeAbove = shapeProvider.apply(position.with(Direction.Axis.Y, shapeYOffset2));
            double decisionBoundary = getDecisionBoundaryForNegativeMovementPushedBackwards(position.y() + 1);
            double decisionBoundary2 = getDecisionBoundaryForPositiveMovementPushedBackwards(shapeYOffset2);

            AABB aabb = new AABB(position.x(), decisionBoundary, position.z(), position.x() + 1, decisionBoundary2, position.z() + 1);
            double yMovement = TestUtils.assertEquals(-0.05, () -> cubeAbove.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveBackwardsTwice(B)");

            TestUtils.assertEquals(1 + position.y() - aabb.minY, () -> cubeBelow.collide(Direction.Axis.Y, aabb, yMovement), position, "testMoveBackwardsTwice(A)");

        }, new Random(TestUtils.SEED));
    }

}
