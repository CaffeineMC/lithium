package net.caffeinemc.mods.lithium.neoforge.mixin.block.hopper;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ContainerOrHandler;
import net.neoforged.neoforge.transfer.item.VanillaInventoryCodeHooks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = HopperBlockEntity.class, priority = 949)
public abstract class HopperBlockEntityMixin {

    @Shadow
    public static native ContainerOrHandler getContainerOrHandlerAt(Level level, BlockPos pos, BlockState state, double x, double y, double z, @javax.annotation.Nullable Direction side);

    @Redirect(
            method = "getSourceContainerOrHandler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getContainerOrHandlerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;DDDLnet/minecraft/core/Direction;)Lnet/neoforged/neoforge/transfer/item/ContainerOrHandler;")
    )
    private static ContainerOrHandler getSourceContainerOrHandler(Level level, BlockPos blockPos, BlockState blockState, double x, double y, double z, Direction direction, @Local(argsOnly = true) Hopper hopper) {
        if (!(hopper instanceof HopperBlockEntityMixin hopperBlockEntity)) {
            return getContainerOrHandlerAt(level, blockPos, blockState, x, y, z, direction); //Hopper Minecarts do not cache Inventories
        }
        return hopperBlockEntity.lithium$getExtractContainerOrHandler(level, blockPos, blockState, x, y, z, direction);
    }

    @Redirect(
            method = "ejectItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getContainerOrHandlerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lnet/neoforged/neoforge/transfer/item/ContainerOrHandler;")
    )
    private static ContainerOrHandler getContainerOrHandlerAt(Level level, BlockPos pos, Direction direction, @Local(argsOnly = true) HopperBlockEntity hopperBlockEntity) {
        return ((HopperBlockEntityMixin) (Object) hopperBlockEntity).lithium$getContainerOrHandlerAt(level, pos, direction);
    }

    @Unique
    public ContainerOrHandler lithium$getContainerOrHandlerAt(Level level, BlockPos pos, Direction side) {
        return this.lithium$getContainerOrHandlerAt(
                level, pos, (double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5, side
        );
    }

    @Unique
    private ContainerOrHandler lithium$getContainerOrHandlerAt(Level level, BlockPos pos, double x, double y, double z, @javax.annotation.Nullable Direction side) {
        Container container = this.lithium$getInsertBlockInventory(level);
        if (container != null) {
            return new ContainerOrHandler(container, null);
        }
        BlockState state = level.getBlockState(pos);
        var blockItemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, state, null, side);
        if (blockItemHandler != null) {
            return new ContainerOrHandler(null, blockItemHandler);
        }
        return VanillaInventoryCodeHooks.getEntityContainerOrHandler(level, x, y, z, side);
    }


    @Unique
    private ContainerOrHandler lithium$getExtractContainerOrHandler(Level level, BlockPos pos, BlockState state, double x, double y, double z, @Nullable Direction side) {
        Container container = this.lithium$getExtractBlockInventory(level, pos, state);
        if (container != null) {
            return new ContainerOrHandler(container, null);
        }
        var blockItemHandler = level.getCapability(Capabilities.Item.BLOCK, pos, state, null, side);
        if (blockItemHandler != null) {
            return new ContainerOrHandler(null, blockItemHandler);
        }
        return VanillaInventoryCodeHooks.getEntityContainerOrHandler(level, x, y, z, side);
    }

    //Implemented in common HopperBlockEntityMixin
    @Unique
    public Container lithium$getInsertBlockInventory(Level world) {
        throw new AssertionError();
    }

    //Implemented in common HopperBlockEntityMixin
    @Unique
    public Container lithium$getExtractBlockInventory(Level world, BlockPos extractBlockPos, BlockState extractBlockState) {
        throw new AssertionError();
    }
}
