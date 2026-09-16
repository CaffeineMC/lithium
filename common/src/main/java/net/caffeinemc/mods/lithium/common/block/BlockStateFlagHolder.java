package net.caffeinemc.mods.lithium.common.block;

import org.jetbrains.annotations.Nullable;

public interface BlockStateFlagHolder {
    int lithium$getAllFlags();

    void lithium$initializeFlags();

    void lithium$resetFlags();

    @Nullable Integer lithium$getAllFlagsOrNull();
}
