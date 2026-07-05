package shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import util.TestUtils;

import java.util.Random;
import java.util.function.Consumer;

public class FindIndexTest {
    private static final long SEED;
    private static final int RANDOM_COORDINATE_COUNT;
    private static final int RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT;

    static {
        RANDOM_COORDINATE_COUNT = Integer.getInteger("lithium.randomCoordinateTestIterations", 1_000_000);
        RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT = Integer.getInteger("lithium.randomCoordinateEachMagnitudeTestIterations", 1000);
        SEED = Long.getLong("lithium.randomCoordinateTestSeed", new Random().nextLong());
        System.out.println("Lithium FindIndexTest Coordinate Seed: " + SEED);
        System.out.println("Lithium FindIndexTest Coordinate Iterations: " + RANDOM_COORDINATE_COUNT);
        System.out.println("Lithium FindIndexTest Coordinate Each Magnitude Iterations: " + RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT);
    }

    @Test
    void testFindIndex() {
        forEachRandomPosition((Vec3 pos) -> {
            double unroundValue = 0.3D;
            VoxelShape voxelShape = Shapes.create(unroundValue, unroundValue, unroundValue, 1 - unroundValue, 1 - unroundValue, 1 - unroundValue).move(pos);
            DoubleList coords = voxelShape.getCoords(Direction.Axis.X);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);

                int upperIndex = voxelShape.findIndex(Direction.Axis.X, coord + 2e-7);
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, coord), pos, "testFindIndex");
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, Math.nextDown(coord)) + 1, pos, "testFindIndex");
            }
        }, new Random(SEED));
    }

    @Test
    void testFindIndex2() {
        forEachRandomPosition((Vec3 pos) -> {
            double roundValue = 0.125D;
            VoxelShape voxelShape = Shapes.create(roundValue, roundValue, roundValue, 1 - roundValue, 1 - roundValue, 1 - roundValue).move(pos);
            DoubleList coords = voxelShape.getCoords(Direction.Axis.X);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);

                int upperIndex = voxelShape.findIndex(Direction.Axis.X, coord + 2e-7);
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, coord), pos, "testFindIndex2");
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, Math.nextDown(coord)) + 1, pos, "testFindIndex2");
            }
        }, new Random(SEED));

    }

    @Test
    void testFindIndex3() {
        forEachRandomPosition((Vec3 pos) -> {
            VoxelShape voxelShape = Shapes.block().move(pos);
            DoubleList coords = voxelShape.getCoords(Direction.Axis.X);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);

                int upperIndex = voxelShape.findIndex(Direction.Axis.X, coord + 2e-7);
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, coord), pos, "testFindIndex3");
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, Math.nextDown(coord)) + 1, pos, "testFindIndex3");
            }
        }, new Random(SEED));
    }


    private void forEachRandomPosition(Consumer<Vec3> consumer, Random random) {
        consumer.accept(new Vec3(0, 0, 0));

        Random random2 = new Random(random.nextLong());
        for (int i = 0; i < RANDOM_COORDINATE_COUNT; i++) {
            double x = randomCoordinate(random2);
            double y = randomCoordinate(random2);
            double z = randomCoordinate(random2);
            consumer.accept(new Vec3(x, y, z));
        }

        random2 = new Random(random.nextLong());
        for (int magnitude = 2; magnitude >= -1074; magnitude--) {
            double factor = Math.pow(2, magnitude);
            for (int i = 0; i < RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT; i++) {
                double x = factor * random2.nextDouble();
                double y = factor * random2.nextDouble();
                double z = factor * random2.nextDouble();
                consumer.accept(new Vec3(x, y, z));
            }
        }
    }

    private static double randomCoordinate(Random random) {
        return random.nextInt(-30_000_000 + 1, 30_000_000) - random.nextDouble();
    }
}
