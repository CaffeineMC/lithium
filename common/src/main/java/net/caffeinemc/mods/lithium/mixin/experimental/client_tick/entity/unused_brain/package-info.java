@MixinConfigOption(
        description = "Skip creating brains for living entities on the client",
        depends = @MixinConfigDependency(dependencyPath = "mixin.experimental.client_tick.entity.base_tick.unused_ambient_sound")
)
package net.caffeinemc.mods.lithium.mixin.experimental.client_tick.entity.unused_brain;

import net.caffeinemc.gradle.MixinConfigDependency;
import net.caffeinemc.gradle.MixinConfigOption;