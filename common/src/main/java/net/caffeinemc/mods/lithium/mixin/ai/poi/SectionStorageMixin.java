package net.caffeinemc.mods.lithium.mixin.ai.poi;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.caffeinemc.mods.lithium.common.util.Distances;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.util.collections.ListeningLong2ObjectOpenHashMap;
import net.caffeinemc.mods.lithium.common.util.functions.FunLongAnd5;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType") // We don't get a choice, this is Minecraft's doing!
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin<R, P> implements RegionBasedStorageSectionExtended<R> {
    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Optional<R>> storage;

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Shadow
    protected abstract void unpackChunk(ChunkPos chunkPos);

    @Shadow
    @Final
    private Codec<P> codec;

    @Shadow
    @Final
    static Logger LOGGER;

    @Shadow
    @Final
    private Function<R, P> packer;

    private Long2ObjectOpenHashMap<BitSet> columns;

    @SuppressWarnings("rawtypes")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(SimpleRegionStorage simpleRegionStorage, Codec codec, Function function, BiFunction biFunction, Function function2, RegistryAccess registryAccess, ChunkIOErrorReporter chunkIOErrorReporter, LevelHeightAccessor levelHeightAccessor, CallbackInfo ci) {
        this.columns = new Long2ObjectOpenHashMap<>();
        this.storage = new ListeningLong2ObjectOpenHashMap<>(this::onEntryAdded, this::onEntryRemoved);
    }

    private void onEntryRemoved(long key, Optional<R> value) {
        int y = Pos.SectionYIndex.fromSectionCoord(this.levelHeightAccessor, SectionPos.y(key));

        // We only care about items belonging to a valid sub-chunk
        if (y < 0 || y >= Pos.SectionYIndex.getNumYSections(this.levelHeightAccessor)) {
            return;
        }

        int x = SectionPos.x(key);
        int z = SectionPos.z(key);

        long pos = ChunkPos.asLong(x, z);
        BitSet flags = this.columns.get(pos);

        if (flags != null) {
            flags.clear(y);
            if (flags.isEmpty()) {
                this.columns.remove(pos);
            }
        }
    }

    private void onEntryAdded(long key, Optional<R> value) {
        int y = Pos.SectionYIndex.fromSectionCoord(this.levelHeightAccessor, SectionPos.y(key));

        // We only care about items belonging to a valid sub-chunk
        if (y < 0 || y >= Pos.SectionYIndex.getNumYSections(this.levelHeightAccessor)) {
            return;
        }

        int x = SectionPos.x(key);
        int z = SectionPos.z(key);

        long pos = ChunkPos.asLong(x, z);

        BitSet flags = this.columns.get(pos);

        if (flags == null) {
            this.columns.put(pos, flags = new BitSet(Pos.SectionYIndex.getNumYSections(this.levelHeightAccessor)));
        }

        flags.set(y, value.isPresent());
    }

    @Override
    @Nullable
    public BitSet lithium$getColumn(long chunkPos) {
        return this.columns.get(chunkPos);
    }

    @Override
    public BitSet lithium$removeColumn(long chunkPos) {
        return this.columns.remove(chunkPos);
    }

    @Override
    public Optional<R> lithium$removeSectionWithoutUpdatingColumn (long sectionPos) {
        return ((ListeningLong2ObjectOpenHashMap<Optional<R>>) this.storage).removeSilently(sectionPos);
    }

    @Override
    public BitSet lithium$getOrAddColumnIfNull(long chunkPos) {
        BitSet column = this.columns.get(chunkPos);

        if (column == null) {
            this.columns.put(chunkPos, column = new BitSet(Pos.SectionYIndex.getNumYSections(this.levelHeightAccessor)));
        }

        return column;
    }

    @Override
    public <S, T, U> U lithium$getFirstInRangeInChunkColumn(int chunkX, int chunkZ,
                                                            long deltaYSqMargin,
                                                            BlockPos center, long radiusSq,
                                                            FunLongAnd5<R, BlockPos, Predicate<Holder<S>>, Predicate<BlockPos>, T, U> sectionMapper,
                                                            Predicate<Holder<S>> predicate, Predicate<BlockPos> filter, T status) {
        BitSet sectionsWithPOI = this.lithium$getNonEmptyPOISections(chunkX, chunkZ);

        if (sectionsWithPOI.isEmpty()) {
            return null;
        }
        int minYSection = Pos.SectionYCoord.getMinYSection(this.levelHeightAccessor);
        for (int chunkYIndex = sectionsWithPOI.nextSetBit(0); chunkYIndex != -1; chunkYIndex = sectionsWithPOI.nextSetBit(chunkYIndex + 1)) {
            int chunkY = chunkYIndex + minYSection;
            long minYDistance = Distances.getClosestBlockCoordInSection(center.getY(), chunkY) - center.getY();
            if (minYDistance * minYDistance <= deltaYSqMargin) {
                R r = this.storage.get(SectionPos.asLong(chunkX, chunkY, chunkZ)).orElse(null);
                U result = sectionMapper.apply(r, center, predicate, filter, status, radiusSq);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @Override
    public Iterable<R> lithium$getInChunkColumn(int chunkX, int chunkZ) {
        BitSet sectionsWithPOI = this.lithium$getNonEmptyPOISections(chunkX, chunkZ);

        // No items are present in this column
        if (sectionsWithPOI.isEmpty()) {
            return Collections::emptyIterator;
        }

        Long2ObjectMap<Optional<R>> loadedElements = this.storage;
        LevelHeightAccessor world = this.levelHeightAccessor;

        return () -> new AbstractIterator<>() {
            private int nextBit = sectionsWithPOI.nextSetBit(0);


            @Override
            protected R computeNext() {
                // If the next bit is <0, that means that no remaining set bits exist
                while (this.nextBit >= 0) {
                    Optional<R> next = loadedElements.get(SectionPos.asLong(chunkX, Pos.SectionYCoord.fromSectionIndex(world, this.nextBit), chunkZ));

                    // Find and advance to the next set bit
                    this.nextBit = sectionsWithPOI.nextSetBit(this.nextBit + 1);

                    if (next.isPresent()) {
                        return next.get();
                    }
                }

                return this.endOfData();
            }
        };
    }

    @Override
    public BitSet lithium$getNonEmptyPOISections(int chunkX, int chunkZ) {
        long pos = ChunkPos.asLong(chunkX, chunkZ);

        BitSet flags = this.columns.get(pos);

        if (flags != null) {
            return flags;
        }

        this.unpackChunk(new ChunkPos(pos));

        return Objects.requireNonNull(this.columns.get(pos), "Failed to load POI section data!");
    }

    @Override
    public Optional<R> lithium$uncheckedGetElementAt(long sectionPos) {
        return this.storage.get(sectionPos);
    }

    @Override
    public int lithium$getChunkYMin() {
        return Pos.SectionYCoord.getMinYSection(this.levelHeightAccessor);
    }

    @Override
    public int lithium$getChunkYMaxInclusive() {
        return Pos.SectionYCoord.getMaxYSectionInclusive(this.levelHeightAccessor);
    }

    /**
     * These mixins are to remove Optional.empty() sections stored in the storage hashmap.
     * This is done to mitigate a memory leak in vanilla as these are never unloaded. The long keys will eventually
     * build up as more chunks have been loaded.
     * The new logic utilizes the Lithium columns lookup for populated sections instead of Optional.empty() stored
     * instead the storage hashmap.
     */


    /**
     * @author jcw780
     * @reason Use Lithium columns look up in write chunk
     */
    @Overwrite
    private <T> com.mojang.serialization.Dynamic<T> writeChunk(ChunkPos chunkPos, DynamicOps<T> dynamicOps) {
        Map<T, T> map = Maps.<T, T>newHashMap();
        final int chunkX = chunkPos.x;
        final int chunkZ = chunkPos.z;
        BitSet sectionsWithPOI = this.columns.get(chunkPos.toLong());

        if (sectionsWithPOI != null) {
            int nextBit = sectionsWithPOI.nextSetBit(0);
            while (nextBit >= 0) {
                final int chunkY = Pos.SectionYCoord.fromSectionIndex(this.levelHeightAccessor, nextBit);
                Optional<R> next = this.storage.get(SectionPos.asLong(chunkX, chunkY, chunkZ));

                // Find and advance to the next set bit
                nextBit = sectionsWithPOI.nextSetBit(nextBit + 1);

                if (next.isPresent()) {
                    DataResult<T> dataResult = this.codec.encodeStart(dynamicOps, (P)this.packer.apply(next.get()));
                    String string = Integer.toString(chunkY);
                    dataResult.resultOrPartial(LOGGER::error).ifPresent(object -> map.put(dynamicOps.createString(string), object));
                }
            }
        }

        return new Dynamic<>(
                dynamicOps,
                dynamicOps.createMap(
                        ImmutableMap.of(
                                dynamicOps.createString("Sections"),
                                dynamicOps.createMap(map),
                                dynamicOps.createString("DataVersion"),
                                dynamicOps.createInt(SharedConstants.getCurrentVersion().dataVersion().version())
                        )
                )
        );
    }

}
