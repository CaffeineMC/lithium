@MixinConfigOption(description = "Only check positions with expiring tickets during ticket expiration." +
        " Can cause reordering of chunks unloading. The chunk unloading order in vanilla is predictable, but depends" +
        " on the hash of the chunk position of the tickets and the hashes of the other chunk tickets, and the order" +
        " of creation of the chunk tickets when hash collisions occur. No known contraptions depend on the unload order."
)
package net.caffeinemc.mods.lithium.mixin.minimal_nonvanilla.world.expiring_chunk_tickets;

import net.caffeinemc.gradle.MixinConfigOption;