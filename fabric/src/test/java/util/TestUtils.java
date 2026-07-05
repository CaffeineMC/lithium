package util;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;

import java.util.function.DoubleSupplier;

public class TestUtils {
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
}
