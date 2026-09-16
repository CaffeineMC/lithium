@MixinConfigOption(
        description = "Speed up random ticks by evaluating random chances early and using a fast block search.",
        depends = {
                @MixinConfigDependency(dependencyPath = "mixin.util.section_data_storage"),
                @MixinConfigDependency(dependencyPath = "mixin.util.block_tracking")

        }
)
package net.caffeinemc.mods.lithium.mixin.world.chunk_ticking.random_block_ticking;

import net.caffeinemc.gradle.MixinConfigDependency;
import net.caffeinemc.gradle.MixinConfigOption;