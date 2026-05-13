package net.caffeinemc.mods.lithium.common.tracking.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

public interface LithiumBlockCounter extends PalettedContainer.CountConsumer<BlockState> {
    void lithium$init(short[] countsByFlag);
}
