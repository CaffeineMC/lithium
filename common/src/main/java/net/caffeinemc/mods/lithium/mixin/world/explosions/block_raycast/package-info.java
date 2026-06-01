@MixinConfigOption(
        description = "Various improvements to explosion block damage, e.g. not accessing blocks along an explosion ray multiple times",
        nonVanillaBehavior = "Explosions do not destroy blocks which are placed by the same explosion damaging or killing entities (e.g. wither roses)."
)
package net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast;

import net.caffeinemc.gradle.MixinConfigOption;