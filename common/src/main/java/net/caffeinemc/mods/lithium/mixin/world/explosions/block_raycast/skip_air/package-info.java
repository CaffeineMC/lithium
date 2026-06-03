@MixinConfigOption(
        description = "Explosions skip exploding air blocks, reducing allocations and collection sizes.",
        nonVanillaBehavior = "Explosions do not destroy blocks which are placed by the same explosion damaging or killing entities " +
                "(e.g. wither roses). Similarly, they do not destroy blocks that are instantly placed from block updates " +
                "from blocks being blown up or entities being damaged (e.g. wither rose being placed). " +
                "The explosion counts how many air blocks it would have blown up to spawn the right amount " +
                "of explosion particles. However, this skips special logic that could have protected the " +
                "air block, e.g. a floating rail protecting air below it from a TNT minecart explosion."
)
package net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.skip_air;

import net.caffeinemc.gradle.MixinConfigOption;