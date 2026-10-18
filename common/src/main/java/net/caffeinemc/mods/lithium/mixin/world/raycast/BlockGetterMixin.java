package net.caffeinemc.mods.lithium.mixin.world.raycast;

import net.caffeinemc.mods.lithium.common.world.BlockHitFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.BiFunction;
import java.util.function.Function;

@Mixin(BlockGetter.class)
public interface BlockGetterMixin {

    @Shadow
    BlockState getBlockState(BlockPos pos);

    @Shadow
    static <T, C> T traverseBlocks(Vec3 start, Vec3 end, C context, BiFunction<C, BlockPos, T> blockHitFactory, Function<C, T> missFactory) {throw new AssertionError();}

    @Shadow
    public BlockHitResult lambda$clip$0(ClipContext par1, BlockPos par2);

    @Shadow
    public static BlockHitResult lambda$clip$1(ClipContext par1) {
        throw new AssertionError();
    }

    /**
     * @author 2No2Name
     * @reason Get rid of unnecessary lambda allocation
     */
    @Overwrite
    default BlockHitResult clip(ClipContext context) {
        return traverseBlocks(context.getFrom(),
                context.getTo(),
                context,
                // This intentionally excludes custom level implementations of create / ponder due to compatibility issues
                (this instanceof ServerLevel || this instanceof Level l && l.isClientSide()) ? new BlockHitFactory.Generic((Level) this, context) : this::lambda$clip$0,
                BlockGetterMixin::lambda$clip$1);
    }
}
