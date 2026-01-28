package net.caffeinemc.mods.lithium.mixin.ai.poi;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.caffeinemc.mods.lithium.common.util.Distances;
import net.caffeinemc.mods.lithium.common.world.interests.PoiOrdering;
import net.caffeinemc.mods.lithium.common.world.interests.PointOfInterestSetExtended;
import net.caffeinemc.mods.lithium.common.world.interests.PointOfInterestStorageExtended;
import net.caffeinemc.mods.lithium.common.world.interests.RegionBasedStorageSectionExtended;
import net.caffeinemc.mods.lithium.common.world.interests.iterator.NearbyPointOfInterestStream;
import net.caffeinemc.mods.lithium.common.world.interests.iterator.SingleChunkPointOfInterestStream;
import net.caffeinemc.mods.lithium.common.world.interests.iterator.SinglePointOfInterestTypeFilter;
import net.caffeinemc.mods.lithium.common.world.interests.iterator.SphereChunkOrderedPoiSetSpliterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Mixin(PoiManager.class)
public abstract class PoiManagerMixin extends SectionStorage<PoiSection, PoiSection.Packed> implements PointOfInterestStorageExtended, RegionBasedStorageSectionExtended<PoiSection> {


    public PoiManagerMixin(SimpleRegionStorage simpleRegionStorage, Codec<PoiSection.Packed> codec, Function<PoiSection, PoiSection.Packed> function, BiFunction<PoiSection.Packed, Runnable, PoiSection> biFunction, Function<Runnable, PoiSection> function2, RegistryAccess registryAccess, ChunkIOErrorReporter chunkIOErrorReporter, LevelHeightAccessor levelHeightAccessor) {
        super(simpleRegionStorage, codec, function, biFunction, function2, registryAccess, chunkIOErrorReporter, levelHeightAccessor);
    }

    /**
     * Gets a random POI that matches the requirements. Uses spherical radius.
     *
     * @reason Retrieve all points of interest in one operation, avoid stream code
     * @author JellySquid
     * As vanilla, the random distribution is uniform.
     * However, this does not return the same point as vanilla when given the same pseudo-random seed.
     */
    @Overwrite
    public Optional<BlockPos> getRandom(Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate,
                                        PoiManager.Occupancy status, BlockPos pos, int radius,
                                        RandomSource rand) {
        ArrayList<PoiRecord> list = this.withinSquareInL2Range(typePredicate, pos, radius, status);

        for (int i = list.size() - 1; i >= 0; i--) {
            //shuffle by swapping randomly
            PoiRecord currentPOI = list.set(rand.nextInt(i + 1), list.get(i));
            list.set(i, currentPOI); //Move to the end of the unconsumed part of the list

            //consume while shuffling, abort shuffling when result found
            if (posPredicate.test(currentPOI.getPos())) {
                return Optional.of(currentPOI.getPos());
            }
        }

        return Optional.empty();
    }


