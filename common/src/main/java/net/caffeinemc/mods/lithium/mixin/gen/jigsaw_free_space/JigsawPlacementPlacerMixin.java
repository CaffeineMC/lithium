package net.caffeinemc.mods.lithium.mixin.gen.jigsaw_free_space;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeCuboidDifference;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public class JigsawPlacementPlacerMixin {
    private static final String TRY_PLACING_CHILDREN = "tryPlacingChildren(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Lorg/apache/commons/lang3/mutable/MutableObject;IZLnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V";

    /**
     * The free space inside the current piece (used when placing children on the interior of a piece) starts out
     * as a single cuboid, which the cuboid-list-based representation can express directly.
     */
    @WrapOperation(
            method = TRY_PLACING_CHILDREN,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;create(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;", ordinal = 0)
    )
    private VoxelShape createInteriorFreeSpace(AABB cuboid, Operation<VoxelShape> original) {
        VoxelShape freeSpace = VoxelShapeCuboidDifference.tryCreate(cuboid);
        if (freeSpace != null) {
            return freeSpace;
        }
        return original.call(cuboid);
    }

    /**
     * A candidate piece is rejected when any part of its deflated bounding box lies outside the free space.
     * For the cuboid-list-based free space this test is a handful of coordinate comparisons instead of a
     * voxel-based shape join.
     */
    @WrapOperation(
            method = TRY_PLACING_CHILDREN,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z")
    )
    private boolean testCandidateOutsideFreeSpace(VoxelShape freeSpace, VoxelShape candidate, BooleanOp op, Operation<Boolean> original) {
        if (op == BooleanOp.ONLY_SECOND && freeSpace instanceof VoxelShapeCuboidDifference cuboidDifference) {
            int result = cuboidDifference.exceedsFreeSpace(candidate);
            if (result != VoxelShapeCuboidDifference.FALLBACK) {
                return result == VoxelShapeCuboidDifference.EXCEEDS;
            }
        }
        return original.call(VoxelShapeCuboidDifference.unwrap(freeSpace), VoxelShapeCuboidDifference.unwrap(candidate), op);
    }

    /**
     * Subtracting a placed piece's bounding box from the free space only appends the cuboid to the list instead
     * of running a voxel-based shape join.
     */
    @WrapOperation(
            method = TRY_PLACING_CHILDREN,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinUnoptimized(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape subtractFromFreeSpace(VoxelShape freeSpace, VoxelShape placedPiece, BooleanOp op, Operation<VoxelShape> original) {
        if (op == BooleanOp.ONLY_FIRST && freeSpace instanceof VoxelShapeCuboidDifference cuboidDifference) {
            VoxelShape result = cuboidDifference.trySubtract(placedPiece);
            if (result != null) {
                return result;
            }
        }
        return original.call(VoxelShapeCuboidDifference.unwrap(freeSpace), VoxelShapeCuboidDifference.unwrap(placedPiece), op);
    }
}
