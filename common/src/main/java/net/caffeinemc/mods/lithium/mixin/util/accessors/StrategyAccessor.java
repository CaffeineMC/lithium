package net.caffeinemc.mods.lithium.mixin.util.accessors;

import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Strategy.class)
public interface StrategyAccessor {
    @Invoker("getConfigurationForPaletteSize")
    Configuration lithium$getConfigurationForPaletteSize(int i);
}
