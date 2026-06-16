package net.caffeinemc.mods.lithium.common.entity;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceReferenceImmutablePair;
import net.caffeinemc.mods.lithium.common.reflection.ReflectionUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Class for grouping Entity classes and Entity types by some property for use in TypeFilterableList
 * It is intended that an EntityClassGroup acts as if it was immutable, however we cannot predict which subclasses of
 * Entity might appear. Therefore, we evaluate whether a class belongs to the class group when it is first seen.
 * Once a class was evaluated the result of it is cached and cannot be changed.
 *
 * @author 2No2Name
 */
public class EntityClassGroup {

    private static final byte ABSENT_VALUE = (byte) 3;

    public static final EntityClassGroup CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE; //aka entities that will attempt to collide with all other entities when moving

    static {
        String remapped_collidesWith = "canCollideWith";
        CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE = new EntityClassGroup(
                (Class<?> entityClass, Supplier<EntityType<?>> entityType) -> ReflectionUtil.hasMethodOverride(entityClass, Entity.class, true, remapped_collidesWith, Entity.class));

        //sanity check: in case intermediary mappings changed, we fail
        if ((!CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(Minecart.class, EntityTypes.MINECART))) {
            throw new AssertionError();
        }
        if ((!CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(WindCharge.class, EntityTypes.WIND_CHARGE)) || (!CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(BreezeWindCharge.class, EntityTypes.BREEZE_WIND_CHARGE))) {
            throw new AssertionError();
        }
        if ((CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.contains(Shulker.class, EntityTypes.SHULKER))) {
            //should not throw an Error here, because another mod *could* add the method to ShulkerEntity. Warning when this sanity check fails.
            Logger.getLogger("Lithium EntityClassGroup").warning("Either Lithium EntityClassGroup is broken or something else gave Shulkers the minecart-like collision behavior.");
        }
        CUSTOM_COLLIDE_LIKE_MINECART_BOAT_WINDCHARGE.clear();
    }

    private final BiPredicate<Class<?>, Supplier<EntityType<?>>> classAndTypeFitEvaluator;
    private volatile Reference2ByteOpenHashMap<Class<?>> class2GroupContains; // 0: Not contained (decision based on class only), 1: Contained (decision based on class only), 2: Check containedClassAndTypePairs (decision based on entity type)
    private volatile @Nullable ObjectOpenHashSet<ReferenceReferenceImmutablePair<Class<?>, EntityType<?>>> containedClassAndTypePairs; //only used if decision is based on entity type

    public EntityClassGroup(BiPredicate<Class<?>, Supplier<EntityType<?>>> classAndTypeFitEvaluator) {
        this.classAndTypeFitEvaluator = classAndTypeFitEvaluator;
        this.clear();
    }

    public void clear() {
        this.class2GroupContains = new Reference2ByteOpenHashMap<>();
        this.class2GroupContains.defaultReturnValue(ABSENT_VALUE);
        this.containedClassAndTypePairs = null;
    }

    public boolean contains(Entity entity) {
        return this.contains(entity.getClass(), entity.getType());
    }

    public boolean contains(Class<?> entityClass, EntityType<?> entityType) {
        byte contains = this.class2GroupContains.getByte(entityClass);
        if (contains < 2) {
            return contains == 1;
        }
        return checkDetailedContains(entityClass, entityType, contains);
    }

    private boolean checkDetailedContains(Class<?> entityClass, EntityType<?> entityType, byte contains) {
        if (contains == ABSENT_VALUE) {
            return this.testAndAddClass(entityClass, entityType);
        } else {
            var containedPairs = this.containedClassAndTypePairs;
            return containedPairs != null && containedPairs.contains(ReferenceReferenceImmutablePair.of(entityClass, entityType));
        }
    }

    boolean testAndAddClass(Class<?> entityClass, EntityType<?> entityType) {
        boolean contains;
        //synchronizing here to avoid multiple threads replacing the map at the same time, and therefore possibly undoing progress
        //it could also be fixed by using an AtomicReference's CAS, but we are writing very rarely (less than 150 times for the total game runtime in vanilla)
        synchronized (this) {
            //test the same condition again after synchronizing, as the collection might have been updated while this thread blocked
            if (this.class2GroupContains.containsKey(entityClass)) {
                return this.contains(entityClass, entityType);
            }

            //construct new map instead of updating the old map to avoid thread safety problems
            //the map is not modified after publication
            Reference2ByteOpenHashMap<Class<?>> newMap = this.class2GroupContains.clone();
            boolean[] accessedEntityType = new boolean[1];
            Supplier<EntityType<?>> entityTypeSupplier = () -> {
                accessedEntityType[0] = true;
                return entityType;
            };
            contains = this.classAndTypeFitEvaluator.test(entityClass, entityTypeSupplier);
            byte containsInfo = contains ? (byte) 1 : (byte) 0;
            if (accessedEntityType[0]) {
                containsInfo = 2; //2: The class group decision is based on both class and type

                ObjectOpenHashSet<ReferenceReferenceImmutablePair<Class<?>, EntityType<?>>> newPairSet = this.containedClassAndTypePairs;
                newPairSet = newPairSet == null ? new ObjectOpenHashSet<>() : newPairSet.clone();
                if (contains) {
                    newPairSet.add(ReferenceReferenceImmutablePair.of(entityClass, entityType));
                    //publish the new set in a volatile field, so that all threads reading after this write can also see all changes to the map done beforehand
                    //since modification on happens in the synchronized block, progress won't be lost
                    this.containedClassAndTypePairs = newPairSet;
                }
            }

            byte previousContainsInfo = newMap.put(entityClass, containsInfo);
            if (previousContainsInfo != ABSENT_VALUE && previousContainsInfo != containsInfo) {
                throw new IllegalStateException("Entity class group class fit evaluator must be a pure function! Class fit for " + entityClass + " changed from " + previousContainsInfo + " to " + containsInfo + " when evaluating for " + entityType + "!");
            }

            //publish the new map in a volatile field, so that all threads reading after this write can also see all changes to the map done beforehand
            this.class2GroupContains = newMap;
        }
        return contains;
    }

    public static class NoDragonClassGroup extends EntityClassGroup {
        public static final NoDragonClassGroup BOAT_SHULKER_LIKE_COLLISION; //aka entities that other entities will do block-like collisions with when moving

        static {
            String remapped_canBeCollidedWith = "canBeCollidedWith";
            BOAT_SHULKER_LIKE_COLLISION = new NoDragonClassGroup(
                    (Class<?> entityClass, Supplier<EntityType<?>> entityType) -> ReflectionUtil.hasMethodOverride(entityClass, Entity.class, true, remapped_canBeCollidedWith, Entity.class));

            if ((!BOAT_SHULKER_LIKE_COLLISION.contains(Shulker.class, EntityTypes.SHULKER))) {
                throw new AssertionError();
            }
            BOAT_SHULKER_LIKE_COLLISION.clear();
        }

        public NoDragonClassGroup(BiPredicate<Class<?>, Supplier<EntityType<?>>> classAndTypeFitEvaluator) {
            super(classAndTypeFitEvaluator);
            if (classAndTypeFitEvaluator.test(EnderDragon.class, () -> {
                throw new IllegalArgumentException("EntityClassGroup.NoDragonClassGroup cannot be initialized: Must exclude EnderDragonEntity without checking entity type!");
            })) {
                throw new IllegalArgumentException("EntityClassGroup.NoDragonClassGroup cannot be initialized: Must exclude EnderDragonEntity!");
            }
        }
    }
}