    /**
     * Elements should be minimal N wrt. {@link PoiOrdering.L2ThenInSquare#INSTANCE}
     */
    @Override
    public Collection<Pair<Holder<PoiType>, BlockPos>> lithium$getNClosestFirstWithType(Predicate<Holder<PoiType>> typeFilter, Predicate<BlockPos> posFilter, BlockPos center, int radius, PoiManager.Occupancy status, long n) {
        int radiusSq = radius * radius;
        NearbyPointOfInterestStream poisInRange = new NearbyPointOfInterestStream(
                typeFilter,
                status,
                poiRecord -> posFilter.test(poiRecord.getPos()),
                center,
                radius,
                this,
                (pos, pos2) -> Distances.isWithinSphereRadius(pos, radiusSq, pos2),
                NearbyPointOfInterestStream.POINT_COMPARATOR
        );

        ArrayList<Pair<Holder<PoiType>, BlockPos>> collectedPois = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean b = poisInRange.tryAdvance(poi -> collectedPois.add(Pair.of(poi.getPoiType(), poi.getPos())));
            if (!b) {
                break;
            }
        }
        return collectedPois;
    }

    /**
     * @author 2No2Name
     * @reason Avoid stream code and avoid searching sections outside the spherical radius
     * Return element is the minimal element wrt. {@link PoiOrdering.InSquare#INSTANCE} that is within the spherical radius
     */
    @Overwrite
    public Optional<BlockPos> find(Predicate<Holder<PoiType>> predicate, Predicate<BlockPos> filter, BlockPos center, int radius, PoiManager.Occupancy status) {
        long radiusSq = (long) radius * (long) radius;

        int minChunkX = center.getX() - radius - 1 >> 4;
        int maxChunkX = center.getX() + radius + 1 >> 4;
        int minChunkZ = center.getZ() - radius - 1 >> 4;
        int maxChunkZ = center.getZ() + radius + 1 >> 4;

        int chunkX = minChunkX;
        int chunkZ = minChunkZ;

        while (chunkZ <= maxChunkZ) {
            long minChunkToBlockDistanceL2Sq = Distances.getMinChunkToBlockDistanceL2Sq(center, chunkX, chunkZ);
            if (minChunkToBlockDistanceL2Sq <= radiusSq) {
                long deltaYSqMargin = radiusSq - minChunkToBlockDistanceL2Sq; //dY² = distance² - dX² - dZ²
                PoiRecord firstMatch = this.lithium$getFirstInRangeInChunkColumn(chunkX, chunkZ,
                        deltaYSqMargin, center, radiusSq,
                        (poiSection, pos, typeFilter, posPredicate, occupancy, maxDistSq) ->
                                ((PointOfInterestSetExtended) poiSection).lithium$getFirstMatchingPoint(pos, maxDistSq, typeFilter, posPredicate, occupancy),
                        predicate, filter, status);
                if (firstMatch != null) {
                    return Optional.of(firstMatch.getPos());
                }
            }

            chunkX++;
            if (chunkX > maxChunkX) {
                chunkZ++;
                chunkX = minChunkX;
            }

        }
        return Optional.empty();
    }

    /**
     * @author 2No2Name
     * @reason Avoid stream code, search in-range sections only and search closer sections first
     * Return element is the minimal element wrt. {@link PoiOrdering.L2ThenInSquare#INSTANCE} that is within spherical radius
     */
    @Overwrite
    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(
            Predicate<Holder<PoiType>> typeFilter, BlockPos center, int radius, PoiManager.Occupancy status
    ) {
        int radiusSq = radius * radius;
        PoiRecord closestPoi = new NearbyPointOfInterestStream(
                typeFilter,
                status,
                null,
                center,
                radius,
                this,
                (pos, pos2) -> Distances.isWithinSphereRadius(pos, radiusSq, pos2),
                NearbyPointOfInterestStream.POINT_COMPARATOR
        ).getFirst();
        return closestPoi == null ? Optional.empty() : Optional.of(Pair.of(closestPoi.getPoiType(), closestPoi.getPos()));
    }

    @Override
    public Optional<BlockPos> lithium$takeAt(Predicate<Holder<PoiType>> typeFilter, BiPredicate<Holder<PoiType>, BlockPos> biPredicate, BlockPos blockPos) {
        Optional<PoiSection> poiSection = this.getOrLoad(SectionPos.asLong(blockPos));
        if (poiSection.isPresent()) {
            PoiRecord poiRecord = ((PointOfInterestSetExtended) poiSection.get()).lithium$getAt(blockPos);
            if (poiRecord != null && typeFilter.test(poiRecord.getPoiType())) {
                poiRecord.acquireTicket();
                return Optional.of(poiRecord.getPos());
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the closest POI that matches the requirements.
     *
     * @reason Avoid stream-heavy code, use a faster iterator and callback-based approach
     * @author 2No2Name
     * <p>
     * Return element is the minimal element wrt. {@link PoiOrdering.L2ThenInSquare#INSTANCE} that is within spherical radius
     */
    @Overwrite
    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius,
                                          PoiManager.Occupancy status) {
        return this.findClosest(predicate, null, center, radius, status);
    }

    /**
     * Gets the closest POI that matches the requirements.
     *
     * @reason Avoid stream code and avoid evaluating expensive predicate unnecessarily
     * @author 2No2Name
     * <p>
     * Return element is the minimal element wrt. {@link PoiOrdering.L2ThenInSquare#INSTANCE} that is within spherical radius
     */
    @Overwrite
    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> predicate,
                                          Predicate<BlockPos> posPredicate, BlockPos center, int radius,
                                          PoiManager.Occupancy status) {
        int radiusSq = radius * radius;
        PoiRecord closest = new NearbyPointOfInterestStream(
                predicate,
                status,
                posPredicate == null ? null : poi -> posPredicate.test(poi.getPos()),
                center,
                radius,
                this,
                (pos, pos2) -> Distances.isWithinSphereRadius(pos, radiusSq, pos2),
                NearbyPointOfInterestStream.POINT_COMPARATOR
        ).getFirst();
        return closest == null ? Optional.empty() : Optional.of(closest.getPos());
    }

    /**
     * Get number of matching POIs in sphere
     *
     * @reason Avoid stream-heavy code, use a faster iterator and callback-based approach
     * @author JellySquid
     */
    @Overwrite
    public long getCountInRange(Predicate<Holder<PoiType>> predicate, BlockPos pos, int radius,
                                PoiManager.Occupancy status) {
        return this.withinSquareInL2Range(predicate, pos, radius, status).size();
    }

    /**
     * Get all POI in sphere around center with given radius.
     * Order is {@link PoiOrdering.InSquare#INSTANCE}
     *
     * @author JellySquid
     * @reason Avoid stream-heavy code, use faster filtering and fetches
     */
    @Overwrite
    public Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> predicate, BlockPos center, int radius, PoiManager.Occupancy status) {
        return StreamSupport.stream(new SphereChunkOrderedPoiSetSpliterator(radius, center, this, predicate, status), false);
    }

    /**
     * @author jcw780
     * @reason Fall back to column checked accesses in case POI search is otherwise unoptimized
     */
    @VisibleForDebug
    @Overwrite
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> predicate, ChunkPos chunkPos, PoiManager.Occupancy occupancy) {
        return StreamSupport.stream(new SingleChunkPointOfInterestStream(predicate, chunkPos, occupancy, this), false);
    }

    @Override
    public Optional<PoiRecord> lithium$findNearestForPortalLogic(BlockPos origin, int radius, Holder<PoiType> type,
                                                                 PoiManager.Occupancy status,
                                                                 Predicate<PoiRecord> afterSortPredicate, WorldBorder worldBorder) {

        boolean worldBorderIsFarAway = worldBorder == null || worldBorder.getDistanceToBorder(origin.getX(), origin.getZ()) > radius + 3;
        Predicate<PoiRecord> poiPredicateAfterSorting;
        if (worldBorderIsFarAway) {
            poiPredicateAfterSorting = afterSortPredicate;
        } else {
            poiPredicateAfterSorting = poi -> worldBorder.isWithinBounds(poi.getPos()) && afterSortPredicate.test(poi);
        }
        Predicate<Holder<PoiType>> typePredicate = new SinglePointOfInterestTypeFilter(type);
        PoiRecord nearestPoi = new NearbyPointOfInterestStream(
                typePredicate,
                status,
                poiPredicateAfterSorting,
                origin,
                radius,
                this,
                (pos, pos2) -> Distances.isWithinCubeRadius(pos, radius, pos2),
                NearbyPointOfInterestStream.NEGATIVE_Y_POINT_COMPARATOR
        ).getFirst();
        return nearestPoi == null ? Optional.empty() : Optional.of(nearestPoi);
    }


    @Unique
    private ArrayList<PoiRecord> withinSquareInL2Range(Predicate<Holder<PoiType>> predicate, BlockPos origin,
                                                       int radius, PoiManager.Occupancy status) {
        int radiusSq = Math.multiplyExact(radius, radius);

        int minChunkX = origin.getX() - radius - 1 >> 4;
        int minChunkZ = origin.getZ() - radius - 1 >> 4;

        int maxChunkX = origin.getX() + radius + 1 >> 4;
        int maxChunkZ = origin.getZ() + radius + 1 >> 4;

        ArrayList<PoiRecord> points = new ArrayList<>();
        Consumer<PoiRecord> collector = point -> {
            if (Distances.isWithinSphereRadius(origin, radiusSq, point.getPos())) {
                points.add(point);
            }
        };

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                for (PoiSection set : this.lithium$getInChunkColumn(x, z)) {
                    ((PointOfInterestSetExtended) set).lithium$collectMatchingPoints(predicate, status, collector);
                }
            }
        }

        return points;
    }

}
