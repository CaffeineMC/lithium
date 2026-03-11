package net.caffeinemc.mods.lithium.mixin.ai.poi.reduce_poi_memory;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.util.collections.ListeningLong2ObjectOpenHashMap;
import net.caffeinemc.mods.lithium.common.world.interests.PoiUnloading;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin<R> implements RegionBasedStorageSectionExtended<R>, PoiUnloading {
    @Shadow
    @Final
    private Object loadLock;

    @Shadow
    @Final
    private LongSet loadedChunks;

    @Shadow
    @Final
    private Long2ObjectMap<CompletableFuture<Optional<SectionStorage.PackedChunk<?>>>> pendingLoads;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Optional<R>> storage;

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Shadow
    protected abstract boolean outsideStoredRange(long l);

    /* Since we are removing Optional.empty() sections, we use the Lithium column lookup in its place.
     * Because these are normally added whenever a new entry is added, we would have missing columns when a whole chunk
     * lacks POI sections.
     * This means we must always add the column whenever a chunk is loaded.
     */
    @Inject(method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/storage/SectionStorage$PackedChunk;)V", at= @At(value = "HEAD"))
    private void initializeColumnBitset(ChunkPos chunkPos, @Coerce Object ignored, CallbackInfo ci) {
        final long pos = chunkPos.toLong();
        this.lithium$getOrAddColumnIfNull(pos);
    }

    // Do not add Optional.empty() sections into the map
    @Redirect(method = "unpackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/storage/SectionStorage$PackedChunk;)V", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;put(JLjava/lang/Object;)Ljava/lang/Object;"))
    private Object removeOptionalEmpties(Long2ObjectMap instance, long l, Object o) {
        if(o.equals(Optional.empty())) {
            return null;
        }
        return instance.put(l, o);
    }

    /**
     * @author jcw780
     * @reason Match vanilla returns after removal of Optional.empty() sections from storage
     * Warning: This has more checks than vanilla to match vanilla behavior.
     * Please use other methods if it is performance sensitive.
     */
    @Overwrite
    @Nullable
    public Optional<R> get(long l) { // Has to be public even though it is protected in vanilla for some reason
        // For some reason PoiManager::isVillageCenter updates called from SectionTracker::getComputedLevel can be out of bounds
        final int y = SectionPos.y(l);
        final int columnIndex = Pos.SectionYIndex.fromSectionCoord(this.levelHeightAccessor, y);
        // Out of range sections will not be in the storage
        if (columnIndex < 0 || columnIndex >= Pos.SectionYIndex.getNumYSections(this.levelHeightAccessor)) {
            //noinspection OptionalAssignedToNull
            return null;
        }

        // Move the access to the front to improve best case (already loaded) performance
        Optional<R> result = this.storage.get(l);

        // noinspection OptionalAssignedToNull
        if (result != null) {
            return result;
        }

        final int x = SectionPos.x(l);
        final int z = SectionPos.z(l);
        final BitSet flags = this.lithium$getColumn(ChunkPos.asLong(x, z));

        // If there are no flags, then the chunk was never loaded - so the section will not be in storage
        if (flags == null) {
            // noinspection OptionalAssignedToNull
            return null;
        }

        // Sections without POI sections are stored as Optional.empty() in vanilla
        if (!flags.get(columnIndex)) {
            return Optional.empty();
        }

        // Section marked as having a POI Section is not in the storage hashmap - this should not happen
        throw new IllegalStateException(String.format("Section %d %d %d missing from storage despite being marked as present", x, y, z));
    }

    /**
     * @author jcw780
     * @reason Match vanilla returns after removal of Optional.empty() sections from storage
     * Warning: This has more checks than vanilla to match vanilla behavior.
     * Please use other methods if it is performance sensitive.
     */
    @Overwrite
    public Optional<R> getOrLoad(long l) {
        if (this.outsideStoredRange(l)) {
            return Optional.empty();
        } else {
            Optional<R> optional = this.storage.get(l);

            // noinspection OptionalAssignedToNull
            if (optional != null) { // Section is in the storage - return early - doing this since most of the time it will be loaded
                return optional;
            }

            ChunkPos chunkPos = SectionPos.of(l).chunk();
            BitSet column = this.lithium$getNonEmptyPOISections(chunkPos.x, chunkPos.z);

            final int columnIndex = Pos.SectionYIndex.fromSectionCoord(this.levelHeightAccessor, SectionPos.y(l));

            //Sections without POI sections are stored as Optional.empty() in vanilla
            if (!column.get(columnIndex)) {
                return Optional.empty();
            }

            optional = this.storage.get(l);
            // noinspection OptionalAssignedToNull
            if (optional == null) {
                throw Util.pauseInIde(new IllegalStateException());
            } else {
                return optional;
            }
        }
    }

    /**
     * This is for unloading all the points of interest in the chunk using the new storage logic.
     * This greatly improves performance since most sections will not have points of interest sections.
     * Intended for use by Lithium's minimal_nonvanilla.poi_unloading or as part of Neoforge default behavior
     * @param chunkPos
     */
    @Override
    public void lithium$unloadChunkPOIs(long chunkPos) {
        // Make sure the injection happens after PoiManager::flush in chunk unload
        // Then we will not need to call flush separately

        // Remove column bitset
        BitSet chunkSections = this.lithium$removeColumn(chunkPos);
        if (chunkSections != null) {
            // This relies on the reduce poi memory optimizations so we only need to remove sections with a POISection
            final int chunkYMin = this.lithium$getChunkYMin();
            final int x = ChunkPos.getX(chunkPos);
            final int z = ChunkPos.getZ(chunkPos);

            int nextSectionY = -1;
            while ((nextSectionY = chunkSections.nextSetBit(nextSectionY + 1)) != -1) {
                // Column is already removed so we do not need to update the column in the columns hashmap
                this.lithium$removeSectionWithoutUpdatingColumn(SectionPos.asLong(x, chunkYMin + nextSectionY, z));
            }
        }

        synchronized (this.loadLock) {
            this.loadedChunks.remove(chunkPos);
            this.pendingLoads.remove(chunkPos);
        }
    }
}
