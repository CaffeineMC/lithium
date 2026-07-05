package shapes;

import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeMatchesAnywhere;
import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeSimpleCube;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import util.TestUtils;

import java.util.Random;

public class JoinIsNotEmptyTest {

    private static final boolean CAN_INVOKE_VANILLA_CODE = !TestUtils.IS_MIXIN_LOADED;

    private static final BitSetDiscreteVoxelShape FULL_CUBE_VOXELS;
    private static final VoxelShape LITHIUM_BLOCK;

    static {
        FULL_CUBE_VOXELS = new BitSetDiscreteVoxelShape(1, 1, 1);
        FULL_CUBE_VOXELS.fill(0, 0, 0);
        LITHIUM_BLOCK = new VoxelShapeSimpleCube(FULL_CUBE_VOXELS, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    }


    public static boolean lithiumJoinIsNotEmpty(final VoxelShape first, final VoxelShape second, final BooleanOp op) {
        boolean firstEmpty = first.isEmpty();
        boolean secondEmpty = second.isEmpty();
        if (!firstEmpty && !secondEmpty) {
            if (first == second) {
                return op.apply(true, true);
            } else {
                boolean firstOnlyMatters = op.apply(true, false);
                boolean secondOnlyMatters = op.apply(false, true);

                for (Direction.Axis axis : AxisCycle.AXIS_VALUES) {
                    if (first.max(axis) < second.min(axis) - 1.0E-7) {
                        return firstOnlyMatters || secondOnlyMatters;
                    }

                    if (second.max(axis) < first.min(axis) - 1.0E-7) {
                        return firstOnlyMatters || secondOnlyMatters;
                    }
                }
                var ret = VoxelShapeMatchesAnywhere.cuboidMatchesAnywhere(first, second, op);
                if (ret == -1) {
                    //Fallback to vanilla
                    return Shapes.joinIsNotEmpty(first, second, op);
                }
                return ret != 0;
            }
        } else {
            return op.apply(!firstEmpty, !secondEmpty);
        }
    }

    @Test
    void testTouchingCubes() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {
            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(1, 0, 0).move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(1, 0, 0).move(pos);

            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testTouchingCubes");
        }, new Random(TestUtils.SEED));
    }

    private static void checkLithiumVanillaEquality(VoxelShape vanillaShape1, VoxelShape vanillaShape2, VoxelShape lithiumShape1, VoxelShape lithiumShape2, Vec3 pos, String testName) {
        TestUtils.assertEquals(Shapes.joinIsNotEmpty(vanillaShape1, vanillaShape2, BooleanOp.AND), () -> lithiumJoinIsNotEmpty(lithiumShape1, lithiumShape2, BooleanOp.AND), pos, testName);
        TestUtils.assertEquals(Shapes.joinIsNotEmpty(vanillaShape1, vanillaShape2, BooleanOp.ONLY_FIRST), () -> lithiumJoinIsNotEmpty(lithiumShape1, lithiumShape2, BooleanOp.ONLY_FIRST), pos, testName);
        TestUtils.assertEquals(Shapes.joinIsNotEmpty(vanillaShape1, vanillaShape2, BooleanOp.ONLY_SECOND), () -> lithiumJoinIsNotEmpty(lithiumShape1, lithiumShape2, BooleanOp.ONLY_SECOND), pos, testName);
        TestUtils.assertEquals(Shapes.joinIsNotEmpty(vanillaShape1, vanillaShape2, BooleanOp.NOT_SAME), () -> lithiumJoinIsNotEmpty(lithiumShape1, lithiumShape2, BooleanOp.NOT_SAME), pos, testName);
    }

    @Test
    void testBarelyOverlappingCubes() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {
            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(1 - 1e-7, 0, 0).move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(1 - 1e-7, 0, 0).move(pos);


            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testBarelyOverlappingCubes");

        }, new Random(TestUtils.SEED));
    }

    @Test
    void testEqualCubes() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {

            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(0, 0, 0).move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(0, 0, 0).move(pos);

            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testEqualCubes");

        }, new Random(TestUtils.SEED));
    }

    @Test
    void testEqualCubes2() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {

            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(pos);


            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testEqualCubes2");

        }, new Random(TestUtils.SEED));
    }

    @Test
    void testAlmostEqualCubes() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {

            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(1e-7, 0, 0).move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(1e-7, 0, 0).move(pos);


            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testAlmostEqualCubes");

        }, new Random(TestUtils.SEED));
    }

    @Test
    void testAlmostEqualCubes2() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {

            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(-1e-7, 0, 0).move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(-1e-7, 0, 0).move(pos);


            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testAlmostEqualCubes2");

        }, new Random(TestUtils.SEED));
    }

    @Test
    void testAlmostEqualCubes3() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {

            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(Math.nextDown(-1e-7), 0, 0).move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(Math.nextDown(-1e-7), 0, 0).move(pos);


            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testAlmostEqualCubes3");

        }, new Random(TestUtils.SEED));
    }

    @Test
    void testAlmostEqualCubes4() {
        if (!CAN_INVOKE_VANILLA_CODE) {
            return;
        }
        TestUtils.forEachRandomPosition((Vec3 pos) -> {

            VoxelShape shape = Shapes.block().move(pos);
            VoxelShape shape2 = Shapes.block().move(Math.nextUp(1e-7), 0, 0).move(pos);
            VoxelShape lithiumShape = LITHIUM_BLOCK.move(pos);
            VoxelShape lithiumShape2 = LITHIUM_BLOCK.move(Math.nextUp(1e-7), 0, 0).move(pos);


            checkLithiumVanillaEquality(shape, shape2, lithiumShape, lithiumShape2, pos, "testAlmostEqualCubes4");

        }, new Random(TestUtils.SEED));
    }
}
