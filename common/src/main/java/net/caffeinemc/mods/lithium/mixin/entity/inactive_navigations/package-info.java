@MixinConfigOption(
        description = "Block updates skip notifying mobs that won't react to the block update anyways",
        depends = @MixinConfigDependency(dependencyPath = "mixin.util.data_storage"),
        enabled = false //TODO fix Intrinsic issue then re-enable
)
package net.caffeinemc.mods.lithium.mixin.entity.inactive_navigations;

import net.caffeinemc.gradle.MixinConfigDependency;
import net.caffeinemc.gradle.MixinConfigOption;