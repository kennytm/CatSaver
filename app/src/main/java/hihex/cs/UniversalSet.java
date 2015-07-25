/*
 * CatSaver
 * Copyright (C) 2015 HiHex Ltd.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package hihex.cs;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * An immutable set that contains all elements.
 *
 * <p>Since a universal set contains everything, it will throw exception when trying to list its content (e.g. calling
 * {@link #size()}). This set should only be used for testing membership i.e. calling {@link #contains(Object)}.</p>
 */
public final class UniversalSet implements Set<Object> {
    private static final UniversalSet INSTANCE = new UniversalSet();

    @Override
    public boolean add(final Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(final Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(final Object object) {
        return true;
    }

    @Override
    public boolean containsAll(final Collection<?> collection) {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Iterator<Object> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(final Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(final Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(final T[] array) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    public static <E> Set<E> instance() {
        return (Set<E>) INSTANCE;
    }
}
