package net.caffeinemc.mods.lithium.neoforge;

import net.caffeinemc.mods.lithium.common.services.PlatformModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class NeoForgeModCompat implements PlatformModCompat {

    @Override
    public boolean canHopperInteractWithApiBlockInventory(HopperBlockEntity hopperBlockEntity, BlockState hopperState, boolean extracting) {
        Direction direction = extracting ? Direction.UP : hopperState.getValue(HopperBlock.FACING);
        BlockPos targetPos = hopperBlockEntity.getBlockPos().relative(direction);

        @Nullable ResourceHandler<ItemResource> target = Objects.requireNonNull(hopperBlockEntity.getLevel()).getCapability(Capabilities.Item.BLOCK, targetPos, null, null, direction.getOpposite());
        return target != null;
    }
}
