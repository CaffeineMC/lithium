package net.caffeinemc.mods.lithium.mixin.util.block_tracking;

import net.caffeinemc.mods.lithium.common.block.BlockStateFlagHolder;
import net.caffeinemc.mods.lithium.common.block.BlockStateFlags;
import net.caffeinemc.mods.lithium.common.block.TrackedBlockStatePredicate;
import net.caffeinemc.mods.lithium.common.initialization.BlockInfoInitializer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1010)
public class BlockStateBaseMixin implements BlockStateFlagHolder {
    @Unique
    private int flags = -1;

    @Override
    public void lithium$initializeFlags() {
        try {
            TrackedBlockStatePredicate.FULLY_INITIALIZED.set(true);

            int flags = 0;

            for (int i = 0; i < BlockStateFlags.FLAGS.length; i++) {
                //noinspection ConstantConditions
                if (BlockStateFlags.FLAGS[i].test((BlockState) (Object) this)) {
                    flags |= 1 << i;
                }
            }

            this.flags = flags;
        } catch (Exception e) {
            this.flags = BlockStateFlags.FALLBACK_FLAGS;
            throw e;
        }
    }

    @Override
    public void lithium$resetFlags() {
        this.flags = -1;
    }

    @Override
    public int lithium$getAllFlags() {
        int blockStateFlags = this.flags;
        if (blockStateFlags == -1) {
            blockStateFlags = this.handleUninitializedBlockStateFlags();
        }
        return blockStateFlags;
    }

    @Unique
    private int handleUninitializedBlockStateFlags() {
        if (!BlockStateFlags.ENABLED) {
            throw new IllegalStateException("Tried to access block state flags even though the feature is disabled!");
        }
        return BlockInfoInitializer.initializeBlockInfoAndGetFlags(this);
    }

    @Override
    public @Nullable Integer lithium$getAllFlagsOrNull() {
        return this.flags == -1 ? null : this.flags;
    }
}
