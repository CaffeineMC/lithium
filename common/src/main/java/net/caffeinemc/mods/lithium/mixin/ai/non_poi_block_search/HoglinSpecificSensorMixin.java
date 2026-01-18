package net.caffeinemc.mods.lithium.mixin.ai.non_poi_block_search;

import net.caffeinemc.mods.lithium.common.ai.non_poi_block_search.CommonBlockSearchesCheckAndCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.sensing.HoglinSpecificSensor;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/**
 * [Vanilla Copy]
 * Optimizes Hoglin repellent search.
 */
@Mixin(HoglinSpecificSensor.class)
public abstract class HoglinSpecificSensorMixin {
    @Unique
    private static final java.util.function.Predicate<BlockState> IS_VALID_REPELLENT_PREDICATE =
            HoglinSpecificSensorMixin::lithium$isValidRepellent;

    @Redirect(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/hoglin/Hoglin;)V",
    at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/sensing/HoglinSpecificSensor;findNearestRepellent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/hoglin/Hoglin;)Ljava/util/Optional;"))
    private Optional<BlockPos> redirectFindNearestRepellent(HoglinSpecificSensor instance, ServerLevel serverLevel,
                                                            Hoglin hoglin) {
        return CommonBlockSearchesCheckAndCache.blockPosFindClosestMatch(serverLevel, hoglin, 8, 4,
                IS_VALID_REPELLENT_PREDICATE, true);
    }

    @Unique
    private static boolean lithium$isValidRepellent(BlockState blockState) {
        return blockState.is(BlockTags.HOGLIN_REPELLENTS);
    }
}
