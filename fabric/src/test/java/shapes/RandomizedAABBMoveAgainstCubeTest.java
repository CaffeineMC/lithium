package shapes;

import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeAlignedCuboid;
import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeSimpleCube;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.Function;

public class RandomizedAABBMoveAgainstCubeTest {

    private static final long SEED;
    private static final int RANDOM_COORDINATE_COUNT;
    private static final int RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT;

    static {
        RANDOM_COORDINATE_COUNT = Integer.getInteger("lithium.randomCoordinateTestIterations", 1_000_000);
        RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT = Integer.getInteger("lithium.randomCoordinateEachMagnitudeTestIterations", 1_000);
        SEED = Long.getLong("lithium.randomCoordinateTestSeed", new Random().nextLong());
        System.out.println("Lithium Test Coordinate Seed: " + SEED);
        System.out.println("Lithium Test Coordinate Iterations: " + RANDOM_COORDINATE_COUNT);
        System.out.println("Lithium Test Coordinate Each Magnitude Iterations: " + RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT);
    }

    @Test
    void testMoveAgainstSimpleCube() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape originCube = shapeProvider.apply(position);
            AABB aabb = new AABB(position.x(), position.y() + 2, position.z(), position.x() + 1, position.y() + 3, position.z() + 1);
            assertEquals(1 + position.y() - aabb.minY, () -> originCube.collide(Direction.Axis.Y, aabb, -5), position, "testMoveAgainstSimpleCube");
        }, new Random(SEED));
    }

    @SuppressWarnings("SameParameterValue")
    private static double getDecisionBoundaryForNegativeMovementPushedBackwards(double pos) {
        DoublePredicate collideCondition = b -> 1.0E-7 >= pos - b && b + 1e-7 >= pos;
        double ulp = 2 * Math.max(Math.ulp(pos), Math.ulp(1e-7));
        double lowerFalse = pos - ulp - (1e-7 + ulp);
        double upperTrue = pos;
        return getFirstTrue(collideCondition, lowerFalse, upperTrue);
    }

    @SuppressWarnings("SameParameterValue")
    private static double getDecisionBoundaryForPositiveMovementPushedBackwards(double pos) {
        DoublePredicate collideCondition = b -> -1.0E-7 <= pos - b && b - 1e-7 < pos;
        double ulp = 2 * Math.max(Math.ulp(pos), Math.ulp(1e-7));
        double lowerTrue = pos - ulp;
        double upperFalse = pos + ulp + (1e-7 + ulp);
        return getLastTrue(collideCondition, lowerTrue, upperFalse);
    }

    private static void forEachRandomPosition(BiConsumer<Vec3, Function<Vec3, VoxelShape>> consumer, Random random) {
        VoxelShape originBlock = Shapes.block();
        forEachRandomPosition(consumer, random, originBlock);

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
            forEachRandomPosition(consumer, random, originBlock);
        }
    }

    private static void forEachRandomPosition(BiConsumer<Vec3, Function<Vec3, VoxelShape>> consumer, Random random, VoxelShape originBlock) {
        Function<Vec3, VoxelShape> voxelShapeProducer = vec3 -> {
            if (vec3.x() == 0 && vec3.y() == 0 && vec3.z() == 0) {
                return originBlock;
            }
            return originBlock.move(vec3.x(), vec3.y(), vec3.z());
        };
        consumer.accept(new Vec3(0, 0, 0), voxelShapeProducer);

        Random random2 = new Random(random.nextLong());
        for (int i = 0; i < RANDOM_COORDINATE_COUNT; i++) {
            double x = randomCoordinate(random2);
            double y = randomCoordinate(random2);
            double z = randomCoordinate(random2);
            consumer.accept(new Vec3(x, y, z), voxelShapeProducer);
        }

        random2 = new Random(random.nextLong());
        for (int magnitude = 2; magnitude >= -1074; magnitude--) {
            double factor = Math.pow(2, magnitude);
            for (int i = 0; i < RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT; i++) {
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
            assertEquals(position.y() + 1 - aabb.minY, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveNegativeBackwards");
        }, new Random(SEED));
    }

    @Test
    void testMoveNegativeBarelyForwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeBelow = shapeProvider.apply(position);
            double decisionBoundary = getDecisionBoundaryForNegativeMovementPushedBackwards(position.y() + 1);
            AABB aabb = new AABB(position.x(), Math.nextDown(decisionBoundary), position.z(), position.x() + 1, position.y() + 1 + 3, position.z() + 1);
            assertEquals(-0.05, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveNegativeBarelyForwards");
        }, new Random(SEED));
    }

    @Test
    void testMovePositiveBackwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeAbove = shapeProvider.apply(position);
            double decisionBoundary = getDecisionBoundaryForPositiveMovementPushedBackwards(position.y());
            AABB aabb = new AABB(position.x(), position.y() - 3, position.z(), position.x() + 1, decisionBoundary, position.z() + 1);
            assertEquals(position.y() - aabb.maxY, () -> cubeAbove.collide(Direction.Axis.Y, aabb, 0.05), position, "testMovePositiveBackwards");
        }, new Random(SEED));
    }

    @Test
    void testMovePositiveBarelyForwards() {
        forEachRandomPosition((position, shapeProvider) -> {
            VoxelShape cubeAbove = shapeProvider.apply(position);
            double decisionBoundary = getDecisionBoundaryForPositiveMovementPushedBackwards(position.y());
            AABB aabb = new AABB(position.x(), position.y() - 3, position.z(), position.x() + 1, Math.nextUp(decisionBoundary), position.z() + 1);
            assertEquals(0.05, () -> cubeAbove.collide(Direction.Axis.Y, aabb, 0.05), position, "testMovePositiveBarelyForwards");
        }, new Random(SEED));
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
            double yMovement = assertEquals(expected, () -> cubeBelow.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveBackwardsTwice(A)");

            double expected2 = shapeYOffset2 - aabb.maxY;
            if (Math.abs(expected) < 1e-7) {
                expected2 = 0.0;
            }
            assertEquals(expected2, () -> cubeAbove.collide(Direction.Axis.Y, aabb, yMovement), position, "testMoveBackwardsTwice(B)");

        }, new Random(SEED));
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
            double yMovement = assertEquals(-0.05, () -> cubeAbove.collide(Direction.Axis.Y, aabb, -0.05), position, "testMoveBackwardsTwice(B)");

            assertEquals(1 + position.y() - aabb.minY, () -> cubeBelow.collide(Direction.Axis.Y, aabb, yMovement), position, "testMoveBackwardsTwice(A)");

        }, new Random(SEED));
    }

    private static double assertEquals(double expected, DoubleSupplier computation, Vec3 position, String testName) {
        String message = "test=" + testName + ", x=" + position.x() + ", y=" + position.y() + ", z=" + position.z();
        double result = computation.getAsDouble();
        try {
            Assertions.assertEquals(expected, result, message);
            return result;
        } catch (AssertionError error) {
            //Add breakpoint here for debugging
            double recomputed = computation.getAsDouble();
            recomputed = computation.getAsDouble();
            recomputed = computation.getAsDouble();
            recomputed = computation.getAsDouble();
            throw error;
        }
    }

    public static double getFirstTrue(DoublePredicate f, double low, double high) {
        if (!Double.isFinite(low) || !Double.isFinite(high)) {
            throw new IllegalArgumentException("Lower and upper bound must be finite!");
        }
        if (low > high) {
            throw new IllegalArgumentException("Lower bound must not be greater than upper bound!!");
        }
        if (f.test(low)) {
            throw new IllegalArgumentException("Lower bound must not meet predicate!");
        }
        if (!f.test(high)) {
            throw new IllegalArgumentException("Higher bound must meet predicate!");
        }

        double ret = computeFirstTrue(f, low, high);

        if (!f.test(ret) || f.test(Math.nextDown(ret))) {
            ret = computeFirstTrue(f, low, high); //For debugging
            throw new AssertionError("computeFirstTrue is implemented incorrectly!");
        }
        return ret;
    }

    private static double getLastTrue(DoublePredicate collideCondition, double lowerTrue, double upperFalse) {
        return Math.nextDown(getFirstTrue(b -> !collideCondition.test(b), lowerTrue, upperFalse));
    }

    public static double computeFirstTrue(DoublePredicate f, double low, double high) {
        //predicate always holds for high, never holds for low

        while (low < high) {
            if (Math.nextUp(low) == high) {
                return high;
            }

            double mid = (low + high) / 2.0;

            if (mid <= low || mid >= high) {
                //In case of precision issues, use another way of computing mid
                mid = low + (high - low) / 2.0;
                if (mid <= low || mid >= high) {
                    //In case of more precision issues, just use anything between high and low
                    mid = Math.nextUp(low);
                }
            }

            if (f.test(mid)) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return high;
    }
}
