package util;

import net.caffeinemc.mods.lithium.common.util.change_tracking.ChangePublisher;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;

import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public class TestUtils {
    @SuppressWarnings("ConstantValue")
    public static final boolean IS_MIXIN_LOADED = ChangePublisher.class.isAssignableFrom(ItemStack.class);

    public static final long SEED;
    public static final int RANDOM_COORDINATE_COUNT;
    public static final int RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT;

    static {
        RANDOM_COORDINATE_COUNT = Integer.getInteger("lithium.randomCoordinateTestIterations", 100);
        RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT = Integer.getInteger("lithium.randomCoordinateEachMagnitudeTestIterations", 2);
        SEED = Long.getLong("lithium.randomCoordinateTestSeed", new Random().nextLong());

        System.out.println("Lithium Test Coordinate Seed: " + SEED);
        System.out.println("Lithium Test Coordinate Iterations: " + RANDOM_COORDINATE_COUNT);
        System.out.println("Lithium Test Coordinate Each Magnitude Iterations: " + RANDOM_COORDINATE_EACH_MAGNITUDE_COUNT);
    }

    public static double assertEquals(double expected, DoubleSupplier computation, Vec3 position, String testName) {
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

    public static boolean assertEquals(boolean expected, BooleanSupplier computation, Vec3 position, String testName) {
        String message = "test=" + testName + ", x=" + position.x() + ", y=" + position.y() + ", z=" + position.z();
        boolean result = computation.getAsBoolean();
        try {
            Assertions.assertEquals(expected, result, message);
            return result;
        } catch (AssertionError error) {
            //Add breakpoint here for debugging
            boolean recomputed = computation.getAsBoolean();
            recomputed = computation.getAsBoolean();
            recomputed = computation.getAsBoolean();
            recomputed = computation.getAsBoolean();
            throw error;
        }
    }

    public static void forEachRandomPosition(Consumer<Vec3> consumer, Random random) {
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
