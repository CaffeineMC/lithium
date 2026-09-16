package net.caffeinemc.mods.lithium.mixin.ai.pathing;

import net.caffeinemc.mods.lithium.common.ai.pathing.BlockStatePathingCache;
import net.caffeinemc.mods.lithium.common.world.blockview.SingleBlockBlockView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements BlockStatePathingCache {
    //TODO this is only initialized if block state flags are initialized too. This should have its own initialization call
    private PathType pathNodeType = null;
    private PathType pathNodeTypeNeighbor = null;

    @Override
    public void lithium$clearPathTypeCache() {
        this.pathNodeType = null;
        this.pathNodeTypeNeighbor = null;
    }

    @Override
    public void lithium$initializePathNodeTypeCache() {
        // Reset the cached path node types, to ensure they are re-calculated.
        this.pathNodeType = null;
        this.pathNodeTypeNeighbor = null;

        BlockState state = this.asState();

        SingleBlockBlockView singleBlockBlockView = SingleBlockBlockView.of(state, BlockPos.ZERO);
        try {
            this.pathNodeType = Validate.notNull(WalkNodeEvaluator.getPathTypeFromState(singleBlockBlockView, BlockPos.ZERO), "Block has no common path node type!");
        } catch (SingleBlockBlockView.SingleBlockViewException | ClassCastException e) {
            //This is usually hit by shulker boxes, as their hitbox depends on the block entity, and the node type depends on the hitbox
            //Also catch ClassCastException in case some modded code casts BlockView to ChunkCache
            this.pathNodeType = null;
        }
        try {
            //Passing null as previous node type to the method signals to other lithium mixins that we only want the neighbor behavior of this block and not its neighbors
            //Using exceptions for control flow, but this way we do not need to copy the code for the cache initialization, reducing required maintenance and improving mod compatibility
            //noinspection DataFlowIssue
            this.pathNodeTypeNeighbor = (WalkNodeEvaluator.checkNeighbourBlocks(new PathfindingContext(singleBlockBlockView, null), 1, 1, 1, null));
            //noinspection ConstantValue
            if (this.pathNodeTypeNeighbor == null) {
                this.pathNodeTypeNeighbor = PathType.OPEN;
            }
        } catch (SingleBlockBlockView.SingleBlockViewException | NullPointerException | ClassCastException e) {
            this.pathNodeTypeNeighbor = null;
        }
    }

    @Override
    public PathType lithium$getPathNodeType() {
        return this.pathNodeType;
    }

    @Override
    public PathType lithium$getNeighborPathNodeType() {
        return this.pathNodeTypeNeighbor;
    }

    @Shadow
    protected abstract BlockState asState();

    @Shadow
    public abstract Block getBlock();
}
