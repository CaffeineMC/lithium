package net.caffeinemc.mods.lithium.common.util.collections;

import java.util.AbstractList;

public class DummyList<T> extends AbstractList<T> {
    @Override
    public T get(int index) {
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public int size() {
        return 0;
    }
}
