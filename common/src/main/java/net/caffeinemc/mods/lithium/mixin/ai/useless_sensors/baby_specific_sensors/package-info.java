@MixinConfigOption(description = "Disable certain sensors when an entity is not a baby." +
        " Would differ from vanilla in the case where an adult mob turns back into a baby mob, as the sensor information is refreshed," +
        " leading to a less-outdated value in the first second of turning back into a baby mob. However, there is no way to turn an mob" +
        " back into a baby without reinitializing the brain, creating entirely new sensors."
)
package net.caffeinemc.mods.lithium.mixin.ai.useless_sensors.baby_specific_sensors;

import net.caffeinemc.gradle.MixinConfigOption;