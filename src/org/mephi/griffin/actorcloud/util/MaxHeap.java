/*
 * Copyright 2015 Griffin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mephi.griffin.actorcloud.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 *
 * @author Griffin
 * @param <E>
 */
public class MaxHeap<E> extends ArrayList<E> implements List<E>, Cloneable, java.io.Serializable {
	Comparator<E> comparator;
	
	public MaxHeap() {
		super();
		comparator = null;
	}
	
	public MaxHeap(Collection<? extends E> c) {
		super(c);
		comparator = null;
		buildHeap();
	}
	
	public MaxHeap(int initialCapacity) {
		super(initialCapacity);
		comparator = null;
	}
	
	public MaxHeap(Comparator<E> comparator) {
		super();
		this.comparator = comparator;
	}
	
	public MaxHeap(Collection<? extends E> c, Comparator<E> comparator) {
		super(c);
		this.comparator = comparator;
		buildHeap();
	}
	
	public MaxHeap(int initialCapacity, Comparator<E> comparator) {
		super(initialCapacity);
		this.comparator = comparator;
	}
	
	private int parent(int index) {
		return (index - 1) / 2;
	}
	
	private int left(int index) {
		return index * 2 + 1;
	}
	
	private int right(int index) {
		return index * 2 + 2;
	}
	
	private void swap(int index1, int index2) {
		E e = super.get(index1);
		super.set(index1, get(index2));
		super.set(index2, e);
	}
	
	private void heapify(int index) {
		int min = index;
		if(comparator == null) {
			if(left(index) < size() && ((Comparable) super.get(left(index))).compareTo(super.get(min)) > 0) min = left(index);
			if(right(index) < size() && ((Comparable) super.get(right(index))).compareTo(super.get(min)) > 0) min = right(index);
		}
		else {
			if(left(index) < size() && comparator.compare(super.get(left(index)), super.get(min)) > 0) min = left(index);
			if(right(index) < size() && comparator.compare(super.get(right(index)), super.get(min)) > 0) min = right(index);
		}
		if(min != index) {
			swap(min, index);
			heapify(min);
		}
	}
	
	public final void buildHeap() {
		for(int index = size() / 2 - 1; index >= 0; index--) heapify(index);
	}
	
	private void fixLeaf(int index) {
		if(comparator == null) {
			while(index > 0 && ((Comparable) super.get(parent(index))).compareTo(super.get(index)) < 0) {
				swap(index, parent(index));
				index = parent(index);
			}
		}
		else {
			while(index > 0 && comparator.compare(super.get(parent(index)), super.get(index)) < 0) {
				swap(index, parent(index));
				index = parent(index);
			}
		}
	}
	
	public E getMax() {
		return super.get(0);
	}
	
	public E removeMax() {
		E res = super.get(0);
		super.set(0, super.remove(super.size() - 1));
		heapify(0);
		return res;
	}
	
	@Override
	public boolean add(E e) {
		super.add(e);
		fixLeaf(super.size() - 1);
		return true;
	}
	
	@Override
	public void add(int index, E e) {
		super.add(index, e);
		buildHeap();
	}
	
	@Override
	public boolean addAll(Collection<? extends E> c) {
		if(super.addAll(c)) {
			buildHeap();
			return true;
		}
		else return false;
	}
	
	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if(super.addAll(index, c)) {
			buildHeap();
			return true;
		}
		else return false;
	}
	
	@Override
	public void forEach(Consumer<? super E> action) {
		try {
			super.forEach(action);
		}
		finally {
			buildHeap();
		}
	}
	
	@Override
	public Iterator<E> iterator() {
		return new Itr();
	}
	
	@Override
	public ListIterator<E> listIterator() {
		return new ListItr(0);
	}
	
	@Override
	public ListIterator<E> listIterator(int index) {
        if (index < 0 || index > super.size()) throw new IndexOutOfBoundsException("Index: " + index);
        return new ListItr(index);
	}
	
	@Override
	public E remove(int index) {
		E res = super.remove(index);
		buildHeap();
		return res;
	}
	
	@Override
	public boolean remove(Object o) {
		if(super.remove(o)) {
			buildHeap();
			return true;
		}
		else return false;
	}
	
	@Override
	public boolean removeAll(Collection <?> c) {
		if(super.removeAll(c)) {
			buildHeap();
			return true;
		}
		else return false;
	}
	
	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		if(super.removeIf(filter)) {
			buildHeap();
			return true;
		}
		else return false;
	}
	
	@Override
	public void replaceAll(UnaryOperator<E> operator) {
		super.replaceAll(operator);
		buildHeap();
	}
	
	@Override
	public boolean retainAll(Collection<?> c) {
		if(super.retainAll(c)) {
			buildHeap();
			return true;
		}
		else return false;
	}
	
	@Override
	public void sort(Comparator<? super E> c) {
		throw new UnsupportedOperationException("Not supported");
	}
	
	@Override
	public Spliterator<E> spliterator() {
		throw new UnsupportedOperationException("Not supported");
	}
	
	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException("Not supported");
	}
	
	@Override
	public E set(int index, E e) {
		if(comparator == null) 
			if(((Comparable) e).compareTo(super.get(index)) > 0) throw new IllegalArgumentException("New value must be less or equal than current");
		else
			if(comparator.compare(e, super.get(index)) > 0) throw new IllegalArgumentException("New value must be less or equal than current");
		E res = super.set(index, e);
		fixLeaf(index);
		return res;
	}
	
	private class Itr implements Iterator<E> {
		int cursor;
		int lastRet = -1;
		int expectedModCount = modCount;
		
		@Override
		public boolean hasNext() {
			return cursor != MaxHeap.this.size();
		}
		
		@Override
		public E next() {
			checkForComodification();
			int i = cursor;
			if (i >= size()) throw new NoSuchElementException();
			cursor = i + 1;
			return MaxHeap.this.get(lastRet = i);
		}
		
		@Override
		public void remove() {
			if (lastRet < 0) throw new IllegalStateException();
			checkForComodification();
			try {
				MaxHeap.this.remove(lastRet);
				cursor = lastRet;
				lastRet = -1;
				expectedModCount = modCount;
            }
			catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
        }
		
		final void checkForComodification() {
			if (modCount != expectedModCount) throw new ConcurrentModificationException();
		}
	}
	
	private class ListItr extends Itr implements ListIterator<E> {
		
		ListItr(int index) {
			super();
			cursor = index;
		}
		
		@Override
		public boolean hasPrevious() {
			return cursor != 0;
		}
		
		@Override
		public int nextIndex() {
			return cursor;
		}
		
		@Override
		public int previousIndex() {
			return cursor - 1;
		}
		
		@Override
		public E previous() {
			checkForComodification();
			int i = cursor - 1;
			if (i < 0) throw new NoSuchElementException();
			cursor = i;
			return MaxHeap.this.get(lastRet = i);
		}
		
		@Override
		public void set(E e) {
			if (lastRet < 0) throw new IllegalStateException();
			checkForComodification();
			try {
				MaxHeap.this.set(lastRet, e);
            }
			catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add(E e) {
			checkForComodification();
			try {
				int i = cursor;
				MaxHeap.this.add(i, e);
				cursor = i + 1;
				lastRet = -1;
				expectedModCount = modCount;
			}
			catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
    }
}
