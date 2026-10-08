@MixinConfigOption(
        description = "Avoid map items in item frames performing inventory scanning quadratic in the number of online players. Avoid continuously sending map updates to players that do not have a map when a map is both in an item frame in the same dimension and any player's inventory",
        nonVanillaBehavior = "In vanilla the first player in a framed map's dimension can reliably see themselves on the " +
                "framed map while wearing a carved pumpkin and holding the map item in their inventory when they have a " +
                "client mod that displays player decorations on framed maps. All other players with the same setup may see " +
                "their player decoration on the map flicker. With this optimization, the carved pumpkin effect should apply " +
                "to all players equally. This has not been confirmed in testing."
)
package net.caffeinemc.mods.lithium.mixin.entity.framed_maps;

import net.caffeinemc.gradle.MixinConfigOption;