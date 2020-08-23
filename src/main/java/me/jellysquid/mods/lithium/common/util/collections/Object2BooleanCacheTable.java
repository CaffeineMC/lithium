package me.jellysquid.mods.lithium.common.util.collections;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.math.MathHelper;

import java.util.function.Predicate;
import me.jellysquid.mods.lithium.common.util.collections.Object2BooleanCacheTable.Node;

/**
 * A lossy hashtable implementation that stores a mapping between an object and a boolean.
 *
 * Any hash collisions will result in an overwrite: this is safe because the correct value can always be recomputed,
 * given that the given operator is deterministic.
 *
 * This implementation is safe to use from multiple threads
 */
public final class Object2BooleanCacheTable<T> {
    private final int capacity;
    private final int mask;

    private final Node<T>[] nodes;

    private final Predicate<T> operator;

    @SuppressWarnings("unchecked")
    public Object2BooleanCacheTable(int capacity, Predicate<T> operator) {
        this.capacity = MathHelper.smallestEncompassingPowerOfTwo(capacity);
        this.mask = this.capacity - 1;

        this.nodes = (Node<T>[]) new Node[this.capacity];

        this.operator = operator;
    }

    private static <T> int hash(T key) {
        return HashCommon.mix(key.hashCode());
    }

    public boolean get(T key) {
        final int idx = hash(key) & this.mask;

        final Node<T> node = this.nodes[idx];
        if (node != null && key.equals(node.key)) {
            return node.value;
        }

        final boolean test = this.operator.test(key);
        this.nodes[idx] = new Node<>(key, test);

        return test;
    }

    static class Node<T> {
        final T key;
        final boolean value;

        Node(T key, boolean value) {
            this.key = key;
            this.value = value;
        }
    }
}
