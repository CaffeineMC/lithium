package net.caffeinemc.mods.lithium.common.initialization;

import net.caffeinemc.mods.lithium.common.ai.pathing.BlockStatePathingCache;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder;
import net.caffeinemc.mods.lithium.common.block.TrackedBlockStatePredicate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockInfoInitializer {
    private static final Object BLOCK_INFO_INITIALIZATION_LOCK = new Object();
    private static final Logger LOGGER = LogManager.getLogger("LithiumBlockInfoInitializer");


    // Initialize the block info.
    public static void initializeBlockInfo() {
        synchronized (BLOCK_INFO_INITIALIZATION_LOCK) {
            try {
                resetBlockInfo(); //First clear all values, since maybe recalculation uses other otherwise cached values

                if (BlockStatePathingCache.class.isAssignableFrom(BlockState.class)) {
                    // Initialize the cached path node types.
                    for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                        try {
                            ((BlockStatePathingCache) blockState).lithium$initializePathNodeTypeCache();
                        } catch (Exception e) {
                            LOGGER.error("Failed to initialize path node type cache", e);
                        }
                    }
                }

                if (BlockStateFlagHolder.class.isAssignableFrom(BlockState.class)) {
                    // Initialize the cached block flags.
                    for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                        try {
                            ((BlockStateFlagHolder) blockState).lithium$initializeFlags();
                        } catch (Exception e) {
                            LOGGER.error("Failed to initialize block state flags", e);
                        }
                    }
                }
            } catch (Exception e) {
                resetBlockInfo();
                throw e;
            }
        }
    }

    public static void resetBlockInfo() {
        synchronized (BLOCK_INFO_INITIALIZATION_LOCK) {
            if (BlockStatePathingCache.class.isAssignableFrom(BlockState.class)) {
                // Clear the cached path node types.
                for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                    ((BlockStatePathingCache) blockState).lithium$clearPathTypeCache();
                }
            }

            if (BlockStateFlagHolder.class.isAssignableFrom(BlockState.class) && TrackedBlockStatePredicate.FULLY_INITIALIZED.get()) {
                // Reset the cached block flags.
                for (BlockState blockState : Block.BLOCK_STATE_REGISTRY) {
                    ((BlockStateFlagHolder) blockState).lithium$resetFlags();
                }
            }
        }
    }

    public static int initializeBlockInfoAndGetFlags(BlockStateFlagHolder blockStateFlagHolder) {
        synchronized (BLOCK_INFO_INITIALIZATION_LOCK) {
            Integer flags = blockStateFlagHolder.lithium$getAllFlagsOrNull();
            while (flags == null) {
                initializeBlockInfo();
                flags = blockStateFlagHolder.lithium$getAllFlagsOrNull();
                //Never loops, but in case something goes wrong the game freezes instead of crashing, lowering likelihood of world corruption
            }
            return flags;
        }
    }
}