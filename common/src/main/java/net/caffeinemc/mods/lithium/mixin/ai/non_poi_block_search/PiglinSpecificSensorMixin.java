package net.caffeinemc.mods.lithium.mixin.ai.non_poi_block_search;

import net.caffeinemc.mods.lithium.common.ai.non_poi_block_search.CommonBlockSearchesCheckAndCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PiglinSpecificSensor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * [Vanilla Copy]
 * Optimizes Piglin repellent search.
 */
@Mixin(PiglinSpecificSensor.class)
public abstract class PiglinSpecificSensorMixin {
    @Unique
    private static final Predicate<BlockState> IS_VALID_REPELLENT_PREDICATE =
            PiglinSpecificSensorMixin::lithium$isValidRepellent;

    @Redirect(method = "doTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/sensing/PiglinSpecificSensor;findNearestRepellent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;"))
    public Optional<BlockPos> redirectFindNearestRepellent(ServerLevel serverLevel, LivingEntity livingEntity) {
        return CommonBlockSearchesCheckAndCache.blockPosFindClosestMatch(serverLevel, livingEntity, 8, 4,
                IS_VALID_REPELLENT_PREDICATE, true);
    }

    @Unique
    private static boolean lithium$isValidRepellent(BlockState blockState){
        final boolean isPiglinRepellent = blockState.is(BlockTags.PIGLIN_REPELLENTS);
        return isPiglinRepellent && blockState.is(Blocks.SOUL_CAMPFIRE) ?
                CampfireBlock.isLitCampfire(blockState) : isPiglinRepellent;
    }
}
