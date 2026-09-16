@MixinConfigOption(
        description = "Chunk sections count certain blocks inside them and provide a method to quickly check whether a" +
                " chunk contains any of these blocks. Furthermore, chunk sections can notify registered listeners about" +
                " certain blocks being placed or broken.",
        depends = {
                @MixinConfigDependency(dependencyPath = "mixin.util.data_storage"),
                @MixinConfigDependency(dependencyPath = "mixin.util.chunk_status_tracking"),
                @MixinConfigDependency(dependencyPath = "mixin.util.initialization"),
                @MixinConfigDependency(dependencyPath = "mixin.util.section_data_storage")
        }
)
package net.caffeinemc.mods.lithium.mixin.util.block_tracking;

import net.caffeinemc.gradle.MixinConfigDependency;
import net.caffeinemc.gradle.MixinConfigOption;