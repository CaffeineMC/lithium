package net.caffeinemc.mods.lithium.forge;

import net.caffeinemc.mods.lithium.common.services.PlatformModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.Objects;

public class ForgeModCompat implements PlatformModCompat {

    /**
     * Lithium skips its hopper fast path when the target exposes a modded inventory instead of a
     * vanilla Container. NeoForge answers that through its transfer API; Forge 26.2 still uses the
     * classic capability system, so the question becomes "does the block entity opposite the hopper
     * expose an IItemHandler on the relevant face".
     */
    @Override
    public boolean canHopperInteractWithApiBlockInventory(HopperBlockEntity hopperBlockEntity, BlockState hopperState, boolean extracting) {
        Direction direction = extracting ? Direction.UP : hopperState.getValue(HopperBlock.FACING);
        BlockPos targetPos = hopperBlockEntity.getBlockPos().relative(direction);

        Level level = Objects.requireNonNull(hopperBlockEntity.getLevel());
        BlockEntity target = level.getBlockEntity(targetPos);
        if (target == null) {
            return false;
        }

        return target.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).isPresent();
    }
}
