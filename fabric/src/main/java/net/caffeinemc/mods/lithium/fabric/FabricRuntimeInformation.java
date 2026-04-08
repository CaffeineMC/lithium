package net.caffeinemc.mods.lithium.fabric;

import net.caffeinemc.mods.lithium.common.services.PlatformRuntimeInformation;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FabricRuntimeInformation implements PlatformRuntimeInformation {
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean platformHasEarlyLoadingScreen() {
        return false;
    }

    @Override
    public boolean isModInLoadingList(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
