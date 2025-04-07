package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.particle.biome_particles;

import net.caffeinemc.mods.lithium.common.client.SharedFields;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin extends Level {

    protected ClientLevelMixin(WritableLevelData writableLevelData, ResourceKey<Level> resourceKey, RegistryAccess registryAccess, Holder<DimensionType> holder, boolean bl, boolean bl2, long l, int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, bl, bl2, l, i);
    }

    //Future work if still showing up in profiler: Can also check the nearby biome palettes for the particle-emitting nether biomes
    @Redirect(
            method = "doAnimateTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isCollisionShapeFullBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean evaluateChanceEarly(BlockState instance, BlockGetter blockGetter, BlockPos blockPos) {
        float maximumParticleChance = Float.intBitsToFloat(SharedFields.MAXIMUM_BIOME_PARTICLE_CHANCE.get());
        return this.random.nextFloat() > maximumParticleChance || instance.isCollisionShapeFullBlock(blockGetter, blockPos);
    }
}
