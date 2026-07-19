package net.caffeinemc.mods.lithium.common.entity;

import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceReferenceImmutablePair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

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

    private final BiPredicate<Class<?>, Supplier<EntityType<?>>> classAndTypeFitEvaluator;
    private volatile Reference2ByteOpenHashMap<Class<?>> class2GroupContains; // 0: Not contained (decision based on class only), 1: Contained (decision based on class only), 2: Check containedClassAndTypePairs (decision based on entity type)
    private volatile @Nullable Object2ByteOpenHashMap<ReferenceReferenceImmutablePair<Class<?>, EntityType<?>>> containedClassAndTypePairs; //only used if decision is based on entity type

    public EntityClassGroup(BiPredicate<Class<?>, Supplier<EntityType<?>>> classAndTypeFitEvaluator) {
        this.classAndTypeFitEvaluator = classAndTypeFitEvaluator;
        this.clear();
    }

    public void clear() {
        Reference2ByteOpenHashMap<Class<?>> newMap = new Reference2ByteOpenHashMap<>();
        newMap.defaultReturnValue(ABSENT_VALUE);
        this.class2GroupContains = newMap;
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
            var map = this.containedClassAndTypePairs;
            if (map != null) {
                contains = map.getByte(ReferenceReferenceImmutablePair.of(entityClass, entityType));
                if (contains != ABSENT_VALUE) {
                    return contains == 1;
                }
            }
            return this.testAndAddClass(entityClass, entityType);
        }
    }

    boolean testAndAddClass(Class<?> entityClass, EntityType<?> entityType) {
        boolean contains;
        //synchronizing here to avoid multiple threads replacing the map at the same time, and therefore possibly undoing progress
        //it could also be fixed by using an AtomicReference's CAS, but we are writing very rarely (less than 150 times for the total game runtime in vanilla)
        synchronized (this) {
            //test the same condition again after synchronizing, as the collection might have been updated while this thread blocked
            byte containsInfo = this.class2GroupContains.getByte(entityClass);
            var pairMap = this.containedClassAndTypePairs;
            if (containsInfo == 0 || containsInfo == 1) {
                return containsInfo == 1;
            } else if (containsInfo == 2 && pairMap != null) {
                containsInfo = pairMap.getByte(ReferenceReferenceImmutablePair.of(entityClass, entityType));
                if (containsInfo == 0 || containsInfo == 1) {
                    return containsInfo == 1;
                }
            }

            boolean[] accessedEntityType = new boolean[1];
            Supplier<EntityType<?>> entityTypeSupplier = () -> {
                accessedEntityType[0] = true;
                return entityType;
            };
            contains = this.classAndTypeFitEvaluator.test(entityClass, entityTypeSupplier);
            containsInfo = contains ? (byte) 1 : (byte) 0;
            if (accessedEntityType[0]) {
                Object2ByteOpenHashMap<ReferenceReferenceImmutablePair<Class<?>, EntityType<?>>> newPairMap = this.containedClassAndTypePairs;
                newPairMap = newPairMap == null ? new Object2ByteOpenHashMap<>() : newPairMap.clone();
                newPairMap.defaultReturnValue(ABSENT_VALUE);
                newPairMap.put(ReferenceReferenceImmutablePair.of(entityClass, entityType), containsInfo);
                //publish the new set in a volatile field, so that all threads reading after this write can also see all changes to the map done beforehand
                //since modification on happens in the synchronized block, progress won't be lost
                this.containedClassAndTypePairs = newPairMap;

                containsInfo = 2; //2: The class group decision is based on both class and type
            }

            //construct new map instead of updating the old map to avoid thread safety problems
            //the map is not modified after publication
            byte previousContainsInfo = this.class2GroupContains.getByte(entityClass);
            if (previousContainsInfo != ABSENT_VALUE && previousContainsInfo != containsInfo) {
                throw new IllegalStateException("Entity class group class fit evaluator must be a pure function! Class fit for " + entityClass + " changed from " + previousContainsInfo + " to " + containsInfo + " when evaluating for " + entityType + "!");
            }

            if (previousContainsInfo == ABSENT_VALUE) {
                Reference2ByteOpenHashMap<Class<?>> newMap = this.class2GroupContains.clone();
                newMap.defaultReturnValue(ABSENT_VALUE);
                newMap.put(entityClass, containsInfo);
                //publish the new map in a volatile field, so that all threads reading after this write can also see all changes to the map done beforehand
                this.class2GroupContains = newMap;
            }
        }
        return contains;
    }
}