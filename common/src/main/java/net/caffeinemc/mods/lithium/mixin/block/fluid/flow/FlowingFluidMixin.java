package net.caffeinemc.mods.lithium.mixin.block.fluid.flow;

import com.google.common.collect.Maps;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.bytes.Byte2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.bytes.Byte2ByteMap;
import it.unimi.dsi.fastutil.bytes.Byte2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.caffeinemc.mods.lithium.common.util.DirectionConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Shadow
    public abstract Fluid getFlowing();


    /*
     * getSpread:
     * Given block position and some new block state
     * Caches for block offsets -> block/fluid state and
     *                block offsets -> can flow down bool
     * For each direction:
     *   Cache block offset -> block/fluid state
     *   Get "updated state" for block offset
     */

    @Shadow
    private static native boolean canPassThroughWall(Direction direction, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, BlockPos blockPos2, BlockState blockState2);

    @Shadow
    private static native boolean canHoldFluid(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Fluid fluid);

    @Shadow
    private static native boolean canHoldSpecificFluid(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Fluid fluid);

    @Redirect(
            method = "canHoldAnyFluid(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z")
    )
    private static boolean isSign(BlockState blockState, TagKey<Block> tagKey, @Local Block block) {
        if (tagKey == BlockTags.SIGNS) {
            //The sign check is expensive when using the block tag lookup.
            return block instanceof SignBlock;
        }
        return blockState.is(tagKey);
    }

    @Shadow
    public abstract Fluid getSource();

    @Shadow
    protected abstract int getSlopeFindDistance(LevelReader world);

    @Shadow
    protected abstract FluidState getNewLiquid(ServerLevel serverLevel, BlockPos blockPos, BlockState blockState);

    @Unique
    private static int getNumIndicesFromRadius(int radius) {
        return (radius + 1) * (2 * radius + 1);
    }

    @Unique
    private static byte indexFromDiamondXZOffset(BlockPos originPos, BlockPos offsetPos, int radius) {
        int xOffset = offsetPos.getX() - originPos.getX();
        int zOffset = offsetPos.getZ() - originPos.getZ();

        int row = (xOffset + zOffset + radius) / 2; //Range [0, radius]
        int column = (xOffset - zOffset + radius); //Range [0, 2*radius]
        int rowLength = 2 * radius + 1;
        return (byte) (row * rowLength + column);
    }

    @Shadow
    protected abstract boolean canMaybePassThrough(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Direction direction, BlockPos blockPos2, BlockState blockState2, FluidState fluidState);

    /**
     * @author 2No2Name
     * @reason Faster implementation
     */
    @Inject(method = "getSpread(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Map;", at = @At("HEAD"), cancellable = true)
    public void getSpread(ServerLevel world, BlockPos pos, BlockState state, CallbackInfoReturnable<Map<Direction, FluidState>> cir) {
        // check immediate walls if branching is possible (at most 2 walls)
        // if branching is possible, do the complex flow calculations
        // otherwise just handle the single possible direction

        Map<Direction, FluidState> flowResultByDirection = Maps.newEnumMap(Direction.class);
        int searchRadius = this.getSlopeFindDistance(world) + 1;
        int numIndicesFromRadius = getNumIndicesFromRadius(searchRadius);
        if (numIndicesFromRadius > 256) {
            //We use bytes to represent the indices, which works with vanilla search radius of up to 5
            //Fall back to vanilla code in case the search radius is too large
            return;
        }
        BlockState[] blockStateCache = new BlockState[numIndicesFromRadius];

        Direction onlyPossibleFlowDirection = null;
        BlockPos onlyBlockPos = null;
        BlockState onlyBlockState = null;
        for (Direction flowDirection : DirectionConstants.HORIZONTAL) {
            BlockPos flowTargetPos = pos.relative(flowDirection);
            byte blockIndex = indexFromDiamondXZOffset(pos, flowTargetPos, searchRadius);
            BlockState flowTargetBlock = world.getBlockState(flowTargetPos);
            blockStateCache[blockIndex] = flowTargetBlock;
            if (this.canMaybeFlowIntoBlock(world, flowTargetBlock, flowTargetPos)) {
                if (onlyPossibleFlowDirection == null) {
                    onlyPossibleFlowDirection = flowDirection;
                    onlyBlockPos = flowTargetPos;
                    onlyBlockState = flowTargetBlock;
                } else {
                    this.calculateComplexFluidFlowDirections(world, pos, state, blockStateCache, flowResultByDirection);
                    cir.setReturnValue(flowResultByDirection);
                    return;
                }
            }
        }
        if (onlyPossibleFlowDirection != null) {
            FluidState onlyFluidState = onlyBlockState.getFluidState();
            if (this.canMaybePassThrough(world, pos, state, onlyPossibleFlowDirection, onlyBlockPos, onlyBlockState, onlyFluidState)) {
                FluidState targetNewFluidState = this.getNewLiquid(world, onlyBlockPos, onlyBlockState);
                if (canHoldSpecificFluid(world, onlyBlockPos, onlyBlockState, targetNewFluidState.getType())) {
                    if (onlyFluidState.canBeReplacedWith(world, onlyBlockPos, targetNewFluidState.getType(), onlyPossibleFlowDirection)) {
                        flowResultByDirection.put(onlyPossibleFlowDirection, targetNewFluidState);
                    }
                }
            }
        }
        cir.setReturnValue(flowResultByDirection);
    }

    /**
     * @author 2No2Name
     * @reason Rearrange to have cheaper checks first
     */
    @Overwrite
    private boolean canPassThrough(BlockGetter world, Fluid fluid, BlockPos pos, BlockState state, Direction face, BlockPos fromPos, BlockState fromState, FluidState fluidState) {
        return canHoldSpecificFluid(world, fromPos, fromState, fluid) && this.canMaybePassThrough(world, pos, state, face, fromPos, fromState, fluidState);
    }

    /**
     * @author 2No2Name
     * @reason Rearrange to have cheaper checks first
     */
    @Overwrite
    public boolean isWaterHole(BlockGetter world, BlockPos pos, BlockState state, BlockPos fromPos, BlockState fromState) {
        return (fromState.getFluidState().getType().isSame((Fluid) (Object) this) || canHoldFluid(world, fromPos, fromState, this.getFlowing())) && canPassThroughWall(Direction.DOWN, world, pos, state, fromPos, fromState);
    }

    @Unique
    private BlockState getBlock(Level world, BlockPos pos, BlockState[] blockStateCache, int key) {
        BlockState blockState = blockStateCache[key];
        if (blockState == null) {
            blockState = world.getBlockState(pos);
            blockStateCache[key] = blockState;
        }
        return blockState;
    }

    @Unique
    private void removeDirectionsWithoutHoleAccess(byte holeAccess, Map<Direction, FluidState> flowResultByDirection) {
        for (int i = 0; i < DirectionConstants.HORIZONTAL.length; i++) {
            if ((holeAccess & (1 << i)) == 0) {
                flowResultByDirection.remove(DirectionConstants.HORIZONTAL[i]);
            }
        }
    }

    /**
     * Fast check to eliminate some choices for the flow direction
     */
    @Unique
    private boolean canMaybeFlowIntoBlock(Level world, BlockState blockState, BlockPos flowTargetPos) {
        //todo maybe use this in more places,
        // maybe use the blockstate predicate system
        return canHoldFluid(world, flowTargetPos, blockState, this.getSource());
    }

    @Unique
    private void calculateComplexFluidFlowDirections(ServerLevel world, BlockPos startPos, BlockState startState, BlockState[] blockStateCache, Map<Direction, FluidState> flowResultByDirection) {
        //Search like breadth-first-search for paths the fluid can flow
        //Only move in directions the fluid can move (e.g. block can contain / be replaced by fluid) (vanilla conditions)
        //For each node remember the first move step (direction) of the paths that led to this node
        //Break when the BFS found all paths of a length up to some length, if any of those paths found a node with a
        // hole below. Then return the union of the stored first move steps of the nodes with a hole below.
        //In total, this finds the directions from the starting location which are the first step towards one of the
        // closest holes, just like vanilla.

        //For each position relevant:
        // Is there a hole below
        // What is the shortest path length to center -
        // Which direct neighbors of the center are on a shortest-path to this location - 4 bits
        // Which direct neighbors of the pos are previous node on the path from center - 4 bits

        Byte2ByteOpenHashMap prevPositions = new Byte2ByteOpenHashMap();
        Byte2ByteOpenHashMap currentPositions = new Byte2ByteOpenHashMap();
        Byte2BooleanOpenHashMap holeCache = new Byte2BooleanOpenHashMap();
        byte holeAccess = 0;
        int searchRadius = this.getSlopeFindDistance(world) + 1;

        //Like vanilla, the first iteration is separate, because getUpdatedState is called to check whether a
        // renewable fluid source block is created in the flow direction.
        for (int i = 0; i < DirectionConstants.HORIZONTAL.length; i++) {
            Direction flowDirection = DirectionConstants.HORIZONTAL[i];
            BlockPos flowTargetPos = startPos.relative(flowDirection);
            byte blockIndex = indexFromDiamondXZOffset(startPos, flowTargetPos, searchRadius);

            BlockState targetBlockState = getBlock(world, flowTargetPos, blockStateCache, blockIndex);
            //TODO use block cache in getUpdatedState
            if (this.canMaybePassThrough(world, startPos, startState, flowDirection, flowTargetPos, targetBlockState, targetBlockState.getFluidState())) {
                FluidState targetNewFluidState = this.getNewLiquid(world, flowTargetPos, targetBlockState);
                if (canHoldSpecificFluid(world, flowTargetPos, targetBlockState, targetNewFluidState.getType())) {//Store the resulting fluid state for each direction, remove later if no closest hole access in this direction.
                    // 1.21.2+ Specialty: Only add the direction if the fluid can replace the other fluid. If it cannot, it still counts for the hole search though.
                    if (targetBlockState.getFluidState().canBeReplacedWith(world, flowTargetPos, targetNewFluidState.getType(), flowDirection)) {
                        flowResultByDirection.put(flowDirection, targetNewFluidState);
                    }

                    if (this.canPassThrough(world, targetNewFluidState.getType(), startPos, startState, flowDirection, flowTargetPos, targetBlockState, targetBlockState.getFluidState())) {
                        prevPositions.put(blockIndex, (byte) (0b10001 << i));
                        if (isHoleBelow(world, holeCache, blockIndex, flowTargetPos, targetBlockState)) {
                            holeAccess |= (byte) (1 << i);
                        }
                    }
                }

            }
        }

        //Iterate over the positions and find the shortest path to the center
        //If a hole is found, stop the iteration
        for (int i = 0; i < this.getSlopeFindDistance(world) && holeAccess == 0; i++) {
            Fluid targetFluid = this.getFlowing();
            for (ObjectIterator<Byte2ByteMap.Entry> iterator = prevPositions.byte2ByteEntrySet().fastIterator(); iterator.hasNext(); ) {
                Byte2ByteMap.Entry entry = iterator.next();
                byte blockIndex = entry.getByteKey();
                byte currentInfo = entry.getByteValue();

                int rowLength = 2 * searchRadius + 1;
                int row = blockIndex / rowLength;
                int column = blockIndex % rowLength;
                int unevenColumn = column % 2;
                int xOffset = (row * 2 + column + unevenColumn - searchRadius * 2) / 2;
                int zOffset = xOffset - column + searchRadius;

                BlockPos currentPos = startPos.offset(xOffset, 0, zOffset);
                BlockState currentState = blockStateCache[blockIndex];

                for (int j = 0; j < DirectionConstants.HORIZONTAL.length; j++) {
                    Direction flowDirection = DirectionConstants.HORIZONTAL[j];
                    int oppositeDirection = DirectionConstants.HORIZONTAL_OPPOSITE_INDICES[j];

                    if (((currentInfo >> 4) & (1 << oppositeDirection)) != (byte) 0) {
                        //In this direction is one of the disallowed directions
                        continue;
                    }
                    BlockPos flowTargetPos = currentPos.relative(flowDirection);
                    byte targetPosBlockIndex = indexFromDiamondXZOffset(startPos, flowTargetPos, searchRadius);
                    if (prevPositions.containsKey(targetPosBlockIndex)) {
                        continue;
                    }

                    byte oldInfo = currentPositions.getOrDefault(targetPosBlockIndex, (byte) 0);
                    byte newInfo = oldInfo;
                    newInfo |= (byte) (0b10000 << j); //Disallow search direction
                    newInfo |= (byte) (currentInfo & 0b1111); //Shortest-reachable with the starting directions
                    if ((newInfo & 0b1111) == (oldInfo & 0b1111)) {
                        currentPositions.put(targetPosBlockIndex, newInfo);
                        continue;
                    }
                    BlockState targetBlockState = getBlock(world, flowTargetPos, blockStateCache, targetPosBlockIndex);
                    if (this.canPassThrough(world, targetFluid, currentPos, currentState, flowDirection, flowTargetPos, targetBlockState, targetBlockState.getFluidState())) {
                        currentPositions.put(targetPosBlockIndex, newInfo);
                        if (isHoleBelow(world, holeCache, targetPosBlockIndex, flowTargetPos, targetBlockState)) {
                            holeAccess |= (byte) (currentInfo & 0b1111);
                        }
                    }
                }
            }

            var tmp = prevPositions;
            prevPositions = currentPositions;
            currentPositions = tmp;
            currentPositions.clear();
        }

        if (holeAccess != 0) {
            //Found at least one hole in any iteration, keep the directions which lead to the closest holes.
            removeDirectionsWithoutHoleAccess(holeAccess, flowResultByDirection);
        }
    }

    @Unique
    private boolean isHoleBelow(LevelReader world, Byte2BooleanOpenHashMap holeCache, byte key, BlockPos flowTargetPos, BlockState targetBlockState) {
        if (holeCache.get(key)) {
            return true;
        }
        BlockPos downPos = flowTargetPos.below();
        BlockState downBlock = world.getBlockState(downPos);
        boolean holeFound = this.isWaterHole(world, flowTargetPos, targetBlockState, downPos, downBlock);
        holeCache.put(key, holeFound);
        return holeFound;
    }
}
