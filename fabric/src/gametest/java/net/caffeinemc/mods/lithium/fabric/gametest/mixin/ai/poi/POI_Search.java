package net.caffeinemc.mods.lithium.fabric.gametest.mixin.ai.poi;


import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.mods.lithium.common.world.interests.PoiOrdering;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class POI_Search implements CustomTestMethodInvoker {

    @GameTest(manualOnly = true)
    public void test(GameTestHelper context, BlockPos center, RandomSource randomSource) {
        ServerLevel serverLevel = context.getLevel();
        PoiManager poiManager = serverLevel.getPoiManager();
        Predicate<Holder<PoiType>> predicate = holder -> holder.is(PoiTypes.NETHER_PORTAL);

        long countInRange = poiManager.getCountInRange(predicate, center, 128, PoiManager.Occupancy.ANY);

        List<BlockPos> inSquarePositions = poiManager.getInSquare(predicate, center, 128, PoiManager.Occupancy.ANY).map(PoiRecord::getPos).collect(Collectors.toList());
        PoiOrdering.InSquare.INSTANCE.checkOrderOrThrow(center, poiManager, inSquarePositions);

        List<BlockPos> inRangePositions = poiManager.getInRange(predicate, center, 128, PoiManager.Occupancy.ANY).map(PoiRecord::getPos).collect(Collectors.toList());
        PoiOrdering.InSquare.INSTANCE.checkOrderOrThrow(center, poiManager, inRangePositions);

        List<BlockPos> inChunkPositions = poiManager.getInChunk(predicate, ChunkPos.containing(center), PoiManager.Occupancy.ANY).map(PoiRecord::getPos).collect(Collectors.toList());
        PoiOrdering.InChunk.INSTANCE.checkOrderOrThrow(center, poiManager, inChunkPositions);

        List<BlockPos> allPositions = poiManager.findAll(predicate, blockPos1 -> true, center, 128, PoiManager.Occupancy.ANY).collect(Collectors.toList());
        PoiOrdering.InSquare.INSTANCE.checkOrderOrThrow(center, poiManager, allPositions);

        List<BlockPos> allWithTypePositions = poiManager.findAllWithType(predicate, blockPos1 -> true, center, 128, PoiManager.Occupancy.ANY).map(Pair::getSecond).collect(Collectors.toList());
        PoiOrdering.InSquare.INSTANCE.checkOrderOrThrow(center, poiManager, allWithTypePositions);

        List<BlockPos> allClosestFirstWithTypePositions = poiManager.findAllClosestFirstWithType(predicate, blockPos1 -> true, center, 128, PoiManager.Occupancy.ANY).map(Pair::getSecond).collect(Collectors.toList());
        PoiOrdering.L2ThenInSquare.INSTANCE.checkOrderOrThrow(center, poiManager, allClosestFirstWithTypePositions);

        boolean fromOverworld = randomSource.nextBoolean();
        WorldBorder worldBorder = serverLevel.getWorldBorder();
        Optional<BlockPos> closestPortalPosition = serverLevel.getPortalForcer().findClosestPortalPosition(center, fromOverworld, worldBorder);
        int radius = fromOverworld ? 16 : 128;
        List<BlockPos> closestPortalPositions =
                poiManager.getInSquare(holder -> holder.is(PoiTypes.NETHER_PORTAL), center, radius, PoiManager.Occupancy.ANY)
                        .map(PoiRecord::getPos)
                        .filter(worldBorder::isWithinBounds)
                        .filter(blockPosx -> serverLevel.getBlockState(blockPosx).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
                        .sorted(Comparator.comparingDouble((BlockPos blockPos2) -> blockPos2.distSqr(center)).thenComparingInt(BlockPos::getY))
                        .toList();
        PoiOrdering.L2ThenMinYThenInSquare.INSTANCE.checkOrderOrThrow(center, poiManager, closestPortalPositions);
        if (closestPortalPositions.isEmpty() != closestPortalPosition.isEmpty()) {
            error("findClosestPortalPosition() emptiness does not match sorted and filtered getInSquare() emptiness");
        } else if (closestPortalPosition.isPresent()) {
            BlockPos closestPos = closestPortalPosition.get();
            if (!closestPortalPositions.getFirst().equals(closestPos)) {
                error("findClosestPortalPosition() result is not the first of sorted and filtered getInSquare()");
            }
        }


        Optional<BlockPos> firstOfAll = poiManager.find(predicate, blockPos -> true, center, 128, PoiManager.Occupancy.ANY);
        if (firstOfAll.isEmpty() != inRangePositions.isEmpty()) {
            error("find() result presence does not match getInRange() emptiness");
        } else if (firstOfAll.isPresent()) {
            BlockPos closestPos = firstOfAll.get();
            if (!inRangePositions.getFirst().equals(closestPos)) {
                error("find() result is not the first of getInRange(): " + closestPos);
            }
        }

        Optional<BlockPos> findClosest = poiManager.findClosest(predicate, blockPos -> true, center, 128, PoiManager.Occupancy.ANY);
        if (findClosest.isEmpty() != allClosestFirstWithTypePositions.isEmpty()) {
            error("findClosest() result presence does not match findAllClosestFirstWithType() emptiness");
        } else if (findClosest.isPresent()) {
            BlockPos closestPos = findClosest.get();
            if (!allClosestFirstWithTypePositions.getFirst().equals(closestPos)) {
                error("findClosest() result is not the first of findAllClosestFirstWithType()");
            }
        }

        Optional<BlockPos> findClosestWithType = poiManager.findClosestWithType(predicate, center, 128, PoiManager.Occupancy.ANY).map(Pair::getSecond);
        if (!findClosestWithType.equals(findClosest)) {
            error("findClosest() result does not equal findClosestWithType()");
        }

        Optional<BlockPos> findClosest2 = poiManager.findClosest(predicate, center, 128, PoiManager.Occupancy.ANY);
        if (!findClosest2.equals(findClosest)) {
            error("findClosest() result does not equal findClosest()");
        }

        Optional<BlockPos> getRandom = poiManager.getRandom(predicate, blockPos -> true, PoiManager.Occupancy.ANY, center, 128, randomSource);
        if (getRandom.isEmpty() != inRangePositions.isEmpty()) {
            error("getRandom() emptiness does not match getInRange() emptiness");
        }
        if (!inRangePositions.isEmpty() && !inRangePositions.contains(getRandom.get())) {
            error("getRandom() result is not contained in getInRange() results");
        }


        // Print to file if it does not exist yet, otherwise compare with existing file.
        File outputFile = new File("poi_search_output/" + context.getLevel().getSeed() + "_" + center.getX() + "_" + center.getY() + "_" + center.getZ() + ".txt");
        File newOutputFile = outputFile.exists() ? new File("poi_search_output/" + context.getLevel().getSeed() + "_" + center.getX() + "_" + center.getY() + "_" + center.getZ() + "_new.txt") : outputFile;

        StringBuilder sb = new StringBuilder();
        sb.append("countInRange:").append(countInRange).append("\n");

        sb.append("getInSquare size:").append(inSquarePositions.size()).append("\n");
        sb.append("getInRange size:").append(inRangePositions.size()).append("\n");
        sb.append("getInChunk size:").append(inChunkPositions.size()).append("\n");
        sb.append("findAll size:").append(allPositions.size()).append("\n");
        sb.append("findAllWithType size:").append(allWithTypePositions.size()).append("\n");
        sb.append("findAllClosestFirstWithType size:").append(allClosestFirstWithTypePositions.size()).append("\n");
        sb.append("closestPortalPosition:").append(closestPortalPosition.map(Vec3i::toString).orElse("empty")).append("\n");
        sb.append("find:").append(firstOfAll.map(Vec3i::toString).orElse("empty")).append("\n");
        sb.append("findClosest:").append(findClosest.map(Vec3i::toString).orElse("empty")).append("\n");
        sb.append("findClosestWithType:").append(findClosestWithType.map(Vec3i::toString).orElse("empty")).append("\n");
        sb.append("findClosest2:").append(findClosest2.map(Vec3i::toString).orElse("empty")).append("\n");

        sb.append("getInSquare: ");
        sb.append(Arrays.toString(inSquarePositions.toArray()));
        sb.append("\n");
        sb.append("getInRange: ");
        sb.append(Arrays.toString(inRangePositions.toArray()));
        sb.append("\n");
        sb.append("getInChunk: ");
        sb.append(Arrays.toString(inChunkPositions.toArray()));
        sb.append("\n");
        sb.append("findAll: ");
        sb.append(Arrays.toString(allPositions.toArray()));
        sb.append("\n");
        sb.append("findAllWithType: ");
        sb.append(Arrays.toString(allWithTypePositions.toArray()));
        sb.append("\n");
        sb.append("findAllClosestFirstWithType: ");
        sb.append(Arrays.toString(allClosestFirstWithTypePositions.toArray()));
        sb.append("\n");

        if (!outputFile.exists()) {
            outputFile.getParentFile().mkdirs();
            try (java.io.FileWriter writer = new java.io.FileWriter(newOutputFile)) {
                writer.write(sb.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            String existingContent;
            try (java.io.FileReader reader = new java.io.FileReader(outputFile);
                 java.io.BufferedReader br = new java.io.BufferedReader(reader)) {
                StringBuilder existingSb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    existingSb.append(line).append("\n");
                }
                existingContent = existingSb.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (!existingContent.equals(sb.toString())) {
                try (java.io.FileWriter writer = new java.io.FileWriter(newOutputFile)) {
                    writer.write(sb.toString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                error("POI search results differ from expected output. See " + newOutputFile.getAbsolutePath());
            }
        }

        context.succeed();
    }

    private static void error(String message) {
        throw new AssertionError(message);
//        System.err.println("ERROR: " + message);
    }


    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        ServerLevel level = context.getLevel();
        RandomSource random = RandomSource.create(level.getSeed()); //Hardcode seed here to be able to check equality with vanilla.

        int iterations = 5;
        for (int i = 0; i < iterations; i++) {

            int x = random.nextInt(60000000) - 30000000;
            int z = random.nextInt(60000000) - 30000000;
            int y = random.nextInt(level.getHeight()) + level.getMinY();

            int chosen = 20 - i;
            double randomPct = 1.0 / Math.pow(2, chosen);

            int radius = 130;

            ObjectOpenHashSet<BlockPos> positions1 = new ObjectOpenHashSet<>();
            ObjectOpenHashSet<BlockPos> positions2 = new ObjectOpenHashSet<>();
            System.out.println("Placing POIs for iteration " + (i + 1) + "/" + (iterations) + " : around (" + x + ", " + y + ", " + z + ") with randomPct=" + randomPct + " and radius=" + radius);
            for (BlockPos blockPos : BlockPos.betweenClosed(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)) {
                BlockState block = level.getBlockState(blockPos);
                if (!block.isAir()) {
                    throw new IllegalStateException("Test may be overwriting a huge number of blocks, run test in a fresh void world instead!");
                } else {
                    if (random.nextFloat() < randomPct && level.isInWorldBounds(blockPos)) {
                        positions1.add(blockPos.immutable());
                    }
                }
            }

            for (BlockPos blockPos : positions1) {
                level.setBlock(blockPos, Blocks.NETHER_PORTAL.defaultBlockState(), 0);
            }

            for (BlockPos blockPos : BlockPos.betweenClosed(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)) {
                if (level.getBlockState(blockPos).is(Blocks.NETHER_PORTAL) && level.isInWorldBounds(blockPos)) {
                    positions2.add(blockPos.immutable());
                }
            }

            if (!positions1.equals(positions2)) {
                throw new IllegalStateException(
                        "Mismatch between placed and actually placed POIs!"
                );
            }
            long poiCount = positions1.size();

            System.out.println("Placed " + poiCount + " POIs, running test method...");

            method.invoke(this, context, new BlockPos(x, y, z), random);
        }
    }
}
