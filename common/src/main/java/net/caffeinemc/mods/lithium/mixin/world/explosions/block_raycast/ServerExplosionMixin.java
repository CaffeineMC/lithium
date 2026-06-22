package net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.caffeinemc.mods.lithium.common.explosion.DirectMappedExplosionBlockCache;
import net.caffeinemc.mods.lithium.common.explosion.LithiumExplosion;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Optimizations for Explosions: Reduce allocations and getChunk/getBlockState calls
 * Original implementation by
 * @author Jellyquid
 * Slight performance and mod compatibility improvements by
 * @author 2No2Name
 * Remove extra iterations in ray generation logic - minor performance improvement
 * @author jcw780
 * Cache explosion resistances and block states
 * @author 2No2Name, original PR by pwouik
 * Switch to direct-mapped cache
 * @author 2No2Name
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin implements LithiumExplosion {

    @Unique
    private static final HashSet<?> DUMMY_HASHSET = new HashSet<>(0);

    /**
     * Size of the direct-mapped cache.
     * 512 entries × (8 + 4 + ref) bytes ≈ ~10 KB – should fit into the L1 cache
     * <p>
     * 8-9 bits seems to be best for TNT in a synthetic test world. Using 9 since some explosions are larger than TNT.
     * ~85% cache hit rate when testing TNT
     */
    @Unique
    private static final int DIRECT_CACHE_BITS = 9;
    @Unique
    private static final int DIRECT_CACHE_SIZE = 1 << DIRECT_CACHE_BITS;
    @Unique
    private static final int DIRECT_CACHE_MASK = (1 << DIRECT_CACHE_BITS) - 1;

    //Store cache in thread local to avoid allocations (5%-ish performance gain)
    @Unique
    private static final ThreadLocal<DirectMappedExplosionBlockCache> BLOCK_CACHE_TL = ThreadLocal.withInitial(() -> new DirectMappedExplosionBlockCache(DIRECT_CACHE_SIZE));

    @Shadow
    @Final
    private float radius;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private ExplosionDamageCalculator damageCalculator;

    @Shadow
    @Final
    private Vec3 center;


    // The cached mutable block position used during block traversal.
    @Unique
    private final BlockPos.MutableBlockPos cachedPos = new BlockPos.MutableBlockPos();

    // The chunk coordinate of the most recently stepped through block.
    @Unique
    private int prevChunkX = Integer.MIN_VALUE;
    @Unique
    private int prevChunkZ = Integer.MIN_VALUE;

    // The chunk belonging to prevChunkPos.
    @Unique
    private ChunkAccess prevChunk;

    //Vanilla uses the number of exploded blocks, which is reduced by the air block optimization. Deduplication using a hashset is necessary as different explosion rays traverse the same blocks.
    @Unique
    private LongOpenHashSet explodedAirPositions;

    /**
     * Direct-mapped cache:
     * Tags are packed {@link BlockPos} to handle hash collisions correctly.
     * Tag {@link Long#MIN_VALUE} means no cached value. MIN_VALUE equates to a block position over 3 million blocks
     * outside the world border. {@link ServerExplosionMixin#performRayCast(RandomSource, double, double, double, LongOpenHashSet)}
     * has a cutoff at the world border (blockX < -30000000). Thus, checking
     * for the sentinel {@link Long#MIN_VALUE} is not needed.
     */
    @Unique
    private long[] directMappedTags;
    @Unique
    private BlockState[] directMappedStates;

    @Unique
    private float[] directMappedResistances;

    @Unique
    private int bottomY, topY;


    /**
     * Whether the explosion cares about air blocks. If >= 1, air blocks do not have to be added to the set of destroyed blocks.
     * 2 is used to indicate that the total number of exploded blocks is unused, thus counting the number of air blocks that would
     * have exploded is not needed.
     * Skipping air blocks reduces the number of BlockPos allocations, shuffling and getBlockState calls in {@link ServerExplosion#interactWithBlocks(List)}
     */
    @SuppressWarnings("JavadocReference")
    @Unique
    private byte skipAirBlocks;

    @Inject(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;Lnet/minecraft/world/phys/Vec3;FZLnet/minecraft/world/level/Explosion$BlockInteraction;)V",
            at = @At("RETURN")
    )
    private void init(ServerLevel serverLevel, Entity entity, DamageSource damageSource, ExplosionDamageCalculator explosionDamageCalculator, Vec3 vec3, float f, boolean bl, Explosion.BlockInteraction blockInteraction, CallbackInfo ci) {
        this.bottomY = this.level.getMinY();
        this.topY = this.level.getMaxY();
    }

    @Override
    public void lithium$setSkipAir() {
        this.skipAirBlocks = 1;
    }

    @Override
    public boolean lithium$isSkippingAir() {
        return this.skipAirBlocks != 0;
    }

    @Override
    public void lithium$setSkipAirWithoutCounting() {
        if (this.lithium$isSkippingAir()) {
            this.skipAirBlocks = 2;
        }
    }

    @Unique
    private void initCaches() {
        DirectMappedExplosionBlockCache arrays = BLOCK_CACHE_TL.get();
        this.directMappedTags = arrays.directMappedTags();
        Arrays.fill(this.directMappedTags, Long.MIN_VALUE);
        this.directMappedStates = arrays.directMappedStates();
        this.directMappedResistances = arrays.directMappedResistances();
        if (this.skipAirBlocks == 1) {
            this.explodedAirPositions = new LongOpenHashSet();
        }
    }

    @Unique
    private static int posToCacheIndex(long posLong) {
        return ((int) HashCommon.mix(posLong)) & DIRECT_CACHE_MASK;
    }

    @Unique
    private void cacheBlock(long posLong, BlockState blockState, float totalResistance) {
        int index = posToCacheIndex(posLong);
        this.directMappedTags[index] = posLong;
        this.directMappedResistances[index] = totalResistance;
        this.directMappedStates[index] = blockState;
    }

    @Unique
    private int getCacheHitIndex(long posLong) {
        //No need to check for the MIN_VALUE sentinel here, since performRayCast prevents MIN_VALUE / the equivalent
        // block position from reaching this method as parameter.
        int index = posToCacheIndex(posLong);
        return this.directMappedTags[index] == posLong ? index : -1;
    }

    @SuppressWarnings("unchecked")
    @Redirect(
            method = "calculateExplodedPositions",
            at = @At(value = "NEW", target = "()Ljava/util/HashSet;", remap = false)
    )
    public HashSet<BlockPos> skipNewHashSet() {
        return (HashSet<BlockPos>) DUMMY_HASHSET;
    }

    @ModifyConstant(
            method = "calculateExplodedPositions",
            constant = @Constant(intValue = 16, ordinal = 1)
    )
    public int skipLoop(int prevValue) {
        return 0;
    }

    @Redirect(
            method = "explode", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I")
    )
    private int getExplodedPositionCount(List<?> instance) {
        return this.explodedAirPositions != null ? this.explodedAirPositions.size() + instance.size() : instance.size();
    }

    /**
     * @author JellySquid
     */
    @Inject(method = "calculateExplodedPositions",
            at = @At(value = "RETURN"))
    public void collectBlocks(CallbackInfoReturnable<List<BlockPos>> cir) {
        this.initCaches();
        // Using integer encoding for the block positions provides a massive speedup and prevents us from needing to
        // allocate a block position for every step we make along each ray, eliminating essentially all the memory
        // allocations of this function. The overhead of packing block positions into integer format is negligible
        // compared to a memory allocation and associated overhead of hashing real objects in a set.
        final LongOpenHashSet touched = new LongOpenHashSet(0);

        final RandomSource random = this.level.getRandom();

        // Explosions work by casting many rays through the world from the origin of the explosion
        for (int rayX = 0; rayX < 16; ++rayX) {
            boolean xPlane = rayX == 0 || rayX == 15;
            double vecX = (((float) rayX / 15.0F) * 2.0F) - 1.0F;

            for (int rayY = 0; rayY < 16; ++rayY) {
                boolean yPlane = rayY == 0 || rayY == 15;
                double vecY = (((float) rayY / 15.0F) * 2.0F) - 1.0F;

                // We only fire rays from the surface of our origin volume
                // This adjustment saves 2744 [14^3] iterations without changing order
                // While not very expensive, this is noticeable upon close inspection of profiles
                final int zIncrement = xPlane || yPlane ? 1: 15;
                for (int rayZ = 0; rayZ < 16; rayZ += zIncrement) {
                    double vecZ = (((float) rayZ / 15.0F) * 2.0F) - 1.0F;
                    this.performRayCast(random, vecX, vecY, vecZ, touched);
                }
            }
        }

        // We can now iterate back over the set of positions we modified and re-build BlockPos objects from them
        // This will only allocate as many objects as there are in the set, where otherwise we would allocate them
        // each step of every ray.
        List<BlockPos> affectedBlocks = cir.getReturnValue();
        LongIterator it = touched.iterator();

        while (it.hasNext()) {
            affectedBlocks.add(BlockPos.of(it.nextLong()));
        }
    }

    @Unique
    private void performRayCast(RandomSource random, double vecX, double vecY, double vecZ, LongOpenHashSet touched) {
        double dist = Math.sqrt((vecX * vecX) + (vecY * vecY) + (vecZ * vecZ));

        double normX = (vecX / dist) * 0.3D;
        double normY = (vecY / dist) * 0.3D;
        double normZ = (vecZ / dist) * 0.3D;

        float strength = this.radius * (0.7F + (random.nextFloat() * 0.6F));

        double stepX = this.center.x();
        double stepY = this.center.y();
        double stepZ = this.center.z();

        int prevX = Integer.MIN_VALUE;
        int prevY = Integer.MIN_VALUE;
        int prevZ = Integer.MIN_VALUE;

        float prevResistance = 0.0F;

        int boundMinY = this.bottomY;
        int boundMaxY = this.topY;

        // Step through the ray until it is finally stopped
        while (strength > 0.0F) {
            int blockX = Mth.floor(stepX);
            int blockY = Mth.floor(stepY);
            int blockZ = Mth.floor(stepZ);

            float resistance;

            // Check whether we have actually moved into a new block this step. Due to how rays are stepped through,
            // over-sampling of the same block positions will occur. Changing this behaviour would introduce differences in
            // aliasing and sampling, which is unacceptable for our purposes. As a band-aid, we can simply re-use the
            // previous result and get a decent boost.
            if (prevX != blockX || prevY != blockY || prevZ != blockZ) {
                if (blockY < boundMinY || blockY > boundMaxY || blockX < -30000000 || blockZ < -30000000 || blockX >= 30000000 || blockZ >= 30000000) {
                    return;
                }
                //The coordinates are within the world bounds, so we can safely traverse the block
                resistance = this.traverseBlock(strength, blockX, blockY, blockZ, touched);

                prevX = blockX;
                prevY = blockY;
                prevZ = blockZ;

                prevResistance = resistance;
            } else {
                resistance = prevResistance;
            }

            strength -= resistance;
            // Apply a constant fall-off
            strength -= 0.22500001F;

            stepX += normX;
            stepY += normY;
            stepZ += normZ;
        }
    }

    /**
     * Called for every step made by a ray being cast by an explosion.
     *
     * @param strength The strength of the ray during this step
     * @param blockX   The x-coordinate of the block the ray is inside of
     * @param blockY   The y-coordinate of the block the ray is inside of
     * @param blockZ   The z-coordinate of the block the ray is inside of
     * @return The resistance of the current block space to the ray
     */
    @Unique
    private float traverseBlock(float strength, int blockX, int blockY, int blockZ, LongOpenHashSet touched) {
        long posLong = BlockPos.asLong(blockX, blockY, blockZ);

        // Use cached blast resistance and block state info
        int index = this.getCacheHitIndex(posLong);
        if (index >= 0) {
            float cachedResistance = this.directMappedResistances[index];
            this.tryMarkBlockForDestruction(strength, cachedResistance, posLong, this.directMappedStates[index], blockX, blockY, blockZ, touched);
            return cachedResistance;
        }

        BlockPos pos = this.cachedPos.set(blockX, blockY, blockZ);

        int chunkX = Pos.ChunkCoord.fromBlockCoord(blockX);
        int chunkZ = Pos.ChunkCoord.fromBlockCoord(blockZ);

        // Avoid calling into the chunk manager as much as possible through managing chunks locally
        if (this.prevChunkX != chunkX || this.prevChunkZ != chunkZ) {
            this.prevChunk = this.level.getChunk(chunkX, chunkZ);

            this.prevChunkX = chunkX;
            this.prevChunkZ = chunkZ;
        }

        final ChunkAccess chunk = this.prevChunk;

        BlockState blockState = Blocks.AIR.defaultBlockState();
        float totalResistance = 0.0F;
        Optional<Float> blastResistance;

        labelGetBlastResistance:
        {
            // If the chunk is missing or out of bounds, assume that it is air
            if (chunk != null) {
                // We operate directly on chunk sections to avoid interacting with BlockPos and to squeeze out as much
                // performance as possible here
                LevelChunkSection section = chunk.getSections()[SectionPos.blockToSectionCoord(blockY) - SectionPos.blockToSectionCoord(this.bottomY)];

                // If the section doesn't exist or it's empty, assume that the block is air
                if (section != null && !section.hasOnlyAir()) {
                    // Retrieve the block state from the chunk section directly to avoid associated overhead
                    blockState = section.getBlockState(blockX & 15, blockY & 15, blockZ & 15);

                    // If the block state is air, it cannot have fluid or any kind of resistance, so just leave
                    if (blockState.getBlock() != Blocks.AIR) {
                        // Rather than query the fluid state from the container as we just did with the block state, we can
                        // simply ask the block state we retrieved what fluid it has. This is exactly what the call would
                        // do anyway, except that it would have to retrieve the block state a second time, adding overhead.
                        FluidState fluidState = blockState.getFluidState();

                        // Get the explosion resistance like vanilla
                        blastResistance = this.damageCalculator.getBlockExplosionResistance((Explosion) (Object) this, this.level, pos, blockState, fluidState);
                        break labelGetBlastResistance;
                    }
                }
            }
            blastResistance = this.damageCalculator.getBlockExplosionResistance((Explosion) (Object) this, this.level, pos, Blocks.AIR.defaultBlockState(), Fluids.EMPTY.defaultFluidState());
        }
        // Calculate how much this block will resist an explosion's ray
        if (blastResistance.isPresent()) {
            totalResistance = (blastResistance.get() + 0.3F) * 0.3F;
        }

        // Cache the block state and the resistance for other explosion rays hitting the same position
        this.cacheBlock(posLong, blockState, totalResistance);

        // Check if this ray is still strong enough to break blocks, and if so, add this position to the set
        // of positions to destroy
        this.tryMarkBlockForDestruction(strength, totalResistance, posLong, blockState, blockX, blockY, blockZ, touched);

        return totalResistance;
    }

    @Unique
    private void tryMarkBlockForDestruction(float strength, float totalResistance, long posLong, BlockState blockState, int blockX, int blockY, int blockZ, LongOpenHashSet touched) {
        float reducedStrength = strength - totalResistance;
        if (reducedStrength > 0.0F) {
            if (this.skipAirBlocks != 0 && blockState.isAir()) {
                if (this.explodedAirPositions != null) {
                    this.explodedAirPositions.add(posLong);
                }
                return;
            }
            BlockPos pos = this.cachedPos.set(blockX, blockY, blockZ);
            if (this.damageCalculator.shouldBlockExplode((Explosion) (Object) this, this.level, pos, blockState, reducedStrength)) {
                touched.add(posLong);
            }
        }
    }
}
