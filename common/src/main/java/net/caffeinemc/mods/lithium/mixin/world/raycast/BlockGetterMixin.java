package net.caffeinemc.mods.lithium.mixin.world.raycast;

import net.caffeinemc.mods.lithium.common.util.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("ShadowModifiers")
@Mixin(BlockGetter.class)
public interface BlockGetterMixin {

    @Shadow
    BlockState getBlockState(BlockPos pos);

    @Shadow
    @Nullable BlockHitResult clipWithInteractionOverride(Vec3 start, Vec3 end, BlockPos pos, VoxelShape shape, BlockState state);

    @Shadow
    static <T, C> T traverseBlocks(Vec3 start, Vec3 end, C context, BiFunction<C, BlockPos, T> blockHitFactory, Function<C, T> missFactory) {throw new AssertionError();}

    @Shadow(aliases = { "lambda$clip$2" })
    public BlockHitResult method_17743(ClipContext par1, BlockPos par2);

    @Shadow(aliases = { "lambda$clip$3" })
    public static BlockHitResult method_17746(ClipContext par1) { throw new AssertionError();}

    /**
     * @author 2No2Name
     * @reason Get rid of unnecessary lambda allocation
     */
    @Overwrite
    default BlockHitResult clip(ClipContext context) {
        return traverseBlocks(context.getFrom(), context.getTo(), context, this instanceof LevelReader ? this.blockHitFactory(context) : this::method_17743, BlockGetterMixin::method_17746);
    }

    @Unique
    private BiFunction<ClipContext, BlockPos, BlockHitResult> blockHitFactory(ClipContext context) {
        return new BiFunction<>() {
            int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
            ChunkAccess chunk = null;
            final boolean handleFluids = ((ClipContextAccessor) context).getFluidHandling() != ClipContext.Fluid.NONE;

            @Override
            public BlockHitResult apply(ClipContext innerContext, BlockPos pos) {
                //[VanillaCopy] BlockView.raycast, but optional fluid handling
                BlockState blockState = this.getBlock((LevelReader) BlockGetterMixin.this, pos);
                Vec3 start = innerContext.getFrom();
                Vec3 end = innerContext.getTo();
                VoxelShape blockShape = innerContext.getBlockShape(blockState, (BlockGetter) BlockGetterMixin.this, pos);
                BlockHitResult blockHitResult = BlockGetterMixin.this.clipWithInteractionOverride(start, end, pos, blockShape, blockState);
                double d = blockHitResult == null ? Double.MAX_VALUE : innerContext.getFrom().distanceToSqr(blockHitResult.getLocation());
                double e = Double.MAX_VALUE;
                BlockHitResult fluidHitResult = null;
                if (this.handleFluids) {
                    FluidState fluidState = blockState.getFluidState();
                    VoxelShape fluidShape = innerContext.getFluidShape(fluidState, (BlockGetter) BlockGetterMixin.this, pos);
                    fluidHitResult = fluidShape.clip(start, end, pos);
                    e = fluidHitResult == null ? Double.MAX_VALUE : innerContext.getFrom().distanceToSqr(fluidHitResult.getLocation());
                }
                return d <= e ? blockHitResult : fluidHitResult;
            }

            /**
             * Modifications to this method should also be done in {@link net.caffeinemc.mods.lithium.mixin.world.explosions.entity_raycast.ServerExplosionMixin#blockHitFactory()}
             */
            private BlockState getBlock(LevelReader world, BlockPos blockPos) {
                if (world.isOutsideBuildHeight(blockPos.getY())) {
                    return Blocks.VOID_AIR.defaultBlockState();
                }
                int chunkX = Pos.ChunkCoord.fromBlockCoord(blockPos.getX());
                int chunkZ = Pos.ChunkCoord.fromBlockCoord(blockPos.getZ());

                // Avoid calling into the chunk manager as much as possible through managing chunks locally
                if (this.chunkX != chunkX || this.chunkZ != chunkZ) {
                    this.chunk = world.getChunk(chunkX, chunkZ);

                    this.chunkX = chunkX;
                    this.chunkZ = chunkZ;
                }

                final ChunkAccess chunk = this.chunk;

                // If the chunk is missing or out of bounds, assume that it is air
                if (chunk != null) {
                    // We operate directly on chunk sections to avoid interacting with BlockPos and to squeeze out as much
                    // performance as possible here
                    LevelChunkSection section = chunk.getSections()[Pos.SectionYIndex.fromBlockCoord(chunk, blockPos.getY())];

                    // If the section doesn't exist or is empty, assume that the block is air
                    if (section != null && !section.hasOnlyAir()) {
                        return section.getBlockState(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15);
                    }
                }

                return Blocks.AIR.defaultBlockState();
            }
        };
    }
}
