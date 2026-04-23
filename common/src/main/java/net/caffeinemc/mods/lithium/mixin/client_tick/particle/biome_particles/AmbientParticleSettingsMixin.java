package net.caffeinemc.mods.lithium.mixin.client_tick.particle.biome_particles;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.lithium.common.client.SharedFields;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.attribute.AmbientParticle;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AmbientParticle.class)
public class AmbientParticleSettingsMixin {

    @Inject(
            method = "<init>", at = @At("RETURN")
    )
    private void findMaximumChance(ParticleOptions particleOptions, float probability, CallbackInfo ci) {
        int current_maximum_chance = SharedFields.MAXIMUM_BIOME_PARTICLE_CHANCE.get();
        while (probability > Float.intBitsToFloat(current_maximum_chance)) {
            current_maximum_chance = SharedFields.MAXIMUM_BIOME_PARTICLE_CHANCE.compareAndExchange(current_maximum_chance, Float.floatToIntBits(probability));
        }
    }

    @ModifyExpressionValue(
            method = "canSpawn", at = @At(value = "FIELD", target = "Lnet/minecraft/world/attribute/AmbientParticle;probability:F", opcode = Opcodes.GETFIELD)
    )
    private float getAdjustedProbability(float original) {
        return original / Float.intBitsToFloat(SharedFields.MAXIMUM_BIOME_PARTICLE_CHANCE.get());
    }
}
