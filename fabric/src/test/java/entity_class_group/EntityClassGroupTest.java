package entity_class_group;

import net.caffeinemc.mods.lithium.common.entity.EntityClassGroup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.vehicle.boat.Boat;
import org.junit.jupiter.api.Test;
import util.TestBootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityClassGroupTest extends TestBootstrap {

    @Test
    public void testEntityTypeDependentClassGroups() {
        var classGroup = new EntityClassGroup((aClass, entityType) -> {
            if (aClass == Sheep.class || aClass == Pig.class) {
                return true;
            }
            if (aClass == Boat.class) {
                return entityType.get() == EntityType.ACACIA_BOAT || entityType.get() == EntityType.BIRCH_BOAT;
            }
            return false;
        });

        assertTrue(classGroup.contains(Sheep.class, EntityType.SHEEP));
        assertTrue(classGroup.contains(Boat.class, EntityType.ACACIA_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityType.OAK_BOAT));
        assertTrue(classGroup.contains(Boat.class, EntityType.BIRCH_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityType.JUNGLE_BOAT));
        assertFalse(classGroup.contains(Skeleton.class, EntityType.SKELETON));
        assertTrue(classGroup.contains(Pig.class, EntityType.PIG));

        //Repeat check since populating the map is different to retrieving from the map
        assertTrue(classGroup.contains(Sheep.class, EntityType.SHEEP));
        assertTrue(classGroup.contains(Boat.class, EntityType.ACACIA_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityType.OAK_BOAT));
        assertTrue(classGroup.contains(Boat.class, EntityType.BIRCH_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityType.JUNGLE_BOAT));
        assertFalse(classGroup.contains(Skeleton.class, EntityType.SKELETON));
        assertTrue(classGroup.contains(Pig.class, EntityType.PIG));
    }
}
