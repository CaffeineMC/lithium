package entity_class_group;

import net.caffeinemc.mods.lithium.common.entity.EntityClassGroup;
import net.minecraft.world.entity.EntityTypes;
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
                return entityType.get() == EntityTypes.ACACIA_BOAT || entityType.get() == EntityTypes.BIRCH_BOAT;
            }
            return false;
        });

        assertTrue(classGroup.contains(Sheep.class, EntityTypes.SHEEP));
        assertTrue(classGroup.contains(Boat.class, EntityTypes.ACACIA_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityTypes.OAK_BOAT));
        assertTrue(classGroup.contains(Boat.class, EntityTypes.BIRCH_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityTypes.JUNGLE_BOAT));
        assertFalse(classGroup.contains(Skeleton.class, EntityTypes.SKELETON));
        assertTrue(classGroup.contains(Pig.class, EntityTypes.PIG));

        //Repeat check since populating the map is different to retrieving from the map
        assertTrue(classGroup.contains(Sheep.class, EntityTypes.SHEEP));
        assertTrue(classGroup.contains(Boat.class, EntityTypes.ACACIA_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityTypes.OAK_BOAT));
        assertTrue(classGroup.contains(Boat.class, EntityTypes.BIRCH_BOAT));
        assertFalse(classGroup.contains(Boat.class, EntityTypes.JUNGLE_BOAT));
        assertFalse(classGroup.contains(Skeleton.class, EntityTypes.SKELETON));
        assertTrue(classGroup.contains(Pig.class, EntityTypes.PIG));
    }
}
