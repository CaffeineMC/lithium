package net.caffeinemc.mods.lithium.common.util;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

public class POIRegistryEntries {
    //Using a separate class, so the registry lookup happens after the registry is initialized
    public static final Holder<PoiType> NETHER_PORTAL_ENTRY = PoiTypes.forState(Blocks.NETHER_PORTAL.defaultBlockState()).orElseThrow(() -> new IllegalStateException("Nether portal poi type not found"));
    public static final Holder<PoiType> HOME_ENTRY = PoiTypes.forState(Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD)).orElseThrow(() -> new IllegalStateException("Home poi type not found"));
}
