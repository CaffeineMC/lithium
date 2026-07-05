package shapes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import util.TestUtils;

import java.util.Random;

public class FindIndexTest {

    @Test
    void testFindIndex() {
        TestUtils.forEachRandomPosition((Vec3 pos) -> {
            double unroundValue = 0.3D;
            VoxelShape voxelShape = Shapes.create(unroundValue, unroundValue, unroundValue, 1 - unroundValue, 1 - unroundValue, 1 - unroundValue).move(pos);
            DoubleList coords = voxelShape.getCoords(Direction.Axis.X);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);

                int upperIndex = voxelShape.findIndex(Direction.Axis.X, coord + 2e-7);
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, coord), pos, "testFindIndex");
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, Math.nextDown(coord)) + 1, pos, "testFindIndex");
            }
        }, new Random(TestUtils.SEED));
    }

    @Test
    void testFindIndex2() {
        TestUtils.forEachRandomPosition((Vec3 pos) -> {
            double roundValue = 0.125D;
            VoxelShape voxelShape = Shapes.create(roundValue, roundValue, roundValue, 1 - roundValue, 1 - roundValue, 1 - roundValue).move(pos);
            DoubleList coords = voxelShape.getCoords(Direction.Axis.X);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);

                int upperIndex = voxelShape.findIndex(Direction.Axis.X, coord + 2e-7);
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, coord), pos, "testFindIndex2");
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, Math.nextDown(coord)) + 1, pos, "testFindIndex2");
            }
        }, new Random(TestUtils.SEED));

    }

    @Test
    void testFindIndex3() {
        TestUtils.forEachRandomPosition((Vec3 pos) -> {
            VoxelShape voxelShape = Shapes.block().move(pos);
            DoubleList coords = voxelShape.getCoords(Direction.Axis.X);
            for (int i = 0; i < coords.size(); i++) {
                double coord = coords.getDouble(i);

                int upperIndex = voxelShape.findIndex(Direction.Axis.X, coord + 2e-7);
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, coord), pos, "testFindIndex3");
                TestUtils.assertEquals(upperIndex, () -> voxelShape.findIndex(Direction.Axis.X, Math.nextDown(coord)) + 1, pos, "testFindIndex3");
            }
        }, new Random(TestUtils.SEED));
    }
}
