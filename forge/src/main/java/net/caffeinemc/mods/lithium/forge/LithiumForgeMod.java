package net.caffeinemc.mods.lithium.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Lithium needs no setup of its own: every optimisation is a mixin, and the platform hooks are
 * reached through ServiceLoader. This class exists because Forge refuses a mods.toml entry with no
 * matching class, where NeoForge tolerates one.
 */
@Mod("lithium")
public class LithiumForgeMod {
    // Forge 26.x injects the loading context rather than NeoForge's mod event bus.
    public LithiumForgeMod(FMLJavaModLoadingContext context) {
    }
}
