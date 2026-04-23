@MixinConfigOption(
        description = "Reduce memory consumption of POI system",
        depends = @MixinConfigDependency(dependencyPath = "mixin.ai.poi")
)
package net.caffeinemc.mods.lithium.mixin.experimental.ai.poi.reduce_poi_memory;

import net.caffeinemc.gradle.MixinConfigDependency;
import net.caffeinemc.gradle.MixinConfigOption;