package net.caffeinemc.mods.lithium.mixin.gen.jigsaw_free_space;

import net.caffeinemc.mods.lithium.common.shapes.VoxelShapeCuboidDifference;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(JigsawPlacement.class)
public class JigsawPlacementMixin {

    /**
     * Replace the initial free space shape (usually the max-radius box minus the start piece's box) with a
     * cuboid-list-based representation. The subsequent piece placement queries and free space subtractions in
     * {@code Placer#tryPlacingChildren} then operate on the cuboid lists directly instead of running vanilla's
     * costly voxel-based shape joins. If the shape cannot be represented exactly, it is passed through unchanged
     * and all operations fall back to vanilla.
     */
    @ModifyVariable(
            method = "addPieces(Lnet/minecraft/world/level/levelgen/RandomState;IZLnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;Ljava/util/List;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static VoxelShape useCuboidDifferenceFreeSpace(VoxelShape freeSpace) {
        return VoxelShapeCuboidDifference.tryConvert(freeSpace);
    }
}
